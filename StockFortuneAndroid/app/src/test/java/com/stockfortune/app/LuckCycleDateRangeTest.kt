package com.stockfortune.app

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.stockfortune.app.data.db.AppDatabase
import com.stockfortune.app.data.repository.AnalysisRepository
import com.stockfortune.app.data.repository.CalendarRepository
import com.stockfortune.app.data.repository.StockRepository
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * Phase 7 代码审查问题三专项验收：大运时间区间精准匹配测试。
 *
 * 验收要求：
 * 1. 不能只用年份判断当前大运，必须根据实际分析日期匹配 [start_date, end_date)；
 * 2. 明确起止边界包含规则：交运当日严格命中新一步大运，前一日属于上一运，后一日属于新运；
 * 3. 防止交运同一天重复命中或没有命中；
 * 4. 特别验证同一公历年内，不同流月分属不同大运的情况；
 * 5. 验证起运前日期优雅返回 null。
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class LuckCycleDateRangeTest {

    private lateinit var app: Application
    private lateinit var db: AppDatabase
    private lateinit var stockRepo: StockRepository
    private lateinit var analysisRepo: AnalysisRepository

    @Before
    fun setup() {
        app = ApplicationProvider.getApplicationContext()
        db = AppDatabase.get(app)
        val classicRepo = com.stockfortune.app.data.repository.ClassicQuoteRepository(app)
        stockRepo = StockRepository(
            db.stockDao(), db.baziDao(), db.filterDao(), db.favoriteDao(), db.metaDao(),
            classicRepo, db.luckCycleDao(), db.natalRelationDao(), db.yongshenDao(),
        )
        analysisRepo = AnalysisRepository(
            db.calendarDao(), db.baziDao(), db.filterDao(), db.scanCacheDao(),
            db.luckCycleDao(), db.natalRelationDao(), db.yongshenDao(), db.stockDao(),
        )
    }

    @Test
    fun testMoutaiHandoverDateStrictBoundary() = runBlocking {
        val luckDao = db.luckCycleDao()
        val moutai = stockRepo.detail("600519")!!
        val stockId = moutai.stock.id

        // 贵州茅台第 1 运 (乙未): 2008-03-18 至 2018-03-18
        // 贵州茅台第 2 运 (甲午): 2018-03-18 至 2028-03-18

        // 1. 交运前一日 (2018-03-17) 必须严格属于第 1 步大运 乙未
        val beforeHandover = luckDao.currentPeriodForDate(stockId, "2018-03-17")
        assertNotNull("交运前一日大运不能为空", beforeHandover)
        assertEquals(1, beforeHandover!!.cycleIndex)
        assertEquals("乙未", beforeHandover.ganzhi)
        assertEquals("2008-03-18", beforeHandover.startDate)
        assertEquals("2018-03-18", beforeHandover.endDate)

        // 2. 交运当日 (2018-03-18) 必须严格命中第 2 步大运 甲午，绝不滞留在上一运
        val onHandoverDay = luckDao.currentPeriodForDate(stockId, "2018-03-18")
        assertNotNull("交运当日大运不能为空", onHandoverDay)
        assertEquals(2, onHandoverDay!!.cycleIndex)
        assertEquals("甲午", onHandoverDay.ganzhi)
        assertEquals("2018-03-18", onHandoverDay.startDate)
        assertEquals("2028-03-18", onHandoverDay.endDate)

        // 3. 交运后一日 (2018-03-19) 必须严格属于第 2 步大运 甲午
        val afterHandover = luckDao.currentPeriodForDate(stockId, "2018-03-19")
        assertNotNull("交运后一日大运不能为空", afterHandover)
        assertEquals(2, afterHandover!!.cycleIndex)
        assertEquals("甲午", afterHandover.ganzhi)
    }

    @Test
    fun testSameCalendarYearDifferentMonthsBelongToDifferentLuckCycles() = runBlocking {
        val luckDao = db.luckCycleDao()
        val moutai = stockRepo.detail("600519")!!
        val stockId = moutai.stock.id

        // 2018 年 3 月 18 日交运
        // 2018 年 1 月处于第 1 运 (乙未)
        val pJan = luckDao.currentPeriodForDate(stockId, "2018-01-15")
        assertNotNull(pJan)
        assertEquals(1, pJan!!.cycleIndex)
        assertEquals("乙未", pJan.ganzhi)

        // 2018 年 5 月处于第 2 运 (甲午)
        val pMay = luckDao.currentPeriodForDate(stockId, "2018-05-15")
        assertNotNull(pMay)
        assertEquals(2, pMay!!.cycleIndex)
        assertEquals("甲午", pMay.ganzhi)

        // 验证年度分析 12 个月逐月大运感知能力
        val year2018 = analysisRepo.yearAnalysis(stockId, 2018)
        assertNotNull(year2018)
        assertEquals(12, year2018!!.months.size)

        // 2018 年 1 月流月分析：月度页与年度流月使用相同算法与大运
        val m1Analysis = analysisRepo.monthDays(stockId, 2018, 1)
        assertNotNull(m1Analysis)
        assertEquals("乙未", m1Analysis!!.currentPeriod?.ganzhi)

        // 2018 年 5 月流月分析
        val m5Analysis = analysisRepo.monthDays(stockId, 2018, 5)
        assertNotNull(m5Analysis)
        assertEquals("甲午", m5Analysis!!.currentPeriod?.ganzhi)

        // 验证 1 月与 5 月命中的五段式大运事实文本不同
        val fp1 = m1Analysis.fiveParagraph
        val fp5 = m5Analysis.fiveParagraph
        assertNotNull(fp1)
        assertNotNull(fp5)
        assertTrue("1月解读包含乙未运信息", fp1!!.basisText.contains("乙未"))
        assertTrue("5月解读包含甲午运信息", fp5!!.basisText.contains("甲午"))
    }

    @Test
    fun testPreLuckCycleDateGracefullyReturnsNull() = runBlocking {
        val luckDao = db.luckCycleDao()
        val moutai = stockRepo.detail("600519")!!
        val stockId = moutai.stock.id

        // 贵州茅台 2001 年上市，2008-03-18 起运
        // 2005 年属于起运前阶段，无有效大运
        val preLuckPeriod = luckDao.currentPeriodForDate(stockId, "2005-06-01")
        assertNull("起运前日期必须返回 null", preLuckPeriod)

        // 月度分析在此阶段正常生成，五段式降级处理且不崩溃
        val mAnalysis = analysisRepo.monthDays(stockId, 2005, 6)
        assertNotNull("起运前流月分析不能崩溃", mAnalysis)
        assertNull(mAnalysis!!.currentPeriod)
        assertNotNull(mAnalysis.fiveParagraph)
    }

    @Test
    fun testChinaTianyingMidYearHandover2025() = runBlocking {
        val luckDao = db.luckCycleDao()
        // 000035 中国天楹: 2025-04-18 交运 (第3步 乙丑 -> 第4步 甲子)
        val tianying = stockRepo.detail("000035")!!
        val stockId = tianying.stock.id

        val before = luckDao.currentPeriodForDate(stockId, "2025-04-17")
        assertNotNull(before)
        assertEquals(3, before!!.cycleIndex)
        assertEquals("乙丑", before.ganzhi)

        val onDay = luckDao.currentPeriodForDate(stockId, "2025-04-18")
        assertNotNull(onDay)
        assertEquals(4, onDay!!.cycleIndex)
        assertEquals("甲子", onDay.ganzhi)

        val after = luckDao.currentPeriodForDate(stockId, "2025-04-19")
        assertNotNull(after)
        assertEquals(4, after!!.cycleIndex)
        assertEquals("甲子", after.ganzhi)
    }
}
