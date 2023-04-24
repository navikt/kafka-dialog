package no.nav.kafka.dialog

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import java.io.File

fun filterTiltakstypeMidlertidigLonnstilskudd(input: String, offset: Long): Boolean {
    try {
        val obj = JsonParser.parseString(input) as JsonObject
        return obj["tiltakstype"].asString == "MIDLERTIDIG_LONNSTILSKUDD"
    } catch (e: Exception) {
        File("/tmp/filtertiltakstypefail").appendText("OFFSET $offset\n${input}\n\n")
        throw RuntimeException("Unable to parse input for tiltakstype filter")
    }
}
