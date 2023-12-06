package no.nav.kafka.dialog

import mu.KotlinLogging
/**
 * KafkaPosterApplication
 * This is the top level of the integration. Its function is to setup a server with the required
 * endpoints for the kubernetes environement (see enableNAISAPI)
 * and create a work loop that alternatives between work sessions (i.e polling from kafka until we are in sync) and
 * an interruptable pause (configured with MS_BETWEEN_WORK).
 */
class KafkaPosterApplication<K, V>(
    val system: SystemEnvironment,
    modifier: ((String, Long) -> String)? = null,
    filter: ((String, Long) -> Boolean)? = null
) : App {
    val poster = KafkaToSFPoster<K, V>(system, modifier, filter)

    private val bootstrapWaitTime = system.envAsLong(env_MS_BETWEEN_WORK)

    private val log = KotlinLogging.logger { }
    override fun start() {
        log.info { "Starting app ${system.env(env_DEPLOY_APP)} - cluster ${system.env(env_DEPLOY_CLUSTER)} (${if (system.isOnPrem()) "Onprem" else "Gcp"}) with poster settings ${system.envAsSettings(env_POSTER_SETTINGS)}" }
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
                poster.runWorkSession()
                conditionalWait(bootstrapWaitTime)
                loop()
            }
        }
    }
}
