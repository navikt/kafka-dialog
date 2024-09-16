package no.nav.kafka.dialog.poster

import mu.KotlinLogging
import no.nav.kafka.dialog.Filter
import no.nav.kafka.dialog.Modifier
import no.nav.kafka.dialog.config_ENCODE_KEY
import no.nav.kafka.dialog.config_FLAG_NO_POST
import no.nav.kafka.dialog.config_FLAG_SEEK
import no.nav.kafka.dialog.config_KAFKA_POLL_DURATION
import no.nav.kafka.dialog.config_KAFKA_TOPIC
import no.nav.kafka.dialog.config_NUMBER_OF_SAMPLES
import no.nav.kafka.dialog.config_SEEK_OFFSET
import no.nav.kafka.dialog.env
import no.nav.kafka.dialog.kafka.KafkaConsumerFactory
import no.nav.kafka.dialog.kafka.modifyRecords
import no.nav.kafka.dialog.kafka.toStringRecords
import no.nav.kafka.dialog.metrics.WorkSessionStatistics
import no.nav.kafka.dialog.salesforce.KafkaMessage
import no.nav.kafka.dialog.salesforce.SalesforceClient
import no.nav.kafka.dialog.salesforce.isSuccess
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.apache.kafka.clients.consumer.ConsumerRecords
import org.apache.kafka.clients.consumer.KafkaConsumer
import org.apache.kafka.common.TopicPartition
import java.io.File
import java.time.Duration
import java.util.Base64

/**
 * KafkaToSFPoster
 * This class is responsible for handling a work session, ie polling and posting to salesforce until we are up-to-date with topic
 * Makes use of SalesforceClient to set up connection to salesforce
 */
