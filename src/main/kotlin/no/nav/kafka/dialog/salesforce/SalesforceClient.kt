package no.nav.kafka.dialog.salesforce

import no.nav.kafka.dialog.env_HTTPS_PROXY
import org.apache.http.HttpHost
import org.apache.http.client.config.RequestConfig
import org.apache.http.impl.client.HttpClients
import org.http4k.client.ApacheClient
import org.http4k.core.Headers
import org.http4k.core.HttpHandler
import org.http4k.core.Method
import org.http4k.core.Request
import org.http4k.core.Response
import java.net.URI

const val SALESFORCE_VERSION = "v61.0"

class SalesforceClient(
    private val httpClient: HttpHandler = apacheClient(),
    private val accessTokenHandler: AccessTokenHandler = DefaultAccessTokenHandler()
) {
    fun postRecords(kafkaMessages: Set<KafkaMessage>): Response {

        val requestBody = SFsObjectRest(records = kafkaMessages).toJson()

        val dstUrl = "${accessTokenHandler.instanceUrl}/services/data/$SALESFORCE_VERSION/composite/sobjects"

        val headers: Headers =
            listOf(
                "Authorization" to "Bearer ${accessTokenHandler.accessToken}",
                "Content-Type" to "application/json;charset=UTF-8"
            )

        val request = Request(Method.POST, dstUrl).headers(headers).body(requestBody)

        return httpClient(request)
    }
}

fun apacheClient(httpsProxy: String? = System.getenv(env_HTTPS_PROXY)): HttpHandler =
    if (httpsProxy == null) {
        ApacheClient()
    } else {
        val up = URI(httpsProxy)
        ApacheClient(
            client =
                HttpClients.custom()
                    .setDefaultRequestConfig(
                        RequestConfig.custom()
                            .setProxy(HttpHost(up.host, up.port, up.scheme))
                            .build()
                    )
                    .build()
        )
    }
