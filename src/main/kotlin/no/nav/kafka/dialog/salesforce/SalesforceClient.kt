package no.nav.kafka.dialog.salesforce

import no.nav.kafka.dialog.config_SALESFORCE_API_VERSION
import no.nav.kafka.dialog.env
import no.nav.kafka.dialog.env_HTTPS_PROXY
import no.nav.sf.pdl.kafka.salesforce.NewAccessTokenHandler
import no.nav.sf.pubsub.token.MigratingAccessTokenHandler
import okhttp3.OkHttpClient
import org.http4k.client.OkHttp
import org.http4k.core.Headers
import org.http4k.core.HttpHandler
import org.http4k.core.Method
import org.http4k.core.Request
import org.http4k.core.Response
import java.io.File
import java.net.InetSocketAddress
import java.net.Proxy
import java.net.URI

val SALESFORCE_VERSION = env(config_SALESFORCE_API_VERSION)

class SalesforceClient(
    private val httpClient: HttpHandler = okHttpClient(),
    private val accessTokenHandler: AccessTokenHandler =
        NewAccessTokenHandler(),
) {
    fun postRecords(kafkaMessages: Set<KafkaMessage>): Response {
        val requestBody = SFsObjectRest(records = kafkaMessages).toJson()

        val dstUrl = "${accessTokenHandler.instanceUrl}/services/data/$SALESFORCE_VERSION/composite/sobjects"

        val headers: Headers =
            listOf(
                "Authorization" to "Bearer ${accessTokenHandler.accessToken}",
                "Content-Type" to "application/json;charset=UTF-8",
            )

        val request = Request(Method.POST, dstUrl).headers(headers).body(requestBody)

        return httpClient(request)
    }
}

fun okHttpClient(httpsProxy: String? = System.getenv(env_HTTPS_PROXY)): HttpHandler =
    if (httpsProxy == null) {
        val dir = File("/tmp/files")
        dir.mkdirs() // ensures /tmp/files exists
        File("/tmp/files/noproxy").writeText("No proxy in use")
        OkHttp()
    } else {
        val up = URI(httpsProxy)

        val proxy =
            Proxy(
                Proxy.Type.HTTP,
                InetSocketAddress(up.host, up.port),
            )

        val client =
            OkHttpClient
                .Builder()
                .proxy(proxy)
                .build()

        val dir = File("/tmp/files")
        dir.mkdirs() // ensures /tmp/files exists
        File("/tmp/files/proxy").writeText("Proxy is in use")

        OkHttp(client)
    }
