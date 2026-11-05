package com.trackgas.app.temperature

import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import org.json.JSONObject

/** Suggests places for a partially typed query; the user must pick one to get coordinates. */
interface LocationResolver {
    fun suggest(query: String): List<MeterLocation>
}

/**
 * Queries Open-Meteo's geocoding API, which matches place names only. Trailing words such as a
 * region ("Bad Gögging Bavaria") are therefore retried as hints that rank the matches.
 */
class OpenMeteoGeocoder : LocationResolver {
    override fun suggest(query: String): List<MeterLocation> {
        val words = query.split(Regex("[,\\s]+")).filter(String::isNotBlank)
        for (nameWordCount in words.size downTo maxOf(1, words.size - MAX_DROPPED_WORDS)) {
            val name = words.take(nameWordCount).joinToString(" ")
            val places = search(name, hints = words.drop(nameWordCount))
            if (places.isNotEmpty()) return places
        }
        return emptyList()
    }

    private fun search(name: String, hints: List<String>): List<MeterLocation> {
        val encodedName = URLEncoder.encode(name, StandardCharsets.UTF_8.name())
        val connection = URL("$ENDPOINT?name=$encodedName&count=$RESULT_COUNT&language=en&format=json")
            .openConnection() as HttpURLConnection
        return try {
            connection.connectTimeout = CONNECT_TIMEOUT_MILLIS
            connection.readTimeout = READ_TIMEOUT_MILLIS
            connection.requestMethod = "GET"
            connection.inputStream.bufferedReader(StandardCharsets.UTF_8).use { reader ->
                parseResponse(reader.readText(), hints)
            }
        } finally {
            connection.disconnect()
        }
    }

    companion object {
        private const val ENDPOINT = "https://geocoding-api.open-meteo.com/v1/search"
        private const val RESULT_COUNT = 3
        private const val MAX_DROPPED_WORDS = 3
        private const val CONNECT_TIMEOUT_MILLIS = 10_000
        private const val READ_TIMEOUT_MILLIS = 10_000

        /** Parses distinct places, listing those whose region or country contains every hint first. */
        internal fun parseResponse(json: String, hints: List<String> = emptyList()): List<MeterLocation> {
            val results = JSONObject(json).optJSONArray("results") ?: return emptyList()
            val candidates = (0 until results.length()).map(results::getJSONObject)
            val (matching, others) = candidates.partition { candidate ->
                hints.isNotEmpty() && hints.all { hint ->
                    listOf("admin1", "admin2", "country").any { key ->
                        candidate.optString(key).contains(hint, ignoreCase = true)
                    }
                }
            }
            return (matching + others)
                .map(::toLocation)
                .distinctBy(MeterLocation::displayName)
                .take(RESULT_COUNT)
        }

        private fun toLocation(result: JSONObject): MeterLocation {
            val displayName = listOf("name", "admin1", "country")
                .mapNotNull { key -> result.optString(key).takeIf(String::isNotBlank) }
                .distinct()
                .joinToString(", ")
            require(displayName.isNotBlank()) { "Geocoding result has no display name" }
            return MeterLocation(
                displayName = displayName,
                latitude = result.getDouble("latitude"),
                longitude = result.getDouble("longitude"),
            )
        }
    }
}
