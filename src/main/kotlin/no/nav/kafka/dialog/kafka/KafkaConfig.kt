package no.nav.kafka.dialog.kafka

import io.confluent.kafka.serializers.KafkaAvroDeserializer
import io.confluent.kafka.serializers.KafkaAvroDeserializerConfig
import no.nav.kafka.dialog.config_FLAG_ALT_ID
import no.nav.kafka.dialog.config_KAFKA_CLIENT_ID
import no.nav.kafka.dialog.env
import no.nav.kafka.dialog.env_KAFKA_BROKERS
import no.nav.kafka.dialog.env_KAFKA_CREDSTORE_PASSWORD
import no.nav.kafka.dialog.env_KAFKA_KEYSTORE_PATH
import no.nav.kafka.dialog.env_KAFKA_SCHEMA_REGISTRY
import no.nav.kafka.dialog.env_KAFKA_SCHEMA_REGISTRY_PASSWORD
import no.nav.kafka.dialog.env_KAFKA_SCHEMA_REGISTRY_USER
import no.nav.kafka.dialog.env_KAFKA_TRUSTSTORE_PATH
import org.apache.kafka.clients.CommonClientConfigs
import org.apache.kafka.clients.consumer.ConsumerConfig
import org.apache.kafka.common.config.SaslConfigs
import org.apache.kafka.common.config.SslConfigs
import org.apache.kafka.common.serialization.StringDeserializer
import java.util.Properties

// Instantiate each get() to fetch config from current state of environment (fetch injected updates of credentials)
private val propertiesBase get() = Properties().apply {
    val clientId = env(config_KAFKA_CLIENT_ID) + (if (env(config_FLAG_ALT_ID).toBoolean()) "-alt" else "")
    putAll(
        mapOf<String, Any>(
            ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG to env(env_KAFKA_BROKERS),
            ConsumerConfig.GROUP_ID_CONFIG to clientId,
            ConsumerConfig.CLIENT_ID_CONFIG to clientId,
            ConsumerConfig.AUTO_OFFSET_RESET_CONFIG to "earliest",
            ConsumerConfig.MAX_POLL_RECORDS_CONFIG to 200,
            ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG to "false",
            CommonClientConfigs.SECURITY_PROTOCOL_CONFIG to "SSL",
            SaslConfigs.SASL_MECHANISM to "PLAIN",
            SslConfigs.SSL_KEYSTORE_LOCATION_CONFIG to env(env_KAFKA_KEYSTORE_PATH),
            SslConfigs.SSL_KEYSTORE_PASSWORD_CONFIG to env(env_KAFKA_CREDSTORE_PASSWORD),
            SslConfigs.SSL_TRUSTSTORE_LOCATION_CONFIG to env(env_KAFKA_TRUSTSTORE_PATH),
            SslConfigs.SSL_TRUSTSTORE_PASSWORD_CONFIG to env(env_KAFKA_CREDSTORE_PASSWORD)
        )
    )
}

val propertiesPlain get() = propertiesBase.apply {
    putAll(
        mapOf<String, Any>(
            ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG to StringDeserializer::class.java,
            ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG to StringDeserializer::class.java
        )
    )
}

val propertiesAvroValueOnly get() = propertiesPlain.apply {
    putAll(
        mapOf<String, Any>(
            ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG to KafkaAvroDeserializer::class.java,
            KafkaAvroDeserializerConfig.SCHEMA_REGISTRY_URL_CONFIG to env(env_KAFKA_SCHEMA_REGISTRY),
            KafkaAvroDeserializerConfig.USER_INFO_CONFIG to "${env(env_KAFKA_SCHEMA_REGISTRY_USER)}:${env(
                env_KAFKA_SCHEMA_REGISTRY_PASSWORD
            )}",
            KafkaAvroDeserializerConfig.BASIC_AUTH_CREDENTIALS_SOURCE to "USER_INFO",
            KafkaAvroDeserializerConfig.SPECIFIC_AVRO_READER_CONFIG to false
        )
    )
}

val propertiesAvro get() = propertiesAvroValueOnly.apply {
    putAll(
        mapOf<String, Any>(
            ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG to KafkaAvroDeserializer::class.java,
        )
    )
}
