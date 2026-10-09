package com.stockfortune.app.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.stockfortune.app.data.dao.BaziDao
import com.stockfortune.app.data.dao.CalendarDao
import com.stockfortune.app.data.dao.FavoriteDao
import com.stockfortune.app.data.dao.FilterDao
import com.stockfortune.app.data.dao.LuckCycleDao
import com.stockfortune.app.data.dao.MetaDao
import com.stockfortune.app.data.dao.NatalRelationDao
import com.stockfortune.app.data.dao.ScanCacheDao
import com.stockfortune.app.data.dao.StockDao
import com.stockfortune.app.data.dao.StockYongshenDao
import com.stockfortune.app.data.entity.AppMetaEntity
import com.stockfortune.app.data.entity.FavoriteEntity
import com.stockfortune.app.data.entity.GanzhiCalendarEntity
import com.stockfortune.app.data.entity.LuckCyclePeriodEntity
import com.stockfortune.app.data.entity.NatalRelationEntity
import com.stockfortune.app.data.entity.ScanCacheEntity
import com.stockfortune.app.data.entity.StockBaziEntity
import com.stockfortune.app.data.entity.StockEntity
import com.stockfortune.app.data.entity.StockHiddenTenGodEntity
import com.stockfortune.app.data.entity.StockLuckCycleEntity
import com.stockfortune.app.data.entity.StockYongshenEntity
import com.stockfortune.app.data.entity.TradeCalendarEntity

/**
 * 预置只读库 + 运行时可写表（缓存 / 收藏 / 预设）。
 * 数据库文件由 `tools/build_assets_db.py` 生成于 assets/databases，APP 不联网。
 */
@Database(
    entities = [
        StockEntity::class, StockBaziEntity::class, StockHiddenTenGodEntity::class,
        GanzhiCalendarEntity::class, TradeCalendarEntity::class, ScanCacheEntity::class,
        FavoriteEntity::class, AppMetaEntity::class,
        StockLuckCycleEntity::class, LuckCyclePeriodEntity::class,
        NatalRelationEntity::class,
        StockYongshenEntity::class,
    ],
    // 版本取自随包生成的清单，避免"清单说 1、实体已经 2"这种只有运行期才发现的脱钩
    version = AssetManifest.SCHEMA_VERSION,
    exportSchema = true,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun stockDao(): StockDao
    abstract fun baziDao(): BaziDao
    abstract fun calendarDao(): CalendarDao
    abstract fun scanCacheDao(): ScanCacheDao
    abstract fun filterDao(): FilterDao
    abstract fun favoriteDao(): FavoriteDao
    abstract fun metaDao(): MetaDao
    abstract fun luckCycleDao(): LuckCycleDao
    abstract fun natalRelationDao(): NatalRelationDao
    abstract fun yongshenDao(): StockYongshenDao

    companion object {
        const val ASSET_PATH = "databases/stock_fortune.db"

        @Volatile
        private var instance: AppDatabase? = null

        private const val DB_NAME = "stock_fortune.db"
        private const val GUARD_PREFS = "asset_guard"
        private const val GUARD_KEY = "identity_hash"

        fun get(context: Context): AppDatabase = instance ?: synchronized(this) {
            // 进锁后必须再读一次：否则两个首启线程各建一个库，前一个连接被永久泄漏
            instance ?: build(context.applicationContext).also { instance = it }
        }

        private fun build(app: Context): AppDatabase {
            val restoredFavorites = ensureAssetUpToDate(app)
            val db = Room.databaseBuilder(app, AppDatabase::class.java, DB_NAME)
                .createFromAsset(ASSET_PATH)
                .setJournalMode(JournalMode.TRUNCATE)
                .build()
            reinsertFavorites(db, restoredFavorites)
            return db
        }

        /**
         * Room 只在数据库文件不存在时才复制 assets。装上带 schema/数据变更的新包后，
         * 旧的本地库会让 Room 抛 "cannot verify the data integrity" 直接崩溃。
         * 这里比对随包生成的身份（schema 哈希 + 数据版本），不一致时先把用户收藏读出、
         * 再删本地库让 Room 重新复制，最后把收藏写回。
         *
         * 删除失败时**不写入新身份**：下次启动会重试，避免出现"守卫已放行但旧库还在"
         * 这种 Room 崩溃且无法自愈的状态。
         */
        private fun ensureAssetUpToDate(context: Context): List<FavoriteBackup> {
            val prefs = context.getSharedPreferences(GUARD_PREFS, Context.MODE_PRIVATE)
            // 必须含 SCHEMA_VERSION：只比 identityHash 时，单独递增 Room version（实体未变
            // 但强制重复制库、或改 DAO 视图）不会触发删库，Room 随即抛
            // "A migration from 1 to 2 was not provided" —— 老用户升级即闪退。
            val identity = "${AssetManifest.SCHEMA_VERSION}/${AssetManifest.IDENTITY_HASH}/" +
                AssetManifest.DATA_VERSION
            if (prefs.getString(GUARD_KEY, null) == identity) return emptyList()
            val dbFile = context.getDatabasePath(DB_NAME)
            if (!dbFile.exists()) {
                prefs.edit().putString(GUARD_KEY, identity).apply()
                return emptyList()
            }
            val backup = readFavorites(dbFile)
            if (!dbFile.delete()) return emptyList()
            context.getDatabasePath("$DB_NAME-wal").delete()
            context.getDatabasePath("$DB_NAME-shm").delete()
            prefs.edit().putString(GUARD_KEY, identity).apply()
            return backup
        }

        private data class FavoriteBackup(val stockId: Long, val addedAt: Long)

        /** 旧库可能没有 favorite 表（更早期版本），读失败按空处理。 */
        private fun readFavorites(dbFile: java.io.File): List<FavoriteBackup> = runCatching {
            android.database.sqlite.SQLiteDatabase.openDatabase(
                dbFile.absolutePath, null, android.database.sqlite.SQLiteDatabase.OPEN_READONLY,
            ).use { sq ->
                sq.rawQuery("SELECT stock_id, added_at FROM favorite", null).use { c ->
                    generateSequence { if (c.moveToNext()) FavoriteBackup(c.getLong(0), c.getLong(1)) else null }.toList()
                }
            }
        }.getOrDefault(emptyList())

        private fun reinsertFavorites(db: AppDatabase, rows: List<FavoriteBackup>) {
            if (rows.isEmpty()) return
            val sq = db.openHelper.writableDatabase
            sq.beginTransaction()
            try {
                rows.forEach { row ->
                    sq.insert(
                        "favorite",
                        android.database.sqlite.SQLiteDatabase.CONFLICT_REPLACE,
                        android.content.ContentValues().apply {
                            put("stock_id", row.stockId)
                            put("added_at", row.addedAt)
                        },
                    )
                }
                sq.setTransactionSuccessful()
            } finally {
                sq.endTransaction()
            }
        }
    }
}
