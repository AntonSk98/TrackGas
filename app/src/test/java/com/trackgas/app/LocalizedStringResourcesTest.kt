package com.trackgas.app

import java.io.File
import javax.xml.XMLConstants
import javax.xml.parsers.DocumentBuilderFactory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class LocalizedStringResourcesTest {
    @Test
    fun englishAndGermanResourcesHaveMatchingKeysAndPlaceholders() {
        val english = readStrings(resourceFile("values/strings.xml"))
        val german = readStrings(resourceFile("values-de/strings.xml"))

        assertEquals(english.keys, german.keys)
        english.forEach { (key, value) ->
            val translated = german.getValue(key)
            assertEquals("translatable attribute for $key", value.translatable, translated.translatable)
            assertEquals(
                "format placeholders for $key",
                placeholders(value.text),
                placeholders(translated.text),
            )
        }
        assertEquals("English", english.getValue("language_english").text)
        assertEquals("Deutsch", german.getValue("language_german").text)
        assertEquals("false", english.getValue("language_english").translatable)
        assertEquals("false", english.getValue("language_german").translatable)
    }

    private fun resourceFile(relativePath: String): File {
        val paths = listOf(
            File("src/main/res/$relativePath"),
            File("app/src/main/res/$relativePath"),
        )
        return paths.firstOrNull(File::isFile)
            ?: error("Could not find Android string resource: $relativePath")
    }

    private fun readStrings(file: File): Map<String, ResourceString> {
        val factory = DocumentBuilderFactory.newInstance().apply {
            setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true)
            setFeature("http://apache.org/xml/features/disallow-doctype-decl", true)
            isXIncludeAware = false
            isExpandEntityReferences = false
        }
        val nodes = factory.newDocumentBuilder().parse(file).getElementsByTagName("string")
        val strings = linkedMapOf<String, ResourceString>()
        for (index in 0 until nodes.length) {
            val node = nodes.item(index)
            val name = node.attributes.getNamedItem("name").nodeValue
            assertFalse("duplicate string resource: $name", strings.containsKey(name))
            val translatable = node.attributes.getNamedItem("translatable")?.nodeValue.orEmpty()
            strings[name] = ResourceString(node.textContent, translatable)
        }
        return strings
    }

    private fun placeholders(value: String): List<String> =
        Regex("%(?:\\d+\\$)?[sd]").findAll(value).map { it.value }.sorted().toList()

    private data class ResourceString(val text: String, val translatable: String)
}
