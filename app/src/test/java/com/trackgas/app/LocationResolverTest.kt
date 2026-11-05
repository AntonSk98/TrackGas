package com.trackgas.app

import com.trackgas.app.temperature.OpenMeteoGeocoder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.assertThrows
import org.junit.Test

/** Covers Open-Meteo geocoder parsing and lookup outcomes. */
class LocationResolverTest {
    @Test
    fun parsesMatchesAndBuildsReadablePlaceNames() {
        val location = OpenMeteoGeocoder.parseResponse(
            """{"results":[{"name":"Berlin","admin1":"Berlin","country":"Germany","latitude":52.52,"longitude":13.405}]}""",
        ).single()

        assertEquals("Berlin, Germany", location.displayName)
        assertEquals(52.52, location.latitude, 0.0)
        assertEquals(13.405, location.longitude, 0.0)
    }

    @Test
    fun returnsNoPlacesForValidResponseWithoutMatches() {
        assertTrue(OpenMeteoGeocoder.parseResponse("""{"results":[]}""").isEmpty())
        assertTrue(OpenMeteoGeocoder.parseResponse("""{"generationtime_ms":0.5}""").isEmpty())
    }

    @Test
    fun listsMatchesWhoseRegionContainsTheHintFirst() {
        val json = """{"results":[
            {"name":"Gögging","admin1":"Saxony","country":"Germany","latitude":50.0,"longitude":12.0},
            {"name":"Gögging","admin1":"Bavaria","country":"Germany","latitude":48.8,"longitude":11.8}]}"""

        assertEquals(listOf("Gögging, Bavaria, Germany", "Gögging, Saxony, Germany"), OpenMeteoGeocoder.parseResponse(json, listOf("bavaria")).map { it.displayName })
        assertEquals("Gögging, Saxony, Germany", OpenMeteoGeocoder.parseResponse(json).first().displayName)
    }

    @Test
    fun limitsSuggestionsToThreePlaces() {
        val places = (1..4).joinToString(",") { number ->
            """{"name":"Place $number","country":"Germany","latitude":50.0,"longitude":12.0}"""
        }

        assertEquals(3, OpenMeteoGeocoder.parseResponse("""{"results":[$places]}""").size)
    }

    @Test
    fun rejectsMalformedJsonAndInvalidCoordinates() {
        assertThrows(Exception::class.java) { OpenMeteoGeocoder.parseResponse("not json") }
        assertThrows(IllegalArgumentException::class.java) {
            OpenMeteoGeocoder.parseResponse(
                """{"results":[{"name":"Nowhere","latitude":100,"longitude":0}]}""",
            )
        }
    }
}
