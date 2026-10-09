package com.stockfortune.app

import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.stockfortune.app.ui.detail.BasicTab
import com.stockfortune.app.ui.detail.MonthTab
import com.stockfortune.app.ui.detail.YearTab
import com.stockfortune.app.ui.theme.StockFortuneTheme
import com.stockfortune.app.ui.vm.StockDetailViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * Phase 6 Android 页面融合综合测试 (Gate G6).
 *
 * 覆盖：
 * 1. Gate G6 多股切换状态隔离测试（600519 -> 601088 -> 603222 -> 000004 无旧数据残留与串流）
 * 2. 年份与月份切换测试（2025/2026/2027 对应大运步与年度综合，12 个月逐月五段式与分项精确提示）
 * 3. 60 禁词合规门禁零容忍扫描（0 命中，0 个单字「忌」字，零买卖暗示）
 * 4. Compose UI 卡片渲染测试（BasicTab/YearTab/MonthTab 对应卡片与折叠展开交互）
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34], qualifiers = "w400dp-h860dp-420dpi")
class Phase6UiIntegrationTest {

    @get:Rule
    val compose = createComposeRule()

    private val forbiddenWords = listOf(
        "买入", "卖出", "涨幅", "收益率", "必涨", "预测涨跌", "稳赚",
        "获利", "收益", "短线", "机会", "潜力", "把握时机", "适合把握",
        "買入", "賣出", "漲幅", "收益率", "必漲", "預測漲跌", "穩賺",
        "獲利", "收益", "短線", "機會", "潛力", "把握時機", "適合把握",
        "忌", "不宜", "宜于", "适合", "慎", "勿", "务必", "尽量", "优先", "应当", "应该",
        "加仓", "减仓", "重仓", "轻仓", "建仓", "进场", "出场", "持币", "见好就收",
        "规避", "谨防", "小心", "可取", "避免", "防", "注意", "冲动", "大额", "投机",
    ).distinct()

    private fun assertZeroForbiddenWords(label: String, text: String) {
        forbiddenWords.forEach { fw ->
            assertFalse("[$label] 命中合规禁词「$fw」: $text", text.contains(fw))
        }
    }

    private suspend fun awaitViewModelReady(vm: StockDetailViewModel, expectedCodePrefix: String, maxWaitMs: Long = 8000) {
        var waited = 0L
        while (waited < maxWaitMs) {
            val s = vm.state.value
            if (!s.loading && s.detail?.stock?.code?.startsWith(expectedCodePrefix) == true && s.year != null && s.month != null) {
                return
            }
            delay(50)
            waited += 50
        }
        val s = vm.state.value
        throw AssertionError("ViewModel 超时未就绪: codePrefix=$expectedCodePrefix, current=${s.detail?.stock?.code}, loading=${s.loading}, year=${s.year != null}, month=${s.month != null}")
    }

