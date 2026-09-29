package com.stockfortune.app.data.repository

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.stockfortune.app.domain.model.TenGod
import kotlinx.coroutines.flow.first

/** 十神筛选勾选状态持久化（DataStore，纯本地）。 */
class SettingsRepository(private val store: DataStore<Preferences>) {
    private object Keys {
        val HIDDEN = stringPreferencesKey("filter_hidden")
        val YEAR = stringPreferencesKey("filter_year")
        val MONTH = stringPreferencesKey("filter_month")
        val DAY = stringPreferencesKey("filter_day")
    }

    suspend fun saveFilter(hidden: Set<TenGod>, year: Set<TenGod>, month: Set<TenGod>, day: Set<TenGod>) {
        store.edit {
            it[Keys.HIDDEN] = hidden.joinToString(",") { g -> g.cn }
            it[Keys.YEAR] = year.joinToString(",") { g -> g.cn }
            it[Keys.MONTH] = month.joinToString(",") { g -> g.cn }
            it[Keys.DAY] = day.joinToString(",") { g -> g.cn }
        }
    }

    suspend fun restoreFilter(): FilterSelection? {
        val prefs = store.data.first()
        val h = parse(prefs[Keys.HIDDEN])
        val y = parse(prefs[Keys.YEAR])
        val m = parse(prefs[Keys.MONTH])
        val d = parse(prefs[Keys.DAY])
        return if (h.isEmpty() && y.isEmpty() && m.isEmpty() && d.isEmpty()) null else FilterSelection(h, y, m, d)
    }

    suspend fun clearFilter() {
        store.edit {
            it.remove(Keys.HIDDEN)
            it.remove(Keys.YEAR)
            it.remove(Keys.MONTH)
            it.remove(Keys.DAY)
        }
    }

    private fun parse(raw: String?): Set<TenGod> =
        raw?.split(",")?.mapNotNull { TenGod.fromCn(it.trim()) }?.toSet() ?: emptySet()

    data class FilterSelection(
        val hidden: Set<TenGod>,
        val year: Set<TenGod>,
        val month: Set<TenGod>,
        val day: Set<TenGod>,
    )
}
