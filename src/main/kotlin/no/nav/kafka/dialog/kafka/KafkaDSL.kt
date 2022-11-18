package no.nav.kafka.dialog

import io.confluent.kafka.schemaregistry.client.CachedSchemaRegistryClient
import io.confluent.kafka.schemaregistry.client.SchemaRegistryClientConfig
import io.confluent.kafka.serializers.KafkaAvroDeserializer
import io.confluent.kafka.serializers.KafkaAvroDeserializerConfig
import java.time.Duration
import java.util.Properties
import kotlin.Exception
import mu.KotlinLogging
import no.nav.kafka.dialog.metrics.ErrorState
import no.nav.kafka.dialog.metrics.KConsumerMetrics
import no.nav.kafka.dialog.metrics.POSTFIX_FAIL
import no.nav.kafka.dialog.metrics.POSTFIX_FIRST
import no.nav.kafka.dialog.metrics.POSTFIX_LATEST
import no.nav.kafka.dialog.metrics.currentConsumerMessageHost
import no.nav.kafka.dialog.metrics.kCommonMetrics
import no.nav.kafka.dialog.metrics.kErrorState
import no.nav.kafka.dialog.metrics.kafkaConsumerOffsetRangeBoard
import org.apache.kafka.clients.CommonClientConfigs
import org.apache.kafka.clients.consumer.ConsumerConfig
import org.apache.kafka.clients.consumer.ConsumerRecords
import org.apache.kafka.clients.consumer.KafkaConsumer
import org.apache.kafka.common.TopicPartition
import org.apache.kafka.common.config.SaslConfigs
import org.apache.kafka.common.config.SslConfigs
import org.apache.kafka.common.security.auth.SecurityProtocol
import org.apache.kafka.common.serialization.ByteArrayDeserializer
import org.apache.kafka.common.serialization.StringDeserializer

private val log = KotlinLogging.logger {}

/***
 * Below used only for special case of parsing avro in app, see Settings.bytesAvro
 */
val schemaRegistryClientConfig = mapOf<String, Any>(
    SchemaRegistryClientConfig.BASIC_AUTH_CREDENTIALS_SOURCE to "USER_INFO",
    SchemaRegistryClientConfig.USER_INFO_CONFIG to "${env(env_KAFKA_SCHEMA_REGISTRY_USER)}:${env(env_KAFKA_SCHEMA_REGISTRY_PASSWORD)}"
)
val registryClient = CachedSchemaRegistryClient(env(env_KAFKA_SCHEMA_REGISTRY), 100, schemaRegistryClientConfig)
/***/

sealed class KafkaConsumerStates {
    object IsOk : KafkaConsumerStates()
    object IsOkNoCommit : KafkaConsumerStates()
    object HasIssues : KafkaConsumerStates()
    object IsFinished : KafkaConsumerStates()
}

