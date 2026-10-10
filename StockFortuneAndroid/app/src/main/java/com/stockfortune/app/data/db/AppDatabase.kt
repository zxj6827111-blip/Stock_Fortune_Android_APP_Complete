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

        @androidx.annotation.VisibleForTesting
        fun resetForTesting() {
            synchronized(this) {
                instance?.close()
                instance = null
            }
        }

        private const val BACKUP_PREFS = "favorites_persistent_backup"
        private const val BACKUP_KEY_JSON = "backup_json"

        private fun build(app: Context): AppDatabase {
            ensureAssetUpToDate(app)
            val db = Room.databaseBuilder(app, AppDatabase::class.java, DB_NAME)
                .createFromAsset(ASSET_PATH)
                .setJournalMode(JournalMode.TRUNCATE)
                .build()
            reinsertFavorites(db, app)
            return db
        }

        /**
         * Room 只在数据库文件不存在时才复制 assets。装上带 schema/数据变更的新包后，
         * 旧的本地库会让 Room 抛 "cannot verify the data integrity" 直接崩溃。
         *
         * 持久化安全升级策略：
         * 1. 升级前先将收藏与股票代码/名称写入独立持久存储（SharedPreferences commit）；
         * 2. 删库重建，即使在复制或建库过程中发生异常中断，持久备份不会丢失；
         * 3. 重建后优先通过股票代码与名称校验/重对齐 stock_id，防止版本间自增 ID 偏移；
         * 4. 只有在事务写回完全成功后才清除持久备份。
         */
        private fun ensureAssetUpToDate(context: Context) {
            val prefs = context.getSharedPreferences(GUARD_PREFS, Context.MODE_PRIVATE)
            val identity = "${AssetManifest.SCHEMA_VERSION}/${AssetManifest.IDENTITY_HASH}/" +
                AssetManifest.DATA_VERSION
            if (prefs.getString(GUARD_KEY, null) == identity) return
            val dbFile = context.getDatabasePath(DB_NAME)
            if (!dbFile.exists()) {
                prefs.edit().putString(GUARD_KEY, identity).commit()
                return
            }

            // 1. 读取旧库收藏并关联股票身份。旧收藏读取失败绝不能被当作空收藏继续删库！
            val readResult = readFavoritesWithDetails(dbFile)
            if (readResult.isFailure) {
                android.util.Log.e("AppDatabase", "读取旧库收藏失败，终止删库以防丢失用户数据: ${readResult.exceptionOrNull()?.message}")
                return
            }

            val backup = readResult.getOrNull() ?: emptyList()
            if (backup.isNotEmpty()) {
                val persistedOk = persistBackup(context, backup)
                if (!persistedOk) {
                    android.util.Log.e("AppDatabase", "持久化收藏备份写入失败，终止删库以防数据丢失")
                    return
                }
            }

            // 2. 移除旧数据库触发 Room 重新复制
            if (!dbFile.delete()) return
            context.getDatabasePath("$DB_NAME-wal").delete()
            context.getDatabasePath("$DB_NAME-shm").delete()
            prefs.edit().putString(GUARD_KEY, identity).commit()
        }

        data class FavoriteBackup(
            val stockId: Long,
            val code: String = "",
            val symbol: String = "",
            val name: String = "",
            val addedAt: Long = 0L,
        )

        private fun persistBackup(context: Context, rows: List<FavoriteBackup>): Boolean {
            val arr = org.json.JSONArray()
            rows.forEach { r ->
                val o = org.json.JSONObject()
                o.put("stockId", r.stockId)
                o.put("code", r.code)
                o.put("symbol", r.symbol)
                o.put("name", r.name)
                o.put("addedAt", r.addedAt)
                arr.put(o)
            }
            return context.getSharedPreferences(BACKUP_PREFS, Context.MODE_PRIVATE)
                .edit().putString(BACKUP_KEY_JSON, arr.toString()).commit()
        }

        private fun loadPersistedBackup(context: Context): List<FavoriteBackup> {
            val s = context.getSharedPreferences(BACKUP_PREFS, Context.MODE_PRIVATE)
                .getString(BACKUP_KEY_JSON, null) ?: return emptyList()
            return runCatching {
                val arr = org.json.JSONArray(s)
                val list = mutableListOf<FavoriteBackup>()
                for (i in 0 until arr.length()) {
                    val o = arr.getJSONObject(i)
                    list.add(
                        FavoriteBackup(
                            stockId = o.getLong("stockId"),
                            code = o.optString("code", ""),
                            symbol = o.optString("symbol", ""),
                            name = o.optString("name", ""),
                            addedAt = o.optLong("addedAt", System.currentTimeMillis()),
                        )
                    )
                }
                list
            }.getOrDefault(emptyList())
        }

        private fun clearPersistedBackup(context: Context): Boolean {
            return context.getSharedPreferences(BACKUP_PREFS, Context.MODE_PRIVATE)
                .edit().remove(BACKUP_KEY_JSON).commit()
        }

        /** 旧库读取：返回 Result。若发生任何未预期异常，返回 Failure，调用方坚决禁止删库。 */
        fun readFavoritesWithDetails(dbFile: java.io.File): Result<List<FavoriteBackup>> = runCatching {
            android.database.sqlite.SQLiteDatabase.openDatabase(
                dbFile.absolutePath, null, android.database.sqlite.SQLiteDatabase.OPEN_READONLY,
            ).use { sq ->
                val hasFav = sq.rawQuery(
                    "SELECT 1 FROM sqlite_master WHERE type='table' AND name='favorite'", null
                ).use { it.moveToFirst() }
                if (!hasFav) return@runCatching emptyList()

                val hasStock = sq.rawQuery(
                    "SELECT 1 FROM sqlite_master WHERE type='table' AND name='stock'", null
                ).use { it.moveToFirst() }

                val sql = if (hasStock) {
                    """SELECT f.stock_id, COALESCE(s.code, ''), COALESCE(s.symbol, ''), COALESCE(s.name, ''), f.added_at
                       FROM favorite f LEFT JOIN stock s ON s.id = f.stock_id"""
                } else {
                    "SELECT stock_id, '', '', '', added_at FROM favorite"
                }

                sq.rawQuery(sql, null).use { c ->
                    generateSequence {
                        if (c.moveToNext()) {
                            FavoriteBackup(
                                stockId = c.getLong(0),
                                code = c.getString(1),
                                symbol = c.getString(2),
                                name = c.getString(3),
                                addedAt = c.getLong(4),
                            )
                        } else null
                    }.toList()
                }
            }
        }

        private fun reinsertFavorites(db: AppDatabase, context: Context) {
            val rows = loadPersistedBackup(context)
            if (rows.isEmpty()) return
            val sq = db.openHelper.writableDatabase
            var mappedCount = 0
            val unmapped = mutableListOf<FavoriteBackup>()

            sq.beginTransaction()
            try {
                rows.forEach { row ->
                    // 严格通过代码或 symbol 找回新库中的自增主键，防止错配
                    var targetId: Long? = null
                    if (row.symbol.isNotBlank() || row.code.isNotBlank()) {
                        sq.query(
                            "SELECT id FROM stock WHERE symbol = ? OR code = ? LIMIT 1",
                            arrayOf(row.symbol, row.code),
                        ).use { c ->
                            if (c.moveToFirst()) {
                                targetId = c.getLong(0)
                            }
                        }
                    }

                    // 股票 ID 找不到可靠代码映射时，尝试验证旧 ID 是否与旧代码一致
                    if (targetId == null && (row.symbol.isNotBlank() || row.code.isNotBlank())) {
                        sq.query(
                            "SELECT id FROM stock WHERE id = ? AND (symbol = ? OR code = ?) LIMIT 1",
                            arrayOf(row.stockId.toString(), row.symbol, row.code),
                        ).use { c ->
                            if (c.moveToFirst()) {
                                targetId = c.getLong(0)
                            }
                        }
                    }

                    // 严禁在找不到任何代码匹配时盲目回退到旧 ID，防止把收藏错挂到不相干股票上！
                    if (targetId != null) {
                        sq.insert(
                            "favorite",
                            android.database.sqlite.SQLiteDatabase.CONFLICT_REPLACE,
                            android.content.ContentValues().apply {
                                put("stock_id", targetId)
                                put("added_at", row.addedAt)
                            },
                        )
                        mappedCount++
                    } else {
                        android.util.Log.w("AppDatabase", "收藏恢复跳过未映射股票并保留待重试：code=${row.code}, symbol=${row.symbol}, oldId=${row.stockId}")
                        unmapped.add(row)
                    }
                }
                sq.setTransactionSuccessful()
            } finally {
                sq.endTransaction()
            }

            // 混合恢复持久安全规则：
            // 1. 全部成功映射时，彻底清空持久备份；
            // 2. 存在未映射项时，仅持久保留未映射项，严禁因 mappedCount > 0 清空所有备份导致未映射项永久丢失；
            // 3. 后续冷启动或数据库更新后可继续安全重试未映射项。
            if (unmapped.isEmpty()) {
                clearPersistedBackup(context)
            } else {
                persistBackup(context, unmapped)
                android.util.Log.w("AppDatabase", "收藏恢复完成：已恢复 $mappedCount 条，仍有 ${unmapped.size} 条未映射股票保留在备份中待重试")
            }
        }
    }
}
