package no.nav.kafka.dialog.kafka
import no.nav.kafka.dialog.config_MESSAGE_ENCODING
import no.nav.kafka.dialog.env
import org.apache.avro.generic.GenericRecord
import org.apache.kafka.clients.consumer.KafkaConsumer

class KafkaConsumerFactory {
    private val messageEncoding = MessageEncoding.valueOf(env(config_MESSAGE_ENCODING))
    fun createConsumer(): KafkaConsumer<out Any, out Any?> = when (messageEncoding) {
        MessageEncoding.PLAIN -> KafkaConsumer<String, String?>(propertiesPlain)
        MessageEncoding.AVRO_VALUE_ONLY -> KafkaConsumer<String, GenericRecord>(propertiesAvroValueOnly)
        MessageEncoding.AVRO -> KafkaConsumer<GenericRecord, GenericRecord>(propertiesAvro)
    }
}
