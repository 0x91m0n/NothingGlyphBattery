package com.nothing.glyphbattery.data

import android.app.LocaleManager
import android.content.Context
import android.os.LocaleList
import com.nothing.glyphbattery.model.AppLanguage

object AppLanguageManager {
    fun apply(context: Context, language: AppLanguage) {
        val localeManager = context.getSystemService(LocaleManager::class.java)
        val locales = if (language == AppLanguage.SYSTEM) {
            LocaleList.getEmptyLocaleList()
        } else {
            LocaleList.forLanguageTags(language.code)
        }
        if (localeManager.applicationLocales.toLanguageTags() != locales.toLanguageTags()) {
            localeManager.applicationLocales = locales
        }
    }
}
