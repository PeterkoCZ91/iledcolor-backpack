package com.batoh.core.common

object UiPreferences {
    const val PREFS_NAME = "batoh_ui_preferences"
    const val KEY_THEME_MODE = "theme_mode"
    const val KEY_GRID_COLUMNS = "grid_columns"
    const val KEY_USER_INTERESTS = "user_interests"
    const val PREF_KLIPY_KEY = "klipy_api_key"
    const val PREF_GIPHY_KEY = "giphy_api_key"

    const val DEFAULT_GRID_COLUMNS = 2
}

enum class UiThemeMode {
    SYSTEM,
    LIGHT,
    DARK
}
