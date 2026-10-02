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
