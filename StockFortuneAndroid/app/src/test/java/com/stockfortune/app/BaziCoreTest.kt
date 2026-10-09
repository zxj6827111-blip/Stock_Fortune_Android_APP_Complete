package com.stockfortune.app

import com.stockfortune.app.domain.calculator.FortuneText
import com.stockfortune.app.domain.calculator.GanzhiCalculator
import com.stockfortune.app.domain.calculator.TenGodCalculator
import com.stockfortune.app.domain.model.Strength
import com.stockfortune.app.domain.model.TenGod
import com.stockfortune.app.domain.model.WealthType
import java.io.BufferedReader
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 历法与十神内核的正确性测试。
 * 期望值来自 `tools/` 下的 Python 参考实现（与 Excel 数据源逐字对齐）生成的 parity 夹具，
 * 以及兄弟项目 stock-metaphysics-platform 的 golden 用例。
 */
class BaziCoreTest {

    private fun load(name: String): List<List<String>> {
        val stream = javaClass.classLoader?.getResourceAsStream("parity/$name")
        assertNotNull("缺少夹具 parity/$name，请先运行 tools/gen_parity_fixtures.py", stream)
        return BufferedReader(stream!!.reader()).readLines()
            .drop(1)
            .filter { it.isNotBlank() }
            .map { it.split(",") }
    }

    @Test
    fun `日柱锚点与兄弟项目 golden 用例一致`() {
        val cases = mapOf(
            "1949-10-01" to "甲子", "1900-01-01" to "甲戌", "1984-02-02" to "丙寅",
            "2001-08-27" to "壬戌", "1991-04-03" to "癸卯", "2018-06-11" to "甲戌",
            "2000-01-01" to "戊午", "2024-02-04" to "戊戌", "1999-12-31" to "丁巳",
            "2026-09-01" to "戊寅", "2026-09-29" to "丙午",
        )
        cases.forEach { (d, expect) -> assertEquals(d, expect, GanzhiCalculator.dayGanzhi(LocalDate.parse(d))) }
    }

    @Test
    fun `日柱在 60 甲子上逐日连续推进`() {
        var d = LocalDate.of(2020, 1, 1)
        var prev = GanzhiCalculator.dayGanzhi(d)
        repeat(400) {
            d = d.plusDays(1)
            val cur = GanzhiCalculator.dayGanzhi(d)
            assertEquals("跳变于 $d", (TenGodCalculator.sexagenaryIndex(prev) + 1) % 60, TenGodCalculator.sexagenaryIndex(cur))
            prev = cur
        }
    }

    @Test
    fun `十神全表与 Python 参考实现一致`() {
        load("ten_god.csv").forEach { (dayStem, other, expect) ->
            assertEquals("$dayStem 见 $other", TenGod.fromCn(expect), TenGodCalculator.tenGod(dayStem, other))
        }
    }

    @Test
    fun `十神规则基本断言`() {
        assertEquals(TenGod.BI_JIAN, TenGodCalculator.tenGod("甲", "甲"))
        assertEquals(TenGod.JIE_CAI, TenGodCalculator.tenGod("甲", "乙"))
        assertEquals(TenGod.SHI_SHEN, TenGodCalculator.tenGod("甲", "丙"))
        assertEquals(TenGod.SHANG_GUAN, TenGodCalculator.tenGod("甲", "丁"))
        assertEquals(TenGod.PIAN_CAI, TenGodCalculator.tenGod("甲", "戊"))
        assertEquals(TenGod.ZHENG_CAI, TenGodCalculator.tenGod("甲", "己"))
        assertEquals(TenGod.QI_SHA, TenGodCalculator.tenGod("甲", "庚"))
        assertEquals(TenGod.ZHENG_GUAN, TenGodCalculator.tenGod("甲", "辛"))
        assertEquals(TenGod.PIAN_YIN, TenGodCalculator.tenGod("甲", "壬"))
        assertEquals(TenGod.ZHENG_YIN, TenGodCalculator.tenGod("甲", "癸"))
    }

