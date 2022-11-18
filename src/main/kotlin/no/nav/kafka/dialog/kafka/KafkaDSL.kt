package no.nav.kafka.dialog

import io.confluent.kafka.schemaregistry.client.CachedSchemaRegistryClient
import io.confluent.kafka.schemaregistry.client.SchemaRegistryClientConfig
import io.confluent.kafka.serializers.KafkaAvroDeserializer
import io.confluent.kafka.serializers.KafkaAvroDeserializerConfig
import java.io.File
import java.time.Duration
import java.util.Properties
import kotlin.Exception
import mu.KotlinLogging
import no.nav.kafka.dialog.metrics.KConsumerMetrics
import no.nav.kafka.dialog.metrics.kCommonMetrics
import org.apache.kafka.clients.CommonClientConfigs
import org.apache.kafka.clients.consumer.ConsumerConfig
import org.apache.kafka.clients.consumer.ConsumerRecords
import org.apache.kafka.clients.consumer.KafkaConsumer
import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.clients.producer.ProducerConfig
import org.apache.kafka.clients.producer.ProducerRecord
import org.apache.kafka.common.TopicPartition
import org.apache.kafka.common.config.SaslConfigs
import org.apache.kafka.common.serialization.ByteArrayDeserializer
import org.apache.kafka.common.serialization.StringDeserializer

private val log = KotlinLogging.logger {}

const val KAFKA_LOCAL = "localhost:9092"

const val EV_kafkaProducerTimeout = "KAFKA_PRODUCERTIMEOUT" // default to 31_000L

// additional kafka environment dependencies - iff kafka security is enabled
const val EV_kafkaSecProc = "KAFKA_SECPROT"
const val EV_kafkaSaslMec = "KAFKA_SASLMEC"

// kafka vault dependencies - iff kafka security is enabled
const val VAULT_kafkaUser = "username"
const val VAULT_kafkaPassword = "password"

// kafka user gcp - vault instance is GCP
const val EV_kafkaUser = "KAFKA_USER"
const val EV_VaultInstance = "VAULT_INSTANCE"

const val POSTFIX_FAIL = "-FAIL"
const val POSTFIX_FIRST = "-FIRST"
const val POSTFIX_LATEST = "-LATEST"

enum class ErrorState() {
    NONE, UNKNOWN_ERROR, AUTHORIZATION, AUTHENTICATION, DESERIALIZATION, TIME_BETWEEN_POLLS,
    SERVICE_UNAVAILABLE, TOPIC_ASSIGNMENT
}

var kErrorState = ErrorState.NONE

class KafkaPosterApplication<K, V>(val settings: List<KafkaToSFPoster.Settings> = listOf(), modifier: ((String, Long) -> String)? = null) {
    val poster = KafkaToSFPoster<K, V>(settings, modifier)

    private val EV_bootstrapWaitTime = "MS_BETWEEN_WORK" // default to 10 minutes
    private val bootstrapWaitTime = AnEnvironment.getEnvOrDefault(EV_bootstrapWaitTime, "600000").toLong()

    private val log = KotlinLogging.logger { }
    fun start() {
        log.info { "Starting app ${env(env_DEPLOY_APP)} with poster settings ${envAsSettings(env_POSTER_SETTINGS)}" }
        enableNAISAPI {
            loop()
        }
        log.info { "Finished" }
    }

    private tailrec fun loop() {
        val stop = ShutdownHook.isActive() || PrestopHook.isActive()
        when {
            stop -> Unit.also { log.info { "Stopped" } }
            !stop -> {
                poster.runWorkSession()
                conditionalWait(bootstrapWaitTime)
                loop()
            }
        }
    }
}

private fun offsetMapsToText(firstOffset: MutableMap<Int, Long>, lastOffset: MutableMap<Int, Long>): String {
    if (firstOffset.isEmpty()) return "NONE"
    return firstOffset.keys.sorted().map {
        "$it:[${firstOffset[it]}-${lastOffset[it]}]"
    }.joinToString(",")
}
val kafkaSchemaRegistryUrl = AnEnvironment.getEnvOrDefault(env_KAFKA_SCHEMA_REGISTRY)

val schemaRegistryClientConfig = mapOf<String, Any>(
    SchemaRegistryClientConfig.BASIC_AUTH_CREDENTIALS_SOURCE to "USER_INFO",
    SchemaRegistryClientConfig.USER_INFO_CONFIG to "${AnEnvironment.getEnvOrDefault(env_KAFKA_SCHEMA_REGISTRY_USER)}:${AnEnvironment.getEnvOrDefault(env_KAFKA_SCHEMA_REGISTRY_PASSWORD)}"
)