class KafkaToSFPoster(
    private val modifier: Modifier? = null,
    private val filter: Filter? = null,
    private val sfClient: SalesforceClient = SalesforceClient(),
    private val kafkaConsumerFactory: KafkaConsumerFactory = KafkaConsumerFactory(),
    private val kafkaTopic: String = env(config_KAFKA_TOPIC),
    private val kafkaPollDuration: Long = env(config_KAFKA_POLL_DURATION).toLong(),
    private val flagSeek: Boolean = env(config_FLAG_SEEK).toBoolean(),
    private val seekOffset: Long = env(config_SEEK_OFFSET).toLong(),
    private var samplesLeft: Int = env(config_NUMBER_OF_SAMPLES).toInt(),
    private val flagNoPost: Boolean = env(config_FLAG_NO_POST).toBoolean(),
    private val flagEncodeKey: Boolean = env(config_ENCODE_KEY).toBoolean()
) {
    private enum class ConsumeResult { SUCCESSFULLY_CONSUMED_BATCH, NO_MORE_RECORDS, FAIL }

    private val log = KotlinLogging.logger { }

    private var hasRunOnce = false

    private lateinit var stats: WorkSessionStatistics

    fun runWorkSession() {
        stats = WorkSessionStatistics()

        // Instantiate each work session to fetch config from current state of environment (fetch injected updates of credentials)
        val kafkaConsumer = setupKafkaConsumer(kafkaTopic)

        hasRunOnce = true

        pollAndConsume(kafkaConsumer)
        kafkaConsumer.close()
    }

    private fun setupKafkaConsumer(kafkaTopic: String) = kafkaConsumerFactory.createConsumer().apply {
        // Using assign rather than subscribe since we need the ability to seek to a particular offset
        val topicPartitions = partitionsFor(kafkaTopic).map { TopicPartition(it.topic(), it.partition()) }
        assign(topicPartitions)
        log.info { "Starting work session on topic $kafkaTopic with ${topicPartitions.size} partitions" }
        if (!hasRunOnce) {
            if (flagSeek) {
                topicPartitions.forEach {
                    seek(it, seekOffset)
                }
            }
        }
    }

    private tailrec fun pollAndConsume(kafkaConsumer: KafkaConsumer<out Any, out Any?>) {
        val records = kafkaConsumer.poll(Duration.ofMillis(kafkaPollDuration)).toStringRecords()

        if (consumeRecords(records) == ConsumeResult.SUCCESSFULLY_CONSUMED_BATCH) {
            kafkaConsumer.commitSync() // Will update position of kafka consumer in kafka cluster
            pollAndConsume(kafkaConsumer)
        }
    }

    private fun consumeRecords(recordsFromTopic: ConsumerRecords<String, String?>): ConsumeResult =
        if (recordsFromTopic.isEmpty) {
            if (!stats.hasConsumed()) {
                WorkSessionStatistics.subsequentWorkSessionsWithEventsCounter.clear()
                WorkSessionStatistics.workSessionsWithoutEventsCounter.inc()
                log.info { "Finished work session without consuming. Number of work sessions without events during lifetime of app: ${WorkSessionStatistics.workSessionsWithoutEventsCounter.get().toInt()}" }
            } else {
                WorkSessionStatistics.subsequentWorkSessionsWithEventsCounter.inc()
                log.info { "Finished work session with activity (subsequent ${WorkSessionStatistics.subsequentWorkSessionsWithEventsCounter.get().toInt()}). $stats" }
            }
            ConsumeResult.NO_MORE_RECORDS
        } else {
            WorkSessionStatistics.workSessionsWithoutEventsCounter.clear()
            stats.updateConsumedStatistics(recordsFromTopic)

            val recordsModified = modifyRecords(recordsFromTopic)

            val recordsModifiedAndFiltered = filterRecords(recordsModified)

            if (samplesLeft > 0) sampleRecords(records = recordsModifiedAndFiltered, originals = recordsFromTopic)

            if (recordsModifiedAndFiltered.count() == 0 || flagNoPost) {
                if (recordsModifiedAndFiltered.count() > 0) updateWhatWouldBeSent(recordsModifiedAndFiltered)

                // Either we have set a flag to not post to salesforce, or the filter ate all candidates -
                // consider it a successfully consumed batch without further action
                ConsumeResult.SUCCESSFULLY_CONSUMED_BATCH
            } else {
                if (sfClient.postRecords(recordsModifiedAndFiltered.toKafkaMessagesSet()).isSuccess()) {
                    stats.updatePostedStatistics(recordsModifiedAndFiltered)
                    ConsumeResult.SUCCESSFULLY_CONSUMED_BATCH
                } else {
                    log.warn { "Failed when posting to SF - $stats" }
                    WorkSessionStatistics.failedSalesforceCallCounter.inc()
                    ConsumeResult.FAIL
                }
            }
        }

    // For testdata:
    private var whatWouldBeSentBatch = 1
    private fun updateWhatWouldBeSent(recordsFiltered: Iterable<ConsumerRecord<String, String?>>) {
        File("/tmp/whatwouldbesent").appendText("BATCH ${whatWouldBeSentBatch++}\n${recordsFiltered.toKafkaMessagesSet().joinToString("\n")}\n\n")
    }

    private fun modifyRecords(records: ConsumerRecords<String, String?>): Iterable<ConsumerRecord<String, String?>> {
        return modifier?.run { records.modifyRecords(this) } ?: records
    }

    private fun filterRecords(records: Iterable<ConsumerRecord<String, String?>>): Iterable<ConsumerRecord<String, String?>> {
        val recordsPostFilter = filter?.run { records.filter { invoke(it) } } ?: records
        stats.incBlockedByFilter(records.count() - recordsPostFilter.count())
        return recordsPostFilter
    }

    private fun Iterable<ConsumerRecord<String, String?>>.toKafkaMessagesSet(): Set<KafkaMessage> {
        val kafkaMessages = this.map {
            KafkaMessage(
                CRM_Topic__c = it.topic(),
                CRM_Key__c = if (flagEncodeKey) it.key().encodeB64() else it.key(),
                CRM_Value__c = it.value()?.encodeB64()
            )
        }

        val uniqueKafkaMessages = kafkaMessages.toSet()
        val uniqueValueCount = uniqueKafkaMessages.count()
        if (kafkaMessages.size != uniqueValueCount) {
            log.warn { "Detected ${kafkaMessages.size - uniqueValueCount} duplicates in $kafkaTopic batch" }
        }
        return uniqueKafkaMessages
    }

    private fun sampleRecords(records: Iterable<ConsumerRecord<String, String?>>, originals: Iterable<ConsumerRecord<String, String?>>) {
        records.forEach {
            val original = originals.first { original -> original.offset() == it.offset() && original.partition() == it.partition() }

            if (samplesLeft-- > 0) {
                File("/tmp/samplesFromTopic").appendText("OFFSET: ${it.partition()} ${it.offset()}\nKEY: ${it.key()}\nVALUE: ${original.value()}\n\n")
                if (modifier != null) {
                    File("/tmp/samplesAfterModifier").appendText("OFFSET: ${it.partition()} ${it.offset()}\nKEY: ${it.key()}\nVALUE: ${it.value()}\n\n")
                }
                log.info { "Saved sample. Samples left: $samplesLeft" }
            }
        }
    }

    private fun String.encodeB64(): String = Base64.getEncoder().encodeToString(this.toByteArray())
}