    @Test
    fun `纳音表抽样正确`() {
        mapOf(
            "甲子" to "海中金", "戊辰" to "大林木", "壬戌" to "大海水", "癸亥" to "大海水",
            "丙寅" to "炉中火", "庚午" to "路旁土", "辛卯" to "松柏木", "丁酉" to "山下火",
        ).forEach { (gz, expect) -> assertEquals(gz, expect, TenGodCalculator.naYin(gz)) }
    }

    @Test
    fun `藏干表共 28 条且本气唯一`() {
        val total = com.stockfortune.app.domain.calculator.BaziTables.HIDDEN_STEMS.values.sumOf { it.size }
        assertEquals(28, total)
        "子丑寅卯辰巳午未申酉戌亥".forEach { b ->
            assertTrue(b.toString(), TenGodCalculator.hiddenStems(b.toString()).isNotEmpty())
            assertEquals(b.toString(), TenGodCalculator.mainQi(b.toString()), TenGodCalculator.hiddenStems(b.toString()).first())
        }
    }

    @Test
    fun `月令旺相休囚死按四时五行令`() {
        // 申月金旺：金旺 水相 土休 火囚 木死
        assertEquals("金旺·水相·土休·火囚·木死", TenGodCalculator.seasonSummary("申"))
        // 辰月土旺：土旺 金相 火休 木囚 水死
        assertEquals("土旺·金相·火休·木囚·水死", TenGodCalculator.seasonSummary("辰"))
    }

    @Test
    fun `财星判定与 Python 参考实现一致`() {
        load("wealth.csv").forEach { (dayStem, stem, branch, expect) ->
            assertEquals(
                "$dayStem 遇 $stem$branch", WealthType.fromCn(expect),
                TenGodCalculator.wealthType(dayStem, stem, branch),
            )
        }
    }

    /**
     * 白话层 30 格必须写全、互不重复。少一格在运行期会 error()，但那时已经上线了；
     * 多写两格一样的，等于拆 30 格的意义（同一十神在身强身弱下读法相反）被抹平。
     */
    @Test
    fun `白话层十神乘强弱三十格齐全且互不重复`() {
        val seen = mutableMapOf<String, String>()
        TenGod.entries.forEach { g ->
            Strength.entries.forEach { st ->
                val text = FortuneText.monthSummary("甲子", g, WealthType.OTHER, "子", st)
                val plain = text.substringAfter("；").substringBeforeLast("；月令")
                assertTrue("$g×$st 白话为空：$text", plain.isNotBlank() && plain != text)
                assertTrue("白话重复：$g×$st 与 ${seen[plain]} 同文", !seen.containsKey(plain))
                seen[plain] = "$g×$st"
            }
        }
        assertEquals("白话格子数应为 10 十神 × 3 强弱", 30, seen.size)
    }

    /**
     * 日主强弱双端一致（Rule v1.2，年月日六字）。夹具里刻意塞了 score 正好 ±2.0 的压线盘：
     * 阈值比较双端一个写 `>=` 一个写 `>`，只有这些样本才测得出来，随机盘几乎全在安全区。
     */
    @Test
    fun `日主强弱与 Python 参考实现一致`() {
        var boundary = 0
        load("strength.csv").forEach { (y, m, d, score, expect) ->
            val s = TenGodCalculator.strengthScore(y, m, d)
            assertEquals("$y $m $d 分值", score.toDouble(), s, 0.004)
            assertEquals("$y $m $d 三态", Strength.fromCn(expect), TenGodCalculator.dayMasterStrength(y, m, d))
            if (kotlin.math.abs(kotlin.math.abs(s) - 2.0) < 0.004) boundary++
        }
        assertTrue("夹具未覆盖 ±2.0 压线盘，阈值比较符没被锁住（命中 $boundary 条）", boundary >= 4)
    }

