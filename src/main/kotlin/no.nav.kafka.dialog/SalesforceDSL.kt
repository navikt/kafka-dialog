package no.nav.kafka.dialog

import io.prometheus.client.Gauge
import io.prometheus.client.Histogram
import java.security.KeyStore
import java.security.PrivateKey
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.Serializable
import mu.KotlinLogging
import no.nav.sf.library.SF_MOCK_PATH_oAuth
import no.nav.sf.library.SF_MOCK_PATH_sObject
import no.nav.sf.library.SF_PATH_composite
import no.nav.sf.library.SalesforceMock
import org.http4k.client.ApacheClient
import org.http4k.core.HttpHandler
import org.http4k.core.Method
import org.http4k.core.Request
import org.http4k.core.Response
import org.http4k.core.Status

private val log = KotlinLogging.logger { }

// Salesforce environment dependencies
const val EV_sfInstance = "SF_INSTANCE"
const val EV_sfTokenHost = "SF_TOKENHOST"
const val EV_sfVersion = "SF_VERSION"
const val EV_httpsProxy = "HTTPS_PROXY"

const val EV_sfObject = "SF_OBJECT"
// Salesforce vault dependencies
const val VAULT_sfClientID = "SFClientID"
const val VAULT_sfUsername = "SFUsername"

// Salesforce vault dependencies related to keystore for signed JWT
const val VAULT_keystoreB64 = "keystoreJKSB64"
const val VAULT_ksPassword = "KeystorePassword"
const val VAULT_pkAlias = "PrivateKeyAlias"
const val VAULT_pkPwd = "PrivateKeyPassword"

/**
 * Getting access token from Salesforce is a little bit involving due to
 * - Need a private key from a key store where the public key is in the connected app in Salesforce
 * - Need to sign a claim (some facts about salesforce) with the private key
 * - Need a access token request using the signed claim
 */

sealed class KeystoreBase {

    object Missing : KeystoreBase()

    data class Exists(val privateKey: PrivateKey) : KeystoreBase() {

        fun sign(data: ByteArray): SignatureBase = runCatching {
            java.security.Signature.getInstance("SHA256withRSA")
                    .apply {
                        initSign(privateKey)
                        update(data)
                    }
                    .run { SignatureBase.Exists(sign().encodeB64()) }
        }
                .onFailure {
                    log.error { "Signing failed - ${it.localizedMessage}" }
                }
                .getOrDefault(SignatureBase.Missing)
    }

    fun signCheckIsOk(): Boolean = this.fold<KeystoreBase, Missing, Exists, Boolean>(
            { false },
            { it.sign("something".toByteArray())
                    .fold<SignatureBase, SignatureBase.Missing, SignatureBase.Exists, Boolean>(
                            { false }, { true }
                    )
            })

    companion object {
        fun fromBase64(ksB64: String, ksPwd: String, pkAlias: String, pkPwd: String): KeystoreBase = runCatching {
            Exists(
                    KeyStore.getInstance("JKS")
                            .apply { load(ksB64.decodeB64().inputStream(), ksPwd.toCharArray()) }
                            .run { getKey(pkAlias, pkPwd.toCharArray()) as PrivateKey }
            )
        }
                .onFailure {
                    log.error { "Keystore issues - ${it.localizedMessage}" }
                }
                .getOrDefault(Missing)
    }
}

sealed class SignatureBase {
    object Missing : SignatureBase()
    data class Exists(val content: String) : SignatureBase()
}

sealed class JWTClaimBase {
    object Missing : JWTClaimBase()

    @Serializable
    data class Exists(
        val iss: String,
        val aud: String,
        val sub: String,
        val exp: String
    ) : JWTClaimBase() {

        private fun toJson(): String = json.stringify(serializer(), this)
        fun addHeader(): String = "${Header().toJson().encodeB64UrlSafe()}.${this.toJson().encodeB64UrlSafe()}"
    }

    companion object {
        // use in MOCK
        fun fromJson(data: String): JWTClaimBase = runCatching {
            json.parse(Exists.serializer(), data) }
                .onFailure {
                    log.error { "Parsing of JWTClaim failed - ${it.localizedMessage}" }
                }
                .getOrDefault(Missing)
    }

    @Serializable
    data class Header(val alg: String = "RS256") {
        fun toJson(): String = json.stringify(serializer(), this)
    }
}

sealed class SFAccessToken {
    object Missing : SFAccessToken()

