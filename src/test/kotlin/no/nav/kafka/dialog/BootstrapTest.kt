package no.nav.kafka.dialog

import kotlin.test.assertFalse
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.apache.kafka.clients.consumer.MockConsumer
import org.apache.kafka.clients.consumer.OffsetResetStrategy.EARLIEST
import org.apache.kafka.common.KafkaException
import org.apache.kafka.common.Node
import org.apache.kafka.common.PartitionInfo
import org.apache.kafka.common.TopicPartition
import org.http4k.base64Encode
import org.http4k.core.MemoryBody
import org.http4k.core.MemoryResponse
import org.http4k.core.Response
import org.http4k.core.Status
import org.http4k.core.Status.Companion.FORBIDDEN
import org.http4k.core.Status.Companion.OK
import org.http4k.core.Status.Companion.UNAUTHORIZED
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class BootstrapTest {

    private lateinit var properties: MutableMap<String, String>
    private lateinit var env: StubSystemEnvironment
    private lateinit var kafkaConsumer: MockConsumer<*, *>
    private lateinit var topicPartition: TopicPartition

    private val sfAccessToken = """ { "access_token": "token" } """
    private val partition = 1
    private val offset = 0L
    private val topic = "KAFKA_TOPIC"
    private val arenaActivity = """ {"after": {"AKTIVITET_ID": 1} } """

    private var shouldThrowExceptionOnCommit = false

    @BeforeEach
    fun setup() {
        properties = mutableMapOf("POSTER_SETTINGS" to "RUN_ONCE, FROM_BEGINNING", "KAFKA_TOPIC" to "KAFKA_TOPIC")
        kafkaConsumer = TestConsumer<String, String>()
        topicPartition = TopicPartition(topic, partition)
        env = StubSystemEnvironment(properties = properties, consumer = kafkaConsumer)
    }

    @AfterEach
    fun teardown() {
        env.reset()
    }

    @Test
    fun `SalesforceClient with successful access token request should continue to process data`() {
        // arrange
        setupAccessToken(OK)
        // act
        application(env).start()
        // assert
        assertTrue(hasDoneWork())
    }

    @Test
    fun `SalesforceClient with eventually successful access token request should continue to process data`() {
        // arrange
        setupAccessToken(FORBIDDEN, times = 2)
        setupAccessToken(OK)
        // act
        application(env).start()
        // assert
        assertTrue(hasDoneWork())
    }

    @Test
    fun `SalesforceClient with failing access token request should not do any further processing`() {
        // arrange
        setupAccessToken(FORBIDDEN, times = 10)
        // act
        application(env).start()
        // assert
        assertFalse(hasDoneWork())
    }

    @Test
    fun `SalesforceClient with consumed Kafka record should do HTTP post`() {
        // arrange
        val myValue = "values"
        setupAccessToken(OK)
        setupKafkaToProduce(myValue)
        setupSalesforceHTTPResponse(success = true)
        // act
        application(env).start()
        // assert
        assertTrue(salesforceRequest().contains("${myValue.base64Encode()}"))
    }

    @Test
    fun `SalesforceClient with NO_POST flag set should not do HTTP post`() {
        // arrange
        setupPosterSetting("NO_POST")
        val myValue = "values"
        setupAccessToken(OK)
        setupKafkaToProduce(myValue)
        setupSalesforceHTTPResponse(success = true)
        // act
        application(env).start()
        // assert
        assertFalse(salesforceRequest().contains("${myValue.base64Encode()}"))
    }

    @Test
    fun `SalesforceClient with ENCODE_KEY flag should encode key in HTTP post`() {
        // arrange
        setupPosterSetting("ENCODE_KEY")
        setupAccessToken(OK)
        setupKafkaToProduce("value")
        setupSalesforceHTTPResponse(success = true)
        // act
        application(env).start()
        // assert
        assertTrue(salesforceRequest().contains("key".base64Encode()))
    }

    @Test
    fun `SalesforceClient receiving SUCCESS on post of data to Salesforce should issue commit in Kafka`() {
        // arrange
        setupAccessToken(OK)
        setupKafkaToProduce("value")
        setupSalesforceHTTPResponse(success = true)
        // act
        application(env).start()
        // assert
        assertTrue(hasKafkaConsumerCommitted())
    }

    @Test
    fun `SalesforceClient receiving NOT_SUCCESS on post of data to Salesforce should not issue commit in Kafka`() {
        // arrange
        setupAccessToken(OK)
        setupKafkaToProduce("value")
        setupSalesforceHTTPResponse(success = false)
        // act
        application(env).start()
        // assert
        assertFalse(hasKafkaConsumerCommitted())
    }

    @Test
    fun `SalesforceClient receiving UNAUTHORIZED on post of data to Salesforce should not issue commit in Kafka`() {
        // arrange
        setupAccessToken(OK)
        setupKafkaToProduce("value")
        setupHTTPResponse(status = UNAUTHORIZED, times = 10)
        // act
        application(env).start()
        // assert
        assertFalse(hasKafkaConsumerCommitted())
    }

    @Test
    fun `SalesforceClient with NO_POST flag should issue commit in Kafka`() {
        // arrange
        setupPosterSetting("NO_POST")
        setupAccessToken(OK)
        setupKafkaToProduce("value")
        setupSalesforceHTTPResponse(success = true)
        // act
        application(env).start()
        // assert
        assertTrue(hasKafkaConsumerCommitted())
    }

    @Test
    fun `SalesforceClient with poll exception should not issue commit in Kafka`() {
        // arrange
        setupAccessToken(OK)
        setupKafkaPollException()
        setupSalesforceHTTPResponse(success = true)
        // act
        application(env).start()
        // assert
        assertFalse(hasDoneWork())
        assertFalse(hasKafkaConsumerCommitted())
    }

    @Test
    fun `SalesforceClient should handle commit exception from Kafka`() {
        // arrange
        setupAccessToken(OK)
        setupKafkaToProduce("value")
        setupSalesforceHTTPResponse(success = true)
        shouldThrowExceptionOnCommit = true
        // act
        application(env).start()
        // assert
        assertFalse(hasKafkaConsumerCommitted())
    }

    @Test
    fun `sf-arbeidsgiveraktivitet app should lookup code in Arena and then POST and commit the answer`() {
        // arrange
        val arenaResponse = """{"aktivitetskode": "EKSPEBIST","aktivitetsgruppekode": "TLTAK"}"""
        setupDeployApp("sf-arbeidsgiveraktivitet")
        setupAccessToken(OK)
        setupKafkaToProduce(arenaActivity)
        setupHTTPResponse(body = arenaResponse)
        setupSalesforceHTTPResponse(success = true)
        // act
        application(env).start()
        // assert
        assertTrue(salesforceRequest().contains(arenaResponse.base64Encode()))
        assertTrue(hasKafkaConsumerCommitted())
    }

    @Test
    fun `sf-arbeidsgiveraktivitet app with FORBIDDEN answer from Arena should not commit in Kafka`() {
        // arrange
        setupDeployApp("sf-arbeidsgiveraktivitet")
        setupAccessToken(OK)
        setupKafkaToProduce(arenaActivity)
        setupHTTPResponse(FORBIDDEN)
        // act
        application(env).start()
        // assert
        assertFalse(hasKafkaConsumerCommitted())
    }

    @Test
    fun `sf-dialogmote app with data with DATE received from Kakfa should convert the DATE before doing POST`() {
        // arrange
        val myValue = """{"date":1234567,"other":"value"}"""
        val myConvertedValue = """{"date":"1970-01-01T00:20:34.567Z","other":"value"}"""
        setupDeployApp("sf-dialogmote")
        setupAccessToken(OK)
        setupKafkaToProduce(myValue)
        setupSalesforceHTTPResponse(success = true)
        // act
        application(env).start()
        // assert
        assertTrue(salesforceRequest().contains(myConvertedValue.base64Encode()))
    }

    @Test
    fun `sf-stilling app where data with key is «adtext» received from Kakfa should filter the value before doing POST`() {
        // arrange
        val myValue = """{"uuid": "1", "adnr": "2", "properties": [{"key": "adtext", "value": "<p>Tag</p>"}]}"""
        val myFilteredValue = """{"uuid":"1","adnr":"2","properties":[]}"""
        setupDeployApp("sf-stilling")
        setupAccessToken(OK)
        setupKafkaToProduce(myValue)
        setupSalesforceHTTPResponse(success = true)
        // act
        application(env).start()
        // assert
        assertTrue(salesforceRequest().contains(myFilteredValue.base64Encode()))
    }

    private fun setupDeployApp(app: String) {
        properties["DEPLOY_APP"] = app
    }

    private fun setupPosterSetting(setting: String) {
        properties["POSTER_SETTINGS"] = "${properties["POSTER_SETTINGS"]}, $setting"
    }

    private fun setupSalesforceHTTPResponse(success: Boolean = true) =
        setupHTTPResponse(body = sfSobjectStatusList(success = success))

    private fun setupKafkaPollException() {
        setupTopicPartition()
        kafkaConsumer.schedulePollTask {
            kafkaConsumer.setPollException(KafkaException("poll exception"))
        }
    }

    private fun <V> setupKafkaToProduce(vararg values: V) {
        setupTopicPartition()
        kafkaConsumer.schedulePollTask {
            repeat(values.size) { pos ->
                @Suppress("UNCHECKED_CAST")
                (kafkaConsumer as MockConsumer<String, V>).addRecord(
                    ConsumerRecord<String, V>(topic, partition, offset, "key", values[pos])
                )
            }
        }
    }

    private fun setupTopicPartition() {
        updatePartitionOffset(topicPartition)
        kafkaConsumer.assign(listOf(topicPartition))
        updatePartition()
    }

    private fun updatePartitionOffset(topicPartition: TopicPartition) =
        HashMap<TopicPartition, Long>().let { offsetMap ->
            offsetMap[topicPartition] = 0L
            kafkaConsumer.updateBeginningOffsets(offsetMap)
        }

    private fun updatePartition() =
        kafkaConsumer.updatePartitions(
            topic, listOf(
                PartitionInfo(
                    topic, partition, Node(1, "host", 1, "rack"), emptyArray<Node>(), emptyArray<Node>()
                )
            )
        )

    private fun setupAccessToken(status: Status = OK, body: String = sfAccessToken, times: Int = 1) {
        env.httpResponses.addAll(response(status, body, times))
    }

    private fun setupHTTPResponse(status: Status = OK, body: String = "", times: Int = 1) {
        env.httpResponses.addAll(response(status, body, times))
    }

    private fun response(status: Status, body: String, times: Int) =
        mutableListOf<Response>().apply {
            repeat(times) {
                this.add(MemoryResponse(status = status, body = MemoryBody(body)))
            }
        }

    private fun commitIssuedHook() = if (shouldThrowExceptionOnCommit) throw Exception() else null
    private fun hasKafkaConsumerCommitted() = kafkaConsumer.committed(setOf(topicPartition)).isNotEmpty()
    private fun sfSobjectStatusList(success: Boolean) = """ [ { "id":"id","success":$success,"errors":[] } ] """
    private fun salesforceRequest() = env.salesforceRequest!!.bodyString()
    private fun hasDoneWork() = env.isDoingWork

    /** Just making the MockConsumer even more test-friendly **/
    inner class TestConsumer<K, V> : MockConsumer<K, V>(EARLIEST) {
        override fun commitSync() {
            commitIssuedHook()
            super.commitSync()
        }

        override fun close() {} // To be able to reuse the consumer we short circuit closing here
    }
}
