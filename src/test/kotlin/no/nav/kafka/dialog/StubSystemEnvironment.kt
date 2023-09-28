package no.nav.kafka.dialog

import java.util.Properties
import org.apache.kafka.clients.consumer.Consumer
import org.http4k.core.HttpHandler
import org.http4k.core.Request
import org.http4k.core.Response

class StubSystemEnvironment(
    val properties: Map<String, String>,
    val isOnPremisis: Boolean = false,
    val consumer: Consumer<*, *>,
    val httpResponses: MutableList<Response> = mutableListOf()

) : SystemEnvironment() {
    private var numberOfRuns = 0
    internal var salesforceRequest: Request? = null
    internal var isDoingWork = false

    private val responses = sequence { httpResponses.forEach { yield(it) } }.iterator()

    override fun isOnPrem() = isOnPremisis

    override fun env(env: String) = properties[env] ?: ""

    override fun envAsLong(env: String) = 0L

    override fun envAsInt(env: String) = 0

    override fun envAsList(env: String) = env(env).split(",").map { it.trim() }.toList()
    @Suppress("UNCHECKED_CAST")
    override fun <K, V> kafkaConsumer(properties: Properties): Consumer<K, V> = consumer as Consumer<K, V>

    override fun httpClient(): Lazy<HttpHandler> = lazy { { r: Request -> salesforceRequest = r; responses.next() } }

    override fun lookUpApacheClient() = httpClient()

    override fun retryConsumptionDelay() = 0L

    override fun accessTokenRetryDelay() = 0L

    override fun hasRunOnceHook(hasRunOnce: Boolean) {
        isDoingWork = hasRunOnce
        // Here we use the hook to short circuit the machinery in order to do a shutdown
        if (numberOfRuns == 1) activateShutdown()
        numberOfRuns = numberOfRuns.inc()
    }

    private fun activateShutdown() = PrestopHook.activate()
    internal fun reset() = PrestopHook.reset()
}
