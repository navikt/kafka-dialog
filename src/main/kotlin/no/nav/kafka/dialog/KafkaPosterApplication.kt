package no.nav.kafka.dialog

import mu.KotlinLogging

class KafkaPosterApplication<K, V>(val settings: List<KafkaToSFPoster.Settings> = listOf(), modifier: ((String, Long) -> String)? = null) {
    val poster = KafkaToSFPoster<K, V>(settings, modifier)

    private val bootstrapWaitTime = envAsLong(env_MS_BETWEEN_WORK)

    private val log = KotlinLogging.logger { }
    fun start() {
        log.info { "Starting app ${env(env_DEPLOY_APP)} with poster settings ${envAsSettings(env_POSTER_SETTINGS)}" }
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
