package no.nav.kafka.dialog.salesforce

import no.nav.kafka.dialog.env_HTTPS_PROXY
import okhttp3.OkHttpClient
import org.http4k.client.OkHttp
import org.http4k.core.Headers
import org.http4k.core.HttpHandler
import org.http4k.core.Method
import org.http4k.core.Request
import org.http4k.core.Response
import java.net.InetSocketAddress
import java.net.Proxy
import java.net.URI

const val SALESFORCE_VERSION = "v61.0"

class SalesforceClient(
    private val httpClient: HttpHandler = okHttpClient(),
    private val accessTokenHandler: AccessTokenHandler = DefaultAccessTokenHandler(),
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

        OkHttp(client)
    }
