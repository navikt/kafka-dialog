package no.nav.kafka.dialog

import mu.KotlinLogging
import no.nav.kafka.dialog.gui.filesHandler
import no.nav.kafka.dialog.metrics.Prometheus
import no.nav.sf.pdl.kafka.salesforce.DefaultAccessTokenHandler
import org.http4k.core.HttpHandler
import org.http4k.core.Method
import org.http4k.core.Response
import org.http4k.core.Status
import org.http4k.core.Status.Companion.OK
import org.http4k.routing.bind
import org.http4k.routing.routes
import java.io.File
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

private val log = KotlinLogging.logger { }

fun naisAPI(): HttpHandler =
    routes(
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
        "/internal/testAccess/new" bind Method.GET to testAccessHandlerNew,
        "/internal/testAccess/validation" bind Method.GET to testAccessHandlerValidation,
        "/internal/files" bind Method.GET to filesHandler(File("/tmp/files")),
        "/internal/files/{path:.*}" bind Method.GET to filesHandler(File("/tmp/files")),
        // "/internal/gui" bind Method.GET to Gui.guiHandler
    )

object ShutdownHook {
    private val log = KotlinLogging.logger { }

    @Volatile
    private var shutdownhookActive = false
    private val mainThread: Thread = Thread.currentThread()

    init {
        log.info { "Installing shutdown hook" }
        Runtime
            .getRuntime()
            .addShutdownHook(
                object : Thread() {
                    override fun run() {
                        shutdownhookActive = true
                        log.info { "shutdown hook activated" }

                        mainThread.join()
                    }
                },
            )
    }

    fun isActive() = shutdownhookActive
}

private val testAccessHandlerNew: HttpHandler = {
    val newAccessTokenHandler = DefaultAccessTokenHandler()
    Response(OK).body("Test access (new) successful: " + newAccessTokenHandler.testAccess())
}

private val testAccessHandlerValidation: HttpHandler = {
    val newAccessTokenHandlerAgainstValidation = DefaultAccessTokenHandler(sfClientId = env(secret_SF_VALIDATION_CLIENT_ID))
    Response(OK).body("Test access (validation) successful: " + newAccessTokenHandlerAgainstValidation.testAccess())
}

val currentTimeStamp: String get() = LocalDateTime.now().format(DateTimeFormatter.ISO_DATE_TIME)
