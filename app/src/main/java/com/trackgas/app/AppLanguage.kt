package com.trackgas.app

import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat

enum class AppLanguage(val localeTag: String, val labelRes: Int) {
    ENGLISH("en", R.string.language_english),
    GERMAN("de", R.string.language_german),
}

object AppLanguages {
    fun current(): AppLanguage {
        val language = AppCompatDelegate.getApplicationLocales()
            .get(0)
            ?.language
        return AppLanguage.entries.firstOrNull { it.localeTag == language } ?: AppLanguage.ENGLISH
    }

    fun select(language: AppLanguage) {
        AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(language.localeTag))
    }
}