    /**
     * 1. Gate G6 多股切换状态隔离测试
     */
    @Test
    fun `多股切换状态完全隔离无串流`() = runBlocking(Dispatchers.Default) {
        val ctx = ApplicationProvider.getApplicationContext<android.app.Application>()
        val container = AppContainer(ctx)
        val vm = StockDetailViewModel(container)

        // 1. 加载 600519 (贵州茅台)
        vm.load("600519", 2026, 2026 to 9, 2026 to 9)
        awaitViewModelReady(vm, "600519")

        val stateMoutai = vm.state.value
        assertTrue("茅台代码前缀应为600519", stateMoutai.detail?.stock?.code?.startsWith("600519") == true)
        assertEquals("贵州茅台", stateMoutai.detail?.stock?.name)
        assertNotNull("茅台大运不应为空", stateMoutai.detail?.luckCycle)
        assertEquals("available", stateMoutai.detail?.luckCycle?.status)
        assertEquals(12, stateMoutai.detail?.luckPeriods?.size)
        assertEquals(6, stateMoutai.detail?.natalRelations?.size)
        assertNotNull("茅台喜用不应为空", stateMoutai.detail?.yongshen)
        assertNotNull("茅台年度综合解读不应为空", stateMoutai.year?.annualSynthesis)
        assertNotNull("茅台月度五段式解读不应为空", stateMoutai.month?.fiveParagraph)
        assertEquals("甲午", stateMoutai.year?.currentPeriod?.ganzhi)

        // 2. 切换到 601088 (中国神华)
        vm.load("601088", 2026, 2026 to 9, 2026 to 9)
        awaitViewModelReady(vm, "601088")

        val stateShenhua = vm.state.value
        assertTrue("神华代码前缀应为601088", stateShenhua.detail?.stock?.code?.startsWith("601088") == true)
        assertEquals("中国神华", stateShenhua.detail?.stock?.name)
        assertNotEquals("日主应已切换", stateMoutai.detail?.bazi?.dayMaster, stateShenhua.detail?.bazi?.dayMaster)
        assertNotNull(stateShenhua.year?.annualSynthesis)
        assertNotNull(stateShenhua.month?.fiveParagraph)
        assertTrue(
            "神华年度综合总结应基于神华日主，不包含茅台信息",
            stateShenhua.year?.annualSynthesis?.structuredSummary?.contains(stateShenhua.detail!!.bazi.dayMaster) == true
        )

        // 3. 切换到 603222 (平盘首日)
        vm.load("603222", 2026, 2026 to 9, 2026 to 9)
        awaitViewModelReady(vm, "603222")

        val statePingpan = vm.state.value
        assertTrue("平盘代码前缀应为603222", statePingpan.detail?.stock?.code?.startsWith("603222") == true)
        assertEquals("unavailable_flat", statePingpan.detail?.luckCycle?.status)
        assertTrue("平盘大运排盘列表应为空", statePingpan.detail?.luckPeriods.isNullOrEmpty())
        assertNull("平盘流年当前大运步应为 null", statePingpan.year?.currentPeriod)
        assertNotNull(statePingpan.month?.fiveParagraph)
        assertTrue(
            "平盘月度应有大运不适用精确提示",
            statePingpan.month?.fiveParagraph?.preciseAdvancedNotice?.contains("大运") == true
        )

        // 4. 切换到 000004 (缺失首日)
        vm.load("000004", 2026, 2026 to 9, 2026 to 9)
        awaitViewModelReady(vm, "000004")

        val stateMissing = vm.state.value
        assertTrue("缺失代码前缀应为000004", stateMissing.detail?.stock?.code?.startsWith("000004") == true)
        assertEquals("unavailable_missing", stateMissing.detail?.luckCycle?.status)
        assertNull("缺失流年当前大运步应为 null", stateMissing.year?.currentPeriod)
        assertNotNull(stateMissing.month?.fiveParagraph)
        assertTrue(
            "缺失月度应有大运不适用精确提示",
            stateMissing.month?.fiveParagraph?.preciseAdvancedNotice?.contains("大运") == true
        )
    }

