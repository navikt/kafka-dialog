package no.nav.kafka.dialog

import io.prometheus.client.Histogram
import java.io.File
import java.net.URI
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonConfiguration
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

// Base vault dependencies
const val EV_vaultInstance = "VAULT_INSTANCE"

// program name will default to current directory name
val PROGNAME by lazy { System.getProperty("user.dir").split("/").last() }

val json = Json(JsonConfiguration.Stable)

// Non strict variant of json parser
val jsonNonStrict = Json(JsonConfiguration.Stable.copy(ignoreUnknownKeys = true, isLenient = true))

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
            rt.observeDuration()
            File("/tmp/lastTokenCall").writeText("uri: ${r.uri}, method: ${r.method}, body: ${r.body}, headers ${r.headers}")
        } }

// general pattern for sealed class with 2 options; missing (left side) versus something else (right side)
inline fun <reified B, reified L : B, reified R : B, T> B.fold(lOp: (L) -> T, rOp: (R) -> T): T = when (this) {
    is L -> lOp(this)
    else -> rOp(this as R)
}

fun ByteArray.encodeB64(): String = org.apache.commons.codec.binary.Base64.encodeBase64URLSafeString(this)
fun String.encodeB64UrlSafe(): String = org.apache.commons.codec.binary.Base64.encodeBase64URLSafeString(this.toByteArray())
fun String.encodeB64(): String = org.apache.commons.codec.binary.Base64.encodeBase64String(this.toByteArray())
fun String.decodeB64(): ByteArray = org.apache.commons.codec.binary.Base64.decodeBase64(this)

interface AnEnvironment {
    companion object {
        fun getEnvOrDefault(k: String, d: String = ""): String = runCatching { System.getenv(k) ?: d }.getOrDefault(d)
    }
}

data class VaultPaths(val secrets: String, val serviceUser: String)

enum class VaultInstancePaths(val path: VaultPaths) {
    LOCAL(VaultPaths("${System.getProperty("user.dir")}/.vault/", "${System.getProperty("user.dir")}/.vault/")),
    SANDBOX(VaultPaths("<NOSANDBOX>", "<NOSANDBOX>")),
    PREPROD(VaultPaths("/var/run/secrets/nais.io/vault/", "/var/run/secrets/nais.io/serviceuser/")),
    PRODUCTION(VaultPaths("/var/run/secrets/nais.io/vault/", "/var/run/secrets/nais.io/serviceuser/")),
    GCP(VaultPaths("/var/run/secrets/", "/var/run/secrets/"))
}

private fun String.getVaultInstance(): VaultInstancePaths =
        if (VaultInstancePaths.values().map { it.name }.contains(this.toUpperCase())) VaultInstancePaths.valueOf(this)
        else VaultInstancePaths.LOCAL

interface AVault {
    companion object {
        private fun getOrDefault(file: File, d: String): String = runCatching { file.readText(Charsets.UTF_8) }
                .onSuccess { log.info { "Vault- read ${file.absolutePath}" } }
                .onFailure { log.error { "Vault- couldn't read ${file.absolutePath}" } }
                .getOrDefault(d)

        fun getSecretOrDefault(
            k: String,
            d: String = "",
            p: VaultPaths = AnEnvironment.getEnvOrDefault(EV_vaultInstance, "LOCAL").getVaultInstance().path
        ): String =
                getOrDefault(File("${p.secrets}$k"), d)

        fun getServiceUserOrDefault(
            k: String,
            d: String = "",
            p: VaultPaths = AnEnvironment.getEnvOrDefault(EV_vaultInstance, "LOCAL").getVaultInstance().path
        ): String =
                getOrDefault(File("${p.serviceUser}$k"), d)
    }
}

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
