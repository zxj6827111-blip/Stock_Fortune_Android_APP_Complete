package com.stockfortune.app

import com.stockfortune.app.domain.calculator.FortuneCopyEngine
import com.stockfortune.app.domain.model.FirstDayPolarity
import com.stockfortune.app.domain.model.ReviewStatus
import com.stockfortune.app.domain.model.Strength
import com.stockfortune.app.domain.model.TenGod
import com.stockfortune.app.domain.model.YongshenCandidateStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Phase 5 离线自然语言组合引擎（FortuneCopyEngine）回归测试。
 */
class FortuneCopyEngineTest {

    private val forbiddenWords = listOf(
        "买入", "卖出", "涨幅", "收益率", "必涨", "预测涨跌", "稳赚",
        "获利", "收益", "短线", "机会", "潜力", "把握时机", "适合把握",
        "買入", "賣出", "漲幅", "收益率", "必漲", "預測漲跌", "穩賺",
        "獲利", "收益", "短線", "機會", "潛力", "把握時機", "適合把握",
        "忌", "不宜", "宜于", "适合", "慎", "勿", "务必", "尽量", "优先", "应当", "应该",
        "加仓", "减仓", "重仓", "轻仓", "建仓", "进场", "出场", "持币", "见好就收",
        "规避", "谨防", "小心", "可取", "避免", "防", "注意", "冲动", "大额", "投机",
    ).distinct()

    private fun assertCompliance(label: String, text: String) {
        forbiddenWords.forEach { fw ->
            assertFalse("[$label] 命中合规禁词「$fw」: $text", text.contains(fw))
        }
    }

    @Test
    fun testTypicalStockMoutaiGeneration() {
        // 贵州茅台 600519.SH: 辛巳 丙申 壬戌 (身强, 首日阳命)
        val interp = FortuneCopyEngine.composeMonthlyInterpretation(
            stockId = 600519L,
            stockCode = "600519.SH",
            dayStem = "壬",
            yearPillar = "辛巳",
            monthPillar = "丙申",
            dayPillar = "壬戌",
            firstDayPolarity = FirstDayPolarity.YANG,
            dayunStatus = "available",
            year = 2026,
            month = 3,
            monthGanzhi = "庚寅", // 天干偏印，地支食神
            natalRelationsCount = 5,
            yongshenStatus = YongshenCandidateStatus.CONFIRMED,
        )

        assertTrue("五段结构齐全", interp.isComplete)
        assertEquals("待人工终审", interp.reviewStatus.cn)
        assertFalse(interp.reviewStatus.isProductionAllowed)

        // 验证各段内容
        assertTrue(interp.basisText.contains("偏印"))
        assertTrue(interp.basisText.contains("食神"))
        assertTrue(interp.themeText.contains("专项技术与知识储备"))
        assertTrue(interp.themeText.contains("身强又见偏印"))
        assertTrue(interp.contradictionText.contains("不同十神分组"))
        assertTrue(interp.businessText.contains("研发投入和资本化率"))
        assertTrue(interp.synthesisText.contains("身强"))

        // 三项高级条件均可用时提示为空
        assertEquals("", interp.preciseAdvancedNotice)

        val fullText = interp.toFormattedMarkdown()
        assertCompliance("贵州茅台2026年3月五段式解读", fullText)
    }

    @Test
    fun testShenhuaWeakGeneration() {
        // 中国神华 601088.SH: 丁丑 庚戌 丙子 (身弱, 首日阳命)
        val interp = FortuneCopyEngine.composeMonthlyInterpretation(
            stockId = 601088L,
            stockCode = "601088.SH",
            dayStem = "丙",
            yearPillar = "丁丑",
            monthPillar = "庚戌",
            dayPillar = "丙子",
            firstDayPolarity = FirstDayPolarity.YANG,
            dayunStatus = "available",
            year = 2026,
            month = 3,
            monthGanzhi = "庚寅", // 偏财 / 偏印
            natalRelationsCount = 0,
            yongshenStatus = YongshenCandidateStatus.CONFIRMED,
        )

        assertTrue(interp.isComplete)
        assertTrue(interp.themeText.contains("资产交易与资金流转"))
        assertTrue(interp.themeText.contains("身弱遇偏财"))
        assertTrue(interp.synthesisText.contains("身弱"))
        assertCompliance("中国神华2026年3月解读", interp.toFormattedMarkdown())
    }

