package no.nav.kafka.dialog

const val env_POSTER_SETTINGS = "POSTER_SETTINGS"
const val env_MS_BETWEEN_WORK = "MS_BETWEEN_WORK"

val parsedsettings = envAsSettings(env_POSTER_SETTINGS)

val application = KafkaPosterApplication<String, String>(listOf(KafkaToSFPoster.Settings.NO_POST))

fun main() = application.start()
