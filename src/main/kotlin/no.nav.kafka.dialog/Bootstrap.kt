package no.nav.kafka.dialog

const val env_POSTER_SETTINGS = "POSTER_SETTINGS"

val application = KafkaPosterApplication<String, String>(envAsSettings(env_POSTER_SETTINGS))

fun main() = application.start()
