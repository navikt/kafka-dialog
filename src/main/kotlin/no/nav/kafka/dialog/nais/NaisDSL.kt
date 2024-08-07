package no.nav.kafka.dialog

import mu.KotlinLogging
import no.nav.kafka.dialog.metrics.Prometheus
import org.http4k.core.HttpHandler
import org.http4k.core.Method
import org.http4k.core.Response
import org.http4k.core.Status
import org.http4k.routing.bind
import org.http4k.routing.routes

private val log = KotlinLogging.logger { }

fun naisAPI(): HttpHandler = routes(
    "/internal/isAlive" bind Method.GET to { Response(Status.OK) },
    "/internal/isReady" bind Method.GET to { Response(Status.OK) },
    "/internal/metrics" bind Method.GET to {
        try {
            val result = Prometheus.metricsAsText
            if (result.isEmpty()) {
                Response(Status.NO_CONTENT)
            } else {
                Response(Status.OK).body(result)
            }
        } catch (e: Exception) {
            log.error { "/prometheus failed writing metrics - ${e.message}" }
            Response(Status.INTERNAL_SERVER_ERROR)
        }
    },
    // "/internal/gui" bind Method.GET to Gui.guiHandler
)

object ShutdownHook {
    private val log = KotlinLogging.logger { }

    @Volatile
    private var shutdownhookActive = false
    private val mainThread: Thread = Thread.currentThread()

    init {
        log.info { "Installing shutdown hook" }
        Runtime.getRuntime()
            .addShutdownHook(
                object : Thread() {
                    override fun run() {
                        shutdownhookActive = true
                        log.info { "shutdown hook activated" }

                        mainThread.join()
                    }
                })
    }

    fun isActive() = shutdownhookActive
}
