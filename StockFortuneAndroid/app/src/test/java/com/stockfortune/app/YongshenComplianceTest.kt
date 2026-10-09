package com.stockfortune.app

import com.stockfortune.app.domain.calculator.YongshenCalculator
import com.stockfortune.app.domain.model.LabelMapper
import com.stockfortune.app.domain.model.Strength
import com.stockfortune.app.domain.model.YongshenCandidateResult
import com.stockfortune.app.domain.model.YongshenCandidateStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.BufferedReader
import java.io.InputStreamReader

/**
 * Phase 3 合规禁词门禁测试（Gate G3 / ADR-0007）。
 * 严格保留 60 个合规禁词（包括收益暗示类与操作建议类，核心禁词单字「忌」零容忍）。
 */
class YongshenComplianceTest {

    private val bannedWords = listOf(
        "买入", "卖出", "涨幅", "收益率", "必涨", "预测涨跌", "稳赚",
        "获利", "收益", "短线", "机会", "潜力", "把握时机", "适合把握",
        "買入", "賣出", "漲幅", "收益率", "必漲", "預測漲跌", "穩賺",
        "獲利", "收益", "短線", "機會", "潛力", "把握時機", "適合把握",
        "忌", "不宜", "宜于", "适合", "慎", "勿", "务必", "尽量", "优先", "应当", "应该",
        "加仓", "减仓", "重仓", "轻仓", "建仓", "进场", "出场", "持币", "见好就收",
        "规避", "谨防", "小心", "可取", "避免", "防", "注意", "冲动", "大额", "投机",
    ).distinct()

    private fun assertCompliance(label: String, text: String) {
        bannedWords.forEach { b ->
            assertFalse("[$label] 命中合规禁词「$b」: $text", text.contains(b))
        }
    }

    @Test
    fun testLabelMapperMappingRules() {
        assertEquals("用神", LabelMapper.mapShenLabel("用神"))
        assertEquals("喜神", LabelMapper.mapShenLabel("喜神"))
        assertEquals("制衡之神", LabelMapper.mapShenLabel("忌神"))
        assertEquals("耗身之神", LabelMapper.mapShenLabel("仇神"))
        assertEquals("调和之神", LabelMapper.mapShenLabel("闲神"))

        // 验证映射后绝无禁词「忌」
        assertCompliance("忌神映射后", LabelMapper.mapShenLabel("忌神"))
    }

    @Test
    fun testDisplaySummaryComplianceAcrossAllStatuses() {
        // 1. 身强
        val strongRes = YongshenCalculator.calculate("辛巳", "丙申", "壬戌")
        val strongSummary = LabelMapper.formatDisplaySummary(strongRes)
        assertCompliance("身强UI摘要", strongSummary)
        assertFalse(strongSummary.contains("忌"))
        assertTrue(strongSummary.contains("用神：土"))
        assertTrue(strongSummary.contains("制衡：金、水"))

        // 2. 身弱
        val weakRes = YongshenCalculator.calculate("戊戌", "戊午", "甲戌")
        val weakSummary = LabelMapper.formatDisplaySummary(weakRes)
        assertCompliance("身弱UI摘要", weakSummary)
        assertFalse(weakSummary.contains("忌"))
        assertTrue(weakSummary.contains("用神：水"))
        assertTrue(weakSummary.contains("制衡：金、土"))

        // 3. 中和
        val balancedRes = YongshenCalculator.calculate("丁丑", "壬子", "甲午")
        val balancedSummary = LabelMapper.formatDisplaySummary(balancedRes)
        assertCompliance("中和UI摘要", balancedSummary)
        assertFalse(balancedSummary.contains("忌"))
        assertTrue(balancedSummary.contains("格局中和平衡"))

        // 4. 不适用
        val unavailRes = YongshenCalculator.calculate("", "", "")
        val unavailSummary = LabelMapper.formatDisplaySummary(unavailRes)
        assertCompliance("不适用UI摘要", unavailSummary)
        assertFalse(unavailSummary.contains("忌"))
    }

    @Test
    fun testParityFixturesRationalesCompliance() {
        val stream = javaClass.getResourceAsStream("/parity/yongshen.csv")
            ?: throw IllegalStateException("缺少 parity/yongshen.csv 夹具")

        val reader = BufferedReader(InputStreamReader(stream, Charsets.UTF_8))
        reader.readLine() // header

        var checkedCount = 0
        reader.forEachLine { line ->
            if (line.isBlank()) return@forEachLine
            val cols = line.split(",")
            val yPillar = cols[0]
            val mPillar = cols[1]
            val dPillar = cols[2]
            val res = YongshenCalculator.calculate(yPillar, mPillar, dPillar)

            assertCompliance("推导理由 [$yPillar $mPillar $dPillar]", res.rationale)
            val summary = LabelMapper.formatDisplaySummary(res)
            assertCompliance("展示摘要 [$yPillar $mPillar $dPillar]", summary)

            assertFalse("推导理由绝对不能出现「忌」", res.rationale.contains("忌"))
            assertFalse("展示摘要绝对不能出现「忌」", summary.contains("忌"))
            checkedCount++
        }

        assertTrue("至少应扫描 40 组夹具文案", checkedCount >= 40)
    }
}
