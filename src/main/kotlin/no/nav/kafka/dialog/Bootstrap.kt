package no.nav.kafka.dialog

val application: App = when (env(env_DEPLOY_APP)) {
    "sf-dialogmote" -> KafkaPosterApplication<String, String>(envAsSettings(env_POSTER_SETTINGS), ::replaceNumbersWithInstants)
    "sf-stilling" -> KafkaPosterApplication<String, String>(envAsSettings(env_POSTER_SETTINGS), ::removeAdTextProperty)
    // "sf-arbeidsgiveraktivitet" -> KafkaPosterApplication<String, String>(envAsSettings(env_POSTER_SETTINGS), ::lookUpArenaActivityDetails)
    "sf-arbeidsgiveraktivitet" -> ArenaTestApplication()
    else -> KafkaPosterApplication<String, String>(envAsSettings(env_POSTER_SETTINGS))
}

fun main() = application.start()
