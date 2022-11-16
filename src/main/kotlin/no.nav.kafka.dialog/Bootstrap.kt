package no.nav.sf.stilling

import kotlinx.coroutines.NonCancellable.isActive
import mu.KotlinLogging
import no.nav.kafka.dialog.PrestopHook
import no.nav.kafka.dialog.ShutdownHook
import no.nav.kafka.dialog.conditionalWait
import no.nav.kafka.dialog.enableNAISAPI
import no.nav.kafka.dialog.envAsLong

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
                log.info("To perform work here...")
                // poster.runWorkSession()
                conditionalWait(bootstrapWaitTime)
                loop()
            }
        }
    }
}
