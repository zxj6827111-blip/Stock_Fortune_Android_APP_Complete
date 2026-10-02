package com.stockfortune.app

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.stockfortune.app.domain.calculator.FortuneText
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * 合规文案门禁：产品边界为"不做涨跌预测、不做收益与操作暗示"。
 * 旧门禁只扫 FortuneText 的函数返回值与 7 个敏感词，扫不到 strings.xml 与常量，
 * 曾让"稳健获利 / 短线机会"这类收益暗示漏进首页统计卡。这里把资源文案一并纳入。
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class ComplianceTextTest {

    private val banned = listOf(
        "买入", "卖出", "涨幅", "收益率", "必涨", "预测涨跌", "稳赚",
        "获利", "收益", "短线", "机会", "潜力", "把握时机", "适合把握",
    )

    /** 引文与文案都保持繁体原貌，禁词必须同时覆盖繁体写法，否则一简化就静默穿过。 */
    private val bannedTraditional = listOf(
        "買入", "賣出", "漲幅", "收益率", "必漲", "預測漲跌", "穩賺",
        "獲利", "收益", "短線", "機會", "潛力", "把握時機", "適合把握",
    )

    private fun assertClean(label: String, text: String) {
        (banned + bannedTraditional).distinct().forEach { b ->
            assertFalse("[$label] 含收益/操作暗示词「$b」: $text", text.contains(b))
        }
    }

    @Test
    fun `资源文案不含收益或操作暗示`() {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        val ids = listOf(
            R.string.app_subtitle, R.string.app_slogan,
            R.string.entry_stock_query_sub, R.string.entry_daily_scan_sub,
            R.string.entry_trade_calendar_sub, R.string.entry_date_select_sub,
            R.string.search_hint, R.string.today_overview,
            R.string.wealth_zheng, R.string.wealth_pian,
            R.string.scanner_title, R.string.scanner_subtitle,
            R.string.filter_title, R.string.filter_subtitle, R.string.filter_desc,
            R.string.filter_group_hidden_desc, R.string.filter_group_year_desc,
            R.string.filter_group_month_desc, R.string.filter_group_day_desc,
            R.string.legend_zheng, R.string.legend_pian, R.string.legend_none,
            R.string.legend_zheng_hint, R.string.legend_pian_hint, R.string.legend_none_hint,
            R.string.date_select_title, R.string.date_select_subtitle, R.string.date_select_hint,
            R.string.mine_offline, R.string.disclaimer, R.string.disclaimer_short,
            R.string.classics_title, R.string.classics_subtitle, R.string.classics_kind_verse,
            R.string.classics_kind_annotation, R.string.classics_source_line,
            R.string.classics_meta_line,
            R.string.classics_disclaimer, R.string.classics_empty, R.string.classics_empty_hint,
            R.string.classics_unavailable, R.string.classics_unavailable_hint,
            R.string.classics_excerpt_note, R.string.field_fate_feature_note,
            R.string.overview_title,
            // 算法口径页的引文口径六行：早先是硬编码在 Composable 里，这道门禁扫不到
            R.string.doc_classics_title, R.string.doc_classics_source, R.string.doc_classics_page,
            R.string.doc_classics_script, R.string.doc_classics_match, R.string.doc_classics_excerpt,
            R.string.doc_classics_overview,
        )
        ids.forEach { id -> assertClean(ctx.resources.getResourceEntryName(id), ctx.getString(id)) }
    }

    /**
     * 引文随 APK 存在 assets 里，过去那种「只扫 FortuneText 与 strings.xml」的门禁扫不到它。
     * 这里把 20 条原文逐条纳入，命中时报出条目与词语，不允许自动删词或放宽规则。
     */
    @Test
    fun `古籍引文逐条不含收益或操作暗示`() {
        val root = org.json.JSONObject(
            ApplicationProvider.getApplicationContext<Context>()
                .assets.open(com.stockfortune.app.data.repository.ClassicQuoteRepository.ASSET_PATH)
                .bufferedReader().use { it.readText() },
        )
        val entries = root.getJSONArray("entries")
        assertEquals("引文条目数变了，门禁要同步复核", 20, entries.length())
        for (i in 0 until entries.length()) {
            val e = entries.getJSONObject(i)
            assertClean(e.getString("entry_id"), e.getString("original_text"))
        }
    }

    /** 十干概览改成据原注写的静态说明，同样要过同一道禁词门禁。 */
    @Test
    fun `十干概览不含收益或操作暗示`() {
        com.stockfortune.app.domain.calculator.BaziTables.STEMS.forEach { s ->
            assertClean("FATE_FEATURE[$s]", FortuneText.fateFeature(s))
        }
    }

    @Test
    fun `代码常量与生成文案不含收益或操作暗示`() {
        assertClean("SCAN_ZHENG_NOTE", FortuneText.SCAN_ZHENG_NOTE)
        assertClean("SCAN_PIAN_NOTE", FortuneText.SCAN_PIAN_NOTE)
        listOf(0 to 0, 3 to 5, 5 to 3, 21 to 0).forEach { (z, p) ->
            assertClean("monthTip($z,$p)", FortuneText.monthTip(z, p))
        }
    }

    /**
     * 逐条扫全部字符串资源，而不是只扫一份手工维护的 id 白名单。
     * 白名单模式下"新加一条 strings.xml 文案"不需要任何人碰这个测试，等于新文案不受检。
     */
    @Test
    fun `每一条字符串资源都过禁词门禁`() {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        val ids = R.string::class.java.fields
            .filter { it.type == Int::class.javaPrimitiveType }
            .map { it.getInt(null) }
        assertTrue("反射不到任何字符串资源，门禁形同虚设", ids.size > 40)
        ids.forEach { id -> assertClean(ctx.resources.getResourceEntryName(id), ctx.getString(id)) }
    }

    /**
     * Composable 里的中文字面量过去完全在门禁之外 —— ui/ 下大量硬编码文案（能力边界声明、
     * 免责语、择日提示）没有任何自动检查，而这是本项目唯一的监管红线。
     *
     * 必须带否定式豁免：唯一的现存命中是「本应用不做涨跌预测、不做收益回测…」，若因含"收益"
     * 被误杀，这条边界声明就只能留在 Kotlin 源码里、永远进不了资源文件 —— 门禁会反过来
     * 逼出它想防的结果。
     */
    @Test
    fun `UI 源码字面量都过禁词门禁`() {
        val uiDir = java.io.File("src/main/java/com/stockfortune/app/ui")
        assertTrue(
            "测试工作目录不是 app 模块根，扫不到 UI 源码：${java.io.File("").absolutePath}",
            uiDir.isDirectory,
        )
        val literals = Regex("\"[^\"\\n]*[\\u4e00-\\u9fa5][^\"\\n]*\"")
        val offenders = ArrayList<String>()
        var files = 0
        uiDir.walkTopDown().filter { it.isFile && it.extension == "kt" }.forEach { file ->
            files++
            val text = file.readText()
            literals.findAll(text).forEach { match ->
                val s = match.value.trim('"')
                (banned + bannedTraditional).distinct().forEach { word ->
                    var i = s.indexOf(word)
                    while (i >= 0) {
                        val before = s.substring(0, i).takeLast(3)
                        val negated = NEGATIONS.any { before.contains(it) }
                        if (!negated) {
                            val line = text.substring(0, match.range.first).count { it == '\n' } + 1
                            offenders += "${file.name}:$line 含「$word」：$s"
                        }
                        i = s.indexOf(word, i + word.length)
                    }
                }
            }
        }
        assertTrue("只扫到 $files 个 UI 源文件，路径或结构变了", files >= 15)
        assertEquals("UI 源码存在收益或操作暗示表述", 0, offenders.size)
        assertEquals(emptyList<String>(), offenders.take(10))
    }

    companion object {
        /** 紧挨禁词出现即视为否定式表述（"不做收益回测"），放行。 */
        private val NEGATIONS = listOf("不", "非", "无", "勿", "禁", "别", "未")
    }
}
