package com.trackgas.app.temperature

import java.io.IOException
import java.math.BigDecimal
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.time.LocalDate
import java.time.Instant
import java.time.ZoneOffset
import org.json.JSONObject

/** Fetches transient UTC hourly samples for inclusive bounding dates. */
interface TemperatureProvider {
    suspend fun fetch(
        location: MeterLocation,
        start: LocalDate,
        endInclusive: LocalDate,
    ): Map<Instant, BigDecimal>
}

/** Retrieves archive hourly values; missing hours remain absent. */
class OpenMeteoTemperatureProvider(
    private val openConnection: (URL) -> HttpURLConnection = { it.openConnection() as HttpURLConnection },
) : TemperatureProvider {
    override suspend fun fetch(
        location: MeterLocation,
        start: LocalDate,
        endInclusive: LocalDate,
    ): Map<Instant, BigDecimal> {
        require(!endInclusive.isBefore(start))
        val parameters = linkedMapOf(
            "latitude" to location.latitude.toString(),
            "longitude" to location.longitude.toString(),
            "start_date" to start.toString(),
            "end_date" to endInclusive.toString(),
            "hourly" to "temperature_2m",
            "timezone" to "UTC",
            "timeformat" to "unixtime",
        )
        val query = parameters.entries.joinToString("&") { (key, value) ->
            "$key=${URLEncoder.encode(value, StandardCharsets.UTF_8.name())}"
        }
        val connection = openConnection(URL("$ENDPOINT?$query"))
        return try {
            connection.connectTimeout = CONNECT_TIMEOUT_MILLIS
            connection.readTimeout = READ_TIMEOUT_MILLIS
            connection.requestMethod = "GET"
            val responseCode = connection.responseCode
            if (responseCode !in 200..299) throw IOException("Open-Meteo returned HTTP $responseCode")
            connection.inputStream.bufferedReader(StandardCharsets.UTF_8).use { reader ->
                parseResponse(reader.readText(), start..endInclusive)
            }
        } finally {
            connection.disconnect()
        }
    }

    companion object {
        private const val ENDPOINT = "https://archive-api.open-meteo.com/v1/archive"
        private const val CONNECT_TIMEOUT_MILLIS = 10_000
        private const val READ_TIMEOUT_MILLIS = 10_000

        internal fun parseResponse(json: String, bounds: ClosedRange<LocalDate>? = null): Map<Instant, BigDecimal> {
            val hourly = JSONObject(json).getJSONObject("hourly")
            val dates = hourly.getJSONArray("time")
            val values = hourly.getJSONArray("temperature_2m")
            require(dates.length() == values.length()) { "Open-Meteo returned misaligned hourly data" }
            val seen = mutableSetOf<Instant>()
            return buildMap {
                for (index in 0 until dates.length()) {
                    val epoch = BigDecimal(dates.get(index).toString()).longValueExact()
                    require(epoch % 3600L == 0L)
                    val date = Instant.ofEpochSecond(epoch)
                    require(bounds == null || date.atOffset(ZoneOffset.UTC).toLocalDate() in bounds)
                    require(seen.add(date)) { "Duplicate hourly timestamp" }
                    if (values.isNull(index)) continue
                    val temperature = BigDecimal(values.get(index).toString())
                    put(date, temperature)
                }

            }
        }

    }
}