    /**
     * 2. 年份与月份切换测试
     */
    @Test
    fun `年份切换与12个月流月五段式全面就绪`() = runBlocking(Dispatchers.Default) {
        val ctx = ApplicationProvider.getApplicationContext<android.app.Application>()
        val container = AppContainer(ctx)
        val vm = StockDetailViewModel(container)

        vm.load("600519", 2026, 2026 to 9, 2026 to 9)
        awaitViewModelReady(vm, "600519")

        val stockId = vm.state.value.detail!!.stock.id

        // 切换年份：2025 -> 2026 -> 2027
        val testYears = listOf(2025 to "乙巳", 2026 to "丙午", 2027 to "丁未")
        for ((y, gz) in testYears) {
            val curYear = container.analysisRepository.yearAnalysis(stockId, y)
            assertNotNull("年份 $y 数据应存在", curYear)
            assertEquals(gz, curYear!!.yearGanzhi)
            assertNotNull("流年大运步应存在", curYear.currentPeriod)
            assertNotNull("年度综合解释应存在", curYear.annualSynthesis)
            assertTrue(curYear.annualSynthesis!!.structuredSummary.contains(gz))
            assertEquals(12, curYear.months.size)
        }

        // 验证 12 个月逐月五段式
        for (m in 1..12) {
            val monthAnalysis = container.analysisRepository.monthDays(stockId, 2026, m)
            assertNotNull("第 $m 月数据应存在", monthAnalysis)
            val fp = monthAnalysis!!.fiveParagraph
            assertNotNull("第 $m 月五段式解读应生成", fp)
            assertTrue("命理依据不应为空", fp!!.basisText.isNotBlank())
            assertTrue("本月主题不应为空", fp.themeText.isNotBlank())
            assertTrue("潜在矛盾不应为空", fp.contradictionText.isNotBlank())
            assertTrue("企业经营观察不应为空", fp.businessText.isNotBlank())
            assertTrue("综合解释不应为空", fp.synthesisText.isNotBlank())
            assertEquals("三项高级算法均可用时分项精确提示应为空", "", fp.preciseAdvancedNotice)
        }
    }

    /**
     * 3. 60 禁词合规门禁零容忍扫描
     */
    @Test
    fun `全量生成文案执行60禁词零容忍合规扫描`() = runBlocking(Dispatchers.Default) {
        val ctx = ApplicationProvider.getApplicationContext<android.app.Application>()
        val container = AppContainer(ctx)

        val stockCodes = listOf("600519", "601088", "603222", "000004")
        for (code in stockCodes) {
            val detail = container.stockRepository.detail(code)!!
            detail.yongshen?.let {
                assertZeroForbiddenWords("$code 喜用理由", it.rationale)
                assertZeroForbiddenWords("$code 喜用调候", it.tiaohouNote)
            }

            for (testYear in listOf(2025, 2026)) {
                val yAnalysis = container.analysisRepository.yearAnalysis(detail.stock.id, testYear)!!
                val syn = yAnalysis.annualSynthesis
                if (syn != null) {
                    assertZeroForbiddenWords("$code $testYear 年度综合主干", syn.structuredSummary)
                    assertZeroForbiddenWords("$code $testYear 年度大运说明", syn.currentPeriodDesc)
                    assertZeroForbiddenWords("$code $testYear 年度十神分布", syn.tenGodDistributionSummary)
                    assertZeroForbiddenWords("$code $testYear 年度合规提示", syn.complianceNotice)
                    syn.seasonalThemes.forEach { (season, theme) ->
                        assertZeroForbiddenWords("$code $testYear 四季演进 $season", theme)
                    }
                }

                // 扫描 12 个月流月五段式
                for (m in 1..12) {
                    val mAnalysis = container.analysisRepository.monthDays(detail.stock.id, testYear, m) ?: continue
                    val fp = mAnalysis.fiveParagraph ?: continue
                    assertZeroForbiddenWords("$code $testYear-$m 依据", fp.basisText)
                    assertZeroForbiddenWords("$code $testYear-$m 主题", fp.themeText)
                    assertZeroForbiddenWords("$code $testYear-$m 矛盾", fp.contradictionText)
                    assertZeroForbiddenWords("$code $testYear-$m 企业观察", fp.businessText)
                    assertZeroForbiddenWords("$code $testYear-$m 综合解释", fp.synthesisText)
                    assertZeroForbiddenWords("$code $testYear-$m 精确提示", fp.preciseAdvancedNotice)
                }
            }
        }
    }

