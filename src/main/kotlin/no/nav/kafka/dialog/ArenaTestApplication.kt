package no.nav.kafka.dialog

import java.io.File
import mu.KotlinLogging
import org.http4k.client.ApacheClient
import org.http4k.core.Method
import org.http4k.core.Request

class ArenaTestApplication : App {
    private val bootstrapWaitTime = envAsLong(env_MS_BETWEEN_WORK)

    private val log = KotlinLogging.logger { }

    val client = ApacheClient()

    override fun start() {
        log.info { "Starting arena test app ${env(env_DEPLOY_APP)} - cluster ${env(env_DEPLOY_CLUSTER)} with poster settings ${envAsSettings(env_POSTER_SETTINGS)}" }
        enableNAISAPI {
            // val uri = "https://arena-ords.nais.adeo.no/arena/api/v1/arbeidsgiver/aktivitet?aktivitetId=140141879"
            val uri = "https://arena-ords-q1.dev.intern.nav.no/arena/api/v1/arbeidsgiver/aktivitet?aktivitetId=140141879"
            val request = Request(Method.GET, uri)
            val response = client(request)
            log.info { "Response gotten" }
            File("/tmp/arenaresponse").writeText(response.toMessage())
            loop()
        }
        log.info { "Finished" }
    }

    private tailrec fun loop() {
        val stop = ShutdownHook.isActive() || PrestopHook.isActive()
        when {
            stop -> Unit.also { log.info { "Stopped" } }
            !stop -> {
                conditionalWait(bootstrapWaitTime)
                loop()
            }
        }
    }
}
