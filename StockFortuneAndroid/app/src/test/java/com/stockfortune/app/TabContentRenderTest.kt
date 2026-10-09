package com.stockfortune.app

import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.stockfortune.app.ui.detail.DailyTab
import com.stockfortune.app.ui.detail.MonthTab
import com.stockfortune.app.ui.detail.YearTab
import com.stockfortune.app.ui.theme.StockFortuneTheme
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * 年度 / 月度 / 每日三个 Tab 用真实预置库数据直接组合渲染，
 * 验证日历网格、月份列表、交易日列表都不会抛异常且内容正确。
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34], qualifiers = "w400dp-h860dp-420dpi")
class TabContentRenderTest {

    @get:Rule
    val compose = createComposeRule()

    private data class Fixtures(
        val year: com.stockfortune.app.domain.model.YearAnalysis,
        val month: com.stockfortune.app.domain.model.MonthAnalysis,
    )

    /**
     * 固定到 2026-09 而不是 LocalDate.now()：下面的断言写的是该月的内容
     * （21 个交易日、9 月 1 日这一行），跟着系统日期走每月都会漂成红灯。
     */
    private fun fixtures(): Fixtures {
        val ctx = ApplicationProvider.getApplicationContext<android.app.Application>()
        val container = AppContainer(ctx)
        val (year, month) = 2026 to 9
        return runBlocking {
            val detail = container.stockRepository.detail("600519")
            assertNotNull("取不到 600519", detail)
            Fixtures(
                year = container.analysisRepository.yearAnalysis(detail!!.stock.id, year)!!,
                month = container.analysisRepository.monthDays(detail.stock.id, year, month)!!,
            )
        }
    }

    @Test
    fun `年度 Tab 渲染 12 个月标签与概览卡`() {
        val f = fixtures()
        assertEquals(12, f.year.months.size)
        compose.setContent {
            StockFortuneTheme {
                YearTab(year = f.year, yearValue = 2026, onPrev = {}, onNext = {}, onMonthClick = {})
            }
        }
        compose.waitUntil(10_000) { compose.onAllNodes(hasText("运势概览", substring = true)).fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("各月十神出现情况").assertExists()
        compose.onNodeWithText("丙午年（2026）").assertExists()
        // 12 个干支月行都在（月支标签唯一，避免 "1月" 命中 "11月" 这类子串歧义）
        listOf("寅月", "卯月", "辰月", "巳月", "午月", "未月", "申月", "酉月", "戌月", "亥月", "子月", "丑月").forEach {
            compose.onNodeWithText(it, substring = true).assertExists()
        }
    }

    /**
     * 年度页的逐月行从「月支 + 财星色块」扩到「月干支 + 区间 + 十神 + 财星 + 判词」。
     * 只断言渲染不抛异常的话，字段加了却不显示也照样绿 —— 这里逐项断言"屏上有这段文字"。
     * 2026 为丙午年，五虎遁丙辛起庚寅，故首个干支月必为庚寅；立春 2/4、惊蛰 3/5，
     * 月支在交节当日翻转，所以庚寅月区间右端是 3.4 而非 3.5。
     */
    @Test
    fun `年度 Tab 逐月行渲染月干支 交节区间与判词`() {
        val f = fixtures()
        val yin = f.year.months.first()
        assertEquals("庚寅", yin.monthGanzhi)
        assertEquals("2026-02-04", yin.startDate)
        assertEquals("2026-03-04", yin.endDate)
        org.junit.Assert.assertTrue("判词不得为空", yin.summary.isNotBlank())

        compose.setContent {
            StockFortuneTheme {
                YearTab(year = f.year, yearValue = 2026, onPrev = {}, onNext = {}, onMonthClick = {})
            }
        }
        compose.waitUntil(10_000) { compose.onAllNodes(hasText("各月十神出现情况")).fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("庚寅").assertExists()
        compose.onNodeWithText("2.4–3.4", substring = true).assertExists()
        compose.onNodeWithText(yin.summary, substring = true).assertExists()
    }

    /**
     * P0-1 回归锁：年度页曾把 WealthType.OTHER 塌缩成 NONE，于是"七杀当值的月"
     * 和"压根没数据的月"共用一个「无」标签。干支月永远有判定结果，NONE 只属于非交易日。
     */
    @Test
    fun `年度页非财月记为其他而非无`() {
        val f = fixtures()
        org.junit.Assert.assertTrue(
            "年度页不应出现 NONE（该值只用于非交易日）：${f.year.months.map { it.wealth }}",
            f.year.months.none { it.wealth == com.stockfortune.app.domain.model.WealthType.NONE },
        )
        org.junit.Assert.assertTrue(
            "12 个干支月应含至少一个非财月，否则这条锁形同虚设：${f.year.months.map { it.wealth }}",
            f.year.months.any { it.wealth == com.stockfortune.app.domain.model.WealthType.OTHER },
        )
    }

    @Test
    fun `月度 Tab 渲染月历与财日统计`() {
        val f = fixtures()
        compose.setContent {
            StockFortuneTheme { MonthTab(m = f.month, onPrev = {}, onNext = {}, onDayClick = {}) }
        }
        compose.waitUntil(10_000) { compose.onAllNodes(hasText("本月交易日十神分布", substring = true)).fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("月度信息").assertExists()
        compose.onNodeWithText("本月提示").assertExists()
    }

    @Test
    fun `每日 Tab 仅列交易日并显示十神标签`() {
        val f = fixtures()
        compose.setContent {
            StockFortuneTheme { DailyTab(m = f.month, onPrev = {}, onNext = {}, onDayClick = {}) }
        }
        compose.waitUntil(10_000) { compose.onAllNodes(hasText("仅显示交易日", substring = true)).fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("天干十神").assertExists()
        compose.onNodeWithText("财星").assertExists()
        compose.onNodeWithText("2026-09-01", substring = true).assertExists()
    }
}
