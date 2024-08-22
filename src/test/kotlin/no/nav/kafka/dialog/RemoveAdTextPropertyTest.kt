package no.nav.kafka.dialog

import org.apache.kafka.clients.consumer.ConsumerRecord
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

internal class RemoveAdTextPropertyTest {

    fun String.toConsumerRecordValue(): ConsumerRecord<String, String?> = ConsumerRecord("topic", 0, 0L, "key", this)

    @Test
    fun removeAdTextProperty_removeAdtextPropertyWhenSolo() {
        assertEquals(
            """{"uuid":"1","adnr":"2","properties":[]}""",
            removeAdTextProperty("""{"uuid": "1", "adnr": "2", "properties": [{"key": "adtext", "value": "<p>Tag</p>"}]}""".toConsumerRecordValue())
        )
    }

    @Test
    fun removeAdTextProperty_removeAdtextPropertyWhenAtEnd() {
        assertEquals(
            """{"uuid":"1","adnr":"2","properties":[{"key":"somekey","value":"somevalue"}]}""",
            removeAdTextProperty("""{"uuid":"1","adnr":"2","properties":[{"key":"somekey","value":"somevalue"},{"key":"adtext","value":"someadtext"}]}""".toConsumerRecordValue())
        )
    }

    @Test
    fun removeAdTextProperty_removeAdtextPropertyWhenAtBeginning() {
        assertEquals(
            """{"uuid":"1","adnr":"2","properties":[{"key":"somekey","value":"somevalue"}]}""",
            removeAdTextProperty("""{"uuid":"1","adnr":"2","properties":[{"key":"adtext","value":"someadtext"},{"key":"somekey","value":"somevalue"}]}""".toConsumerRecordValue())
        )
    }

    @Test
    fun removeAdTextProperty_removeAdtextPropertyWhenInMiddle() {
        assertEquals(
            """{"uuid":"1","adnr":"2","properties":[{"key":"somekey","value":"somevalue"},{"key":"anotherkey","value":"anothervalue"}]}""",
            removeAdTextProperty("""{"uuid":"1","adnr":"2","properties":[{"key":"somekey","value":"somevalue"},{"key":"adtext","value":"someadtext"},{"key":"anotherkey","value":"anothervalue"}]}""".toConsumerRecordValue())
        )
    }
}
