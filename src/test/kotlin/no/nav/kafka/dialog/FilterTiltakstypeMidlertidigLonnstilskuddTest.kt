package no.nav.kafka.dialog

import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test

class FilterTiltakstypeMidlertidigLonnstilskuddTest {
    @Test
    fun filterTiltakstypeMidlertidigLonnstilskudd_positive_and_negative() {
        Assertions.assertEquals(true,
            filterTiltakstypeMidlertidigLonnstilskudd("{\"tiltakstype\":\"MIDLERTIDIG_LONNSTILSKUDD\"}", 0L))

        Assertions.assertEquals(false,
            filterTiltakstypeMidlertidigLonnstilskudd("{\"tiltakstype\":\"SOMETHING_ELSE\"}", 0L)) }
}
