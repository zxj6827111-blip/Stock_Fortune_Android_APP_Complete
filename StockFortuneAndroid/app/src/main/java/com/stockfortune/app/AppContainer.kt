package com.stockfortune.app

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore
import com.stockfortune.app.data.db.AppDatabase
import com.stockfortune.app.data.repository.AnalysisRepository
import com.stockfortune.app.data.repository.CalendarRepository
import com.stockfortune.app.data.repository.ClassicQuoteRepository
import com.stockfortune.app.data.repository.SettingsRepository
import com.stockfortune.app.data.repository.StockRepository

private val Context.filterStore: DataStore<Preferences> by preferencesDataStore(name = "filter_state")

/** 手写依赖容器：单模块、无 Hilt，保持离线包体与构建链路简单。 */
class AppContainer(private val context: Context) {
    private val db by lazy { AppDatabase.get(context) }
    val calendarRepository by lazy { CalendarRepository(db.calendarDao()) }
    val classicQuoteRepository by lazy { ClassicQuoteRepository(context) }
    val stockRepository by lazy {
        StockRepository(
            db.stockDao(), db.baziDao(), db.filterDao(), db.favoriteDao(), db.metaDao(),
            classicQuoteRepository,
            db.luckCycleDao(), db.natalRelationDao(), db.yongshenDao(),
        )
    }
    val analysisRepository by lazy {
        AnalysisRepository(
            db.calendarDao(), db.baziDao(), db.filterDao(), db.scanCacheDao(),
            db.luckCycleDao(), db.natalRelationDao(), db.yongshenDao(), db.stockDao(),
        )
    }
    val settingsRepository by lazy { SettingsRepository(context.filterStore) }
    val filterStore: DataStore<Preferences> by lazy { context.filterStore }
    val database get() = db
}

class StockFortuneApp : android.app.Application() {
    override fun onCreate() {
        super.onCreate()
        AppRuntime.container = AppContainer(this)
    }
}

/** 进程级容器句柄，供 Compose 之外的位置（工厂 lambda 等）取用。 */
object AppRuntime {
    lateinit var container: AppContainer
        internal set
}
