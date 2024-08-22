package no.nav.kafka.dialog

import org.apache.kafka.clients.consumer.ConsumerRecord
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test

class FilterOnActivityCodesTest {

    fun String.toConsumerRecordValue(): ConsumerRecord<String, String?> = ConsumerRecord("topic", 0, 0L, "key", this)

    @Test
    fun filterOnActivityCodes_positive_and_negative() {
        Assertions.assertEquals(
            true,
            filterOnActivityCodes(
                (
                    "{\"aktivitetskode\":\"${aktivitetsfilterValidCodes.value.first().aktivitetskode}\"" +
                        ",\"aktivitetsgruppekode\":\"${aktivitetsfilterValidCodes.value.first().aktivitetsgruppekode}\"}"
                    )
                    .toConsumerRecordValue()
            )
        )

        Assertions.assertEquals(
            false,
            filterOnActivityCodes(
                (
                    "{\"aktivitetskode\":\"NOT_A_VALID_CODE\"" +
                        ",\"aktivitetsgruppekode\":\"${aktivitetsfilterValidCodes.value.first().aktivitetsgruppekode}\"}"
                    )
                    .toConsumerRecordValue()
            )
        )

        Assertions.assertEquals(
            false,
            filterOnActivityCodes(
                (
                    "{\"aktivitetskode\":\"${aktivitetsfilterValidCodes.value.first().aktivitetskode}\"" +
                        ",\"aktivitetsgruppekode\":\"NOT_A_VALID_CODE\"}"
                    )
                    .toConsumerRecordValue()
            )
        )
    }
}
