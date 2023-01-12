package no.nav.kafka.dialog

val application = when (env(env_DEPLOY_APP)) {
    "sf-dialogmote" -> KafkaPosterApplication<String, String>(envAsSettings(env_POSTER_SETTINGS), ::replaceNumbersWithInstants)
    "sf-stilling" -> KafkaPosterApplication<String, String>(envAsSettings(env_POSTER_SETTINGS), ::removeAdTextProperty)
    else -> KafkaPosterApplication<String, String>(envAsSettings(env_POSTER_SETTINGS))
}

fun main() = application.start()
