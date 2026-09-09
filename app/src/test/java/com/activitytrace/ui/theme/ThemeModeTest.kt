package com.activitytrace.ui.theme

import org.junit.Assert.assertEquals
import org.junit.Test

class ThemeModeTest {

    @Test
    fun fromPreferenceParsesKnownValues() {
        assertEquals(ThemeMode.SYSTEM, ThemeMode.fromPreference("SYSTEM"))
        assertEquals(ThemeMode.LIGHT, ThemeMode.fromPreference("LIGHT"))
        assertEquals(ThemeMode.DARK, ThemeMode.fromPreference("DARK"))
        assertEquals(ThemeMode.OLED, ThemeMode.fromPreference("OLED"))
    }

    @Test
    fun fromPreferenceFallsBackToSystemForUnknownValue() {
        assertEquals(ThemeMode.SYSTEM, ThemeMode.fromPreference("garbage"))
        assertEquals(ThemeMode.SYSTEM, ThemeMode.fromPreference("olED"))
        assertEquals(ThemeMode.SYSTEM, ThemeMode.fromPreference(""))
    }

    @Test
    fun fromPreferenceFallsBackToSystemForNull() {
        assertEquals(ThemeMode.SYSTEM, ThemeMode.fromPreference(null))
    }

    @Test
    fun fromPreferenceFallsBackToSystemForEmptyString() {
        assertEquals(ThemeMode.SYSTEM, ThemeMode.fromPreference(""))
    }
}