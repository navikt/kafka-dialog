package no.nav.kafka.dialog

val application: KafkaPosterApplication = when (env(config_DEPLOY_APP)) {
    "sf-dialogmote" -> KafkaPosterApplication(modifier = replaceNumbersWithInstants)
    "sf-stilling" -> KafkaPosterApplication(modifier = removeAdTextProperty)
    "sf-arbeidsgiveraktivitet" -> KafkaPosterApplication(modifier = lookUpArenaActivityDetails, filter = filterOnActivityCodes)
    else -> KafkaPosterApplication()
}

fun main() = application.start()
