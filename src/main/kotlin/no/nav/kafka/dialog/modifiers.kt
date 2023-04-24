package no.nav.kafka.dialog

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.google.gson.JsonPrimitive
import java.io.File
import java.time.Instant

/**
 * removeAdTextProperty
 *
 * Given a json-string - remove properties.adtext if present
 */
fun removeAdTextProperty(input: String, offset: Long): String {
    try {
        val obj = JsonParser.parseString(input) as JsonObject
        if (obj.has("properties")) {
            val array = obj["properties"].asJsonArray
            array.removeAll { (it as JsonObject)["key"].asString == "adtext" }
            obj.add("properties", array)
        }
        return obj.toString()
    } catch (e: Exception) {
        File("/tmp/removepropertyfail").appendText("OFFSET $offset\n${input}\n\n")
        throw RuntimeException("Unable to parse event to remove adtext property")
    }
}

/**
 * replaceNumbersWithInstants
 *
 * Given a json-string with Numbers in the first level of json - replace those with Instants
 */
fun replaceNumbersWithInstants(input: String, offset: Long): String {
    try {
        val obj = JsonParser.parseString(input) as JsonObject
        obj.keySet().forEach {
            if (obj[it].isJsonPrimitive) {
                if ((obj[it] as JsonPrimitive).isNumber) {
                    obj.addProperty(it, Instant.ofEpochMilli(obj.get(it).asLong).toString())
                }
            }
        }
        return obj.toString()
    } catch (e: Exception) {
        File("/tmp/replacewithinstantsfail").appendText("OFFSET $offset\n${input}\n\n")
        throw RuntimeException("Unable to replace longs to instants in modifier")
    }
}