val registryClient = CachedSchemaRegistryClient(kafkaSchemaRegistryUrl, 100, schemaRegistryClientConfig)

// Create once in app, and keep for token cache etc.
class KafkaToSFPoster<K, V>(val settings: List<Settings> = listOf(), val modifier: ((String, Long) -> String)? = null) {
    enum class Settings {
        DEFAULT, FROM_BEGINNING, NO_POST, SAMPLE, RUN_ONCE, ENCODE_KEY, AVRO_VALUE, BYTES_AVRO_VALUE
    }
    val sfClient = SalesforceClient()
    var numberOfWorkSessionsWithoutEvents = 0
    val encodeKey = settings.contains(Settings.ENCODE_KEY)
    val avroValue = settings.contains(Settings.AVRO_VALUE)
    val bytesAvroValue = settings.contains(Settings.BYTES_AVRO_VALUE)
    val fromBeginning = settings.contains(Settings.FROM_BEGINNING)
    val noPost = settings.contains(Settings.NO_POST)
    val sample = settings.contains(Settings.SAMPLE)
    var runOnce = settings.contains(Settings.RUN_ONCE)
    var samples = 3
    var hasRunOnce = false

    fun runWorkSession() {
        if (runOnce && hasRunOnce) {
            log.info { "Work session skipped due to setting Only Run Once, and has consumed once" }
        }
        var firstOffsetPosted: MutableMap<Int, Long> = mutableMapOf()
        var lastOffsetPosted: MutableMap<Int, Long> = mutableMapOf()
        var consumedInCurrentRun = 0

        val kafkaConsumerConfig = if (avroValue) AKafkaConsumer.configAvroGcp else if (bytesAvroValue) AKafkaConsumer.configBytesAvroGcp else AKafkaConsumer.configPlainGCP
        // Instansiate each time to fetch config from current state of environment (fetch injected updates of credentials etc):

        val consumer = if (bytesAvroValue) {
            AKafkaConsumer<K, ByteArray>(kafkaConsumerConfig,
                listOf(AnEnvironment.getEnvOrDefault(env_KAFKA_TOPIC, PROGNAME).trim()),
                AnEnvironment.getEnvOrDefault(env_KAFKA_POLLDURATION, "10000").toLong(),
                fromBeginning)
        } else {
            AKafkaConsumer<K, V>(kafkaConsumerConfig,
                listOf(AnEnvironment.getEnvOrDefault(env_KAFKA_TOPIC, PROGNAME).trim()),
                AnEnvironment.getEnvOrDefault(env_KAFKA_POLLDURATION, "10000").toLong(),
                fromBeginning)
        }

        val deserializer = KafkaAvroDeserializer(registryClient)
        val kafkaAvroDeserializerConfig = kafkaConsumerConfig + mapOf<String, Any>(
            KafkaAvroDeserializerConfig.SPECIFIC_AVRO_READER_CONFIG to "true",
            "schema.registry.url" to kafkaSchemaRegistryUrl
        )
        deserializer.configure(kafkaAvroDeserializerConfig, false)

        sfClient.enablesObjectPost { postActivities ->
            val isOk = consumer.consume { cRecords ->
                hasRunOnce = true
                if (cRecords.isEmpty) {
                    if (consumedInCurrentRun == 0) {
                        log.info { "Work: Finished session without consuming" }
                    } else {
                        log.info { "Work: Finished session with activity. $consumedInCurrentRun consumed records, posted offset range: ${offsetMapsToText(firstOffsetPosted, lastOffsetPosted)}" }
                    }
                    KafkaConsumerStates.IsFinished
                } else {
                    numberOfWorkSessionsWithoutEvents = 0
                    kCommonMetrics.noOfConsumedEvents.inc(cRecords.count().toDouble())
                    if (sample && samples > 0) {
                        cRecords.forEach { if (samples > 0) {
                            if (bytesAvroValue) {
                                File("/tmp/samples").appendText("KEY: ${it.key()}\nVALUE: ${(deserializer.deserialize(it.topic(), it.value() as ByteArray) as V)}\n\n")
                            } else {
                                File("/tmp/samples").appendText("KEY: ${it.key()}\nVALUE: ${it.value()}\n\n")
                            }
                            samples--
                            log.info { "Saved sample. Samples left: $samples" }
                        } }
                    }
                    val body = SFsObjectRest(
                        records = cRecords.map {
                            KafkaMessage(
                                CRM_Topic__c = it.topic(),
                                CRM_Key__c = if (encodeKey) it.key().toString().encodeB64() else it.key().toString(),
                                CRM_Value__c = (if (bytesAvroValue) (deserializer.deserialize(it.topic(), it.value() as ByteArray) as V).toString() else it.value())
                                    .let { value -> if (modifier == null) value.toString().encodeB64() else modifier.invoke(value.toString(), it.offset()).encodeB64() }
                            )
                        }
                    ).toJson()
                    if (!noPost) {
                        when (postActivities(body).isSuccess()) {
                            true -> {
                                kCommonMetrics.noOfPostedEvents.inc(cRecords.count().toDouble())
                                if (!firstOffsetPosted.containsKey(cRecords.first().partition())) firstOffsetPosted[cRecords.first().partition()] = cRecords.first().offset()
                                lastOffsetPosted[cRecords.last().partition()] = cRecords.last().offset()
                                cRecords.forEach { kCommonMetrics.latestPostedOffset.labels(it.partition().toString()).set(it.offset().toDouble()) }
                                KafkaConsumerStates.IsOk
                            }
                            false -> {
                                log.warn { "Failed when posting to SF" }
                                kCommonMetrics.producerIssues.inc()
                                KafkaConsumerStates.HasIssues
                            }
                        }
                    }
                    consumedInCurrentRun += cRecords.count()
                    KafkaConsumerStates.IsOk
                }
            }
            if (!isOk) {
                kCommonMetrics.consumerIssues.inc()
                log.warn { "Consumer in PlainKafkaToSFPoster reports NOK" }
            }
        }
        if (consumedInCurrentRun == 0) numberOfWorkSessionsWithoutEvents++
    }
}