    /**
     * 4. Compose UI 卡片渲染与交互测试
     */
    @Test
    fun `BasicTab 渲染原局关系 喜用候选与大运排盘卡片`() {
        val ctx = ApplicationProvider.getApplicationContext<android.app.Application>()
        val container = AppContainer(ctx)
        val detail = runBlocking { container.stockRepository.detail("600519")!! }

        compose.setContent {
            StockFortuneTheme {
                BasicTab(detail = detail)
            }
        }

        // 1. 四柱边界与日历精度
        compose.onNodeWithText("时柱说明", substring = true).assertExists()
        compose.onNodeWithText("日历标准", substring = true).assertExists()

        // 2. 原局综合解读与关系矩阵
        compose.onNodeWithText("原局综合解读").assertExists()
        compose.onNodeWithText("3×3 离线关系矩阵", substring = true).assertExists()

        // 3. 喜用格局与流通候选
        compose.onNodeWithText("喜用格局与流通候选").assertExists()
        compose.onNodeWithText("扶抑用神", substring = true).assertExists()
        compose.onNodeWithText("扶抑喜神", substring = true).assertExists()

        // 4. 当前大运与起运说明
        compose.onNodeWithText("当前大运与起运说明").assertExists()
        compose.onNodeWithText("12步大运周期排盘", substring = true).assertExists()
        compose.onNodeWithText("甲午", substring = true).assertExists()
    }

    @Test
    fun `YearTab 渲染年度综合命理解释卡片`() {
        val ctx = ApplicationProvider.getApplicationContext<android.app.Application>()
        val container = AppContainer(ctx)
        val yearAnalysis = runBlocking {
            container.analysisRepository.yearAnalysis(1L, 2026)!!
        }

        compose.setContent {
            StockFortuneTheme {
                YearTab(
                    year = yearAnalysis,
                    yearValue = 2026,
                    onPrev = {},
                    onNext = {},
                    onMonthClick = {},
                )
            }
        }

        compose.onNodeWithText("年度综合命理解释").assertExists()
        compose.onNodeWithText("流月十神分布", substring = true).assertExists()
        compose.onNodeWithText("四季阶段演进", substring = true).assertExists()
        compose.onNodeWithText("春季阶段", substring = true).assertExists()
        compose.onNodeWithText("综合解读归纳", substring = true).assertExists()
        compose.onAllNodes(hasText("点击查看月度五段式解读 →", substring = true))[0].assertExists()
    }

    @Test
    fun `MonthTab 渲染月度五段式解读并支持展开命理依据`() {
        val ctx = ApplicationProvider.getApplicationContext<android.app.Application>()
        val container = AppContainer(ctx)
        val monthAnalysis = runBlocking {
            container.analysisRepository.monthDays(1L, 2026, 9)!!
        }

        compose.setContent {
            StockFortuneTheme {
                MonthTab(
                    m = monthAnalysis,
                    onPrev = {},
                    onNext = {},
                    onDayClick = {},
                )
            }
        }

        // 月度五段式卡片渲染
        compose.onNodeWithText("月度五段式解读").assertExists()
        compose.onNodeWithText("B. 本月主题").assertExists()
        compose.onNodeWithText("C. 潜在矛盾").assertExists()
        compose.onNodeWithText("D. 企业经营观察").assertExists()
        compose.onNodeWithText("E. 综合解释").assertExists()

        // 折叠命理依据与点击展开
        compose.onNodeWithText("展开查看依据 ▾").assertExists()
        compose.onNodeWithText("展开查看依据 ▾").performClick()
        compose.onNodeWithText("收起依据 ▴").assertExists()
        compose.onNodeWithText("命中规则依据：", substring = true).assertExists()
    }

    @Test
    fun `平盘股大运卡片渲染不适用合规披露说明`() {
        val ctx = ApplicationProvider.getApplicationContext<android.app.Application>()
        val container = AppContainer(ctx)
        val pingpanDetail = runBlocking { container.stockRepository.detail("603222")!! }

        compose.setContent {
            StockFortuneTheme {
                BasicTab(detail = pingpanDetail)
            }
        }

        compose.onNodeWithText("当前大运与起运说明").assertExists()
        compose.onNodeWithText("平盘（不适用）", substring = true).assertExists()
        compose.onNodeWithText("大运不适用说明", substring = true).assertExists()
        compose.onAllNodes(hasText("ADR-0001", substring = true))[0].assertExists()
    }
}
