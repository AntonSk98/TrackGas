package com.trackgas.app

import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import java.util.Locale

enum class AppLanguage(val localeTag: String, val labelRes: Int) {
    ENGLISH("en", R.string.language_english),
    GERMAN("de", R.string.language_german),
}

object AppLanguages {
    fun current(locales: Iterable<Locale>): AppLanguage =
        locales.firstNotNullOfOrNull { locale ->
            AppLanguage.entries.firstOrNull { it.localeTag == locale.language }
        } ?: AppLanguage.ENGLISH

    fun select(language: AppLanguage) {
        AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(language.localeTag))
    }
}
