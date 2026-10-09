package com.stockfortune.app

import com.stockfortune.app.domain.calculator.OfflineCopyRuleEvaluator
import com.stockfortune.app.domain.model.AlgorithmAvailability
import com.stockfortune.app.domain.model.CopyRuleDefinition
import com.stockfortune.app.domain.model.CopySection
import com.stockfortune.app.domain.model.FiveParagraphInterpretation
import com.stockfortune.app.domain.model.PreciseAdvancedNotice
import com.stockfortune.app.domain.model.ProductionGate
import com.stockfortune.app.domain.model.ReviewStatus
import com.stockfortune.app.domain.model.Strength
import com.stockfortune.app.domain.model.TenGod
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Phase 4 离线文案集成与五段式契约回归测试。
 */
class CopywritingContractTest {

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
    fun testPreciseAdvancedNoticeAllAvailableProducesEmptyString() {
        val notice = PreciseAdvancedNotice.formatNotice(
            dayunState = AlgorithmAvailability.AVAILABLE,
            natalState = AlgorithmAvailability.AVAILABLE,
            yongshenState = AlgorithmAvailability.AVAILABLE,
        )
        assertEquals("三项高级条件均可用时，分项提示必须为空字符串", "", notice)
    }

    @Test
    fun testPreciseAdvancedNoticeSingleUnavailableDoesNotCrossDeny() {
        // 1. 仅大运不适用
        val noticeDayunOnly = PreciseAdvancedNotice.formatNotice(
            dayunState = AlgorithmAvailability.UNAVAILABLE,
            natalState = AlgorithmAvailability.AVAILABLE,
            yongshenState = AlgorithmAvailability.AVAILABLE,
        )
        assertTrue(noticeDayunOnly.contains("大运"))
        assertFalse("单项大运不可用时不应连带否定原局", noticeDayunOnly.contains("原局"))
        assertFalse("单项大运不可用时不应连带否定喜用", noticeDayunOnly.contains("喜用"))

        // 2. 仅原局关系不适用
        val noticeNatalOnly = PreciseAdvancedNotice.formatNotice(
            dayunState = AlgorithmAvailability.AVAILABLE,
            natalState = AlgorithmAvailability.UNAVAILABLE,
            yongshenState = AlgorithmAvailability.AVAILABLE,
        )
        assertTrue(noticeNatalOnly.contains("原局关系"))
        assertFalse(noticeNatalOnly.contains("大运"))
        assertFalse(noticeNatalOnly.contains("喜用"))

        // 3. 仅喜用待定
        val noticeYongshenOnly = PreciseAdvancedNotice.formatNotice(
            dayunState = AlgorithmAvailability.AVAILABLE,
            natalState = AlgorithmAvailability.AVAILABLE,
            yongshenState = AlgorithmAvailability.PENDING,
        )
        assertTrue(noticeYongshenOnly.contains("喜用候选"))
        assertFalse(noticeYongshenOnly.contains("大运"))
        assertFalse(noticeYongshenOnly.contains("原局"))
    }

    @Test
    fun testFiveParagraphStructureCompletenessAndFormatting() {
        val interp = FiveParagraphInterpretation(
            stockId = 600519L,
            stockCode = "600519",
            year = 2026,
            month = 5,
            basisText = "流月天干见比肩，月令为巳火，原局按三柱六字归为身强。",
            themeText = "同类互助与资源协同构成本月主线，各方权责边界需清晰辨认。",
            contradictionText = "身强见比肩，关注内部资源分配与协同效率的平衡。",
            businessText = "经营观察聚焦于主营业务持续能力与现金流周转质量。",
            synthesisText = "本月宜夯实内部协同机制，稳步推进各项常规业务。",
            preciseAdvancedNotice = "",
            hitRuleIds = listOf("BAS_STEM_BIJIAN", "THM_BIJIAN_STRONG", "ENT_BIJIAN"),
            reviewStatus = ReviewStatus.PENDING_REVIEW,
            isMock = false,
        )

        assertTrue("五段结构齐全", interp.isComplete)
        assertFalse("待人工终审文案不得直接用于生产发布", interp.reviewStatus.isProductionAllowed)

        val md = interp.toFormattedMarkdown()
        assertTrue(md.contains("### 【命理依据】"))
        assertTrue(md.contains("### 【本月主题】"))
        assertTrue(md.contains("### 【潜在矛盾】"))
        assertTrue(md.contains("### 【企业经营观察】"))
        assertTrue(md.contains("### 【综合解释】"))
        assertCompliance("五段式Markdown输出", md)
    }

