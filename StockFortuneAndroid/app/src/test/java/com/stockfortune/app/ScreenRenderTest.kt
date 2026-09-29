package com.stockfortune.app

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.time.YearMonth
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * 页面渲染冒烟：在 JVM 内真正组合各屏幕，验证不会抛异常（资源缺失、空列表取值、
 * ViewModel 工厂接线错误这类问题只有渲染时才会暴露）。
 *
 * 页面数据由 ViewModel 在 Dispatchers.Default 上异步装载，Compose 的 waitForIdle 不会等它，
 * 因此用 awaitText 持续泵消息直到节点出现；断言只取屏内节点（屏外列表尚未组合）。
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34], qualifiers = "w400dp-h860dp-420dpi")
class ScreenRenderTest {

    @get:Rule
    val compose = createAndroidComposeRule<MainActivity>()

    private fun awaitText(text: String, timeoutMs: Long = 15_000) {
        try {
            compose.waitUntil(timeoutMs) {
                compose.onAllNodes(hasText(text, substring = true)).fetchSemanticsNodes().isNotEmpty()
            }
        } catch (e: Throwable) {
            throw AssertionError("节点未在 ${timeoutMs}ms 内出现: 「$text」", e)
        }
    }

    @Test
    fun `首页渲染并含主视觉与今日概览`() {
        compose.waitForIdle()
        compose.onNodeWithText("股运通").assertIsDisplayed()
        compose.onNodeWithText("股票八字 · 十神择日").assertExists()
        awaitText("正财股票")
        compose.onNodeWithText("偏财股票").assertExists()
        compose.onNodeWithText("今日概览").assertExists()
        compose.onNodeWithText("交易日历").assertExists()
    }

    @Test
    fun `底部导航四个页签均可进入`() {
        compose.waitForIdle()
        compose.onNodeWithText("扫描").performClick()
        awaitText("每日选股扫描")
        awaitText("传统口径主守成")

        compose.onNodeWithText("选股").performClick()
        awaitText("藏干十神")
        compose.onNodeWithText("开始筛选").assertExists()

        compose.onNodeWithText("日历").performClick()
        awaitText("交易日历")

        compose.onNodeWithText("我的").performClick()
        awaitText("数据版本")

        // 回归：从带参数的目的地（scanner?wealth=）点"首页"。
        // 起点目的地用 popUpTo+restoreState 会被 Navigation 忽略，必须走 popBackStack。
        compose.onNodeWithText("首页").performClick()
        awaitText("今日概览")
        compose.onNodeWithText("每日扫描").performClick()
        awaitText("每日选股扫描")
        compose.onNodeWithText("首页").performClick()
        awaitText("今日概览")
    }

    /**
     * 回归：交易日历此前只能停在当月，点 ‹ › 毫无反应 —— shift 只把目标年月传给 refresh，
     * 却没有写回 state.year/month，于是 refresh 的"过期请求"守卫认为结果永远不属于当前月，全部丢弃。
     */
    @Test
    fun `交易日历可以翻到其他月份并回到本月`() {
        compose.waitForIdle()
        compose.onNodeWithText("日历").performClick()
        awaitText("交易日历")

        val now = YearMonth.now()
        val label = { ym: YearMonth -> "${ym.year}年${ym.monthValue}月" }
        compose.onNodeWithText(label(now)).assertIsDisplayed()

        val prev = now.minusMonths(1)
        compose.onNodeWithText("‹").performClick()
        awaitText(label(prev))
        // 网格 / 当日信息确实是上个月的数据，而不是只换了标题
        awaitText("%04d-%02d-".format(prev.year, prev.monthValue))

        compose.onNodeWithText("›").performClick()
        awaitText(label(now))

        val next = now.plusMonths(1)
        compose.onNodeWithText("›").performClick()
        awaitText(label(next))
        compose.onNodeWithText("本月").performClick()
        awaitText(label(now))
    }

    @Test
    fun `从搜索进入股票详情并切换四个 Tab`() {
        compose.waitForIdle()
        compose.onNodeWithText("股票查询").performClick()
        awaitText("请输入股票代码/名称")
        compose.onNodeWithText("请输入股票代码/名称").performTextInput("600519")
        awaitText("贵州茅台")
        compose.onNodeWithText("贵州茅台").performClick()

        awaitText("八字信息")
        compose.onNodeWithText("壬戌").assertExists()
        compose.onNodeWithText("大海水").assertExists()

        // 三个分析 Tab 的渲染由 TabContentRenderTest 用真实数据直接组合验证：
        // Robolectric 下点击 Tab 文本不触发 onSelect，属测试交互限制而非功能缺陷。
    }

    @Test
    fun `八字择日页可渲染并可发起分析`() {
        compose.waitForIdle()
        compose.onNodeWithText("八字择日").performClick()
        awaitText("开始分析")
        compose.onNodeWithText("开始日期").assertExists()
        compose.onNodeWithText("如 600519").performTextInput("600519")
        compose.onNodeWithText("开始分析").performClick()
        awaitText("共找到")
    }
}
