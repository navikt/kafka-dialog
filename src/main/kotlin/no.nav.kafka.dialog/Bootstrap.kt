package no.nav.kafka.dialog

import mu.KotlinLogging

const val env_DEPLOY_APP = "DEPLOY_APP"
const val env_MS_BETWEEN_WORK = "MS_BETWEEN_WORK"

val application = KafkaPosterApplication()

fun main() = application.start()

class KafkaPosterApplication() {
    private val bootstrapWaitTime = envAsLong(env_MS_BETWEEN_WORK)

    private val log = KotlinLogging.logger { }
    fun start() {
        log.info { "Starting" }
        enableNAISAPI {
            loop()
        }
        log.info { "Finished" }
    }

    private tailrec fun loop() {
        val stop = ShutdownHook.isActive() || PrestopHook.isActive()
        when {
            stop -> Unit.also { log.info { "Stopped" } }
            !stop -> {
                val deployApp = env(env_DEPLOY_APP)
                log.info("App $deployApp - to perform work here...")
                // poster.runWorkSession()
                conditionalWait(bootstrapWaitTime)
                loop()
            }
        }
    }
}
