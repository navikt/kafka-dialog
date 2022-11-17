package no.nav.sf.library

import com.google.gson.reflect.TypeToken
import java.lang.reflect.Type
import mu.KotlinLogging
import no.nav.kafka.dialog.gson
import org.http4k.core.Response
import org.http4k.core.Status

/**
 * Please refer to
 * https://developer.salesforce.com/docs/atlas.en-us.api_rest.meta/api_rest/resources_composite_sobjects_collections_create.htm
 */

private val log = KotlinLogging.logger { }

/**
 * The general sObject REST API for posting records of different types
 * In this case, post of KafkaMessage containing attribute refering to Salesforce custom object KafkaMessage__c
 */

// @Serializable
data class SFsObjectRest(
    val allOrNone: Boolean = true,
    val records: List<KafkaMessage>
) {

    fun toJson() = gson.toJson(this) // json.stringify(serializer(), this)

    companion object {
        fun fromJson(data: String): SFsObjectRest = runCatching { gson.fromJson(data, SFsObjectRest::class.java) as SFsObjectRest/*json.parse(serializer(), data)*/ }
                .onFailure {
                    log.error { "Parsing of SFsObjectRest request failed - ${it.localizedMessage}" }
                }
                .getOrDefault(SFsObjectRest(true, emptyList()))
    }
}

// @Serializable
data class KafkaMessage(
    val attributes: SFsObjectRestAttributes = SFsObjectRestAttributes(),
    /*@SerialName("CRM_Topic__c")*/
    val topic: String,
    /*@SerialName("CRM_Key__c")*/
    val key: String,
    /*@SerialName("CRM_Value__c")*/
    val value: String
)

// @Serializable
data class SFsObjectRestAttributes(
    val type: String = "KafkaMessage__c"
)

// @Serializable
data class SFsObjectRestWithOffset(
    val allOrNone: Boolean = true,
    val records: List<KafkaMessageWithOffset>
) {
    fun toJson() = gson.toJson(this)

    companion object {
        fun fromJson(data: String): SFsObjectRestWithOffset = runCatching { gson.fromJson(data, SFsObjectRestWithOffset::class.java) as SFsObjectRestWithOffset }
            .onFailure {
                log.error { "Parsing of SFsObjectRest request failed - ${it.localizedMessage}" }
            }
            .getOrDefault(SFsObjectRestWithOffset(true, emptyList()))
    }
}

// @Serializable
data class KafkaMessageWithOffset(
    val attributes: SFsObjectRestAttributes = SFsObjectRestAttributes(),
    /*@SerialName("CRM_Topic__c")*/
    val topic: String,
    /*@SerialName("CRM_Key__c")*/
    val key: String,
    /*@SerialName("CRM_Value__c")*/
    val value: String,
    /*@SerialName("CRM_Partition__c")*/
    val partition: Int,
    /*@SerialName("CRM_Offset__c")*/
    val offset: Long
)

// @Serializable
data class SFsObjectRestAttributesWithOffset(
    val type: String = "KafkaMessage__c",
    val partition: Int = 0,
    val offset: Long = 0
)

/**
 * Keep in mind that Salesforce gives 200 OK independent of successful posting of records or not
 * The json response is modelled below
 *
 * A sealed class for 2 scenarios
 * 1) Failure during parsing of json response - SFsObjectStatusInvalid
 * 2) Successful parsing of json response - SFsObjectStatus for each posted record
 *
 */

sealed class SFsObjectStatusBase {
    object SFsObjectStatusInvalid : SFsObjectStatusBase()

    // @Serializable
    internal data class SFsObjectStatus(
        val id: String = "",
        val success: Boolean,
        val errors: List<SFObjectError> = emptyList()
    ) : SFsObjectStatusBase()

    companion object {
        fun fromJson(data: String): List<SFsObjectStatusBase> = runCatching {
            val listOfMyClassObject: Type = object : TypeToken<ArrayList<SFsObjectStatusBase>>() {}.getType()
            gson.fromJson(data, listOfMyClassObject) as List<SFsObjectStatusBase> // json.parse(SFsObjectStatus.serializer().list, data)
        }
                .onFailure { log.error { "Parsing of Salesforce sObject REST response failed - ${it.localizedMessage}" } }
                .getOrDefault(listOf(SFsObjectStatusInvalid))
    }
}

// @Serializable
internal data class SFObjectError(
    val statusCode: String,
    val message: String,
    val fields: List<String> = emptyList()
)

// ext. function for easier management of post response...

internal fun List<SFsObjectStatusBase>.parsingFailure(): Boolean =
        filterIsInstance<SFsObjectStatusBase.SFsObjectStatusInvalid>().isNotEmpty()

internal fun List<SFsObjectStatusBase>.allRecordsPosted(): Boolean =
        filterIsInstance<SFsObjectStatusBase.SFsObjectStatus>().all { it.success }

internal fun List<SFsObjectStatusBase>.getErrMessage(): String = runCatching {
    filterIsInstance<SFsObjectStatusBase.SFsObjectStatus>()
            .first { !it.success }.errors.first().message
}
        .getOrDefault("")

internal fun List<SFsObjectStatusBase.SFsObjectStatus>.toJson(): String =
        gson.toJson(this)

fun Response.isSuccess(): Boolean = when (status) {
    Status.OK -> SFsObjectStatusBase.fromJson(bodyString()).let { status ->
        when (status.parsingFailure()) {
            true -> false
            else -> {
                if (status.allRecordsPosted()) true
                else {
                    log.error { "Posting of at least one record failed - ${status.getErrMessage()}" }
                    false
                }
            }
        }
    }
    else -> {
        log.error { "Post request to Salesforce failed - ${status.description}(${status.code})" }
        false
    }
}