    @Test
    fun testRulePriorityAndConflictGroupResolution() {
        val ctx = OfflineCopyRuleEvaluator.EvaluationContext(
            stockId = 1L,
            stockCode = "000001",
            year = 2026,
            month = 6,
            monthStemGod = TenGod.BI_JIAN,
            monthBranchMainQiGod = TenGod.ZHENG_CAI,
            strength = Strength.STRONG,
        )

        val ruleLowPriority = CopyRuleDefinition(
            ruleId = "RULE_THEME_LOW",
            section = CopySection.THEME,
            triggerDsl = "month.stem_god == \"比肩\"",
            priority = 80,
            conflictGroup = "THEME_MAIN",
            evidenceKeys = listOf("month.stem_god"),
            text = "低优先级主题描述",
            ruleVersion = "v1.3",
        )

        val ruleHighPriority = CopyRuleDefinition(
            ruleId = "RULE_THEME_HIGH",
            section = CopySection.THEME,
            triggerDsl = "month.stem_god == \"比肩\" && natal.strength_state == \"身强\"",
            priority = 110,
            conflictGroup = "THEME_MAIN",
            evidenceKeys = listOf("month.stem_god", "natal.strength_state"),
            text = "高优先级主题描述：身强见比肩",
            ruleVersion = "v1.3",
        )

        val result = OfflineCopyRuleEvaluator.evaluate(ctx, listOf(ruleLowPriority, ruleHighPriority))
        assertEquals("同一冲突组必须选取最高 priority 的规则", "高优先级主题描述：身强见比肩", result.themeText)
        assertTrue(result.hitRuleIds.contains("RULE_THEME_HIGH"))
        assertFalse(result.hitRuleIds.contains("RULE_LOW_PRIORITY"))
    }

    @Test
    fun testLegacyNoRenderRuleIsBlockedFromOutput() {
        val ctx = OfflineCopyRuleEvaluator.EvaluationContext(
            stockId = 1L,
            stockCode = "000001",
            year = 2026,
            month = 6,
            monthStemGod = TenGod.BI_JIAN,
            monthBranchMainQiGod = TenGod.ZHENG_CAI,
            strength = Strength.STRONG,
            dayunAvailability = AlgorithmAvailability.UNAVAILABLE,
        )

        val legacyRule = CopyRuleDefinition(
            ruleId = "NA_ADVANCED_MISSING",
            section = CopySection.CONTRADICTION,
            triggerDsl = "dayun.availability == \"UNAVAILABLE\"",
            priority = 50,
            conflictGroup = "LEGACY_GROUP",
            evidenceKeys = listOf("dayun.availability"),
            text = "大运、原局关系、喜用候选的输出尚未形成可采信证据，相关互动暂不展开。",
            ruleVersion = "legacy",
            isLegacyNoRender = true, // 明确标记仅审计、禁止渲染
        )

        val result = OfflineCopyRuleEvaluator.evaluate(ctx, listOf(legacyRule))
        assertFalse("旧通用缺项句必须禁止渲染", result.contradictionText.contains("大运、原局关系、喜用候选的输出尚未形成可采信证据"))
        assertFalse("命中规则ID中不应出现被阻止渲染的规则", result.hitRuleIds.contains("NA_ADVANCED_MISSING"))
    }

    @Test
    fun testProductionGateIntegrity() {
        assertFalse(ProductionGate.AUDIT_ONLY.isDeployable)
        assertFalse(ProductionGate.CANDIDATE_ONLY.isDeployable)
        assertFalse(ProductionGate.PRODUCTION_BLOCKED.isDeployable)
        assertTrue(ProductionGate.PRODUCTION_RELEASE.isDeployable)

        assertFalse(ReviewStatus.PENDING_REVIEW.isProductionAllowed)
        assertFalse(ReviewStatus.RETURNED_FOR_REVISION.isProductionAllowed)
        assertTrue(ReviewStatus.APPROVED.isProductionAllowed)
    }
}