    @Serializable
    data class Exists(
        val access_token: String = "",
        val scope: String = "",
        val instance_url: String = "",
        val id: String = "",
        val token_type: String = "",
        val issued_at: String = "",
        val signature: String = ""
    ) : SFAccessToken() {

        fun getPostRequest(sObjectPath: String): Request = log.debug { "Doing getPostRequest with instance_url: $instance_url sObjectPath: $sObjectPath" }.let {
            Request(
                    Method.POST, "$instance_url$sObjectPath")
                    .header("Authorization", "$token_type $access_token")
                    .header("Content-Type", "application/json;charset=UTF-8").also { log.debug { "Returning Request: $it" } }
        }
    }

    companion object {
        fun fromJson(data: String): SFAccessToken = runCatching { json.parse(Exists.serializer(), data) }
                .onFailure {
                    log.error { "Parsing of authorization response failed - ${it.localizedMessage}" }
                }
                .getOrDefault(Missing)
    }
}

// some metrics for Salesforce client
data class SFMetrics(
    val responseLatency: Histogram = Histogram
            .build()
            .name("sf_response_latency_seconds_histogram")
            .help("Salesforce response latency since last restart")
            .register(),
    val failedAccessTokenRequest: Gauge = Gauge
            .build()
            .name("sf_failed_access_token_request_gauge")
            .help("No. of failed access token requests to Salesforce since last restart")
            .register(),
    val postRequest: Gauge = Gauge
            .build()
            .name("sf_post_request_gauge")
            .help("No. of post requests to Salesforce since last restart")
            .register(),
    val accessTokenRefresh: Gauge = Gauge
            .build()
            .name("sf_access_token_refresh_gauge")
            .help("No. of required access token refresh to Salesforce since last restart")
            .register()
) {
    fun clear() {
        failedAccessTokenRequest.clear()
        postRequest.clear()
        accessTokenRefresh.clear()
    }
}

