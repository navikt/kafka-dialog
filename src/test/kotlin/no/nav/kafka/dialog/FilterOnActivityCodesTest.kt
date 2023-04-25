package no.nav.kafka.dialog

import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test

class FilterOnActivityCodesTest {
    @Test
    fun filterOnActivityCodes_positive_and_negative() {
        Assertions.assertEquals(true,
            filterOnActivityCodes("{\"aktivitetskode\":\"${aktivitetsfilterValidCodes.value.first().aktivitetskode}\"" +
                    ",\"aktivitetsgruppekode\":\"${aktivitetsfilterValidCodes.value.first().aktivitetsgruppekode}\"}", 0L)
        )

        Assertions.assertEquals(false,
            filterOnActivityCodes("{\"aktivitetskode\":\"NOT_A_VALID_CODE\"" +
                    ",\"aktivitetsgruppekode\":\"${aktivitetsfilterValidCodes.value.first().aktivitetsgruppekode}\"}", 0L)
        )

        Assertions.assertEquals(false,
            filterOnActivityCodes("{\"aktivitetskode\":\"${aktivitetsfilterValidCodes.value.first().aktivitetskode}\"" +
                    ",\"aktivitetsgruppekode\":\"NOT_A_VALID_CODE\"}", 0L)
        )
    }
}