open class AKafkaConsumer<K, V>(
    val config: Map<String, Any>,
    val topics: List<String> = AnEnvironment.getEnvOrDefault(env_KAFKA_TOPIC, PROGNAME).getKafkaTopics(),
    val pollDuration: Long = AnEnvironment.getEnvOrDefault(env_KAFKA_POLLDURATION, "10000").toLong(),
    val fromBeginning: Boolean = false
) {
    fun consume(doConsume: (ConsumerRecords<K, V>) -> KafkaConsumerStates): Boolean =
            getKafkaConsumerByConfig(config, topics, pollDuration, fromBeginning, doConsume)

    companion object {

        val metrics = KConsumerMetrics()

        val configBase: Map<String, Any>
            get() = mapOf<String, Any>(
                    ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG to AnEnvironment.getEnvOrDefault(env_KAFKA_BROKERS, KAFKA_LOCAL),
                    ConsumerConfig.GROUP_ID_CONFIG to AnEnvironment.getEnvOrDefault(env_KAFKA_CLIENTID, PROGNAME),
                    ConsumerConfig.CLIENT_ID_CONFIG to AnEnvironment.getEnvOrDefault(env_KAFKA_CLIENTID, PROGNAME),
                    ConsumerConfig.AUTO_OFFSET_RESET_CONFIG to "earliest",
                    ConsumerConfig.MAX_POLL_RECORDS_CONFIG to 200, // Use of SF REST API
                    ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG to "false"
            ).let { cMap ->
                if (AnEnvironment.getEnvOrDefault(env_KAFKA_SECURITY, "false").toBoolean()) {
                    cMap
                            .addKafkaSecurityProtocolAndMechanism(
                                AnEnvironment.getEnvOrDefault(EV_kafkaSecProc),
                                AnEnvironment.getEnvOrDefault(EV_kafkaSaslMec)
                            )
                            .addKafkaSecurityUser(
                                    if (AnEnvironment.getEnvOrDefault(EV_VaultInstance) != VaultInstancePaths.GCP.name) {
                                        AVault.getServiceUserOrDefault(VAULT_kafkaUser)
                                    } else {
                                        AnEnvironment.getEnvOrDefault(EV_kafkaUser, "Missing $EV_kafkaUser in nais.yaml")
                                    },
                                    if (AnEnvironment.getEnvOrDefault(EV_VaultInstance) != VaultInstancePaths.GCP.name) {
                                        AVault.getServiceUserOrDefault(VAULT_kafkaPassword)
                                    } else {
                                        val srvUsername = AnEnvironment.getEnvOrDefault(EV_kafkaUser, "Missing $EV_kafkaUser in nais.yaml")
                                        val srvPassword = AVault.getSecretOrDefault(srvUsername)
                                        if (srvPassword.isNullOrEmpty()) {
                                            log.error { "Password not set in GCP Secrets for key $srvUsername" }
                                        }
                                        srvPassword
                                    }
                            ).also { log.info { "Authenticate in context of ${AnEnvironment.getEnvOrDefault(EV_VaultInstance)}" } }
                } else cMap
            }

        val configGCP: Map<String, Any>
            get() = configBase + mapOf<String, Any>(
                "security.protocol" to "SSL",
                "ssl.keystore.location" to AnEnvironment.getEnvOrDefault(
                    "KAFKA_KEYSTORE_PATH",
                    "KAFKA_KEYSTORE_PATH MISSING"
                ),
                "ssl.keystore.password" to AnEnvironment.getEnvOrDefault(
                    "KAFKA_CREDSTORE_PASSWORD",
                    "KAFKA_CREDSTORE_PASSWORD MISSING"
                ),
                "ssl.truststore.location" to AnEnvironment.getEnvOrDefault(
                    "KAFKA_TRUSTSTORE_PATH",
                    "KAFKA_TRUSTSTORE_PATH MISSING"
                ),
                "ssl.truststore.password" to AnEnvironment.getEnvOrDefault(
                    "KAFKA_CREDSTORE_PASSWORD",
                    "KAFKA_CREDSTORE_PASSWORD MISSING"
                )
            )

        val configPlainGCP: Map<String, Any>
            get() = configGCP + mapOf<String, Any>(
                ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG to StringDeserializer::class.java,
                ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG to StringDeserializer::class.java
            )

        val configAvroGcp: Map<String, Any>
            get() = configGCP + mapOf<String, Any>(
                ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG to KafkaAvroDeserializer::class.java,
                ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG to KafkaAvroDeserializer::class.java,
                "schema.registry.url" to AnEnvironment.getEnvOrDefault("KAFKA_SCHEMA_REGISTRY", "http://localhost:8081"),
                KafkaAvroDeserializerConfig.USER_INFO_CONFIG to "${AnEnvironment.getEnvOrDefault("KAFKA_SCHEMA_REGISTRY_USER", "")}:${AnEnvironment.getEnvOrDefault("KAFKA_SCHEMA_REGISTRY_PASSWORD", "")}",
                KafkaAvroDeserializerConfig.BASIC_AUTH_CREDENTIALS_SOURCE to "USER_INFO",
                KafkaAvroDeserializerConfig.SPECIFIC_AVRO_READER_CONFIG to true
            )

        val configBytesAvroGcp: Map<String, Any>
            get() = configGCP + mapOf<String, Any>(
                ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG to StringDeserializer::class.java,
                ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG to ByteArrayDeserializer::class.java,
                "schema.registry.url" to AnEnvironment.getEnvOrDefault("KAFKA_SCHEMA_REGISTRY", "http://localhost:8081"),
                KafkaAvroDeserializerConfig.USER_INFO_CONFIG to "${AnEnvironment.getEnvOrDefault("KAFKA_SCHEMA_REGISTRY_USER", "")}:${AnEnvironment.getEnvOrDefault("KAFKA_SCHEMA_REGISTRY_PASSWORD", "")}",
                KafkaAvroDeserializerConfig.BASIC_AUTH_CREDENTIALS_SOURCE to "USER_INFO",
                KafkaAvroDeserializerConfig.SPECIFIC_AVRO_READER_CONFIG to true
            )
    }
}

