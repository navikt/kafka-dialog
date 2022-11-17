package no.nav.kafka.dialog.salesforce

import com.github.benmanes.caffeine.cache.Cache
import com.github.benmanes.caffeine.cache.Caffeine
import java.util.UUID
import java.util.concurrent.TimeUnit
import mu.KotlinLogging
import no.nav.kafka.dialog.AnEnvironment
import no.nav.kafka.dialog.EV_sfVersion
import no.nav.kafka.dialog.JWTClaimBase
import no.nav.kafka.dialog.decodeB64
import no.nav.sf.library.SFsObjectRest
import no.nav.sf.library.SFsObjectStatusBase
import no.nav.sf.library.toJson
import org.http4k.core.HttpHandler
import org.http4k.core.Method
import org.http4k.core.Request
import org.http4k.core.Response
import org.http4k.core.Status
import org.http4k.core.then
import org.http4k.filter.ServerFilters
import org.http4k.lens.Header
import org.http4k.lens.Query
import org.http4k.routing.bind
import org.http4k.routing.routes

/**
 * Salesforce simplified mock, scoped for posting of KafkaMessage__c - 2 endpoints
 * - issue of access token iff authorized, time-to-live as configured
 * - post of KafkaMessage__c records
 */

private val log = KotlinLogging.logger { }

const val SF_MOCK_QUERY_grantType = "grant_type"
const val SF_MOCK_VALUE_grantType = "urn:ietf:params:oauth:grant-type:jwt-bearer"

const val SF_MOCK_QUERY_assertion = "assertion" // value to be verified in received request

const val SF_MOCK_HEADER_authorization = "Authorization" // value to be verified in received request

const val SF_MOCK_VALUE_clientID = "validClientID"
const val SF_MOCK_VALUE_username = "validUsername"

const val SF_MOCK_VALUE_tokenType = "Bearer"

// NB! THE MOCK PATHS are used in related client.
// Thus, these reflects the real world - DO NOT CHANGE unless the real world change...
const val SF_MOCK_PATH_oAuth = "/services/oauth2/token"
val SF_MOCK_PATH_sObject = lazy { "/services/data/${AnEnvironment.getEnvOrDefault(EV_sfVersion, "v48.0")}/composite/sobjects" }

val SF_PATH_composite = lazy { "/services/data/${AnEnvironment.getEnvOrDefault(EV_sfVersion, "v48.0")}/composite/" }

const val SF_MOCK_AT_TTL = 3L
val SF_MOCK_AT_UNIT = TimeUnit.MINUTES

class SalesforceMock(
    accessTokenTTL: Long = SF_MOCK_AT_TTL,
    timeUnit: TimeUnit = SF_MOCK_AT_UNIT,
    private val instanceUrl: String = ""
) {

    private val cache: Cache<String, String> = Caffeine.newBuilder()
            .expireAfterWrite(accessTokenTTL, timeUnit)
            .maximumSize(1_000)
            .build()

    private fun Cache<String, String>.exists(at: String): Boolean = this.getIfPresent(at)?.let { true } ?: false

    private val accessToken: () -> String = { UUID.randomUUID().toString() }
    private val atJSON: (String) -> String = { at ->
        """{"access_token":"$at","instance_url":"$instanceUrl","id":"","token_type":"$SF_MOCK_VALUE_tokenType","issued_at":"mock","signature":"mock"}"""
    }
    private val respAccessToken: (String) -> Response = { at -> Response(Status.OK).body(at) }
    private val respUnauthorized = Response(Status.UNAUTHORIZED)

    // use of lenses
    private val authoHeader = Header.required(SF_MOCK_HEADER_authorization)
    private val grantQuery = Query.required(SF_MOCK_QUERY_grantType)
    private val assertQuery = Query.required(SF_MOCK_QUERY_assertion)

    // simplified - not testing time or signed data...
    private fun claimIsOk(c: String): Boolean = when (val jc = JWTClaimBase.fromJson(c.split('.')[1].decodeB64().toString(Charsets.UTF_8))) {
        is JWTClaimBase.Missing -> false
        is JWTClaimBase.Exists -> jc.iss == SF_MOCK_VALUE_clientID && jc.sub == SF_MOCK_VALUE_username
    }

    private fun Request.isAuthorizedForAccessToken(): Boolean =
            (grantQuery(this) == SF_MOCK_VALUE_grantType) && claimIsOk(assertQuery(this))

    private fun Request.issueAccessToken(): Response = when (this.isAuthorizedForAccessToken()) {
        true -> {
            val at = accessToken().also { cache.put(it, it) }
            log.info { "Issue of access token - OK($at)" }
            respAccessToken(atJSON(at))
        }
        else -> {
            log.error { "Issue of access token - UNAUTHORIZED" }
            respUnauthorized
        }
    }

    private fun String.validAccessToken(): Boolean = this.let {
        val (bearer, at) = it.split(" ")
        (bearer == SF_MOCK_VALUE_tokenType) && cache.exists(at)
    }

    // simplified - not testing the records
    private fun Request.managePost(): Response = when (authoHeader(this).validAccessToken()) {
        false -> {
            log.error { "POST sObject - UNAUTHORIZED" }
            respUnauthorized
        }
        else -> {
            val sObjects = SFsObjectRest.fromJson(bodyString())
            log.info { "POST sObject - OK (${sObjects.records.size} records received)" }
            Response(Status.OK).body(
                    List(sObjects.records.size) { SFsObjectStatusBase.SFsObjectStatus(success = true) }.toJson()
            )
        }
    }

    val client: HttpHandler
        get() = ServerFilters.CatchLensFailure().then(
                routes(
                        SF_MOCK_PATH_oAuth bind Method.POST to { it.issueAccessToken() },
                        SF_MOCK_PATH_sObject.value bind Method.POST to { it.managePost() }
                )
        )
}
