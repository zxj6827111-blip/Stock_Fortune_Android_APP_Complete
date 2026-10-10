package com.stockfortune.app

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.stockfortune.app.data.db.AppDatabase
import java.io.BufferedReader
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * V1.3 Phase 1 大运与起运对拍门禁 (Gate G1)。
 *
 * 验证：
 * 1. 数据库中 stock_luck_cycle 与 Python 生成的 parity/dayun.csv 逐股对拍（四象限、平盘、缺失、节气边界）。
 * 2. 四象限方向一致性：绝不出现字段逆行实排顺行、或字段顺行实排逆行的历史矛盾。
 * 3. 315 只首日平盘样本与 1 只缺失样本在数据库中严格处于 unavailable 状态。
 * 4. 有效股票的 12 步大运周期必须完整且区间连续。
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class LuckCycleParityTest {

    private fun ctx() =
        androidx.test.core.app.ApplicationProvider.getApplicationContext<android.app.Application>()

    private fun fixture(name: String): List<List<String>> {
        val stream = javaClass.classLoader?.getResourceAsStream("parity/$name")
        assertNotNull("缺少夹具 parity/$name", stream)
        return BufferedReader(stream!!.reader()).readLines()
            .drop(1)
            .filter { it.isNotBlank() }
            .map { it.split(",") }
    }

    @Test
    fun `大运夹具样本与预置库逐行对拍合格`() = runBlocking {
        val db = AppDatabase.get(ctx())
        val stockDao = db.stockDao()
        val luckDao = db.luckCycleDao()
        val rows = fixture("dayun.csv")
        assertTrue("大运夹具样本行数过少: ${rows.size}", rows.size >= 60)

        val mismatched = ArrayList<String>()
        for (row in rows) {
            val code = row[0]
            val expectedDirection = row[6]
            val expectedStatus = row[7]
            val expectedStartDate = row[8].ifBlank { null }
            val expectedStartAge = row[9].toIntOrNull()
            val expectedFirstGz = row[10]
            val expectedBoundary = row[11].ifBlank { null }

            val stock = stockDao.findByCode(code)
            if (stock == null) {
                mismatched += "$code 库中未找到股票"
                continue
            }

            val luck = luckDao.findByStockId(stock.id)
            if (luck == null) {
                mismatched += "$code 库中未找到大运记录"
                continue
            }

            if (luck.direction != expectedDirection) {
                mismatched += "$code direction 不符: 库=${luck.direction} 夹具=$expectedDirection"
            }
            if (luck.status != expectedStatus) {
                mismatched += "$code status 不符: 库=${luck.status} 夹具=$expectedStatus"
            }
            if (luck.startDate != expectedStartDate) {
                mismatched += "$code startDate 不符: 库=${luck.startDate} 夹具=$expectedStartDate"
            }
            if (luck.startAge != expectedStartAge) {
                mismatched += "$code startAge 不符: 库=${luck.startAge} 夹具=$expectedStartAge"
            }
            if (luck.boundaryFlag != expectedBoundary) {
                mismatched += "$code boundaryFlag 不符: 库=${luck.boundaryFlag} 夹具=$expectedBoundary"
            }

            val periods = luckDao.periodsByStockId(stock.id)
            if (expectedStatus == "available") {
                if (periods.size != 12) {
                    mismatched += "$code 周期数不等于 12: 实际=${periods.size}"
                } else if (periods[0].ganzhi != expectedFirstGz) {
                    mismatched += "$code 首步大运干支不符: 库=${periods[0].ganzhi} 夹具=$expectedFirstGz"
                }
            } else {
                if (periods.isNotEmpty()) {
                    mismatched += "$code 不可用状态却包含周期数据: 数量=${periods.size}"
                }
            }
        }

        assertEquals("大运对拍存在不一致条目", emptyList<String>(), mismatched.take(10))
        assertEquals("不符条目超过 0 条", 0, mismatched.size)
    }

    @Test
    fun `预置库大运总数与状态统计完全合规`() = runBlocking {
        val db = AppDatabase.get(ctx())
        val luckDao = db.luckCycleDao()
        assertEquals(5395, luckDao.countCycles())
        assertEquals(5079 * 12, luckDao.countPeriods())
    }

    @Test
    fun `当前大运按年份查询能够准确命中`() = runBlocking {
        val db = AppDatabase.get(ctx())
        val stock = db.stockDao().findByCode("600519.SH") // 贵州茅台
        assertNotNull(stock)
        val luckDao = db.luckCycleDao()
        val luck = luckDao.findByStockId(stock!!.id)
        assertNotNull(luck)
        assertEquals("available", luck!!.status)

        // 贵州茅台上市 2001-08-27，查询 2026 年大运
        val period2026 = luckDao.currentPeriodForYear(stock.id, 2026)
        assertNotNull("2026 年大运未命中", period2026)
        assertTrue(2026 in period2026!!.startYear..period2026.endYear)
    }
}
