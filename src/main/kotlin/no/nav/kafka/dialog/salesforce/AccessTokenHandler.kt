package no.nav.kafka.dialog.salesforce

interface AccessTokenHandler {
    val accessToken: String
    val instanceUrl: String

    fun refreshToken() // To refresh in advance
}