class SalesforceClient(
    private val useCompositePath: Boolean = false,
    private val httpClient: Lazy<HttpHandler> = getHttpClientByInstance(SalesforceMock(10L, TimeUnit.SECONDS)),
    private val tokenHost: Lazy<String> = lazy { AnEnvironment.getEnvOrDefault(EV_sfTokenHost) },
    private val clientID: String = AVault.getSecretOrDefault(VAULT_sfClientID),
    private val username: String = AVault.getSecretOrDefault(VAULT_sfUsername),
    private val keystore: KeystoreBase = KeystoreBase.fromBase64(
        AVault.getSecretOrDefault(VAULT_keystoreB64),
        AVault.getSecretOrDefault(VAULT_ksPassword),
        AVault.getSecretOrDefault(VAULT_pkAlias),
        AVault.getSecretOrDefault(VAULT_pkPwd)
),
    private val retryDelay: Long = 1_500,
    transferAT: SFAccessToken = SFAccessToken.Missing
) {
    private val claim: JWTClaimBase.Exists
        get() = JWTClaimBase.Exists(
            iss = clientID,
            aud = tokenHost.value,
            sub = username,
            exp = ((System.currentTimeMillis() / 1000) + 300).toString()
        )

    private val tokenURL: String
        get() = "${tokenHost.value}$SF_MOCK_PATH_oAuth"

    private val accessTokenRequest: Request
        get() = claim.let { c ->

            // try to sign the complete claim (header included) using private key
            val fullClaimSignature = keystore
                    .fold<
                            KeystoreBase,
                            KeystoreBase.Missing,
                            KeystoreBase.Exists,
                            SignatureBase>({ SignatureBase.Missing }, { it.sign(c.addHeader().toByteArray()) })

            // try to get the signed content
            val content = fullClaimSignature.fold<
                    SignatureBase,
                    SignatureBase.Missing,
                    SignatureBase.Exists,
                    String>({ "" }, { it.content })

            // build the request, the assertion to be verified by host with related public key
            Request(Method.POST, tokenURL)
                    .header("Content-Type", "application/x-www-form-urlencoded")
                    .query("grant_type", "urn:ietf:params:oauth:grant-type:jwt-bearer")
                    .query("assertion", "${c.addHeader()}.$content")
        }

    private fun Response.parseAccessToken(): SFAccessToken = when (status) {
        Status.OK -> SFAccessToken.fromJson(bodyString())
        else -> {
            metrics.failedAccessTokenRequest.inc()
            // Report error only after last retry
            log.warn { "Failed access token request at parseAccessToken- ${status.description} ($status) "/*:  this.headers: ${this.headers} this: $this this.body: ${this.body}. Bodystring ${bodyString()}" */ }
            SFAccessToken.Missing
        }
    }

    // should do tailrec, but due to only a few iterations ...
    private fun getAccessTokenWithRetries(retry: Int = 1, maxRetries: Int = 4): SFAccessToken =
            httpClient.value
                    .measure(accessTokenRequest, metrics.responseLatency)

                    .parseAccessToken()
                    .fold<SFAccessToken, SFAccessToken.Missing, SFAccessToken.Exists, SFAccessToken>(
                            {
                                if (retry > maxRetries) it.also { log.error { "Fail to fetch access token (including retries)" } }
                                else {
                                    runCatching { runBlocking { delay(retry * retryDelay) } }
                                    getAccessTokenWithRetries(retry + 1, maxRetries)
                                }
                            },
                            { it }
                    )

    private var accessToken: SFAccessToken = transferAT

    fun enablesObjectPost(doSomething: ((String) -> Response) -> Unit): Boolean {

        val doPostRequest: (String, SFAccessToken.Exists) -> Response = { b, at ->
            httpClient.value.measure(at.getPostRequest(if (useCompositePath) SF_PATH_composite.value else SF_MOCK_PATH_sObject.value).body(b), metrics.responseLatency)
                    .also { metrics.postRequest.inc() }
        }

        fun doPost(id: String, at: SFAccessToken.Exists): Pair<Response, SFAccessToken> =
                doPostRequest(id, at).let { r ->
                    log.debug { "SF doPost initial response with http status - ${r.status} "/*and body ${r.body} and headers ${r.headers}" */ }
                    when (r.status) {
                        Status.UNAUTHORIZED -> {
                            metrics.accessTokenRefresh.inc()
                            // try to get new access token
                            getAccessTokenWithRetries()
                                    .fold<
                                            SFAccessToken,
                                            SFAccessToken.Missing,
                                            SFAccessToken.Exists,
                                            Pair<Response, SFAccessToken>>(
                                            { Pair(Response(Status.UNAUTHORIZED), it) },
                                            { Pair(doPostRequest(id, it), it) }
                                    )
                        }
                        Status.OK -> {
                            log.debug { "Returned status OK" }
                            Pair(r, at)
                        }
                        Status.CREATED -> {
                            log.info { "Returned status CREATED" }
                            Pair(r, at)
                        }
                        else -> {
                            log.error { "SF doPost issue with http status - ${r.status} "/*and body ${r.body} and headers ${r.headers}"*/ }
                            if (r.status.code == 503) {
                                kCommonMetrics.consumeErrorServiceUnavailable.inc()
                                kErrorState = ErrorState.SERVICE_UNAVAILABLE
                            } else {
                                kCommonMetrics.unknownErrorConsume.inc()
                                kErrorState = ErrorState.UNKNOWN_ERROR
                            }
                            Pair(r, at)
                        }
                    }
                }

        // in case of missing access token from last invocation or very first start, try refresh
        if (accessToken is SFAccessToken.Missing) accessToken = getAccessTokenWithRetries()

        return accessToken.fold<SFAccessToken, SFAccessToken.Missing, SFAccessToken.Exists, Boolean>(
                { false },
                {
                    log.info { "SF access token exists" }
                    val transfer: (String) -> Response = { b ->
                        doPost(b, it).let { p ->
                            if (it != p.second) accessToken = p.second
                            p.first
                        }
                    }
                    runCatching { doSomething(transfer) }
                            .onSuccess {
                                log.info { "Salesforce - end of sObject post availability with success" }
                            }
                            .onFailure {
                                log.error { "Salesforce - end of sObject post availability failed - ${it.localizedMessage} message - ${it.message }" }
                            }
                    true
                }
        )
    }

    // no need to re-read env. variables (static) and transfer access token
    // vault related params are re-read from vault
    fun copyRelevant(): SalesforceClient = SalesforceClient(
            httpClient = httpClient,
            tokenHost = tokenHost,
            transferAT = accessToken
    )

    companion object {

        val metrics = SFMetrics()

        fun getHttpClientByInstance(mockRef: SalesforceMock): Lazy<HttpHandler> = lazy {
            log.info { "crm-utilities : getHttpClientByInstance - SFInstance : ${AnEnvironment.getEnvOrDefault(EV_sfInstance, "LOCAL (Default)")} SFVersion : ${AnEnvironment.getEnvOrDefault(EV_sfVersion, "(Default)")} VaultInstance : ${AnEnvironment.getEnvOrDefault(EV_vaultInstance, "(Default)")}" }
            when (AnEnvironment.getEnvOrDefault(EV_sfInstance, "LOCAL")) {
                "LOCAL" -> mockRef.client
                "PREPROD" -> ApacheClient.supportProxy(AnEnvironment.getEnvOrDefault(EV_httpsProxy))
                "PRODUCTION" -> ApacheClient.supportProxy(AnEnvironment.getEnvOrDefault(EV_httpsProxy))
                else -> ApacheClient()
            }
        }
    }
}
