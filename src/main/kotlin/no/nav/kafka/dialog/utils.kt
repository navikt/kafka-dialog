package no.nav.kafka.dialog

import com.google.gson.Gson
import io.prometheus.client.Histogram
import java.io.File
import java.net.URI
import kotlin.streams.toList
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

private val log = KotlinLogging.logger { }

val gson = Gson()

fun ApacheClient.supportProxy(httpsProxy: String): HttpHandler = httpsProxy.let { p ->
    when {
        p.isEmpty() -> this()
        else -> {
            val up = URI(p)
            this(client =
            HttpClients.custom()
                .setDefaultRequestConfig(
                    RequestConfig.custom()
                        .setProxy(HttpHost(up.host, up.port, up.scheme))
                        .setRedirectsEnabled(false)
                        .setCookieSpec(CookieSpecs.IGNORE_COOKIES)
                        .build())
                .build()
            )
        }
    }
}

fun HttpHandler.measure(r: Request, m: Histogram): Response =
    m.startTimer().let { rt -> this(r).also {
        rt.observeDuration() // Histogram will store response time
        File("/tmp/lastTokenCall").writeText("uri: ${r.uri}, method: ${r.method}, body: ${r.body}, headers ${r.headers}")
    } }

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
 * Shortcuts for fetching environment variables
 */
fun env(env: String): String { return System.getenv(env) }

fun envAsLong(env: String): Long { return System.getenv(env).toLong() }

fun envAsInt(env: String): Int { return System.getenv(env).toInt() }

fun envAsList(env: String): List<String> { return System.getenv(env).split(",").map { it.trim() }.toList() }

fun envAsSettings(env: String): List<KafkaToSFPoster.Settings> { return envAsList(env).stream().map { KafkaToSFPoster.Settings.valueOf(it) }.toList() }
