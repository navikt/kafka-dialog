package no.nav.kafka.dialog

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.google.gson.JsonPrimitive
import java.io.File
import java.time.Instant
import mu.KotlinLogging
import org.http4k.client.ApacheClient
import org.http4k.core.HttpHandler
import org.http4k.core.Method
import org.http4k.core.Request
import org.http4k.core.Status

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
        throw RuntimeException("Unable to parse event to remove adtext property, offset $offset, message: ${e.message}")
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
        throw RuntimeException("Unable to replace longs to instants in modifier, offset $offset, message ${e.message}")
    }
}

val lookUpApacheClient: Lazy<HttpHandler> = lazy { ApacheClient() } // No need for proxy

fun lookUpArenaActivityDetails(input: String, offset: Long): String {
    try {
        val obj = JsonParser.parseString(input) as JsonObject
        val aktivitetsId = obj["after"].asJsonObject.get("AKTIVITET_ID").asLong

        val client = lookUpApacheClient.value
        val uri = "${env(env_ARENA_HOST)}/arena/api/v1/arbeidsgiver/aktivitet?aktivitetId=$aktivitetsId"
        val request = Request(Method.GET, uri)
        val response = client.invoke(request)

        File("/tmp/arenaresponse").writeText(response.toMessage())
        if (response.status == Status.NO_CONTENT) {
            val log = KotlinLogging.logger { }
            log.warn("No content found for aktivitetsid $aktivitetsId")
            return """{"aktivitetskode":"NO_CONTENT","aktivitetsgruppekode":"NO_CONTENT"}"""
        } else if (response.status != Status.OK) {
            File("/tmp/arenaResponseUnsuccessful").writeText("At offset: $offset\n" + response.toMessage())
            throw RuntimeException("Unsuccessful response from Arena at lookup, offset $offset")
        }
        return response.bodyString()
    } catch (e: Exception) {
        throw RuntimeException("Unable to lookup activity details, offset $offset, message ${e.message}")
    }
}
