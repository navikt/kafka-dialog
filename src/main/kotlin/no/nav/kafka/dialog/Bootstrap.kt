package no.nav.kafka.dialog

val application = KafkaPosterApplication<String, String>(envAsSettings(env_POSTER_SETTINGS))

fun main() = application.start()
