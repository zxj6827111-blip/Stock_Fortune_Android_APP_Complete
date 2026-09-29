package com.stockfortune.app

import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.stockfortune.app.data.db.AppDatabase
import com.stockfortune.app.data.repository.AnalysisRepository
import com.stockfortune.app.data.repository.CalendarRepository
import com.stockfortune.app.data.repository.StockRepository
import com.stockfortune.app.domain.model.FilterQuery
import com.stockfortune.app.domain.model.TenGod
import com.stockfortune.app.domain.model.WealthType
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * 运行时冒烟测试（Robolectric，JVM 内执行）：
 * 覆盖"预置库能否被 Room 打开 → DAO 真实查询 → 业务组装 → 首页能否渲染"这条最容易在真机上崩掉的链路。
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class RuntimeSmokeTest {

    private lateinit var db: AppDatabase
    private lateinit var stocks: StockRepository
    private lateinit var calendar: CalendarRepository
    private lateinit var analysis: AnalysisRepository

    @Before
    fun setUp() {
        val ctx = androidx.test.core.app.ApplicationProvider.getApplicationContext<android.app.Application>()
        db = AppDatabase.get(ctx)
        stocks = StockRepository(db.stockDao(), db.baziDao(), db.filterDao(), db.favoriteDao(), db.metaDao())
        calendar = CalendarRepository(db.calendarDao())
        analysis = AnalysisRepository(db.calendarDao(), db.baziDao(), db.filterDao(), db.scanCacheDao())
    }

    // AppDatabase 是进程单例，测试内不关闭：关闭会让后续用例拿到已关连接池的实例

    @Test
    fun `Room 能打开预置库并读到元数据`() = runBlocking {
        assertEquals(5395, db.stockDao().count())
        val meta = stocks.meta()
        assertEquals("5395", meta["stock_count"])
        assertNotNull("data_version 缺失", meta["data_version"])
        assertEquals("bazi-rule-v1.1", meta["rule_version"])
    }

    @Test
    fun `Room 打开预置库并通过身份与版本校验`() {
        val opened = db.openHelper.readableDatabase
        opened.query("PRAGMA user_version").use { c ->
            assertTrue(c.moveToFirst())
            assertEquals(1, c.getInt(0))
        }
        opened.query("SELECT identity_hash FROM room_master_table WHERE id=42").use { c ->
            assertTrue("room_master_table 缺行 → Room 会拒绝打开", c.moveToFirst())
            assertEquals(32, c.getString(0).length)
        }
    }

    @Test
    fun `股票详情与四柱读取正确`() = runBlocking {
        val detail = stocks.detail("600519")
        assertNotNull("查不到贵州茅台", detail)
        assertEquals("600519.SH", detail!!.stock.code)
        assertEquals("贵州茅台", detail.stock.name)
        assertEquals("2001-08-27", detail.stock.listingDate)
        assertEquals("壬戌", detail.bazi.dayPillar)
        assertEquals("乙巳", detail.bazi.hourPillar)
        assertEquals("大海水", detail.bazi.naYin)
        assertTrue("藏干十神为空", detail.hiddenTenGods.isNotEmpty())
    }

    @Test
    fun `搜索支持代码与名称`() = runBlocking {
        assertTrue(stocks.search("600519").first().name == "贵州茅台")
        assertTrue(stocks.search("茅台").isNotEmpty())
        assertTrue(stocks.search("000001").first().name.contains("平安银行"))
    }

    @Test
    fun `搜索通配符被转义而不是命中全部`() = runBlocking {
        assertTrue(stocks.search("%").isEmpty())
        assertTrue(stocks.search("_").isEmpty())
    }

    @Test
    fun `日历仓库返回交易日与干支`() = runBlocking {
        assertEquals("2026-09-29", calendar.nearestTradeDayOnOrBefore("2026-09-29"))
        assertEquals("2026-09-24", calendar.nearestTradeDayOnOrBefore("2026-09-27"))  // 09-25 中秋休市
        val gz = calendar.ganzhi("2026-09-29")!!
        assertEquals("丙午", gz.yearGanzhi)
        assertEquals("丁酉", gz.monthGanzhi)
        assertEquals("丙午", gz.dayGanzhi)
        assertTrue(calendar.isTradeDay("2026-09-29"))
        assertTrue(!calendar.isTradeDay("2026-09-27"))
    }

    @Test
    fun `年度月度每日分析可组装`() = runBlocking {
        val id = stocks.detail("600519")!!.stock.id
        val year = analysis.yearAnalysis(id, 2026)!!
        assertEquals(12, year.months.size)
        assertEquals("丙午", year.yearGanzhi)
        assertTrue("12 个月标签缺失财星", year.months.any { it.wealth.isWealth })

        val month = analysis.monthDays(id, 2026, 9)!!
        assertEquals(21, month.tradeDayCount)
        assertEquals(3, month.zhengCount)
        assertEquals(5, month.pianCount)
        assertEquals(month.tradeDayCount, month.tradeDays.size)
        assertTrue("每日标签应为流日天干十神", month.tradeDays.all { it.dayTenGod in TenGod.entries })

        val other = analysis.monthDays(id, 2026, 10)!!
        assertTrue("非交易日不应进入 tradeDays", other.tradeDays.none { !it.isTradeDay })
    }

    @Test
    fun `全市场扫描与十神筛选与 Python 口径一致`() = runBlocking {
        // Robolectric 每个用例都会新建 data 目录并复制 8MB 预置库，首查含复制开销；
        // 这里量的是真机上的稳态耗时（库已就位）。
        analysis.scan("2026-09-29", persist = false)
        val start = System.currentTimeMillis()
        val scan = analysis.scan("2026-09-29", persist = false)
        val cost = System.currentTimeMillis() - start
        assertEquals(465, scan.zhengCount)
        assertEquals(511, scan.pianCount)
        assertTrue("单日全市场扫描稳态耗时 ${cost}ms 超阈值", cost < 800)
        assertTrue(scan.rows.first().wealth == WealthType.ZHENG_CAI)

        val filtered = analysis.filter(FilterQuery(setOf(TenGod.ZHENG_CAI), emptySet(), emptySet(), emptySet(), "2026-09-29"))
        assertTrue("藏干正财筛选结果过少", filtered.size > 1000)
        // 流日丙午：癸日主透正财（465 只）、壬日主本气丁为正财（511 只），筛选按"天干或本气"命中
        val both = analysis.filter(FilterQuery(emptySet(), emptySet(), emptySet(), setOf(TenGod.ZHENG_CAI), "2026-09-29"))
        assertEquals(scan.zhengCount + scan.pianCount, both.size)
        assertEquals(976, both.size)
    }

    @Test
    fun `不同筛选条件必须产出不同结果并标注命中十神`() = runBlocking {
        fun q(hidden: Set<TenGod> = emptySet(), day: Set<TenGod> = emptySet(), date: String = "2026-09-29") =
            FilterQuery(hidden, emptySet(), emptySet(), day, date)

        val bijian = analysis.filter(q(hidden = setOf(TenGod.BI_JIAN)))
        val zhengcai = analysis.filter(q(hidden = setOf(TenGod.ZHENG_CAI)))
        assertTrue("比肩条件无结果", bijian.isNotEmpty())
        assertTrue("正财条件无结果", zhengcai.isNotEmpty())
        assertTrue("结果未标注命中十神", bijian.all { it.matchedGods.contains(TenGod.BI_JIAN) })
        assertTrue("结果未标注命中十神", zhengcai.all { it.matchedGods.contains(TenGod.ZHENG_CAI) })
        assertNotEquals(
            "两种条件的头部结果完全相同（回归：旧版按正财+代码排序导致看起来一模一样）",
            bijian.map { it.symbol }.take(50), zhengcai.map { it.symbol }.take(50),
        )

        val dayZ = analysis.filter(q(day = setOf(TenGod.ZHENG_CAI)))
        val dayP = analysis.filter(q(day = setOf(TenGod.PIAN_CAI)))
        assertTrue(dayZ.all { it.matchedGods.contains(TenGod.ZHENG_CAI) })
        assertTrue(dayP.all { it.matchedGods.contains(TenGod.PIAN_CAI) })

        // 换基准日后，流日维度结果必须变化（流年/流月/流日都相对基准日成立）
        val other = analysis.filter(q(day = setOf(TenGod.ZHENG_CAI), date = "2026-09-28"))
        assertNotEquals("换日期后结果不变", dayZ.map { it.symbol }, other.map { it.symbol })
    }

    @Test
    fun `单日详情返回流年流月流日与藏干四维加推导链`() = runBlocking {
        val id = stocks.detail("000605")!!.stock.id
        val d = analysis.dayDetail(id, "2026-09-01")
        assertNotNull("000605 单日详情为空", d)
        assertEquals("癸", d!!.dayMaster)
        assertTrue("2026-09-01 应为交易日", d.isTradeDay)
        assertEquals(listOf("流年", "流月", "流日"), d.flows.map { it.label })
        assertEquals(listOf("丙午", "丙申", "戊寅"), d.flows.map { it.ganzhi })
        assertEquals(
            "三个流运维度的十神必须全部就位",
            listOf(
                TenGod.ZHENG_CAI to TenGod.PIAN_CAI,
                TenGod.ZHENG_CAI to TenGod.ZHENG_YIN,
                TenGod.ZHENG_GUAN to TenGod.SHANG_GUAN,
            ),
            d.flows.map { it.stemGod to it.branchGod },
        )
        assertEquals(
            listOf(WealthType.ZHENG_CAI, WealthType.ZHENG_CAI, WealthType.OTHER),
            d.flows.map { it.wealth },
        )
        assertEquals("本命四柱藏干应为 4 柱 8 干", 8, d.hidden.sumOf { it.items.size })
        assertEquals(6, d.hiddenGods.size)
        assertEquals(listOf("年柱", "月柱", "日柱", "时柱"), d.hidden.map { it.label })
        val dayPillar = d.hidden.first { it.label == "日柱" }
        assertEquals("藏干须按本气·中气·余气排序", listOf("己", "癸", "辛"), dayPillar.items.map { it.stem })
        assertTrue("财星判定须自带推导链", d.basis.size >= 4 && d.basis.any { it.contains("藏而不透") })
    }

    @Test
    fun `八字择日返回财日并标记交易日`() = runBlocking {
        val id = stocks.detail("600519")!!.stock.id
        val rows = analysis.dateSelect(id, "2026-09-01", "2026-09-30")
        assertTrue("择日为空", rows.isNotEmpty())
        val tradeRows = rows.filter { it.isTradeDay }
        assertEquals(3, tradeRows.count { it.wealth == WealthType.ZHENG_CAI })
        assertEquals(5, tradeRows.count { it.wealth == WealthType.PIAN_CAI })
        assertTrue(rows.all { it.wealth.isWealth })
        assertTrue(rows.any { !it.isTradeDay } || rows.all { it.isTradeDay })
        val onlyTrade = analysis.dateSelect(id, "2026-09-01", "2026-09-30", onlyTradeDays = true)
        assertTrue(onlyTrade.all { it.isTradeDay })
        assertTrue(onlyTrade.size <= rows.size)
    }

    @Test
    fun `收藏与缓存写入可用`() = runBlocking {
        val id = stocks.detail("000001")!!.stock.id
        assertEquals(true, stocks.toggleFavorite(id))
        assertTrue(stocks.favoriteStocks().any { it.id == id })
        assertEquals(false, stocks.toggleFavorite(id))
        analysis.scan("2026-09-28")
        assertTrue("scan_cache 未写入", analysis.cacheSize() > 0)
        analysis.clearCache()
        assertEquals(0, analysis.cacheSize())
    }
}