fun String.getKafkaTopics(delim: String = ","): List<String> = this.let { topic ->
    if (topic.contains(delim)) topic.split(delim).map { it.trim() } else listOf(topic.trim())
}

sealed class KafkaConsumerStates {
    object IsOk : KafkaConsumerStates()
    object IsOkNoCommit : KafkaConsumerStates()
    object HasIssues : KafkaConsumerStates()
    object IsFinished : KafkaConsumerStates()
}

internal fun <K, V> getKafkaConsumerByConfig(
    config: Map<String, Any>,
    topics: List<String> = AnEnvironment.getEnvOrDefault(env_KAFKA_TOPIC, PROGNAME).getKafkaTopics(),
    pollDuration: Long = AnEnvironment.getEnvOrDefault(env_KAFKA_POLLDURATION, "10000").toLong(),
    fromBeginning: Boolean = false,
    doConsume: (ConsumerRecords<K, V>) -> KafkaConsumerStates
): Boolean =
    try {
        kErrorState = ErrorState.NONE
        KafkaConsumer<K, V>(Properties().apply { config.forEach { set(it.key, it.value) } })
            .apply {
                if (fromBeginning)
                    this.runCatching {
                        assign(
                            topics.flatMap { topic ->
                                partitionsFor(topic).map { TopicPartition(it.topic(), it.partition()) }
                            }
                        )
                    }.onFailure {
                        kErrorState = ErrorState.TOPIC_ASSIGNMENT
                        log.error { "Failure during topic partition(s) assignment for $topics - ${it.message}" }
                    }
                else
                    this.runCatching {
                        subscribe(topics)
                    }.onFailure {
                        kErrorState = ErrorState.TOPIC_ASSIGNMENT
                        log.error { "Failure during subscription for $topics -  ${it.message}" }
                    }
            }
            .use { c ->
                if (fromBeginning) c.runCatching {
                    c.seekToBeginning(emptyList())
                }.onFailure {
                    log.error { "Failure during SeekToBeginning - ${it.message}" }
                }

                var exitOk = true

                tailrec fun loop(keepGoing: Boolean, retriesLeft: Int = 5): Unit = when {
                    ShutdownHook.isActive() || PrestopHook.isActive() || !keepGoing -> (if (ShutdownHook.isActive() || PrestopHook.isActive()) { log.warn { "Kafka stopped consuming prematurely due to hook" }; Unit } else Unit)
                    else -> {
                        val pollstate = c.pollAndConsumption(pollDuration, retriesLeft > 0, doConsume)
                        val retries = if (pollstate == Pollstate.RETRY) (retriesLeft - 1).coerceAtLeast(0) else 0
                        if (pollstate == Pollstate.RETRY) {
                            // We will retry poll in a minute
                            log.info { "Kafka consumer - No records found on $topics, retry consumption in 60s. Retries left: $retries" }
                            conditionalWait(60000)
                        } else if (pollstate == Pollstate.FAILURE) {
                            exitOk = false
                        }
                        loop(pollstate.shouldContinue(), retries)
                    }
                }
                log.info { "Kafka consumer is ready to consume from topic $topics" }
                loop(true)
                log.info { "Closing KafkaConsumer, kErrorstate: $kErrorState" }
                return exitOk
            }
    } catch (e: Exception) {
        log.error { "Failure during kafka consumer construction - ${e.message}" }
        false
    }

