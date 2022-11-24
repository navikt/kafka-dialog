package no.nav.kafka.dialog

import io.confluent.kafka.serializers.KafkaAvroDeserializer
import io.confluent.kafka.serializers.KafkaAvroDeserializerConfig
import java.io.File
import mu.KotlinLogging
import no.nav.kafka.dialog.metrics.kCommonMetrics
import no.nav.kafka.dialog.metrics.numberOfWorkSessionsWithoutEvents
import org.apache.avro.generic.GenericRecord

/**
 * KafkaToSFPoster
 * This class is responsible for handling a work session, ie polling and posting to salesforce until we are up-to-date with topic
 * Makes use of SalesforceClient to setup connection to salesforce
 * Makes use of AKafkaConsumer to perform polling. This class provides code for how to process each capture batch
 * (Returns KafkaConsumerStates.IsOk only when we are sure the data has been sent )
 */
class KafkaToSFPoster<K, V>(val settings: List<Settings> = listOf(), val modifier: ((String, Long) -> String)? = null) {
    private val log = KotlinLogging.logger { }

    enum class Settings {
        DEFAULT, FROM_BEGINNING, NO_POST, SAMPLE, RUN_ONCE, ENCODE_KEY, AVRO_VALUE, AVRO_VALUE_ONLY
    }
    val sfClient = SalesforceClient()

    val fromBeginning = settings.contains(Settings.FROM_BEGINNING)
    val noPost = settings.contains(Settings.NO_POST)
    val sample = settings.contains(Settings.SAMPLE)
    var runOnce = settings.contains(Settings.RUN_ONCE)
    val encodeKey = settings.contains(Settings.ENCODE_KEY)
    val avroValue = settings.contains(Settings.AVRO_VALUE)
    val avroValueOnly = settings.contains(Settings.AVRO_VALUE_ONLY)

    var samples = numberOfSamplesInSampleRun
    var hasRunOnce = false
    fun runWorkSession() {
        if (runOnce && hasRunOnce) {
            log.info { "Work session skipped due to setting Only Run Once, and has consumed once" }
            return
        }
        // kCommonMetrics.clearWorkSessionMetrics()
        var firstOffsetPosted: MutableMap<Int, Long> = mutableMapOf() /** First offset posted per kafka partition **/
        var lastOffsetPosted: MutableMap<Int, Long> = mutableMapOf() /** Last offset posted per kafka partition **/
        var consumedInCurrentRun = 0

        val kafkaConsumerConfig = if (avroValue) AKafkaConsumer.configAvro else if (avroValueOnly) AKafkaConsumer.configAvroValueOnly else AKafkaConsumer.configPlain
        // Instansiate each time to fetch config from current state of environment (fetch injected updates of credentials etc):

        val consumer = if (avroValueOnly) {
            log.info { " Special case bytes Avro - instantiate GenericRecord" }
            // AKafkaConsumer<K, ByteArray>(kafkaConsumerConfig, env(env_KAFKA_TOPIC), envAsLong(env_KAFKA_POLL_DURATION), fromBeginning, hasRunOnce)
            AKafkaConsumer<K, GenericRecord>(kafkaConsumerConfig, env(env_KAFKA_TOPIC), envAsLong(env_KAFKA_POLL_DURATION), fromBeginning, hasRunOnce)
        } else {
            // Todo temp
            AKafkaConsumer<GenericRecord, GenericRecord>(kafkaConsumerConfig, env(env_KAFKA_TOPIC), envAsLong(env_KAFKA_POLL_DURATION), fromBeginning, hasRunOnce)
        }

        /**
         * Below used only for special case bytesAvroValue
         */
        val deserializer = KafkaAvroDeserializer(registryClient)
        val kafkaAvroDeserializerConfig = kafkaConsumerConfig + mapOf<String, Any>(
            KafkaAvroDeserializerConfig.SPECIFIC_AVRO_READER_CONFIG to "true",
            KafkaAvroDeserializerConfig.SCHEMA_REGISTRY_URL_CONFIG to env(env_KAFKA_SCHEMA_REGISTRY)
        )
        deserializer.configure(kafkaAvroDeserializerConfig, false)
        /***/

        sfClient.enablesObjectPost { postActivities ->
            val isOk = consumer.consume { cRecords ->
                hasRunOnce = true
                if (cRecords.isEmpty) {
                    if (consumedInCurrentRun == 0) {
                        log.info { "Work: Finished session without consuming. Number if work sessions without event during lifetime of app: $numberOfWorkSessionsWithoutEvents" }
                    } else {
                        log.info { "Work: Finished session with activity. $consumedInCurrentRun consumed records, posted offset range: ${offsetMapsToText(firstOffsetPosted, lastOffsetPosted)}" }
                    }
                    KafkaConsumerStates.IsFinished
                } else {
                    numberOfWorkSessionsWithoutEvents = 0
                    kCommonMetrics.noOfConsumedEvents.inc(cRecords.count().toDouble())
                    consumedInCurrentRun += cRecords.count()
                    if (sample && samples > 0) {
                        cRecords.forEach { if (samples > 0) {
                            if (avroValueOnly) {
                                // log.info { "Special case bytes Avro - SAMPLE - deserialize from bytearray to object as provided Ad" }
                                // File("/tmp/samples").appendText("KEY: ${it.key()}\nVALUE: ${(deserializer.deserialize(it.topic(), it.value() as ByteArray) as V)}\n\n")
                                File("/tmp/samples").appendText("KEY: ${it.key()}\nVALUE: ${it.value()}\n\n")
                                log.info { "Special case bytes Avro - SAMPLE - made a sample" }
                            } else {
                                log.info { "Samples" }
                                log.info { "VALUE: ${it.value() as? String}" }
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
                                CRM_Value__c = /*(if (bytesAvroValue) (deserializer.deserialize(it.topic(), it.value() as ByteArray) as V).toString() else */ it.value().toString()
                                    .let { value -> if (modifier == null) value.toString().encodeB64() else modifier.invoke(value.toString(), it.offset()).encodeB64() }
                            )
                        }
                    ).toJson()
                    if (noPost) {
                        KafkaConsumerStates.IsOk
                    } else {
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
                }
            }
            if (!isOk) {
                // Consumer issues is expected due to rotation of credentials, relocating app by kubernetes etc and is not critical.
                // As long as we do not commit offset until we sent the data it will be sent at next attempt
                kCommonMetrics.consumerIssues.inc()
                log.warn { "Kafka consumer reports NOK" }
            }
        }
        if (consumedInCurrentRun == 0) numberOfWorkSessionsWithoutEvents++
    }
}