    @Test
    fun `财星判定透干优先`() {
        // 壬日主遇丙午：透丙为偏财（午藏丁才是正财），必须判为偏财
        assertEquals(WealthType.PIAN_CAI, TenGodCalculator.wealthType("壬", "丙", "午"))
        // 干支皆非财 → 其他
        assertEquals(WealthType.OTHER, TenGodCalculator.wealthType("甲", "甲", "子"))
        // 无财透干但本气为财 → 取本气（庚为七杀，丑本气己土是甲木的正财）
        assertEquals(WealthType.ZHENG_CAI, TenGodCalculator.wealthType("甲", "庚", "丑"))
    }

    @Test
    fun `干支日历夹具与 Python 日粒度实现一致`() {
        load("ganzhi_sample.csv").forEach { (date, y, m, d) ->
            assertEquals("日柱 $date", d, GanzhiCalculator.dayGanzhi(LocalDate.parse(date)))
            // 月柱/年柱依赖节气表（预置库），此处校验 60 甲子合法性
            assertTrue("$date $y", TenGodCalculator.sexagenaryIndex(y) >= 0)
            assertTrue("$date $m", TenGodCalculator.sexagenaryIndex(m) >= 0)
        }
    }

    @Test
    fun `股票四柱夹具的日柱与时柱可由算式复算`() {
        load("stocks.csv").forEach { r ->
            val dayPillar = r[4]
            val hourPillar = r[5]
            val naYin = r[6]
            val code = r[0]
            assertEquals("$code 日柱", dayPillar, GanzhiCalculator.dayGanzhi(LocalDate.parse(r[1])))
            val si = com.stockfortune.app.domain.calculator.BaziTables.BRANCHES.indexOf("巳")
            assertEquals("$code 时柱", hourPillar, TenGodCalculator.hourStem(dayPillar[0].toString(), si) + "巳")
            assertEquals("$code 纳音", naYin, TenGodCalculator.naYin(dayPillar))
        }
    }

    @Test
    fun `文案不得泄漏英文枚举名`() {
        val texts = mutableListOf<String>()
        com.stockfortune.app.domain.calculator.BaziTables.STEMS.forEach { s ->
            TenGod.entries.forEach { g ->
                texts += FortuneText.monthSummary("丙申", g, WealthType.OTHER, "申", Strength.BALANCED)
                texts += FortuneText.yearIndustryNote(s, "午")
            }
            WealthType.entries.forEach { w ->
                TenGod.entries.forEach { g -> texts += FortuneText.yearWealthSummary(w, "丙午", g, Strength.BALANCED) }
                texts += FortuneText.wealthBasis(s, "戊寅", "戊", "寅", w, true)
            }
        }
        texts.forEach { t ->
            TenGod.entries.forEach { assertTrue("文案含枚举名 ${it.name}：$t", !t.contains(it.name)) }
            WealthType.entries.forEach { if (it != WealthType.NONE) assertTrue("文案含枚举名 ${it.name}：$t", !t.contains(it.name)) }
        }
    }

    @Test
    fun `单日财星推导链解释为何判为其他`() {
        // 癸水日主 × 2026-09-01 戊寅：天干正官、寅本气甲为伤官 → 其他；寅中丙火正财属中气，藏而不透
        val other = FortuneText.wealthBasis("癸", "戊寅", "戊", "寅", WealthType.OTHER, true)
        assertTrue("应说明我克为财：${other[0]}", other[0].contains("我克者为财"))
        assertTrue("财星应为丙丁：${other[0]}", other[0].contains("丙（正财）") && other[0].contains("丁（偏财）"))
        assertTrue(other[1].contains("正官") && other[1].contains("伤官"))
        assertTrue(other[2].contains("均不见财星"))
        assertTrue("应点明藏干不计：$other", other.any { it.contains("丙（中气·正财）") && it.contains("藏而不透") })

        val tou = FortuneText.wealthBasis("癸", "丙午", "丙", "午", WealthType.ZHENG_CAI, true)
        assertTrue(tou.last().contains("财星透于天干"))
        val benqi = FortuneText.wealthBasis("癸", "甲午", "甲", "午", WealthType.PIAN_CAI, true)
        assertTrue("本气财星应说明天干不透：${benqi.last()}", benqi.last().contains("天干不透") && benqi.last().contains("偏财"))

        val closed = FortuneText.wealthBasis("癸", "戊寅", "戊", "寅", WealthType.NONE, false)
        assertEquals(1, closed.size)
        assertTrue(closed[0].contains("非交易日"))
    }

