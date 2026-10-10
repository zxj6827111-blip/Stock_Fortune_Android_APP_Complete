package com.stockfortune.app

import android.app.Application
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.stockfortune.app.data.db.AppDatabase
import com.stockfortune.app.data.db.AssetManifest
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.io.File
import java.io.FileOutputStream

/**
 * Phase 7 代码审查问题六专项验收：真实 V1.2 至 V1.3 升级与收藏无损恢复测试。
 *
 * 验收要求：
 * 1. 使用真实 V1.2 预置数据库 (Schema 1) 作为升级前输入；
 * 2. 验证 V1.2 Schema 1 升级到 V1.3 Schema 4 的全过程；
 * 3. 先以真实股票内部 ID 添加多只收藏（严禁以 600519 作为内部 stockId）；
 * 4. 证明升级后收藏仍然严格对应原股票代码和名称（茅台 4898, 平安 349, 比亚迪 1541, 平安银行 5161）；
 * 5. 验证升级中断崩溃恢复：持久备份在 SharedPreferences 中未提交前不丢失；
 * 6. 验证自增 ID 偏移自愈：通过 symbol/code 自动重对齐新 ID。
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class V12ToV13UpgradeTest {

    private lateinit var app: Application
    private val dbName = "stock_fortune.db"

    @Before
    fun setup() {
        app = ApplicationProvider.getApplicationContext()
        AppDatabase.resetForTesting()
        clearAllState()
    }

    @After
    fun tearDown() {
        AppDatabase.resetForTesting()
        clearAllState()
    }

    private fun clearAllState() {
        val dbFile = app.getDatabasePath(dbName)
        if (dbFile.exists()) dbFile.delete()
        app.getDatabasePath("$dbName-wal").delete()
        app.getDatabasePath("$dbName-shm").delete()
        app.getSharedPreferences("asset_guard", Context.MODE_PRIVATE).edit().clear().commit()
        app.getSharedPreferences("favorites_persistent_backup", Context.MODE_PRIVATE).edit().clear().commit()
    }

    private fun copyRealV12DatabaseToApp(): File {
        val targetFile = app.getDatabasePath(dbName)
        targetFile.parentFile?.mkdirs()

        // 从 test resources 中读取真实 V1.2 预置库 (user_version = 1)
        val resourceStream = javaClass.classLoader!!.getResourceAsStream("databases/v1.2_stock_fortune.db")
            ?: throw IllegalStateException("真实 V1.2 数据库资源 databases/v1.2_stock_fortune.db 未找到")

        FileOutputStream(targetFile).use { out ->
            resourceStream.copyTo(out)
        }
        return targetFile
    }

    @Test
    fun testRealV12ToV13UpgradeWithRealStockIds() = runBlocking {
        // 1. 部署真实 V1.2 数据库
        val dbFile = copyRealV12DatabaseToApp()
        assertTrue("V1.2 数据库文件应存在", dbFile.exists())

        // 2. 校验升级前为 Schema 1
        SQLiteDatabase.openDatabase(dbFile.absolutePath, null, SQLiteDatabase.OPEN_READWRITE).use { sq ->
            sq.rawQuery("PRAGMA user_version", null).use { c ->
                assertTrue(c.moveToFirst())
                assertEquals("升级前 Schema 版本必须为 1", 1, c.getInt(0))
            }

            // 3. 写入多只真实股票内部 ID 的收藏（非股票代码模拟 ID）：
            // 茅台: 4898 (600519.SH), 中国平安: 349 (601318.SH), 比亚迪: 1541 (002594.SZ), 平安银行: 5161 (000001.SZ)
            sq.execSQL("DELETE FROM favorite")
            sq.execSQL("INSERT INTO favorite (stock_id, added_at) VALUES (4898, 1700000001000)")
            sq.execSQL("INSERT INTO favorite (stock_id, added_at) VALUES (349, 1700000002000)")
            sq.execSQL("INSERT INTO favorite (stock_id, added_at) VALUES (1541, 1700000003000)")
            sq.execSQL("INSERT INTO favorite (stock_id, added_at) VALUES (5161, 1700000004000)")

            sq.rawQuery("SELECT COUNT(*) FROM favorite", null).use { c ->
                assertTrue(c.moveToFirst())
                assertEquals("升级前应有 4 条真实收藏", 4, c.getInt(0))
            }
        }

        // 4. 模拟 V1.2 安装守卫状态（触发版本变更检测）
        app.getSharedPreferences("asset_guard", Context.MODE_PRIVATE)
            .edit()
            .putString("identity_hash", "1/OLD_V1_2_HASH/v1.2.0")
            .commit()

        // 5. 触发 V1.3 AppDatabase 初始化与升级自愈
        val v13Db = AppDatabase.get(app)
        assertNotNull("升级后数据库实例不能为空", v13Db)

        // 6. 验证升级后 Schema 达到 v4 且 Room 身份匹配
        val opened = v13Db.openHelper.readableDatabase
        opened.query("PRAGMA user_version").use { c ->
            assertTrue(c.moveToFirst())
            assertEquals("升级后 Schema 版本必须为 4", AssetManifest.SCHEMA_VERSION, c.getInt(0))
        }
        opened.query("SELECT identity_hash FROM room_master_table WHERE id=42").use { c ->
            assertTrue(c.moveToFirst())
            assertEquals("升级后 Room 身份哈希必须严格一致", AssetManifest.IDENTITY_HASH, c.getString(0))
        }

        // 7. 证明升级后收藏全部保留且严格对应原股票代码与名称
        val favoriteStocks = v13Db.favoriteDao().stocksWithInfo()
        assertEquals("升级后收藏数量必须完全一致（4条）", 4, favoriteStocks.size)

        val favStockIds = favoriteStocks.map { it.id }.toSet()
        assertTrue("必须保留贵州茅台 (ID 4898)", favStockIds.contains(4898L))
        assertTrue("必须保留中国平安 (ID 349)", favStockIds.contains(349L))
        assertTrue("必须保留比亚迪 (ID 1541)", favStockIds.contains(1541L))
        assertTrue("必须保留平安银行 (ID 5161)", favStockIds.contains(5161L))

        assertTrue(v13Db.favoriteDao().isFavorite(4898L))
        assertTrue(v13Db.favoriteDao().isFavorite(349L))
        assertTrue(v13Db.favoriteDao().isFavorite(1541L))
        assertTrue(v13Db.favoriteDao().isFavorite(5161L))

        // 严格逐一比对股票代码与名称
        val moutai = v13Db.stockDao().findById(4898L)
        assertNotNull(moutai)
        assertEquals("600519.SH", moutai!!.code)
        assertEquals("贵州茅台", moutai.name)

        val pingan = v13Db.stockDao().findById(349L)
        assertNotNull(pingan)
        assertEquals("601318.SH", pingan!!.code)
        assertEquals("中国平安", pingan.name)

        val byd = v13Db.stockDao().findById(1541L)
        assertNotNull(byd)
        assertEquals("002594.SZ", byd!!.code)
        assertEquals("比亚迪", byd.name)

        val bank = v13Db.stockDao().findById(5161L)
        assertNotNull(bank)
        assertEquals("000001.SZ", bank!!.code)
        assertEquals("平安银行", bank.name)

        // 8. 验证持久备份已在成功写入后原子清理
        val backupJson = app.getSharedPreferences("favorites_persistent_backup", Context.MODE_PRIVATE)
            .getString("backup_json", null)
        assertTrue("持久备份在事务写入成功后应已清除", backupJson == null)
    }

    @Test
    fun testCrashInterruptionRecoveryFromPersistedBackup() = runBlocking {
        // 模拟升级中间进程崩溃：旧库已删，但持久备份 SharedPreferences 已安全写入
        val backupArr = org.json.JSONArray().apply {
            put(org.json.JSONObject().apply {
                put("stockId", 4898L)
                put("code", "600519.SH")
                put("symbol", "600519")
                put("name", "贵州茅台")
                put("addedAt", 1700000001000)
            })
            put(org.json.JSONObject().apply {
                put("stockId", 349L)
                put("code", "601318.SH")
                put("symbol", "601318")
                put("name", "中国平安")
                put("addedAt", 1700000002000)
            })
        }

        app.getSharedPreferences("favorites_persistent_backup", Context.MODE_PRIVATE)
            .edit()
            .putString("backup_json", backupArr.toString())
            .commit()

        // 重新启动应用，触发 AppDatabase.get(app)
        val db = AppDatabase.get(app)
        val favs = db.favoriteDao().stocksWithInfo()

        assertEquals("崩溃恢复后收藏应完整还原", 2, favs.size)
        assertTrue(favs.any { it.id == 4898L })
        assertTrue(favs.any { it.id == 349L })
    }

    @Test
    fun testIdShiftAutoRealignmentBySymbolAndCode() = runBlocking {
        // 模拟极端情形：旧版本中内部 ID 为 99999L，但股票代码为 600519.SH / 600519
        val backupArr = org.json.JSONArray().apply {
            put(org.json.JSONObject().apply {
                put("stockId", 99999L) // 偏移的旧 ID
                put("code", "600519.SH")
                put("symbol", "600519")
                put("name", "贵州茅台")
                put("addedAt", 1700000001000)
            })
        }

        app.getSharedPreferences("favorites_persistent_backup", Context.MODE_PRIVATE)
            .edit()
            .putString("backup_json", backupArr.toString())
            .commit()

        val db = AppDatabase.get(app)
        val favs = db.favoriteDao().stocksWithInfo()

        assertEquals(1, favs.size)
        // 验证自动通过 symbol/code 找回了 V1.3 中的真实内部 ID (4898L)
        assertEquals("ID偏移时应自动通过代码对齐新ID", 4898L, favs[0].id)
        assertEquals("贵州茅台", favs[0].name)
        assertEquals("600519.SH", favs[0].code)
    }

    @Test
    fun testUnmappableStockNeverFallsBackToMismatchedId() = runBlocking {
        // 验证：当旧收藏记录包含不存在的代码（如退市已注销的假股票），严禁回退到可能属于别人的旧 ID
        val backupArr = org.json.JSONArray().apply {
            put(org.json.JSONObject().apply {
                put("stockId", 1L) // ID=1 在新库中是中国天楹
                put("code", "999999.SH") // 不存在的代码
                put("symbol", "999999")
                put("name", "虚构退市股")
                put("addedAt", 1700000001000)
            })
        }

        app.getSharedPreferences("favorites_persistent_backup", Context.MODE_PRIVATE)
            .edit()
            .putString("backup_json", backupArr.toString())
            .commit()

        val db = AppDatabase.get(app)
        val favs = db.favoriteDao().stocksWithInfo()

        // 严禁将虚构股票错误挂载到 ID=1 (中国天楹) 名下！
        assertTrue("无法映射的股票严禁错误挂到其他股票名下", favs.none { it.name == "中国天楹" })
        assertEquals("无法可靠映射的收藏应被安全跳过", 0, favs.size)
    }

    @Test
    fun testCorruptedDbFileDoesNotWipeData() = runBlocking {
        // 模拟旧库文件损坏或无法读取：此时系统坚决禁止删除数据库
        val targetFile = app.getDatabasePath(dbName)
        targetFile.parentFile?.mkdirs()
        targetFile.writeText("THIS IS A CORRUPTED DB FILE CONTENT")

        app.getSharedPreferences("asset_guard", Context.MODE_PRIVATE)
            .edit()
            .putString("identity_hash", "1/OLD_HASH/v1.2.0")
            .commit()

        // 执行升级准备逻辑（验证损坏文件不会被静默当空库抹杀）
        val result = AppDatabase.readFavoritesWithDetails(targetFile)
        assertTrue("损坏数据库读取必须返回 Failure", result.isFailure)
    }

    @Test
    fun testMixedFavoritesRecoveryWithRetrySafety() = runBlocking {
        // 场景验收：旧库存在 10 条收藏，其中 9 条可以映射，1 条无法映射。
        // 要求：
        // 1. 9 条正常恢复；
        // 2. 未映射的 1 条继续保存于可靠的待恢复记录；
        // 3. 不得因 mappedCount > 0 就清除所有备份；
        // 4. 重新启动后能够安全重试；
        // 5. 不允许错误映射到其他股票。

        val realStocks = listOf(
            Triple(4898L, "600519.SH", "贵州茅台"),
            Triple(349L, "601318.SH", "中国平安"),
            Triple(1541L, "002594.SZ", "比亚迪"),
            Triple(5161L, "000001.SZ", "平安银行"),
            Triple(2026L, "600036.SH", "招商银行"),
            Triple(132L, "300750.SZ", "宁德时代"),
            Triple(1793L, "000333.SZ", "美的集团"),
            Triple(2998L, "000002.SZ", "万科A"),
            Triple(5209L, "601857.SH", "中国石油"),
        )

        val backupArr = org.json.JSONArray()
        var ts = 1700000000000L
        realStocks.forEach { (id, code, name) ->
            backupArr.put(org.json.JSONObject().apply {
                put("stockId", id)
                put("code", code)
                put("symbol", code.substringBefore("."))
                put("name", name)
                put("addedAt", ts++)
            })
        }
        // 第 10 条：无法映射的代码（如退市已注销的股票）
        backupArr.put(org.json.JSONObject().apply {
            put("stockId", 99999L)
            put("code", "999999.SH")
            put("symbol", "999999")
            put("name", "退市未知股")
            put("addedAt", ts++)
        })

        assertEquals("总备份数量必须为 10 条", 10, backupArr.length())

        app.getSharedPreferences("favorites_persistent_backup", Context.MODE_PRIVATE)
            .edit()
            .putString("backup_json", backupArr.toString())
            .commit()

        // 第一次启动应用，触发恢复
        val db1 = AppDatabase.get(app)
        val favs1 = db1.favoriteDao().stocksWithInfo()

        // 校验 1：9 条正常恢复，未映射的 1 条不入库
        assertEquals("9 条可识别股票必须正常恢复", 9, favs1.size)
        realStocks.forEach { (id, code, name) ->
            val f = favs1.firstOrNull { it.id == id }
            assertNotNull("股票 $name ($code) 必须成功恢复", f)
            assertEquals(code, f!!.code)
            assertEquals(name, f.name)
        }
        assertTrue("严禁将未映射股票错误入库或挂载", favs1.none { it.id == 99999L || it.code == "999999.SH" })

        // 校验 2 & 3：不得因 mappedCount > 0 清除所有备份，未映射项必须保存在持久备份中
        val backupPrefs = app.getSharedPreferences("favorites_persistent_backup", Context.MODE_PRIVATE)
        val remainingJson1 = backupPrefs.getString("backup_json", null)
        assertNotNull("未映射记录必须继续持久化保留在备份中", remainingJson1)
        val remainingArr1 = org.json.JSONArray(remainingJson1)
        assertEquals("备份中应仅保留未映射的 1 条记录", 1, remainingArr1.length())
        assertEquals("999999.SH", remainingArr1.getJSONObject(0).getString("code"))

        // 校验 4：重启应用，验证安全幂等重试
        AppDatabase.resetForTesting()
        val db2 = AppDatabase.get(app)
        val favs2 = db2.favoriteDao().stocksWithInfo()
        assertEquals("重启后数据库内恢复的 9 条收藏不受影响", 9, favs2.size)

        val remainingJson2 = backupPrefs.getString("backup_json", null)
        assertNotNull("重启后未映射记录依然安全保留待重试", remainingJson2)
        val remainingArr2 = org.json.JSONArray(remainingJson2)
        assertEquals(1, remainingArr2.length())
        assertEquals("999999.SH", remainingArr2.getJSONObject(0).getString("code"))

        // 校验 5：模拟字典补齐该股票，再次启动能够成功重试并清空备份
        val sq2 = db2.openHelper.writableDatabase
        sq2.execSQL(
            """INSERT INTO stock (id, code, symbol, name, exchange, board, listing_date, first_day_flag, industry, industry_full, stock_nature)
               VALUES (99999, '999999.SH', '999999', '退市未知股', 'SH', '主板', '2020-01-01', 'NORMAL', '综合', '综合类', '普通股')"""
        )
        AppDatabase.resetForTesting()
        val db3 = AppDatabase.get(app)
        val favs3 = db3.favoriteDao().stocksWithInfo()
        assertEquals("字典更新重试后全部 10 条收藏均成功恢复", 10, favs3.size)
        assertTrue("第 10 条股票已成功入库", favs3.any { it.code == "999999.SH" })

        val finalBackupJson = backupPrefs.getString("backup_json", null)
        assertTrue("所有收藏均成功恢复后，持久备份应彻底清空", finalBackupJson == null)
    }
}
