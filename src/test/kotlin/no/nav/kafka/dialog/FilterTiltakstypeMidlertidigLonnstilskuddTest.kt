package no.nav.kafka.dialog

import org.apache.kafka.clients.consumer.ConsumerRecord
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test

class FilterTiltakstypeMidlertidigLonnstilskuddTest {

    fun String.toConsumerRecordValue(): ConsumerRecord<String, String?> = ConsumerRecord("topic", 0, 0L, "key", this)

    @Test
    fun filterTiltakstypeMidlertidigLonnstilskudd_positive_and_negative() {
        Assertions.assertEquals(
            true,
            filterTiltakstypeMidlertidigLonnstilskudd("{\"tiltakstype\":\"MIDLERTIDIG_LONNSTILSKUDD\"}".toConsumerRecordValue())
        )

        Assertions.assertEquals(
            false,
            filterTiltakstypeMidlertidigLonnstilskudd("{\"tiltakstype\":\"SOMETHING_ELSE\"}".toConsumerRecordValue())
        )
    }
}
