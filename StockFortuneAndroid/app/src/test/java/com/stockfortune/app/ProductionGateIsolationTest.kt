package com.stockfortune.app

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
import org.junit.Assert.assertEquals
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * 生产发布门禁 (Production Gate) 与内部审核预览双轨运行测试。
 * 验证：
 * 1. 内部审核构建（isProductionBuild = false）允许按规定预览候审文案供专家人工审核；
 * 2. 正式发布构建（isProductionBuild = true）严格依据 reviewStatus 和 productionGate 双重门禁拦截，未批准文案绝不外溢；
 * 3. 没有合格正式规则时明确输出受控不可用状态，系统绝不伪造五段式内容。
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class ProductionGateIsolationTest {

    private val pendingRule = CopyRuleDefinition(
        ruleId = "BAS_STEM_ZHENGCAI",
        section = CopySection.BASIS,
        triggerDsl = """month.stem_god == "正财"""",
        priority = 100,
        conflictGroup = "STEM_GOD",
        evidenceKeys = listOf("month.stem_god"),
        text = "流月天干对应正财，仅表示该位置的十神分类；不等于公司已经出现某类经营事件。",
        ruleVersion = "1.3.0",
        reviewStatus = ReviewStatus.PENDING_REVIEW,
        productionGate = ProductionGate.CANDIDATE_ONLY,
        isLegacyNoRender = false,
        module = "基础",
    )

    private val approvedRule = CopyRuleDefinition(
        ruleId = "BAS_APPROVED_TEST",
        section = CopySection.BASIS,
        triggerDsl = """month.stem_god == "正财"""",
        priority = 120,
        conflictGroup = "STEM_GOD",
        evidenceKeys = listOf("month.stem_god"),
        text = "经法务与学术专家终审批准的标准正式文案正文。",
        ruleVersion = "1.3.0",
        reviewStatus = ReviewStatus.APPROVED,
        productionGate = ProductionGate.PRODUCTION_RELEASE,
        isLegacyNoRender = false,
        module = "基础",
    )

    private fun createSampleContext(isProduction: Boolean): OfflineCopyRuleEvaluator.EvaluationContext {
        return OfflineCopyRuleEvaluator.EvaluationContext(
            stockId = 1,
            stockCode = "600519.SH",
            year = 2026,
            month = 1,
            dayStem = "甲",
            monthStem = "己",
            monthBranch = "丑",
            monthStemGod = TenGod.ZHENG_CAI,
            monthBranchMainQiGod = TenGod.ZHENG_CAI,
            strength = Strength.BALANCED,
            firstDayPolarity = FirstDayPolarity.YANG,
            isProductionBuild = isProduction,
        )
    }

    @Test
    fun testInternalAuditMode_AllowsPendingReviewPreview() {
        // 内部审核模式：允许预览候审正文供人工审阅，状态保持 PENDING_REVIEW
        val ctx = createSampleContext(isProduction = false)
        val result = OfflineCopyRuleEvaluator.evaluate(ctx, listOf(pendingRule))

        assertEquals(ReviewStatus.PENDING_REVIEW, result.reviewStatus)
        assertTrue("内部预览允许查看待审候选文案正文", result.basisText.contains("流月天干对应正财"))
        assertTrue("规则 ID 应能准确追溯", result.hitRuleIds.contains("BAS_STEM_ZHENGCAI"))
    }

    @Test
    fun testProductionReleaseMode_StrictlyBlocksPendingRules() {
        // 生产正式模式：待审规则必须被彻底拦截，不得流入正式输出
        val ctx = createSampleContext(isProduction = true)
        val result = OfflineCopyRuleEvaluator.evaluate(ctx, listOf(pendingRule))

        assertEquals(ReviewStatus.PENDING_REVIEW, result.reviewStatus)
        assertFalse("正式生产环境严禁外溢待审候选正文", result.basisText.contains("流月天干对应正财"))
        assertTrue("正式生产环境应输出受控拦截保护声明", result.basisText.contains("【候审保护】"))
        assertTrue("命中规则列表应为空（未获正式发布授权）", result.hitRuleIds.isEmpty())
    }

    @Test
    fun testProductionReleaseMode_RendersApprovedRulesOnly() {
        // 生产正式模式：若存在已批准规则，则正常放行
        val ctx = createSampleContext(isProduction = true)
        val result = OfflineCopyRuleEvaluator.evaluate(ctx, listOf(approvedRule))

        assertEquals(ReviewStatus.APPROVED, result.reviewStatus)
        assertTrue(result.basisText.contains("经法务与学术专家终审批准"))
        assertTrue(result.hitRuleIds.contains("BAS_APPROVED_TEST"))
    }

    @Test
    fun testControlledUnavailable_WhenNoValidRules() {
        // 当没有合格规则匹配时，系统明确受控不可用，拒绝伪造五段式内容
        val ctx = createSampleContext(isProduction = false)
        val result = OfflineCopyRuleEvaluator.evaluate(ctx, emptyList())

        assertEquals(ReviewStatus.PENDING_REVIEW, result.reviewStatus)
        assertTrue(result.basisText.contains("【受控不可用】"))
        assertTrue(result.synthesisText.contains("系统拒绝伪造输出"))
        assertTrue(result.hitRuleIds.isEmpty())
    }
}
