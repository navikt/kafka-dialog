package no.nav.kafka.dialog

import io.prometheus.client.hotspot.DefaultExports
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import mu.KotlinLogging
import no.nav.kafka.dialog.metrics.WorkSessionStatistics
import no.nav.kafka.dialog.poster.KafkaToSFPoster
import org.http4k.server.Netty
import org.http4k.server.asServer
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId

/**
 * KafkaPosterApplication
 * This is the top level of the integration. Its function is to set up a server with the required
 * endpoints for the kubernetes environment
 * and create a work loop that alternatives between work sessions (i.e polling from kafka until we are in sync) and
 * an interruptable pause (configured with MS_BETWEEN_WORK).
 */
class KafkaPosterApplication(
    modifier: Modifier? = null,
    filter: Filter? = null
) {
    private val poster = KafkaToSFPoster(modifier, filter)

    private val msBetweenWork = env(config_MS_BETWEEN_WORK).toLong()

    private val log = KotlinLogging.logger { }

    fun start() {
        log.info {
            "Starting app ${env(config_DEPLOY_APP)} - ${env(config_DEPLOY_CLUSTER)} - messageEncoding ${env(config_MESSAGE_ENCODING)}" +
                (if (env(config_FLAG_SEEK).toBoolean()) " - SEEK ${env(config_SEEK_OFFSET).toLong()}" else "") +
                (if (env(config_NUMBER_OF_SAMPLES).toInt() > 0) " - SAMPLE ${env(config_NUMBER_OF_SAMPLES)}" else "") +
                (if (env(config_FLAG_NO_POST).toBoolean()) " - NO_POST" else "") +
                (if (env(config_FLAG_ALT_ID).toBoolean()) " - ALT_ID" else "") +
                (if (env(config_ENCODE_KEY).toBoolean()) " - ENCODE_KEY" else "") +
                (if (env(config_LIMIT_ON_DATES).toBoolean()) " - LIMIT_ON_DATES" else "")
        }
        DefaultExports.initialize() // Instantiate Prometheus standard metrics
        naisAPI().asServer(Netty(8080)).start()

        val limitOnDates = env(config_LIMIT_ON_DATES).toBoolean()

        while (!ShutdownHook.isActive()) {
            try {
                if (limitOnDates) {
                    val activeDates = envAsLocalDates(config_ACTIVE_DATES)
                    if (activeDates.any { LocalDate.now() == it }) {
                        poster.runWorkSession()
                    } else {
                        log.info { "Today (${LocalDate.now()}) not found within active dates ($activeDates) - will sleep until tomorrow" }
                        conditionalWait(findMillisToHourNextDay(hourToStartWorkSessionOnActiveDate))
                    }
                } else {
                    poster.runWorkSession()
                }
            } catch (e: Exception) {
                log.error { "A work session failed:\n${e.stackTraceToString()}" }
                WorkSessionStatistics.workSessionExceptionCounter.inc()
            }
            conditionalWait(msBetweenWork)
        }
    }

    /**
     * conditionalWait
     * Interruptable wait function
     */
    private fun conditionalWait(ms: Long) =
        runBlocking {
            log.debug { "Will wait $ms ms" }

            val waitJob = launch {
                runCatching { delay(ms) }
                    .onSuccess { log.info { "waiting completed" } }
                    .onFailure { log.info { "waiting interrupted" } }
            }

            tailrec suspend fun loop(): Unit = when {
                waitJob.isCompleted -> Unit
                ShutdownHook.isActive() -> waitJob.cancel()
                else -> {
                    delay(250L)
                    loop()
                }
            }
            loop()
            waitJob.join()
        }

    fun envAsLocalDates(env: String): List<LocalDate> = System.getenv(env).split(",")
        .map { LocalDate.parse(it.trim()) }

    private fun Long.parsedAsMillisSecondsTime(): String {
        val seconds = (this / 1000) % 60
        val minutes = (this / (1000 * 60)) % 60
        val hours = (this / (1000 * 60 * 60))

        return "($hours hours, $minutes minutes, $seconds seconds)"
    }

    fun findMillisToHourNextDay(hour: Int): Long {
        val currentDateTime = LocalDateTime.now()
        val zone = ZoneId.systemDefault()
        val givenHourNextDay = currentDateTime.plusDays(1).with(LocalTime.of(hour, 0)).atZone(zone)

        val result = givenHourNextDay.toInstant().toEpochMilli() - System.currentTimeMillis()

        log.info { "Will sleep until hour $hour next day, that is ${result.parsedAsMillisSecondsTime()} from now" }
        return result
    }
}

const val hourToStartWorkSessionOnActiveDate = 8
