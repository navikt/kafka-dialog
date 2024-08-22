package no.nav.kafka.dialog.kafka

import no.nav.kafka.dialog.Modifier
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.apache.kafka.clients.consumer.ConsumerRecords
import org.apache.kafka.common.TopicPartition

fun ConsumerRecords<out Any, out Any?>.toStringRecords(): ConsumerRecords<String, String?> {
    return transformRecords { it.value().toString() }
}

fun ConsumerRecords<String, String?>.modifyRecords(modifier: Modifier): ConsumerRecords<String, String?> {
    return transformRecords { modifier(it) }
}

private inline fun <K, V> ConsumerRecords<K, V>.transformRecords(
    valueTransform: (ConsumerRecord<K, V>) -> String?
): ConsumerRecords<String, String?> {
    val transformedRecords = mutableMapOf<TopicPartition, MutableList<ConsumerRecord<String, String?>>>()

    // long timestamp, TimestampType timestampType, int serializedKeySize, int serializedValueSize
    for (partition in this.partitions()) {
        val records = transformedRecords.getOrPut(partition) { mutableListOf() }
        for (partitionRecord in this.records(partition)) {
            records.add(
                ConsumerRecord(
                    partitionRecord.topic(),
                    partitionRecord.partition(),
                    partitionRecord.offset(),
                    partitionRecord.timestamp(),
                    partitionRecord.timestampType(),
                    partitionRecord.serializedKeySize(),
                    partitionRecord.serializedValueSize(),
                    partitionRecord.key().toString(), // Perform String transform on Key
                    valueTransform(partitionRecord), // Perform String or Modify transform on Value
                    partitionRecord.headers(),
                    partitionRecord.leaderEpoch()
                )
            )
        }
    }

    return ConsumerRecords(transformedRecords)
}
