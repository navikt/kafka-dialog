package no.nav.kafka.dialog

import mu.KotlinLogging
import no.nav.kafka.dialog.metrics.Metrics
import no.nav.kafka.dialog.metrics.kCommonMetrics
import no.nav.kafka.dialog.metrics.numberOfWorkSessionsWithoutEvents
import org.apache.avro.generic.GenericRecord
import java.io.File

/**
 * KafkaToSFPoster
 * This class is responsible for handling a work session, ie polling and posting to salesforce until we are up-to-date with topic
 * Makes use of SalesforceClient to setup connection to salesforce
 * Makes use of AKafkaConsumer to perform polling. This class provides code for how to process each capture batch
 * (Returns KafkaConsumerStates.IsOk only when we are sure the data has been sent )
 */
class KafkaToSFPoster<K, V>(
    val settings: List<Settings> = listOf(),
    val modifier: ((String, Int, Long) -> String)? = null,
    val filter: ((String, Int, Long) -> Boolean)? = null
) {
    private val log = KotlinLogging.logger { }

    enum class Settings {
        DEFAULT, FROM_BEGINNING, NO_POST, SAMPLE, RUN_ONCE, ENCODE_KEY, AVRO_KEY_VALUE, AVRO_VALUE, LIMIT_ON_DATES
    }
    val sfClient = SalesforceClient()

    val fromBeginning = settings.contains(Settings.FROM_BEGINNING)
    val noPost = settings.contains(Settings.NO_POST)
    val sample = settings.contains(Settings.SAMPLE)
    var runOnce = settings.contains(Settings.RUN_ONCE)
    val encodeKey = settings.contains(Settings.ENCODE_KEY)
    val avroKeyValue = settings.contains(Settings.AVRO_KEY_VALUE)
    val avroValue = settings.contains(Settings.AVRO_VALUE)

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

        val kafkaConsumerConfig = if (avroKeyValue) AKafkaConsumer.configAvro else if (avroValue) AKafkaConsumer.configAvroValueOnly else AKafkaConsumer.configPlain

        // Instansiate each time to fetch config from current state of environment (fetch injected updates of credentials etc):
        val consumer = if (avroKeyValue) {
            AKafkaConsumer<GenericRecord, GenericRecord>(kafkaConsumerConfig, env(env_KAFKA_TOPIC), envAsLong(env_KAFKA_POLL_DURATION), fromBeginning, hasRunOnce)
        } else if (avroValue) {
            AKafkaConsumer<K, GenericRecord>(kafkaConsumerConfig, env(env_KAFKA_TOPIC), envAsLong(env_KAFKA_POLL_DURATION), fromBeginning, hasRunOnce)
        } else {
            AKafkaConsumer<K, V>(kafkaConsumerConfig, env(env_KAFKA_TOPIC), envAsLong(env_KAFKA_POLL_DURATION), fromBeginning, hasRunOnce)
        }

        sfClient.enablesObjectPost { postActivities ->
            val isOk = consumer.consume { cRecordsPreFilter ->
                hasRunOnce = true
                if (cRecordsPreFilter.isEmpty) {
                    if (consumedInCurrentRun == 0) {
                        log.info { "Work: Finished session without consuming. Number if work sessions without event during lifetime of app: $numberOfWorkSessionsWithoutEvents" }
                    } else {
                        log.info { "Work: Finished session with activity. $consumedInCurrentRun consumed records, posted offset range: ${offsetMapsToText(firstOffsetPosted, lastOffsetPosted)}" }
                    }
                    KafkaConsumerStates.IsFinished
                } else {
                    numberOfWorkSessionsWithoutEvents = 0
                    kCommonMetrics.noOfConsumedEvents.inc(cRecordsPreFilter.count().toDouble())

                    cRecordsPreFilter.forEach { kCommonMetrics.latestConsumedOffset.labels(it.partition().toString()).set(it.offset().toDouble()) }

                    val kafkaData = cRecordsPreFilter.map {
                        KafkaData(
                            topic = it.topic(),
                            offset = it.offset(),
                            partition = it.partition(),
                            key = it.key().toString(),
                            value = if (modifier == null) it.value().toString() else modifier.invoke(it.value().toString(), it.partition(), it.offset()),
                            originValue = it.value().toString()
                        )
                    }.filter { filter == null || filter!!(it.value, it.partition, it.offset).also { if (!it) Metrics.blockedByFilter.inc() } }.toList()

                    kCommonMetrics.noOfEventsBlockedByFilter.inc((cRecordsPreFilter.count() - kafkaData.size).toDouble())
                    consumedInCurrentRun += kafkaData.size
                    if (sample && samples > 0) {
                        kafkaData.forEach {
                            if (samples > 0) {
                                File("/tmp/samples").appendText(
                                    "KEY: ${it.key}\nVALUE: ${it.value}" +
                                        (if (modifier != null) "\nORIGIN VALUE: ${it.originValue}" else "") + "\n\n"
                                )
                                samples--
                                log.info { "Saved sample. Samples left: $samples" }
                            }
                        }
                    }
                    val body = SFsObjectRest(
                        records = kafkaData.map {
                            KafkaMessage(
                                CRM_Topic__c = it.topic,
                                CRM_Key__c = if (encodeKey) it.key.encodeB64() else it.key,
                                CRM_Value__c = it.value.encodeB64()
                            )
                        }
                    ).toJson()
                    if (noPost || kafkaData.isEmpty()) {
                        KafkaConsumerStates.IsOk
                    } else {
                        when (postActivities(body).isSuccess()) {
                            true -> {
                                kCommonMetrics.noOfPostedEvents.inc(kafkaData.size.toDouble())
                                if (!firstOffsetPosted.containsKey(kafkaData.first().partition)) firstOffsetPosted[kafkaData.first().partition] = kafkaData.first().offset
                                lastOffsetPosted[kafkaData.last().partition] = kafkaData.last().offset
                                kafkaData.forEach { kCommonMetrics.latestPostedOffset.labels(it.partition.toString()).set(it.offset.toDouble()) }
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