    @Test
    fun testFlatPolarityPinganBankDegradation() {
        // 平安银行 000001.SZ (平盘 FLAT)
        val interp = FortuneCopyEngine.composeMonthlyInterpretation(
            stockId = 1L,
            stockCode = "000001.SZ",
            dayStem = "癸",
            yearPillar = "辛未",
            monthPillar = "丙申",
            dayPillar = "癸酉",
            firstDayPolarity = FirstDayPolarity.FLAT,
            dayunStatus = "unavailable_flat",
            year = 2026,
            month = 3,
            monthGanzhi = "庚寅",
            natalRelationsCount = 3,
            yongshenStatus = YongshenCandidateStatus.CONFIRMED,
        )

        assertTrue(interp.isComplete)
        assertTrue("命理依据中明确说明平盘并暂停大运", interp.basisText.contains("首日表现为平盘"))
        assertTrue("分项提示中明确说明大运不展开", interp.preciseAdvancedNotice.contains("大运"))
        assertFalse("分项提示不连带否定原局", interp.preciseAdvancedNotice.contains("原局"))
        assertFalse("分项提示不连带否定喜用", interp.preciseAdvancedNotice.contains("喜用"))
        assertCompliance("平安银行平盘解读", interp.toFormattedMarkdown())
    }

    @Test
    fun testStrengthDifferencesOnSameTenGod() {
        // 同一十神组合：月干正财，月支正财
        val interpStrong = FortuneCopyEngine.composeMonthlyInterpretation(
            stockId = 100L, stockCode = "TEST_STRONG", dayStem = "甲",
            yearPillar = "甲寅", monthPillar = "乙卯", dayPillar = "甲辰", // 身强
            firstDayPolarity = FirstDayPolarity.YANG, dayunStatus = "available",
            year = 2026, month = 2, monthGanzhi = "己丑", // 正财/正财
            natalRelationsCount = 1, yongshenStatus = YongshenCandidateStatus.CONFIRMED,
        )

        val interpWeak = FortuneCopyEngine.composeMonthlyInterpretation(
            stockId = 200L, stockCode = "TEST_WEAK", dayStem = "甲",
            yearPillar = "庚申", monthPillar = "辛酉", dayPillar = "甲戌", // 身弱
            firstDayPolarity = FirstDayPolarity.YANG, dayunStatus = "available",
            year = 2026, month = 2, monthGanzhi = "己丑", // 正财/正财
            natalRelationsCount = 1, yongshenStatus = YongshenCandidateStatus.CONFIRMED,
        )

        // 验证同一个十神组合在不同强弱下语义产生依据充分的差异
        assertNotEquals("身强与身弱的主题副线必须有显著差异", interpStrong.themeText, interpWeak.themeText)
        assertTrue(interpStrong.themeText.contains("身强遇正财"))
        assertTrue(interpWeak.themeText.contains("身弱遇正财"))
        assertNotEquals("身强与身弱的综合解释必须有显著差异", interpStrong.synthesisText, interpWeak.synthesisText)
    }

    @Test
    fun testSynthesisParagraphSummarizesPreviousSections() {
        val interp = FortuneCopyEngine.composeMonthlyInterpretation(
            stockId = 1L, stockCode = "000001", dayStem = "甲",
            yearPillar = "甲寅", monthPillar = "乙卯", dayPillar = "甲子",
            firstDayPolarity = FirstDayPolarity.YANG, dayunStatus = "available",
            year = 2026, month = 1, monthGanzhi = "戊子",
            natalRelationsCount = 2, yongshenStatus = YongshenCandidateStatus.CONFIRMED,
        )

        assertTrue(interp.synthesisText.isNotBlank())
        assertTrue("综合解释体现强弱归类与解释边界", interp.synthesisText.contains("现行三柱六字口径"))
        assertFalse("综合解释不应包含未经核实的情绪化套话", interp.synthesisText.contains("大吉大利"))
    }
}