// Investigative hack:
var currentConsumerMessageHost = "DEFAULT"
var kafkaConsumerOffsetRangeBoard: MutableMap<String, Pair<Long, Long>> = mutableMapOf()

enum class Pollstate {
    FAILURE, RETRY, OK, FINISHED
}

fun Pollstate.shouldContinue(): Boolean {
    return this == Pollstate.RETRY || this == Pollstate.OK
}

private fun <K, V> KafkaConsumer<K, V>.pollAndConsumption(pollDuration: Long, retryIfNoRecords: Boolean, doConsume: (ConsumerRecords<K, V>) -> KafkaConsumerStates): Pollstate =
    runCatching {
        poll(Duration.ofMillis(pollDuration)) as ConsumerRecords<K, V>
    }
            .onFailure {
                log.error { "Failure during poll - ${it.message}, MsgHost: $currentConsumerMessageHost, Exception class name ${it.javaClass.name}\nMsgBoard: $kafkaConsumerOffsetRangeBoard \nGiven up on poll directly. Do not commit. Do not continue" }
                if (it is org.apache.kafka.common.errors.AuthorizationException) {
                    log.error { "Detected authorization exception (OZ). Should perform refresh" } // might be due to rotation of kafka certificates
                    kCommonMetrics.pollErrorAuthorization.inc()
                    kErrorState = ErrorState.AUTHORIZATION
                } else if (it is org.apache.kafka.common.errors.AuthenticationException) {
                    log.error { "Detected authentication exception (EC). Should perform refresh" } // might be due to rotation of kafka certificates
                    kCommonMetrics.pollErrorAuthentication.inc()
                    kErrorState = ErrorState.AUTHENTICATION
                } else if (it.message?.contains("deserializing") == true) {
                    log.error { "Detected deserializing exception." }
                    kCommonMetrics.pollErrorDeserialization.inc()
                    kErrorState = ErrorState.DESERIALIZATION
                } else {
                    log.error { "Unknown/unhandled error at poll" }
                    kCommonMetrics.unknownErrorPoll.inc()
                    kErrorState = ErrorState.UNKNOWN_ERROR
                }
                return Pollstate.FAILURE
            }
            .getOrDefault(ConsumerRecords<K, V>(emptyMap()))
            .let { cRecords ->
                if (cRecords.isEmpty && retryIfNoRecords) {
                    return Pollstate.RETRY
                }
                val consumerState = AKafkaConsumer.metrics.consumerLatency.startTimer().let { rt ->
                    runCatching { doConsume(cRecords) }
                            .onFailure {
                                log.error { "Failure during doConsume, MsgHost: $currentConsumerMessageHost - cause: ${it.cause}, message: ${it.message}. Stack: ${it.printStackTrace()}, Exception class name ${it.javaClass.name}\\" }
                                if (it.message?.contains("failed to respond") == true || it.message?.contains("terminated the handshake") == true) {
                                    kErrorState = ErrorState.SERVICE_UNAVAILABLE
                                    kCommonMetrics.consumeErrorServiceUnavailable.inc()
                                } else {
                                    kErrorState = ErrorState.UNKNOWN_ERROR
                                    kCommonMetrics.unknownErrorConsume.inc()
                                }
                            }
                            .getOrDefault(KafkaConsumerStates.HasIssues).also { rt.observeDuration() }
                }
                when (consumerState) {
                    KafkaConsumerStates.IsOk -> {
                        try {
                            val hasConsumed = cRecords.count() > 0
                            if (hasConsumed) { // Only need to commit anything if records was fetched
                                commitSync()
                                if (!kafkaConsumerOffsetRangeBoard.containsKey("$currentConsumerMessageHost$POSTFIX_FIRST")) kafkaConsumerOffsetRangeBoard["$currentConsumerMessageHost$POSTFIX_FIRST"] = Pair(cRecords.first().offset(), cRecords.last().offset())
                                kafkaConsumerOffsetRangeBoard["$currentConsumerMessageHost$POSTFIX_LATEST"] = Pair(cRecords.first().offset(), cRecords.last().offset())
                            }
                            Pollstate.OK
                        } catch (e: Exception) {
                            if (cRecords.count() > 0) {
                                kafkaConsumerOffsetRangeBoard["$currentConsumerMessageHost$POSTFIX_FAIL"] = Pair(cRecords.first().offset(), cRecords.last().offset())
                            }
                            log.error { "Failure during commit, MsgHost: $currentConsumerMessageHost, leaving - ${e.message}, Exception name: ${e.javaClass.name}\n" +
                                    "MsgBoard: $kafkaConsumerOffsetRangeBoard" }
                            if (e.message?.contains("rebalanced") == true) {
                                log.error { "Detected rebalance/time between polls exception." } // might be due to rotation of kafka certificates
                                kCommonMetrics.commitErrorTimeBetweenPolls.inc()
                                kErrorState = ErrorState.TIME_BETWEEN_POLLS
                            } else {
                                kCommonMetrics.unknownErrorCommit.inc()
                                kErrorState = ErrorState.UNKNOWN_ERROR
                            }
                            Pollstate.FAILURE
                        }
                    }
                    KafkaConsumerStates.IsOkNoCommit -> Pollstate.OK
                    KafkaConsumerStates.HasIssues -> {
                        if (cRecords.count() > 0) {
                            kafkaConsumerOffsetRangeBoard["$currentConsumerMessageHost$POSTFIX_FAIL"] = Pair(cRecords.first().offset(), cRecords.last().offset())
                        }
                        log.error { "Leaving consumer on HasIssues ErrorState: ${kErrorState.name} (specific error should be reported earlier), MsgHost: $currentConsumerMessageHost\nMsgBoard: $kafkaConsumerOffsetRangeBoard" }
                        Pollstate.FAILURE
                    }
                    KafkaConsumerStates.IsFinished -> {
                        log.info { "Consumer finished, leaving\n" +
                                "MsgBoard: $kafkaConsumerOffsetRangeBoard" }
                        Pollstate.FINISHED
                    }
                    else -> {
                        log.error { "Illegal state" }
                        Pollstate.FAILURE
                    }
                }
            }

