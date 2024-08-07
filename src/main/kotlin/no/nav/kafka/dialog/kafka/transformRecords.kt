package no.nav.kafka.dialog.kafka

import org.apache.kafka.clients.consumer.ConsumerRecord
import org.apache.kafka.clients.consumer.ConsumerRecords
import org.apache.kafka.common.TopicPartition

fun ConsumerRecords<out Any, out Any?>.toStringRecords(): ConsumerRecords<String, String?> {

    val transformedRecords = mutableMapOf<TopicPartition, MutableList<ConsumerRecord<String, String?>>>()

    for (partition in this.partitions()) {
        for (partitionRecord in this.records(partition)) {
            if (!transformedRecords.containsKey(partition)) transformedRecords[partition] = mutableListOf()
            transformedRecords[partition]!!.add(
                ConsumerRecord<String, String?>(
                    partitionRecord.topic(),
                    partitionRecord.partition(),
                    partitionRecord.offset(),
                    partitionRecord.key().toString(),
                    partitionRecord.value().toString()
                )
            )
        }
    }
    return ConsumerRecords(transformedRecords)
}
