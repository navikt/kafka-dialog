package no.nav.kafka.dialog

import com.google.gson.Gson
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import org.apache.kafka.clients.consumer.ConsumerRecord
import java.io.File

typealias Filter = ((ConsumerRecord<String, String?>) -> Boolean)

/**
 * Not in use, but kept for reference - only approve json with tiltakstype == "MIDLERTIDIG_LONNSTILSKUDD"
 */
val filterTiltakstypeMidlertidigLonnstilskudd: Filter = { record ->
    try {
        val obj = JsonParser.parseString(record.value()) as JsonObject
        obj["tiltakstype"].asString == "MIDLERTIDIG_LONNSTILSKUDD"
    } catch (e: Exception) {
        throw RuntimeException("Unable to parse input for tiltakstype filter, partition ${record.partition()}, offset ${record.offset()}, message ${e.message}")
    }
}

data class ValidCode(val aktivitetskode: String, val aktivitetsgruppekode: String)

val aktivitetsfilterValidCodes: Lazy<Array<ValidCode>> = lazy {
    Gson().fromJson<Array<ValidCode>>(KafkaPosterApplication::class.java.getResource("/aktivitetsfilter.json").readText(), Array<ValidCode>::class.java)
}

val filterOnActivityCodes: Filter = { record ->
    try {
        val obj = JsonParser.parseString(record.value()) as JsonObject
        val aktivitetskode = obj["aktivitetskode"].asString
        val aktivitetsgruppekode = obj["aktivitetsgruppekode"].asString
        val validCodes = aktivitetsfilterValidCodes.value
        (validCodes.any { it.aktivitetskode == aktivitetskode && it.aktivitetsgruppekode == aktivitetsgruppekode })
    } catch (e: Exception) {
        File("/tmp/filterOnActivityCodesFail").appendText("$record\nMESSAGE ${e.message}\n\n")
        throw (RuntimeException("Exception at filter on activity codes, partition ${record.partition()} offset ${record.offset()}, message " + e.message))
    }
}
