package no.nav.kafka.dialog

fun application(system: SystemEnvironment): App = when (system.env(env_DEPLOY_APP)) {
    "sf-dialogmote" -> KafkaPosterApplication<String, String>(system, ::replaceNumbersWithInstants)
    "sf-stilling" -> KafkaPosterApplication<String, String>(system, ::removeAdTextProperty)
    "sf-arbeidsgiveraktivitet" -> KafkaPosterApplication<String, String>(system, lookUpArenaActivityDetails(system), ::filterOnActivityCodes)
    else -> KafkaPosterApplication<String, String>(system)
}

fun main() = application(SystemEnvironment()).start()
