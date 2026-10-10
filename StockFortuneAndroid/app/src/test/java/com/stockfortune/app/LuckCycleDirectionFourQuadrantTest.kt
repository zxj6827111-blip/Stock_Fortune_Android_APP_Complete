package com.stockfortune.app

import com.stockfortune.app.data.entity.LuckCyclePeriodEntity
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
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * 大运方向四象限与边界安全测试。
 * 验证文案层依据预计算的实际大运方向（dayunDirection）判断，禁止根据首日阴阳重新推断。
 * 平盘、缺失、冲突均不得命中确定性顺逆文案。
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class LuckCycleDirectionFourQuadrantTest {

    private val forwardRule = CopyRuleDefinition(
        ruleId = "ADV_DY_DIRECTION_FORWARD",
        section = CopySection.BASIS,
        triggerDsl = """{"dayun.direction": "FORWARD", "dayun.availability": "AVAILABLE"}""",
        priority = 90,
        conflictGroup = "DY_DIRECTION",
        evidenceKeys = listOf("dayun.direction"),
        text = "大运依规则顺推步进，展现正序展开节律。",
        ruleVersion = "1.3.0",
        reviewStatus = ReviewStatus.PENDING_REVIEW,
        productionGate = ProductionGate.CANDIDATE_ONLY,
        isLegacyNoRender = false,
        module = "大运",
    )

    private val reverseRule = CopyRuleDefinition(
        ruleId = "ADV_DY_DIRECTION_REVERSE",
        section = CopySection.BASIS,
        triggerDsl = """{"dayun.direction": "REVERSE", "dayun.availability": "AVAILABLE"}""",
        priority = 90,
        conflictGroup = "DY_DIRECTION",
        evidenceKeys = listOf("dayun.direction"),
        text = "大运依规则逆推步进，呈现逆序回溯节律。",
        ruleVersion = "1.3.0",
        reviewStatus = ReviewStatus.PENDING_REVIEW,
        productionGate = ProductionGate.CANDIDATE_ONLY,
        isLegacyNoRender = false,
        module = "大运",
    )

    private val samplePeriod = LuckCyclePeriodEntity(
        id = 1,
        stockId = 1,
        cycleIndex = 1,
        ganzhi = "甲子",
        stem = "甲",
        branch = "子",
        startDate = "2020-01-01",
        endDate = "2030-01-01",
        startYear = 2020,
        endYear = 2030,
        startAge = 1,
        endAge = 10,
        ruleVersion = "1.3.0",
    )

    private fun createContext(
        firstDayPolarity: FirstDayPolarity,
        dayunDirection: String?,
        dayunAvailability: AlgorithmAvailability = AlgorithmAvailability.AVAILABLE,
        hasPeriod: Boolean = true,
    ): OfflineCopyRuleEvaluator.EvaluationContext {
        return OfflineCopyRuleEvaluator.EvaluationContext(
            stockId = 1,
            stockCode = "600519.SH",
            year = 2026,
            month = 1,
            dayStem = "甲",
            monthStem = "丙",
            monthBranch = "寅",
            monthStemGod = TenGod.SHI_SHEN,
            monthBranchMainQiGod = TenGod.BI_JIAN,
            strength = Strength.BALANCED,
            firstDayPolarity = firstDayPolarity,
            dayunAvailability = dayunAvailability,
            currentLuckPeriod = if (hasPeriod) samplePeriod else null,
            dayunDirection = dayunDirection,
        )
    }

    @Test
    fun testQuadrant1_YangYearYangPolarity_Forward() {
        // 阳年阳日（如甲年甲日上市）：顺排 FORWARD
        val ctx = createContext(
            firstDayPolarity = FirstDayPolarity.YANG,
            dayunDirection = "FORWARD",
        )
        assertTrue(OfflineCopyRuleEvaluator.matchesTrigger(forwardRule.triggerDsl, ctx))
        assertFalse(OfflineCopyRuleEvaluator.matchesTrigger(reverseRule.triggerDsl, ctx))
    }

    @Test
    fun testQuadrant2_YangYearYinPolarity_Reverse() {
        // 阳年阴日（如甲年乙日上市）：逆排 REVERSE
        val ctx = createContext(
            firstDayPolarity = FirstDayPolarity.YIN,
            dayunDirection = "REVERSE",
        )
        assertFalse(OfflineCopyRuleEvaluator.matchesTrigger(forwardRule.triggerDsl, ctx))
        assertTrue(OfflineCopyRuleEvaluator.matchesTrigger(reverseRule.triggerDsl, ctx))
    }

    @Test
    fun testQuadrant3_YinYearYangPolarity_Reverse() {
        // 阴年阳日（如乙年甲日上市）：逆排 REVERSE
        val ctx = createContext(
            firstDayPolarity = FirstDayPolarity.YANG,
            dayunDirection = "REVERSE",
        )
        // 验证：即使首日为阳，只要大运推步事实为 REVERSE，文案层绝不误判为 FORWARD！
        assertFalse(OfflineCopyRuleEvaluator.matchesTrigger(forwardRule.triggerDsl, ctx))
        assertTrue(OfflineCopyRuleEvaluator.matchesTrigger(reverseRule.triggerDsl, ctx))
    }

    @Test
    fun testQuadrant4_YinYearYinPolarity_Forward() {
        // 阴年阴日（如乙年乙日上市）：顺排 FORWARD
        val ctx = createContext(
            firstDayPolarity = FirstDayPolarity.YIN,
            dayunDirection = "FORWARD",
        )
        // 验证：即使首日为阴，只要大运推步事实为 FORWARD，文案层绝不误判为 REVERSE！
        assertTrue(OfflineCopyRuleEvaluator.matchesTrigger(forwardRule.triggerDsl, ctx))
        assertFalse(OfflineCopyRuleEvaluator.matchesTrigger(reverseRule.triggerDsl, ctx))
    }

    @Test
    fun testBoundary_FlatPolarity_NeitherMatches() {
        // 平盘（首日涨跌幅为0）：方向未决，顺逆均不得命中
        val ctx = createContext(
            firstDayPolarity = FirstDayPolarity.FLAT,
            dayunDirection = "UNAVAILABLE_FLAT",
            dayunAvailability = AlgorithmAvailability.UNAVAILABLE,
        )
        assertFalse(OfflineCopyRuleEvaluator.matchesTrigger(forwardRule.triggerDsl, ctx))
        assertFalse(OfflineCopyRuleEvaluator.matchesTrigger(reverseRule.triggerDsl, ctx))
    }

    @Test
    fun testBoundary_MissingPolarity_NeitherMatches() {
        // 缺失（首日数据缺失）：顺逆均不得命中
        val ctx = createContext(
            firstDayPolarity = FirstDayPolarity.MISSING,
            dayunDirection = "UNAVAILABLE_MISSING",
            dayunAvailability = AlgorithmAvailability.UNAVAILABLE,
        )
        assertFalse(OfflineCopyRuleEvaluator.matchesTrigger(forwardRule.triggerDsl, ctx))
        assertFalse(OfflineCopyRuleEvaluator.matchesTrigger(reverseRule.triggerDsl, ctx))
    }

    @Test
    fun testBoundary_ConflictPolarity_NeitherMatches() {
        // 冲突：顺逆均不得命中
        val ctx = createContext(
            firstDayPolarity = FirstDayPolarity.CONFLICT,
            dayunDirection = "UNAVAILABLE_CONFLICT",
            dayunAvailability = AlgorithmAvailability.UNAVAILABLE,
        )
        assertFalse(OfflineCopyRuleEvaluator.matchesTrigger(forwardRule.triggerDsl, ctx))
        assertFalse(OfflineCopyRuleEvaluator.matchesTrigger(reverseRule.triggerDsl, ctx))
    }

    @Test
    fun testBoundary_BeforeStartAge_NeitherMatches() {
        // 起运前（无当前大运）：顺逆均不得命中
        val ctx = createContext(
            firstDayPolarity = FirstDayPolarity.YANG,
            dayunDirection = "FORWARD",
            dayunAvailability = AlgorithmAvailability.AVAILABLE,
            hasPeriod = false,
        )
        assertFalse(OfflineCopyRuleEvaluator.matchesTrigger(forwardRule.triggerDsl, ctx))
        assertFalse(OfflineCopyRuleEvaluator.matchesTrigger(reverseRule.triggerDsl, ctx))
    }
}
