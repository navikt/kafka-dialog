package no.nav.kafka.dialog

import no.nav.syfo.dialogmote.avro.KDialogmoteStatusEndring

val application = when (env(env_DEPLOY_APP)) {
    "sf-dialogmote" -> KafkaPosterApplication<String, KDialogmoteStatusEndring>(envAsSettings(env_POSTER_SETTINGS))
    else -> KafkaPosterApplication<String, String>(envAsSettings(env_POSTER_SETTINGS))
}

fun main() = application.start()
