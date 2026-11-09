package com.linzin.linzindeskclock.localization

import android.app.LocaleManager
import android.content.Context
import android.content.res.Configuration
import android.os.Build
import android.os.LocaleList
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat

private const val LANGUAGE_KEY = "pref_app_language"
private const val MIGRATED_KEY = "pref_app_language_migrated_to_system"

/** Migrate the original preference once; Android 13's system setting then owns the locale. */
fun restoreAppLanguage(context: Context) {
    val prefs = context.getSharedPreferences("desk_clock_prefs", Context.MODE_PRIVATE)
    val saved = prefs.getString(LANGUAGE_KEY, null)
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        if (!prefs.getBoolean(MIGRATED_KEY, false)) {
            val manager = context.getSystemService(LocaleManager::class.java)
            if (manager.applicationLocales.isEmpty && saved != null && saved != "system") {
                manager.applicationLocales = LocaleList.forLanguageTags(saved)
            }
            prefs.edit().putBoolean(MIGRATED_KEY, true).apply()
        }
    } else if (saved != null) {
        AppCompatDelegate.setApplicationLocales(
            if (saved == "system") LocaleListCompat.getEmptyLocaleList()
            else LocaleListCompat.forLanguageTags(saved)
        )
    }
}

fun setAppLanguage(context: Context, languageCode: String) {
    context.getSharedPreferences("desk_clock_prefs", Context.MODE_PRIVATE).edit()
        .putString(LANGUAGE_KEY, languageCode).putBoolean(MIGRATED_KEY, true).apply()
    val tags = if (languageCode == "system") "" else languageCode
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        context.getSystemService(LocaleManager::class.java).applicationLocales = LocaleList.forLanguageTags(tags)
    } else {
        AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(tags))
    }
}

fun getCurrentLanguageTag(context: Context): String {
    val tags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        context.getSystemService(LocaleManager::class.java).applicationLocales.toLanguageTags()
    } else {
        AppCompatDelegate.getApplicationLocales().toLanguageTags()
    }
    return tags.substringBefore(',').ifBlank { "system" }
}

/** Services have no AppCompat delegate on Android 12, so localise their Compose context too. */
fun Context.withAppLanguage(): Context {
    val tag = getCurrentLanguageTag(this)
    if (tag == "system") return this
    return createConfigurationContext(Configuration(resources.configuration).apply {
        setLocales(LocaleList.forLanguageTags(tag))
    })
}
