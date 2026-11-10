package com.trackgas.app

import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Test

class AppLanguageTest {
    @Test fun germanConfigurationSelectsGermanWithoutAnExplicitOverride() {
        assertEquals(AppLanguage.GERMAN, AppLanguages.current(listOf(Locale.GERMAN)))
    }

    @Test fun regionalVariantsMatchTheirSupportedLanguage() {
        listOf("de-DE", "de-AT", "de-CH").forEach { tag ->
            assertEquals(tag, AppLanguage.GERMAN, AppLanguages.current(listOf(Locale.forLanguageTag(tag))))
        }
        assertEquals(AppLanguage.ENGLISH, AppLanguages.current(listOf(Locale.US)))
    }

    @Test fun englishConfigurationSelectsEnglish() {
        assertEquals(AppLanguage.ENGLISH, AppLanguages.current(listOf(Locale.ENGLISH)))
    }

    @Test fun emptyOrUnsupportedPreferencesFallBackToEnglish() {
        assertEquals(AppLanguage.ENGLISH, AppLanguages.current(emptyList()))
        assertEquals(AppLanguage.ENGLISH, AppLanguages.current(listOf(Locale.FRENCH, Locale.JAPANESE)))
    }

    @Test fun unsupportedPrimaryLocaleDoesNotHideSupportedSecondaryLocale() {
        assertEquals(
            AppLanguage.GERMAN,
            AppLanguages.current(listOf(Locale.FRENCH, Locale.forLanguageTag("de-AT"), Locale.ENGLISH)),
        )
    }

    @Test fun firstSupportedLocaleWins() {
        assertEquals(AppLanguage.ENGLISH, AppLanguages.current(listOf(Locale.ENGLISH, Locale.GERMAN)))
        assertEquals(AppLanguage.GERMAN, AppLanguages.current(listOf(Locale.GERMAN, Locale.ENGLISH)))
    }

    @Test fun everySupportedLanguageResolvesFromItsDeclaredTag() {
        AppLanguage.entries.forEach { language ->
            assertEquals(language, AppLanguages.current(listOf(Locale.forLanguageTag(language.localeTag))))
        }
    }

    @Test fun updatedConfigurationIsResolvedOnEveryCall() {
        assertEquals(AppLanguage.GERMAN, AppLanguages.current(listOf(Locale.GERMAN)))
        assertEquals(AppLanguage.ENGLISH, AppLanguages.current(listOf(Locale.ENGLISH)))
        assertEquals(AppLanguage.GERMAN, AppLanguages.current(listOf(Locale.GERMAN)))
    }
}
