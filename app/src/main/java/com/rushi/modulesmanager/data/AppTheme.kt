package com.rushi.modulesmanager.data

enum class AppTheme(
    val key: String,
    val label: String,
    val description: String,
) {
    SYSTEM("system", "System", "Follow the device theme"),
    LIGHT("light", "Light", "Always use the light theme"),
    DARK("dark", "Dark", "Always use the dark theme"),
    ;

    companion object {
        fun fromKey(key: String?): AppTheme = entries.firstOrNull { it.key == key } ?: SYSTEM
    }
}
