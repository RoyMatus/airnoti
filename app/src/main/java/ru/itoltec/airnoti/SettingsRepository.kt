package ru.itoltec.airnoti

import android.content.Context
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.*

private val Context.settingsStore by preferencesDataStore("airnoti")
data class Settings(val enabled: Boolean = false, val all: Boolean = true, val packages: Set<String> = emptySet(), val address: String = "", val strength: Int = 0) {
    fun accepts(pkg: String) = enabled && (all || pkg in packages)
}
class SettingsRepository(context: Context) {
    private val store = context.settingsStore
    val flow = store.data.map { p -> Settings(p[ENABLED] ?: false, p[ALL] ?: true, p[PACKAGES] ?: emptySet(), p[ADDRESS] ?: "", p[STRENGTH] ?: 0) }
    suspend fun enabled(value: Boolean) { store.edit { it[ENABLED] = value } }
    suspend fun all(value: Boolean) { store.edit { it[ALL] = value } }
    suspend fun select(pkg: String, checked: Boolean) { store.edit { it[PACKAGES] = (it[PACKAGES] ?: emptySet()).let { old -> if (checked) old + pkg else old - pkg } } }
    suspend fun device(address: String) { store.edit { it[ADDRESS] = address } }
    suspend fun strength(value: Int) { require(value in setOf(0, 2, 3)); store.edit { it[STRENGTH] = value } }
    companion object {
        private val ENABLED = booleanPreferencesKey("enabled")
        private val ALL = booleanPreferencesKey("all")
        private val PACKAGES = stringSetPreferencesKey("packages")
        private val ADDRESS = stringPreferencesKey("address")
        private val STRENGTH = intPreferencesKey("strength")
    }
}