sealed class Key<out K> {
    object Missing : Key<Nothing>()
    data class Exist<K>(val k: K) : Key<K>()
}

sealed class Value<out V> {
    object Missing : Value<Nothing>()
    data class Exist<V>(val v: V) : Value<V>()
}

sealed class KeyAndValue<out K, out V> {
    object Missing : KeyAndValue<Nothing, Nothing>()
    data class Exist<K, V>(val k: K, val v: V) : KeyAndValue<K, V>()
}

sealed class KeyAndTombstone<out K> {
    object Missing : KeyAndTombstone<Nothing>()
    data class Exist<K>(val k: K) : KeyAndTombstone<K>()
}

sealed class AllRecords<out K, out V> {
    object Interrupted : AllRecords<Nothing, Nothing>()
    object Failure : AllRecords<Nothing, Nothing>()
    data class Exist<K, V>(val records: List<Pair<Key<K>, Value<V>>>) : AllRecords<K, V>()

    fun isOk(): Boolean = this is Exist
    fun hasMissingKey(): Boolean = (this is Exist) && this.records.map { it.first }.contains(Key.Missing)
    fun hasMissingValue(): Boolean = (this is Exist) && this.records.map { it.second }.contains(Value.Missing)
    fun getKeysValues(): List<KeyAndValue.Exist<out K, out V>> = if (this is Exist)
        this.records.map {
            if (it.first is Key.Exist && it.second is Value.Exist)
                KeyAndValue.Exist((it.first as Key.Exist<K>).k, (it.second as Value.Exist<V>).v)
            else KeyAndValue.Missing
        }.filterIsInstance<KeyAndValue.Exist<K, V>>()
    else emptyList()
    fun getKeysTombstones(): List<KeyAndTombstone.Exist<out K>> = if (this is Exist)
        this.records.map {
            if (it.first is Key.Exist && it.second is Value.Missing)
                KeyAndTombstone.Exist((it.first as Key.Exist<K>).k)
            else KeyAndTombstone.Missing
        }.filterIsInstance<KeyAndTombstone.Exist<K>>()
    else emptyList()
}