    @Test
    fun `五鼠遁与五虎遁边界`() {
        assertEquals("乙", TenGodCalculator.hourStem("壬", com.stockfortune.app.domain.calculator.BaziTables.BRANCHES.indexOf("巳")))
        assertEquals("丁", TenGodCalculator.hourStem("戊", com.stockfortune.app.domain.calculator.BaziTables.BRANCHES.indexOf("巳")))
        // 1984-02-02（立春前）属癸亥年，丑月应为乙丑
        assertEquals("乙", TenGodCalculator.monthStem("癸", com.stockfortune.app.domain.calculator.BaziTables.BRANCHES.indexOf("丑")))
        // 丙年寅月起庚寅
        assertEquals("庚", TenGodCalculator.monthStem("丙", com.stockfortune.app.domain.calculator.BaziTables.BRANCHES.indexOf("寅")))
    }

    @Test
    fun `文案表不出现投资建议类措辞`() {
        val banned = listOf("买入", "卖出", "涨幅", "收益率", "必涨", "预测涨跌", "稳赚")
        val texts = TenGodCalculator.let {
            com.stockfortune.app.domain.calculator.BaziTables.STEMS.flatMap { s ->
                listOf(
                    FortuneText.fateFeature(s),
                    FortuneText.yearWealthSummary(WealthType.ZHENG_CAI, "丙午", TenGod.ZHENG_CAI, Strength.STRONG),
                    FortuneText.yearWealthSummary(WealthType.ZHENG_CAI, "丙午", TenGod.BI_JIAN, Strength.WEAK),
                    FortuneText.yearIndustryNote(s, "午"),
                    FortuneText.yearAdvice(WealthType.PIAN_CAI),
                    FortuneText.monthTip(3, 5),
                    FortuneText.monthSummary("己亥", TenGod.BI_JIAN, WealthType.ZHENG_CAI, "亥", Strength.WEAK),
                )
            }
        }
        texts.forEach { t -> banned.forEach { b -> assertTrue("文案含敏感词 $b: $t", !t.contains(b)) } }
    }

    /**
     * 「透干」只在天干本身为财时成立。己土日主遇己亥月：月干己是比肩，
     * 财在亥的本气壬上，Rule v1.1 判正财但属藏支 —— 旧文案不分情况一律写「透正财」。
     * 用例取自 603002（壬辰 乙巳 己卯 己巳）2026 年的真实月份。
     */
    @Test
    fun `财在地支本气时文案记藏支而非透干`() {
        val monthHidden = FortuneText.monthSummary("己亥", TenGod.BI_JIAN, WealthType.ZHENG_CAI, "亥", Strength.WEAK)
        assertTrue("藏支月不得写「透」：$monthHidden", !monthHidden.contains("透"))
        assertTrue("藏支月须点明藏支：$monthHidden", monthHidden.contains("正财藏支"))

        val monthShown = FortuneText.monthSummary("壬辰", TenGod.ZHENG_CAI, WealthType.ZHENG_CAI, "辰", Strength.STRONG)
        assertTrue("透干月应写「透正财」：$monthShown", monthShown.contains("透正财"))

        val yearHidden = FortuneText.yearWealthSummary(WealthType.PIAN_CAI, "丙午", TenGod.PIAN_YIN, Strength.WEAK)
        assertTrue("流年同理：$yearHidden", yearHidden.contains("财星藏支") && !yearHidden.contains("透干"))
        assertTrue("流年透干：", FortuneText.yearWealthSummary(WealthType.PIAN_CAI, "丙午", TenGod.PIAN_CAI, Strength.STRONG)
            .contains("财星透干"))
    }
}
