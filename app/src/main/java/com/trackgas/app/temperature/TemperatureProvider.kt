package com.trackgas.app.temperature

import java.io.IOException
import java.math.BigDecimal
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.time.LocalDate
import org.json.JSONObject

/** Fetches daily mean Celsius values for a location and inclusive date range. */
interface TemperatureProvider {
    suspend fun fetch(
        location: MeterLocation,
        start: LocalDate,
        endInclusive: LocalDate,
    ): Map<LocalDate, BigDecimal>
}

/** Retrieves archive daily means from Open-Meteo and omits days with no reported value. */
class OpenMeteoTemperatureProvider : TemperatureProvider {
    override suspend fun fetch(
        location: MeterLocation,
        start: LocalDate,
        endInclusive: LocalDate,
    ): Map<LocalDate, BigDecimal> {
        require(!endInclusive.isBefore(start))
        val parameters = linkedMapOf(
            "latitude" to location.latitude.toString(),
            "longitude" to location.longitude.toString(),
            "start_date" to start.toString(),
            "end_date" to endInclusive.toString(),
            "daily" to "temperature_2m_mean",
            "timezone" to "auto",
        )
        val query = parameters.entries.joinToString("&") { (key, value) ->
            "$key=${URLEncoder.encode(value, StandardCharsets.UTF_8.name())}"
        }
        val connection = URL("$ENDPOINT?$query").openConnection() as HttpURLConnection
        return try {
            connection.connectTimeout = CONNECT_TIMEOUT_MILLIS
            connection.readTimeout = READ_TIMEOUT_MILLIS
            connection.requestMethod = "GET"
            val responseCode = connection.responseCode
            if (responseCode !in 200..299) throw IOException("Open-Meteo returned HTTP $responseCode")
            connection.inputStream.bufferedReader(StandardCharsets.UTF_8).use { reader ->
                parseResponse(reader.readText())
            }
        } finally {
            connection.disconnect()
        }
    }

    companion object {
        private const val ENDPOINT = "https://archive-api.open-meteo.com/v1/archive"
        private const val CONNECT_TIMEOUT_MILLIS = 10_000
        private const val READ_TIMEOUT_MILLIS = 10_000

        internal fun parseResponse(json: String): Map<LocalDate, BigDecimal> {
            val daily = JSONObject(json).getJSONObject("daily")
            val dates = daily.getJSONArray("time")
            val values = daily.getJSONArray("temperature_2m_mean")
            require(dates.length() == values.length()) { "Open-Meteo returned misaligned daily data" }
            return buildMap {
                for (index in 0 until dates.length()) {
                    if (values.isNull(index)) continue
                    val date = LocalDate.parse(dates.getString(index))
                    val temperature = BigDecimal(values.get(index).toString())
                    put(date, temperature)
                }
            }
        }
    }
}
