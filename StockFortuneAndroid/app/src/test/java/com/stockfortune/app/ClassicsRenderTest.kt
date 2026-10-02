package com.stockfortune.app

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.stockfortune.app.data.repository.ClassicQuoteRepository
import com.stockfortune.app.data.repository.ClassicQuoteResult
import com.stockfortune.app.data.repository.StockDetail
import com.stockfortune.app.ui.detail.BasicTab
import com.stockfortune.app.ui.profile.AlgorithmDocScreen
import com.stockfortune.app.ui.theme.StockFortuneTheme
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * 「典籍依据」卡与算法口径页的渲染检查。
 * 用真机同款视口（Pixel 7 逻辑宽度）跑，避免只在宽屏上才暴露的截断。
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-420dpi")
class ClassicsRenderTest {

    @get:Rule
    val compose = createComposeRule()

    private fun detail(code: String): StockDetail {
        val ctx = ApplicationProvider.getApplicationContext<android.app.Application>()
        return runBlocking {
            val d = AppContainer(ctx).stockRepository.detail(code)
            assertNotNull("查不到 $code", d)
            d!!
        }
    }

    private fun show(d: StockDetail) {
        compose.setContent { StockFortuneTheme { BasicTab(d) } }
        compose.waitUntil(15_000) {
            compose.onAllNodes(hasText("典籍依据")).fetchSemanticsNodes().isNotEmpty()
        }
    }

    @Test
    fun `甲日主样本带出歌诀与原注节选及完整出处`() {
        val d = detail("000012")
        assertEquals("甲", d.bazi.dayMaster)
        val quotes = (d.classics as ClassicQuoteResult.Found).quotes
        show(d)
        compose.onNodeWithText("按日主归类的原文参考 · 日主 甲").assertExists()
        quotes.forEach { q ->
            // 不设省略行数：整句原文必须完整可测
            compose.onNodeWithText(q.originalText, substring = true).assertExists()
        }
        // 两条引文同书同版同页，出处行必然各出现一次
        compose.onAllNodesWithText("《${quotes[0].bookTitle}》· ${quotes[0].edition}", substring = true)
            .assertCountEquals(2)
        val meta = "篇名 ${quotes[0].chapter} · 小节 ${quotes[0].section} · 扫描页码 ${quotes[0].scanPage}"
        compose.onAllNodesWithText(meta).assertCountEquals(2)
        compose.onNodeWithText("歌诀", substring = true).assertExists()
        compose.onNodeWithText("原注节选", substring = true).assertExists()
        compose.onNodeWithText("原注为节选，非全段").assertExists()
        compose.onNodeWithText("以下为传统文献原文，非本系统推断，不构成任何投资建议。").assertExists()
    }

    @Test
    fun `无匹配时只影响本卡其余信息照常展示`() {
        val d = detail("000012")
        show(d.copy(classics = ClassicQuoteResult.NoMatch))
        compose.onNodeWithText("暂无对应引文").assertExists()
        compose.onNodeWithText("该日主暂未收录引文条目").assertExists()
        assertRestOfTabIntact()
    }

    @Test
    fun `引文加载失败时只影响本卡其余信息照常展示`() {
        val d = detail("000012")
        show(d.copy(classics = ClassicQuoteResult.LoadFailed))
        compose.onNodeWithText("引文暂不可用").assertExists()
        compose.onNodeWithText("离线引文读取失败，其余信息不受影响").assertExists()
        assertRestOfTabIntact()
    }

    /** 卡片降级不得连带打断概览、八字、阴阳三块。 */
    private fun assertRestOfTabIntact() {
        compose.onNodeWithText("企业特征与命理概览（本项目概述）", substring = true).assertExists()
        compose.onNodeWithText("八字信息").assertExists()
        compose.onNodeWithText("命理特征（本项目概述）").assertExists()
    }

    /**
     * 十干概览必须逐字落在所选原注上：简体概述里的锚点词，要能在繁体原注节选里
     * 找到对应的繁体锚点。这样"改概览"和"改选段"任何一边单独动都会被拦下。
     */
    @Test
    fun `十干概览已与选定原注一致且不再出现底本冲突表述`() {
        val ctx = ApplicationProvider.getApplicationContext<android.app.Application>()
        val repo = ClassicQuoteRepository(ctx)
        // 日主 to (简体概述锚点, 原注节选中的繁体锚点)
        val anchors = mapOf(
            "甲" to ("根干之木" to "根幹之木"),
            "乙" to ("枝叶之木" to "枝葉之木"),
            "丙" to ("焚烈之火" to "焚烈之火"),
            "丁" to ("温煖之火" to "溫煖之火"),
            "戊" to ("山冈之土" to "山岡之土"),
            "己" to ("田园之土" to "田園之土"),
            "庚" to ("太白之精" to "太白之精"),
            "辛" to ("温柔清润" to "溫柔清潤"),
            "壬" to ("癸水之源" to "癸水之源"),
            "癸" to ("上达天津" to "上達天津"),
        )
        runBlocking {
            anchors.forEach { (stem, pair) ->
                val (inOverview, inNote) = pair
                val overview = com.stockfortune.app.domain.calculator.FortuneText.fateFeature(stem)
                val note = ((repo.forDayStem(stem)) as ClassicQuoteResult.Found).quotes[1].originalText
                assertTrue("$stem 概述「$overview」缺少锚点「$inOverview」", overview.contains(inOverview))
                assertTrue("$stem 原注节选「$note」缺少锚点「$inNote」", note.contains(inNote))
            }
        }
        // 与底本直接冲突、或没有选段依据的旧表述
        listOf("城墙厚土", "珠玉之金", "刀剑之金", "太阳之火", "灯烛之火", "喜甲木疏", "得壬水淘").forEach { old ->
            com.stockfortune.app.domain.calculator.BaziTables.STEMS.forEach { stem ->
                val overview = com.stockfortune.app.domain.calculator.FortuneText.fateFeature(stem)
                assertTrue("$stem 概述仍含旧表述「$old」：$overview", !overview.contains(old))
            }
        }
    }

    @Test
    fun `算法口径页说明引文版本与扫描页码口径`() {
        compose.setContent { StockFortuneTheme { AlgorithmDocScreen(onBack = {}) } }
        compose.waitUntil(15_000) {
            compose.onAllNodes(hasText("三·六、典籍引文口径")).fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithText("dtjy-v1", substring = true).assertExists()
        compose.onNodeWithText("扫描页码", substring = true).assertExists()
        compose.onNodeWithText("不是该刊本的印刷叶码", substring = true).assertExists()
        compose.onNodeWithText("保持繁体原貌与原书夹注", substring = true).assertExists()
        compose.onNodeWithText("能匹配到某个日主不代表这些条件在该股命局中成立", substring = true).assertExists()
    }
}
