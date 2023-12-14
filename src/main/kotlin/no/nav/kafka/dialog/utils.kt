package no.nav.kafka.dialog

import com.google.gson.Gson
import io.prometheus.client.Histogram
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import mu.KotlinLogging
import org.apache.http.HttpHost
import org.apache.http.client.config.CookieSpecs
import org.apache.http.client.config.RequestConfig
import org.apache.http.impl.client.HttpClients
import org.http4k.client.ApacheClient
import org.http4k.core.HttpHandler
import org.http4k.core.Request
import org.http4k.core.Response
import java.io.File
import java.net.URI
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import kotlin.streams.toList

private val log = KotlinLogging.logger { }

val gson = Gson()

val isOnPrem: Boolean = env(env_DEPLOY_CLUSTER) == "dev-fss" || env(env_DEPLOY_CLUSTER) == "prod-fss"

fun ApacheClient.supportProxy(httpsProxy: String): HttpHandler = httpsProxy.let { p ->
    when {
        p.isEmpty() -> this()
        else -> {
            val up = URI(p)
            this(
                client =
                    HttpClients.custom()
                        .setDefaultRequestConfig(
                            RequestConfig.custom()
                                .setProxy(HttpHost(up.host, up.port, up.scheme))
                                .setRedirectsEnabled(false)
                                .setCookieSpec(CookieSpecs.IGNORE_COOKIES)
                                .build()
                        )
                        .build()
            )
        }
    }
}

fun HttpHandler.measure(r: Request, m: Histogram): Response =
    m.startTimer().let { rt ->
        this(r).also {
            rt.observeDuration() // Histogram will store response time
            File("/tmp/lastTokenCall").writeText("uri: ${r.uri}, method: ${r.method}, body: ${r.body}, headers ${r.headers}")
        }
    }

fun ByteArray.encodeB64(): String = org.apache.commons.codec.binary.Base64.encodeBase64URLSafeString(this)
fun String.encodeB64UrlSafe(): String = org.apache.commons.codec.binary.Base64.encodeBase64URLSafeString(this.toByteArray())
fun String.encodeB64(): String = org.apache.commons.codec.binary.Base64.encodeBase64String(this.toByteArray())
fun String.decodeB64(): ByteArray = org.apache.commons.codec.binary.Base64.decodeBase64(this)

/**
 * conditionalWait
 * Interruptable wait function
 */
fun conditionalWait(ms: Long) =
    runBlocking {

        log.info { "Will wait $ms ms" }

        val cr = launch {
            runCatching { delay(ms) }
                .onSuccess { log.info { "waiting completed" } }
                .onFailure { log.info { "waiting interrupted" } }
        }

        tailrec suspend fun loop(): Unit = when {
            cr.isCompleted -> Unit
            ShutdownHook.isActive() || PrestopHook.isActive() -> cr.cancel()
            else -> {
                delay(250L)
                loop()
            }
        }

        loop()
        cr.join()
    }

/**
 * offsetMapsToText
 * Create a string to represent the spans of offsets that has been posted
 * Example: 0:[12034-16035],1:[11240-15273]
 */
fun offsetMapsToText(firstOffset: MutableMap<Int, Long>, lastOffset: MutableMap<Int, Long>): String {
    if (firstOffset.isEmpty()) return "NONE"
    return firstOffset.keys.sorted().map {
        "$it:[${firstOffset[it]}-${lastOffset[it]}]"
    }.joinToString(",")
}

/**
 * Representation of Kafka data used for modifiers and filter, final state for salesforce format is found in SalesforceDSL KafkaMessage
 */
data class KafkaData(val topic: String, val offset: Long, val partition: Int, val key: String, val value: String, val originValue: String)

private fun Long.parsedAsMillisSecondsTime(): String {
    val seconds = (this / 1000) % 60
    val minutes = (this / (1000 * 60)) % 60
    val hours = (this / (1000 * 60 * 60))

    return "($hours hours, $minutes minutes, $seconds seconds)"
}

fun findMillisToHourNextDay(hour: Int): Long {
    val currentDateTime = LocalDateTime.now()
    val zone = ZoneId.systemDefault()
    val givenHourNextDay = currentDateTime.plusDays(1).with(LocalTime.of(hour, 0)).atZone(zone)

    val result = givenHourNextDay.toInstant().toEpochMilli() - System.currentTimeMillis()

    log.info { "Will sleep until hour $hour next day, that is ${result.parsedAsMillisSecondsTime()} from now" }
    return result
}

/**
 * Shortcuts for fetching environment variables
 */
fun env(env: String): String = System.getenv(env)

fun envAsLong(env: String): Long = System.getenv(env).toLong()

fun envAsInt(env: String): Int = System.getenv(env).toInt()

fun envAsList(env: String): List<String> = System.getenv(env).split(",").map { it.trim() }

fun envAsLocalDates(env: String): List<LocalDate> = System.getenv(env).split(",")
    .map { LocalDate.parse(it.trim()) }

fun envAsSettings(env: String): List<KafkaToSFPoster.Settings> = System.getenv(env).split(",")
    .map { KafkaToSFPoster.Settings.valueOf(it.trim()) }.toList()
