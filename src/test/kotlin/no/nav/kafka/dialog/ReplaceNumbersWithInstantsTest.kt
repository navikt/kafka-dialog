package no.nav.kafka.dialog

import org.apache.kafka.clients.consumer.ConsumerRecord
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test
import java.time.Instant

class ReplaceNumbersWithInstantsTest {

    fun String.toConsumerRecordValue(): ConsumerRecord<String, String?> = ConsumerRecord("topic", 0, 0L, "key", this)

    @Test
    fun replaceNumbersWithInstants_epochMillisRepresentationOfInstantWillBeTranslatedToThatInstant() {
        val knownInstant = Instant.parse("1983-09-27T03:00:00Z")
        val instantAsEpochMillis = knownInstant.toEpochMilli()

        Assertions.assertEquals(
            "{\"number\":\"$knownInstant\"}",
            replaceNumbersWithInstants("{\"number\":$instantAsEpochMillis}".toConsumerRecordValue())
        )
    }

    @Test
    fun replaceNumbersWithInstants_onlyNumberInputTranslatesToInstant() {
        Assertions.assertEquals(
            """{"someText":"text","aStringNumber":"1234567","number":"1970-01-01T00:20:34.567Z","aBoolean":true}""",
            replaceNumbersWithInstants(
                """{"someText":"text","aStringNumber":"1234567","number":1234567,"aBoolean":true}""".toConsumerRecordValue()
            )
        )
    }
}
