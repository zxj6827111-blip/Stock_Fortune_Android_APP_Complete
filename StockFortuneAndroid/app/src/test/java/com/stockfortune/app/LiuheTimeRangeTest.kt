package com.stockfortune.app

import com.stockfortune.app.data.entity.LuckCyclePeriodEntity
import com.stockfortune.app.data.entity.NatalRelationEntity
import com.stockfortune.app.domain.calculator.FortuneCopyEngine
import com.stockfortune.app.domain.calculator.OfflineCopyRuleEvaluator
import com.stockfortune.app.domain.model.AlgorithmAvailability
import com.stockfortune.app.domain.model.CopyRuleDefinition
import com.stockfortune.app.domain.model.CopySection
import com.stockfortune.app.domain.model.FirstDayPolarity
import com.stockfortune.app.domain.model.ProductionGate
import com.stockfortune.app.domain.model.ReviewStatus
import com.stockfortune.app.domain.model.Strength
import com.stockfortune.app.domain.model.TenGod
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * 六合时间范围与边界测试（覆盖至少 6 组边界）。
 * 验证：
 * 1. 原局内部六合不虚构流月有效日期；
 * 2. 流月对原局六合使用真实节气月区间，绝不误用十年大运；
 * 3. 大运对原局六合使用真实大运区间；
 * 4. 同月跨交节/交运时明确实际时段。
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class LiuheTimeRangeTest {

    private val samplePeriod = LuckCyclePeriodEntity(
        id = 1,
        stockId = 1,
        cycleIndex = 1,
        ganzhi = "甲子",
        stem = "甲",
        branch = "子",
        startDate = "2025-04-18",
        endDate = "2035-04-18",
        startYear = 2025,
        endYear = 2035,
        startAge = 10,
        endAge = 20,
        ruleVersion = "1.3.0",
    )

    private val templateRule = CopyRuleDefinition(
        ruleId = "ADV_LH_TIME_TEST",
        section = CopySection.CONTRADICTION,
        triggerDsl = """{"liuhe.pair": "ZICHOU"}""",
        priority = 90,
        conflictGroup = "LH_TEST",
        evidenceKeys = listOf("liuhe.pair"),
        text = "六合支对{relation.pair}在{relation.position_a}与{relation.position_b}成立，有效期自{relation.valid_from}至{relation.valid_to}。",
        ruleVersion = "1.3.0",
        reviewStatus = ReviewStatus.PENDING_REVIEW,
        productionGate = ProductionGate.CANDIDATE_ONLY,
        isLegacyNoRender = false,
        module = "六合",
    )

    @Test
    fun testCase1_NatalInternalLiuhe_PermanentScope() {
        // 边界 1：原局内部六合（先天格局）
        val hit = FortuneCopyEngine.LiuheHit(
            pairName = "子丑",
            pairCode = "ZICHOU",
            scope = "NATAL_INTERNAL",
            branchA = "子",
            branchB = "丑",
            posA = "原局年支",
            posB = "原局月支",
            validFrom = "原局固有",
            validTo = "终身成立",
            timeDescription = "原局内部先天存在，长期持续作用",
        )
        val ctx = OfflineCopyRuleEvaluator.EvaluationContext(
            stockId = 1,
            stockCode = "600519.SH",
            year = 2026,
            month = 2,
            monthStem = "庚",
            monthBranch = "寅",
            monthStemGod = TenGod.QI_SHA,
            monthBranchMainQiGod = TenGod.BI_JIAN,
            strength = Strength.BALANCED,
            firstDayPolarity = FirstDayPolarity.YANG,
            currentLuckPeriod = samplePeriod,
            hitLiuhe = hit,
        )
        val text = OfflineCopyRuleEvaluator.substitutePlaceholders(templateRule.text, ctx)
        assertTrue(text.contains("自原局固有至终身成立"))
        assertFalse("原局内部六合严禁包含大运日期", text.contains("2025-04-18"))
    }

    @Test
    fun testCase2_FlowMonthToNatalYearBranch_SolarTermInterval() {
        // 边界 2：流月支丑 与 原局年支子 形成六合，有效时间必须为该流月节气月（2026-01-05至2026-02-03）
        val hit = FortuneCopyEngine.LiuheHit(
            pairName = "子丑",
            pairCode = "ZICHOU",
            scope = "FLOW_MONTH_TO_NATAL",
            branchA = "丑",
            branchB = "子",
            posA = "流月支",
            posB = "原局年支",
            validFrom = "2026-01-05",
            validTo = "2026-02-03",
            timeDescription = "流月己丑期间有效（2026-01-05 至 2026-02-03）",
        )
        val ctx = OfflineCopyRuleEvaluator.EvaluationContext(
            stockId = 1,
            stockCode = "600519.SH",
            year = 2026,
            month = 1,
            monthStem = "己",
            monthBranch = "丑",
            monthStemGod = TenGod.ZHENG_CAI,
            monthBranchMainQiGod = TenGod.ZHENG_CAI,
            strength = Strength.BALANCED,
            firstDayPolarity = FirstDayPolarity.YANG,
            currentLuckPeriod = samplePeriod,
            hitLiuhe = hit,
            flowMonthStartDate = "2026-01-05",
            flowMonthEndDate = "2026-02-03",
        )
        val text = OfflineCopyRuleEvaluator.substitutePlaceholders(templateRule.text, ctx)
        assertTrue("流月六合必须使用流月日期", text.contains("自2026-01-05至2026-02-03"))
        assertFalse("流月六合严禁把十年大运当作有效期", text.contains("2035-04-18"))
    }

    @Test
    fun testCase3_FlowMonthToNatalMonthBranch() {
        // 边界 3：流月支与原局月支六合
        val hit = FortuneCopyEngine.LiuheHit(
            pairName = "子丑",
            pairCode = "ZICHOU",
            scope = "FLOW_MONTH_TO_NATAL",
            branchA = "丑",
            branchB = "子",
            posA = "流月支",
            posB = "原局月支",
            validFrom = "2026-02-04",
            validTo = "2026-03-05",
        )
        val ctx = OfflineCopyRuleEvaluator.EvaluationContext(
            stockId = 1,
            stockCode = "600519.SH",
            year = 2026,
            month = 2,
            monthStemGod = TenGod.QI_SHA,
            monthBranchMainQiGod = TenGod.BI_JIAN,
            strength = Strength.BALANCED,
            currentLuckPeriod = samplePeriod,
            hitLiuhe = hit,
            flowMonthStartDate = "2026-02-04",
            flowMonthEndDate = "2026-03-05",
        )
        val text = OfflineCopyRuleEvaluator.substitutePlaceholders(templateRule.text, ctx)
        assertTrue(text.contains("自2026-02-04至2026-03-05"))
    }

    @Test
    fun testCase4_FlowMonthToNatalDayBranch() {
        // 边界 4：流月支与原局日支六合
        val hit = FortuneCopyEngine.LiuheHit(
            pairName = "子丑",
            pairCode = "ZICHOU",
            scope = "FLOW_MONTH_TO_NATAL",
            branchA = "丑",
            branchB = "子",
            posA = "流月支",
            posB = "原局日支",
            validFrom = "2026-03-06",
            validTo = "2026-04-04",
        )
        val ctx = OfflineCopyRuleEvaluator.EvaluationContext(
            stockId = 1,
            stockCode = "600519.SH",
            year = 2026,
            month = 3,
            monthStemGod = TenGod.ZHENG_GUAN,
            monthBranchMainQiGod = TenGod.JIE_CAI,
            strength = Strength.BALANCED,
            currentLuckPeriod = samplePeriod,
            hitLiuhe = hit,
        )
        val text = OfflineCopyRuleEvaluator.substitutePlaceholders(templateRule.text, ctx)
        assertTrue(text.contains("自2026-03-06至2026-04-04"))
    }

    @Test
    fun testCase5_DayunToNatalBranch_TenYearInterval() {
        // 边界 5：大运支与原局支六合，此时真实有效区间确实为大运十年（2025-04-18 至 2035-04-18）
        val hit = FortuneCopyEngine.LiuheHit(
            pairName = "子丑",
            pairCode = "ZICHOU",
            scope = "DAYUN_TO_NATAL",
            branchA = "子",
            branchB = "丑",
            posA = "大运支",
            posB = "原局日支",
            validFrom = samplePeriod.startDate,
            validTo = samplePeriod.endDate,
            timeDescription = "大运甲子十年区间有效（2025-04-18至2035-04-18）",
        )
        val ctx = OfflineCopyRuleEvaluator.EvaluationContext(
            stockId = 1,
            stockCode = "600519.SH",
            year = 2026,
            month = 5,
            monthStemGod = TenGod.ZHENG_YIN,
            monthBranchMainQiGod = TenGod.SHI_SHEN,
            strength = Strength.BALANCED,
            currentLuckPeriod = samplePeriod,
            hitLiuhe = hit,
        )
        val text = OfflineCopyRuleEvaluator.substitutePlaceholders(templateRule.text, ctx)
        assertTrue("大运六合允许采用大运十年起止日期", text.contains("自2025-04-18至2035-04-18"))
    }

    @Test
    fun testCase6_CrossTransitionMonth_ExplicitBoundary() {
        // 边界 6：流月跨节气交节时，明确记录该节气月起止
        val hit = FortuneCopyEngine.LiuheHit(
            pairName = "子丑",
            pairCode = "ZICHOU",
            scope = "FLOW_MONTH_TO_NATAL",
            branchA = "丑",
            branchB = "子",
            posA = "流月支",
            posB = "原局年支",
            validFrom = "2026-04-05",
            validTo = "2026-05-04",
            timeDescription = "公历4月于4月5日交清明节入壬辰，有效时段自2026-04-05至2026-05-04",
        )
        val ctx = OfflineCopyRuleEvaluator.EvaluationContext(
            stockId = 1,
            stockCode = "600519.SH",
            year = 2026,
            month = 4,
            monthStemGod = TenGod.PIAN_YIN,
            monthBranchMainQiGod = TenGod.PIAN_CAI,
            strength = Strength.BALANCED,
            currentLuckPeriod = samplePeriod,
            hitLiuhe = hit,
        )
        val text = OfflineCopyRuleEvaluator.substitutePlaceholders(templateRule.text, ctx)
        assertEquals("六合支对子丑在流月支与原局年支成立，有效期自2026-04-05至2026-05-04。", text)
    }
}
