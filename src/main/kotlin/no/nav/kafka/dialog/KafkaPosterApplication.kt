package no.nav.kafka.dialog

import java.time.LocalDate
import mu.KotlinLogging

/**
 * KafkaPosterApplication
 * This is the top level of the integration. Its function is to setup a server with the required
 * endpoints for the kubernetes environement (see enableNAISAPI)
 * and create a work loop that alternatives between work sessions (i.e polling from kafka until we are in sync) and
 * an interruptable pause (configured with MS_BETWEEN_WORK).
 */
class KafkaPosterApplication<K, V>(
    val settings: List<KafkaToSFPoster.Settings> = listOf(),
    modifier: ((String, Long) -> String)? = null,
    filter: ((String, Long) -> Boolean)? = null
) : App {
    val poster = KafkaToSFPoster<K, V>(settings, modifier, filter)

    val limitOnDates = settings.contains(KafkaToSFPoster.Settings.LIMIT_ON_DATES)

    private val bootstrapWaitTime = envAsLong(env_MS_BETWEEN_WORK)

    private val log = KotlinLogging.logger { }

    override fun start() {
        log.info { "Starting app ${env(env_DEPLOY_APP)} - cluster ${env(env_DEPLOY_CLUSTER)} (${if (isOnPrem) "Onprem" else "Gcp"}) with poster settings ${envAsSettings(env_POSTER_SETTINGS)}" }
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
                if (limitOnDates) {
                    val activeDates = envAsLocalDates(env_ACTIVE_DATES)
                    if (activeDates.any { LocalDate.now() == it }) {
                        poster.runWorkSession()
                        conditionalWait(bootstrapWaitTime)
                    } else {
                        log.info { "Today (${LocalDate.now()}) not found within active dates ($activeDates) - will sleep until tomorrow" }
                        conditionalWait(findMillisToHourNextDay(hourToStartWorkSessionOnActiveDate))
                    }
                } else {
                    poster.runWorkSession()
                    conditionalWait(bootstrapWaitTime)
                }
                loop()
            }
        }
    }
}
