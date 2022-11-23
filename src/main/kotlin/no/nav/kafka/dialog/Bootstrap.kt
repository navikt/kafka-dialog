package no.nav.kafka.dialog

import no.nav.pam.stilling.ext.avro.Ad
import no.nav.syfo.dialogmote.avro.KDialogmoteStatusEndring

val application = when (env(env_DEPLOY_APP)) {
    "sf-dialogmote" -> KafkaPosterApplication<String, KDialogmoteStatusEndring>(envAsSettings(env_POSTER_SETTINGS))
    "sf-stilling" -> KafkaPosterApplication<String, Ad>(envAsSettings(env_POSTER_SETTINGS), ::truncateAdtext).also { "Instansiating sf-stilling application with Ad" }
    else -> KafkaPosterApplication<String, String>(envAsSettings(env_POSTER_SETTINGS))
}

fun main() = application.start()
