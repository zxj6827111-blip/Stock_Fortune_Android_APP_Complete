package com.stockfortune.app

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.stockfortune.app.data.db.AppDatabase
import com.stockfortune.app.data.db.AssetManifest
import com.stockfortune.app.data.repository.CalendarRepository
import com.stockfortune.app.data.repository.ClassicQuoteRepository
import java.io.BufferedReader
import java.security.MessageDigest
import java.util.Locale
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * 出厂资产与代码声明的一致性测试。
 *
 * 这一层此前完全没有覆盖：预置库由 `tools/build_database.py` + `inject_room_hash.py`
 * 生成，`.kt` 清单是脚本回写的源码文件 —— 忘跑脚本、手工替换 .db、或只递增 Room version
 * 都不会被任何测试发现，只能在老用户升级后以 "cannot verify the data integrity" 的形式崩出来。
 * 而 16467 行日历里年柱 / 月柱的口径在 Kotlin 侧原先只有 1 个日期被测。
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class AssetParityTest {

    private fun ctx() =
        androidx.test.core.app.ApplicationProvider.getApplicationContext<android.app.Application>()

    private fun fixture(name: String): List<List<String>> {
        val stream = javaClass.classLoader?.getResourceAsStream("parity/$name")
        assertNotNull("缺少夹具 parity/$name，请先运行 tools/gen_parity_fixtures.py", stream)
        return BufferedReader(stream!!.reader()).readLines()
            .drop(1)
            .filter { it.isNotBlank() }
            .map { it.split(",") }
    }

    @Test
    fun `清单身份与预置库字节一致`() {
        // inject_room_hash.py 把 md5(整库)[:12] 写进 AssetManifest.DATA_VERSION 后缀。
        // 忘跑脚本就重新打包时 DATA_VERSION 与库内容脱钩，资产守卫既不会删旧库、
        // 也不会提示，Room 随即在已安装设备上抛完整性错误。
        val bytes = ctx().assets.open(AppDatabase.ASSET_PATH).use { it.readBytes() }
        val digest = MessageDigest.getInstance("MD5").digest(bytes)
        val hex = digest.joinToString("") { "%02x".format(it) }
        val tag = AssetManifest.DATA_VERSION
        assertTrue("DATA_VERSION 缺少内容哈希后缀: $tag", tag.contains('+'))
        assertEquals("预置库内容与 AssetManifest 不符（跑一遍 tools/inject_room_hash.py）",
            hex.take(12), tag.substringAfterLast('+'))
    }

    @Test
    fun `预置库内 Room 身份与清单一致`() = runBlocking {
        val sq = AppDatabase.get(ctx()).openHelper.readableDatabase
        sq.query("SELECT identity_hash FROM room_master_table").use { c ->
            assertTrue("预置库缺少 room_master_table", c.moveToFirst())
            assertEquals(AssetManifest.IDENTITY_HASH, c.getString(0))
        }
        sq.query("PRAGMA user_version").use { c ->
            assertTrue(c.moveToFirst())
            assertEquals("库内版本与清单 SCHEMA_VERSION 不符", AssetManifest.SCHEMA_VERSION, c.getInt(0))
        }
    }

    @Test
    fun `日历夹具的年柱月柱与预置库逐行一致`() = runBlocking {
        val calendar = CalendarRepository(AppDatabase.get(ctx()).calendarDao())
        val rows = fixture("ganzhi_sample.csv")
        // 夹具由 gen_parity_fixtures.py 生成，含 1991-2035 每个交节当日 —— 正是最容易
        // 整月漂移的那批日期。旧测试只断言它们是"合法干支"，等于没有校验。
        assertTrue("夹具行数过少，疑似生成脚本变更: ${rows.size}", rows.size > 600)
        val mismatched = ArrayList<String>()
        rows.forEach { (date, yearGz, monthGz, dayGz) ->
            val db = calendar.ganzhi(date)
            if (db == null) {
                mismatched += "$date 库内无该日"
            } else {
                if (db.yearGanzhi != yearGz) mismatched += "$date 年柱 库=${db.yearGanzhi} 夹具=$yearGz"
                if (db.monthGanzhi != monthGz) mismatched += "$date 月柱 库=${db.monthGanzhi} 夹具=$monthGz"
                if (db.dayGanzhi != dayGz) mismatched += "$date 日柱 库=${db.dayGanzhi} 夹具=$dayGz"
            }
        }
        assertEquals("年/月柱与 Python 参考实现不符", emptyList<String>(), mismatched.take(10))
        assertEquals("不符条目超过 0 条", 0, mismatched.size)
    }

    @Test
    fun `每个交节当日的月柱都发生切换`() = runBlocking {
        val calendar = CalendarRepository(AppDatabase.get(ctx()).calendarDao())
        val rows = fixture("ganzhi_sample.csv")
        val transitions = ArrayList<String>()
        for (i in 1 until rows.size) {
            val prev = rows[i - 1]
            val cur = rows[i]
            if (prev[0].take(4) == cur[0].take(4) && prev[2] != cur[2]) {
                transitions += cur[0]
            }
        }
        // 夹具按时间排序，同年内月柱不同的相邻样本必然跨过至少一个节
        assertTrue("同年内出现月柱差异的样本对为 0，夹具或月柱口径异常", transitions.isNotEmpty())
        assertNotNull(calendar.ganzhi(rows.first()[0]))
    }
}
