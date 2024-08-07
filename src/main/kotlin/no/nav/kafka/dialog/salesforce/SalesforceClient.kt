package no.nav.kafka.dialog.salesforce

import org.http4k.client.ApacheClient
import org.http4k.core.Headers
import org.http4k.core.HttpHandler
import org.http4k.core.Method
import org.http4k.core.Request
import org.http4k.core.Response

const val SALESFORCE_VERSION = "v57.0"

class SalesforceClient(
    private val httpClient: HttpHandler = ApacheClient(),
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
