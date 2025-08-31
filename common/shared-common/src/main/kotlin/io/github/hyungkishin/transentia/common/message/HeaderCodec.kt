package io.github.hyungkishin.transentia.common.message

import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.databind.ObjectMapper

class HeaderCodec(private val om: ObjectMapper) {
    private val mapRef = object : TypeReference<Map<String, Any?>>() {}

    /** JSON 문자열 -> Map<String,String> (널/공백 제거) */
    fun jsonToFlatMap(headersJson: String?): Map<String, String> {
        if (headersJson.isNullOrBlank()) return emptyMap()
        val raw: Map<String, Any?> = om.readValue(headersJson, mapRef)
        return raw.mapNotNull { (k, v) ->
            val s = v?.toString()?.trim()
            if (k.isBlank() || s.isNullOrEmpty()) null else k to s
        }.toMap()
    }

    /** Map -> JSON 문자열 (필요시 Outbox.headers 저장용) */
    fun mapToJson(map: Map<String, String>): String =
        om.writeValueAsString(map)
}