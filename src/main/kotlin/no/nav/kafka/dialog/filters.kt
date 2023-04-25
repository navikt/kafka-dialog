package no.nav.kafka.dialog

import com.google.gson.Gson
import com.google.gson.JsonObject
import com.google.gson.JsonParser

/**
 * Not in used, but kept for reference - only approve json with tiltakstype == "MIDLERTIDIG_LONNSTILSKUDD"
 */
fun filterTiltakstypeMidlertidigLonnstilskudd(input: String, offset: Long): Boolean {
    try {
        val obj = JsonParser.parseString(input) as JsonObject
        return obj["tiltakstype"].asString == "MIDLERTIDIG_LONNSTILSKUDD"
    } catch (e: Exception) {
        throw RuntimeException("Unable to parse input for tiltakstype filter, offset $offset, message ${e.message}")
    }
}

data class ValidCode(val aktivitetskode: String, val aktivitetsgruppekode: String)

val aktivitetsfilterValidCodes: Lazy<Array<ValidCode>> = lazy {
    Gson().fromJson<Array<ValidCode>>(KafkaPosterApplication::class.java.getResource("/aktivitetsfilter.json").readText(), Array<ValidCode>::class.java)
}

fun filterOnActivityCodes(input: String, offset: Long): Boolean {
    try {
        val obj = JsonParser.parseString(input) as JsonObject
        val aktivitetskode = obj["aktivitetskode"].asString
        val aktivitetsgruppekode = obj["aktivitetsgruppekode"].asString
        val validCodes = aktivitetsfilterValidCodes.value
        return (validCodes.any { it.aktivitetskode == aktivitetskode && it.aktivitetsgruppekode == aktivitetsgruppekode })
    } catch (e: Exception) {
        throw (RuntimeException("Exception at filter on activity codes, offset $offset, message " + e.message))
    }
}
