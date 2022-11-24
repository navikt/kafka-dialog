package no.nav.kafka.dialog

import no.nav.syfo.dialogmote.avro.KDialogmoteStatusEndring

val application = when (env(env_DEPLOY_APP)) {
    "sf-dialogmote" -> KafkaPosterApplication<String, KDialogmoteStatusEndring>(envAsSettings(env_POSTER_SETTINGS)) // When using generic: Do on three fields dialogmoteTidspunkt, statusEndringTidspunkt, tilfelleStartdato Instant.ofEpochMilli(1649232000000)
    "sf-stilling" -> KafkaPosterApplication<String, ByteArray>(envAsSettings(env_POSTER_SETTINGS), ::truncateAdtext)
    else -> KafkaPosterApplication<String, String>(envAsSettings(env_POSTER_SETTINGS))
}

fun main() = application.start()