open class AKafkaConsumer<K, V>(
    val config: Map<String, Any>,
    val topic: String = env(env_KAFKA_TOPIC),
    val pollDuration: Long = envAsLong(env_KAFKA_POLLDURATION),
    val fromBeginning: Boolean = false
) {
    fun consume(doConsume: (ConsumerRecords<K, V>) -> KafkaConsumerStates): Boolean =
            getKafkaConsumerByConfig(config, topic, pollDuration, fromBeginning, doConsume)

    companion object {
        val metrics = KConsumerMetrics()

        val configBase: Map<String, Any>
            get() = mapOf<String, Any>(
                ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG to env(env_KAFKA_BROKERS),
                ConsumerConfig.GROUP_ID_CONFIG to env(env_KAFKA_CLIENTID),
                ConsumerConfig.CLIENT_ID_CONFIG to env(env_KAFKA_CLIENTID),
                ConsumerConfig.AUTO_OFFSET_RESET_CONFIG to "earliest",
                ConsumerConfig.MAX_POLL_RECORDS_CONFIG to 200,
                ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG to "false",
                CommonClientConfigs.SECURITY_PROTOCOL_CONFIG to SecurityProtocol.SSL,
                SaslConfigs.SASL_MECHANISM to "PLAIN",
                SslConfigs.SSL_KEYSTORE_LOCATION_CONFIG to env(env_KAFKA_KEYSTORE_PATH),
                SslConfigs.SSL_KEYSTORE_PASSWORD_CONFIG to env(env_KAFKA_CREDSTORE_PASSWORD),
                SslConfigs.SSL_TRUSTSTORE_LOCATION_CONFIG to env(env_KAFKA_TRUSTSTORE_PATH),
                SslConfigs.SSL_TRUSTSTORE_PASSWORD_CONFIG to env(env_KAFKA_CREDSTORE_PASSWORD)
            )

        val configPlain: Map<String, Any>
            get() = configBase + mapOf<String, Any>(
                ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG to StringDeserializer::class.java,
                ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG to StringDeserializer::class.java
            )

        val configAvro: Map<String, Any>
            get() = configBase + mapOf<String, Any>(
                ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG to KafkaAvroDeserializer::class.java,
                ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG to KafkaAvroDeserializer::class.java,
                KafkaAvroDeserializerConfig.SCHEMA_REGISTRY_URL_CONFIG to env(env_KAFKA_SCHEMA_REGISTRY),
                KafkaAvroDeserializerConfig.USER_INFO_CONFIG to "${env(env_KAFKA_SCHEMA_REGISTRY_USER)}:${env(env_KAFKA_SCHEMA_REGISTRY_PASSWORD)}",
                KafkaAvroDeserializerConfig.BASIC_AUTH_CREDENTIALS_SOURCE to "USER_INFO",
                KafkaAvroDeserializerConfig.SPECIFIC_AVRO_READER_CONFIG to true
            )

        val configBytesAvro: Map<String, Any>
            get() = configAvro + mapOf<String, Any>(
                ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG to StringDeserializer::class.java,
                ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG to ByteArrayDeserializer::class.java
            )
    }
}

internal fun <K, V> getKafkaConsumerByConfig(
    config: Map<String, Any>,
    topic: String,
    pollDuration: Long = envAsLong(env_KAFKA_POLLDURATION),
    fromBeginning: Boolean = false,
    doConsume: (ConsumerRecords<K, V>) -> KafkaConsumerStates
): Boolean =
    try {
        kErrorState = ErrorState.NONE
        KafkaConsumer<K, V>(Properties().apply { config.forEach { set(it.key, it.value) } })
            .apply {
                if (fromBeginning)
                    this.runCatching {
                        assign(partitionsFor(topic).map { TopicPartition(it.topic(), it.partition()) })
                    }.onFailure {
                        kErrorState = ErrorState.TOPIC_ASSIGNMENT
                        log.error { "Failure during topic partition(s) assignment for $topic - ${it.message}" }
                    }
                else
                    this.runCatching {
                        subscribe(listOf(topic))
                    }.onFailure {
                        kErrorState = ErrorState.TOPIC_ASSIGNMENT
                        log.error { "Failure during subscription for $topic -  ${it.message}" }
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
                            log.info { "Kafka consumer - No records found on $topic, retry consumption in 60s. Retries left: $retries" }
                            conditionalWait(60000)
                        } else if (pollstate == Pollstate.FAILURE) {
                            exitOk = false
                        }
                        loop(pollstate.shouldContinue(), retries)
                    }
                }
                log.info { "Kafka consumer is ready to consume from topic $topic" }
                loop(true)
                log.info { "Closing KafkaConsumer, kErrorstate: $kErrorState" }
                return exitOk
            }
    } catch (e: Exception) {
        log.error { "Failure during kafka consumer construction - ${e.message}" }
        false
    }

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
                }
            }

// Reference Producer :
/*
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
                    ProducerConfig.DELIVERY_TIMEOUT_MS_CONFIG to AnEnvironment.getEnvOrDefault(EV_kafkaProducerTimeout, "31000").toInt(),
                CommonClientConfigs.SECURITY_PROTOCOL_CONFIG to SecurityProtocol.SSL,
                SaslConfigs.SASL_MECHANISM to "PLAIN"
            ).let { cMap ->
                if (AnEnvironment.getEnvOrDefault(env_KAFKA_SECURITY, "false").toBoolean()) {
                    cMap
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


 */

// Reference kafka user fetch onprem:
/*

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

 */
