package no.nav.kafka.dialog

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

internal class RemoveAdTextPropertyTest {
    @Test
    fun removeAdTextProperty_removeAdtextPropertyWhenSolo() {
        assertEquals(
            """{"uuid":"1","adnr":"2","properties":[]}""",
            removeAdTextProperty("""{"uuid": "1", "adnr": "2", "properties": [{"key": "adtext", "value": "<p>Tag</p>"}]}""", 0, 1L)
        )
    }

    @Test
    fun removeAdTextProperty_removeAdtextPropertyWhenAtEnd() {
        assertEquals(
            """{"uuid":"1","adnr":"2","properties":[{"key":"somekey","value":"somevalue"}]}""",
            removeAdTextProperty("""{"uuid":"1","adnr":"2","properties":[{"key":"somekey","value":"somevalue"},{"key":"adtext","value":"someadtext"}]}""", 0, 1L)
        )
    }

    @Test
    fun removeAdTextProperty_removeAdtextPropertyWhenAtBeginning() {
        assertEquals(
            """{"uuid":"1","adnr":"2","properties":[{"key":"somekey","value":"somevalue"}]}""",
            removeAdTextProperty("""{"uuid":"1","adnr":"2","properties":[{"key":"adtext","value":"someadtext"},{"key":"somekey","value":"somevalue"}]}""", 0, 1L)
        )
    }

    @Test
    fun removeAdTextProperty_removeAdtextPropertyWhenInMiddle() {
        assertEquals(
            """{"uuid":"1","adnr":"2","properties":[{"key":"somekey","value":"somevalue"},{"key":"anotherkey","value":"anothervalue"}]}""",
            removeAdTextProperty("""{"uuid":"1","adnr":"2","properties":[{"key":"somekey","value":"somevalue"},{"key":"adtext","value":"someadtext"},{"key":"anotherkey","value":"anothervalue"}]}""", 0, 1L)
        )
    }
}