fun <K, V> getAllRecords(
    config: Map<String, Any>,
    topics: List<String> = AnEnvironment.getEnvOrDefault(env_KAFKA_TOPIC, PROGNAME).getKafkaTopics()
): AllRecords<K, V> =
        try {
            KafkaConsumer<K, V>(Properties().apply { config.forEach { set(it.key, it.value) } })
                    .apply {
                        this.runCatching {
                            assign(
                                    topics.flatMap { topic ->
                                        partitionsFor(topic).map { TopicPartition(it.topic(), it.partition()) }
                                    }
                            )
                        }.onFailure {
                            log.error { "Failure during topic partition(s) assignment for $topics - ${it.message}" }
                        }
                    }
                    .use { c ->
                        c.runCatching { seekToBeginning(emptyList()) }
                                .onFailure { log.error { "Failure during SeekToBeginning - ${it.message}" } }

                        tailrec fun loop(records: List<Pair<Key<K>, Value<V>>>, retriesWhenEmpty: Int = 10): AllRecords<K, V> = when {
                            ShutdownHook.isActive() || PrestopHook.isActive() -> AllRecords.Interrupted
                            else -> {
                                val cr = c.runCatching { Pair(true, poll(Duration.ofMillis(2_000)) as ConsumerRecords<K, V>) }
                                        .onFailure { log.error { "Failure during poll, get AllRecords - ${it.localizedMessage}" } }
                                        .getOrDefault(Pair(false, ConsumerRecords<K, V>(emptyMap())))

                                when {
                                    !cr.first -> AllRecords.Failure
                                    cr.second.isEmpty -> {
                                        if (records.isEmpty() && cr.second.count() == 0) {
                                            if (retriesWhenEmpty > 0) {
                                                log.info { "Did not find any records will poll again (left $retriesWhenEmpty times)" }
                                                loop(emptyList(), retriesWhenEmpty - 1)
                                            } else {
                                                log.warn { "Cannot find any records, is topic empty?" }
                                                AllRecords.Exist(emptyList())
                                            }
                                        } else {
                                            AllRecords.Exist(records)
                                        }
                                    }
                                    else -> loop((records + cr.second.map {
                                        Pair(
                                                if (it.key() == null) Key.Missing else Key.Exist(it.key() as K),
                                                if (it.value() == null) Value.Missing else Value.Exist(it.value() as V)
                                        )
                                    }))
                                }
                            }
                        }
                        loop(emptyList()).also { log.info { "Closing KafkaConsumer" } }
                    }
        } catch (e: Exception) {
            log.error { "Failure during kafka consumer construction - ${e.message}" }
            AllRecords.Failure
        }

