package no.nav.kafka.dialog

val devContext = env(config_DEPLOY_CLUSTER) == "dev-gcp" || env(config_DEPLOY_CLUSTER) == "dev-fss"

val application: KafkaPosterApplication = when (env(config_DEPLOY_APP)) {
    "sf-dialogmote" -> KafkaPosterApplication(modifier = replaceNumbersWithInstants)
    "sf-stilling" -> KafkaPosterApplication(modifier = removeAdTextProperty)
    "sf-arbeidsgiveraktivitet" -> KafkaPosterApplication(filter = filterOnActivityCodes, modifier = lookUpArenaActivityDetails)
    else -> KafkaPosterApplication()
}

fun main() = application.start()