class AKafkaProducer<K, V>(
    val config: Map<String, Any>
) {
    fun produce(doProduce: KafkaProducer<K, V>.() -> Unit): Boolean = getKafkaProducerByConfig(config, doProduce)

    companion object {

        val configBase: Map<String, Any>
            get() = mapOf<String, Any>(
                    ProducerConfig.BOOTSTRAP_SERVERS_CONFIG to AnEnvironment.getEnvOrDefault(env_KAFKA_BROKERS, KAFKA_LOCAL),
                    ProducerConfig.CLIENT_ID_CONFIG to AnEnvironment.getEnvOrDefault(env_KAFKA_CLIENTID, PROGNAME),
                    ProducerConfig.ACKS_CONFIG to "all",
                    ProducerConfig.DELIVERY_TIMEOUT_MS_CONFIG to AnEnvironment.getEnvOrDefault(EV_kafkaProducerTimeout, "31000").toInt()

            ).let { cMap ->
                if (AnEnvironment.getEnvOrDefault(env_KAFKA_SECURITY, "false").toBoolean()) {
                    cMap
                            .addKafkaSecurityProtocolAndMechanism(
                                    AnEnvironment.getEnvOrDefault(EV_kafkaSecProc),
                                    AnEnvironment.getEnvOrDefault(EV_kafkaSaslMec)
                            )
                            .addKafkaSecurityUser(
                                    if (AnEnvironment.getEnvOrDefault(EV_VaultInstance) != VaultInstancePaths.GCP.name) {
                                        AVault.getServiceUserOrDefault(VAULT_kafkaUser)
                                    } else {
                                        AnEnvironment.getEnvOrDefault(EV_kafkaUser, "Missing $EV_kafkaUser in nais.yaml")
                                    },
                                    if (AnEnvironment.getEnvOrDefault(EV_VaultInstance) != VaultInstancePaths.GCP.name) {
                                        AVault.getServiceUserOrDefault(VAULT_kafkaPassword)
                                    } else {
                                        val srvUsername = AnEnvironment.getEnvOrDefault(EV_kafkaUser, "Missing $EV_kafkaUser in nais.yaml")
                                        val srvPassword = AVault.getSecretOrDefault(srvUsername)
                                        if (srvPassword.isNullOrEmpty()) {
                                            log.error { "Password not set in GCP Secrets for key $srvUsername" }
                                        }
                                        srvPassword
                                    }
                            ).also { log.info { "Authenticate in context of ${AnEnvironment.getEnvOrDefault(EV_VaultInstance)}" } }
                } else cMap
            }
    }
}

internal fun <K, V> getKafkaProducerByConfig(config: Map<String, Any>, doProduce: KafkaProducer<K, V>.() -> Unit): Boolean =
        try {
            KafkaProducer<K, V>(
                    Properties().apply { config.forEach { set(it.key, it.value) } }
            ).use {
                it.runCatching { doProduce() }
                        .onFailure { log.error { "Failure during doProduce - ${it.localizedMessage}" } }
            }
            true
        } catch (e: Exception) {
            log.error { "Failure during kafka producer construction - ${e.message}" }
            false
        }

fun <K, V> KafkaProducer<K, V>.send(topic: String, key: K, value: V): Boolean = this.runCatching {
    send(ProducerRecord(topic, key, value)).get().hasOffset()
}
        .getOrDefault(false)

fun <K, V> KafkaProducer<K, V>.sendNullKey(topic: String, value: V): Boolean = this.runCatching {
    send(ProducerRecord(topic, null, value)).get().hasOffset()
}
        .getOrDefault(false)

fun <K, V> KafkaProducer<K, V>.sendNullValue(topic: String, key: K): Boolean = this.runCatching {
    send(ProducerRecord(topic, key, null)).get().hasOffset()
}

        .getOrDefault(false)

internal fun Map<String, Any>.addKafkaSecurityProtocolAndMechanism(
    secProtocol: String = "SASL_PLAINTEXT",
    saslMechanism: String = "PLAIN"
): Map<String, Any> = this.let {

    val mMap = this.toMutableMap()

    mMap[CommonClientConfigs.SECURITY_PROTOCOL_CONFIG] = secProtocol
    mMap[SaslConfigs.SASL_MECHANISM] = saslMechanism

    mMap.toMap()
}

internal fun Map<String, Any>.addKafkaSecurityUser(
    username: String,
    password: String
): Map<String, Any> = this.let {

    val mMap = this.toMutableMap()

    val jaasPainLogin = "org.apache.kafka.common.security.plain.PlainLoginModule"
    val jaasRequired = "required"

    mMap[SaslConfigs.SASL_JAAS_CONFIG] = "$jaasPainLogin $jaasRequired " +
            "username=\"$username\" password=\"$password\";"

    mMap.toMap()
}
