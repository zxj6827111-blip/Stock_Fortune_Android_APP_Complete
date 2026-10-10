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
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * Phase 4 离线文案集成与五段式契约回归测试。
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
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

    @Test
    fun testUnknownTriggerConditionNeverDefaultsToTrue() {
        val ctx = OfflineCopyRuleEvaluator.EvaluationContext(
            stockId = 1L,
            stockCode = "000001",
            year = 2026,
            month = 6,
            monthStemGod = TenGod.BI_JIAN,
            monthBranchMainQiGod = TenGod.ZHENG_CAI,
            strength = Strength.STRONG,
        )

        // 1. 未识别的 DSL 条件
        assertFalse("未知 DSL 字段严禁默认为 true", OfflineCopyRuleEvaluator.matchesTrigger("unknown_prop.foo == \"bar\"", ctx))
        assertFalse("未知比较符严禁默认为 true", OfflineCopyRuleEvaluator.matchesTrigger("month.stem_god != \"比肩\"", ctx))

        // 2. 未识别的 JSON 条件
        assertFalse("未知 JSON 属性严禁默认为 true", OfflineCopyRuleEvaluator.matchesTrigger("{\"unknown.metric\": 123}", ctx))
        assertFalse("非法 JSON 语法严禁默认为 true", OfflineCopyRuleEvaluator.matchesTrigger("{invalid_json: true}", ctx))

        // 3. 空条件
        assertFalse("空条件必须返回 false", OfflineCopyRuleEvaluator.matchesTrigger("", ctx))
        assertFalse("空白字符必须返回 false", OfflineCopyRuleEvaluator.matchesTrigger("   ", ctx))

        // 4. 未知条件规则在评估中绝对不命中
        val unknownRule = CopyRuleDefinition(
            ruleId = "UNKNOWN_RULE",
            section = CopySection.THEME,
            triggerDsl = "unknown_condition == \"active\"",
            priority = 100,
            conflictGroup = "THEME_UNKNOWN",
            evidenceKeys = listOf("unknown"),
            text = "不应出现的未知规则文案",
            ruleVersion = "v1.3",
        )
        val result = OfflineCopyRuleEvaluator.evaluate(ctx, listOf(unknownRule))
        assertFalse(result.hitRuleIds.contains("UNKNOWN_RULE"))
        assertFalse(result.themeText.contains("不应出现的未知规则文案"))
    }

    @Test
    fun testNoRuleMatchMustRemainPendingReview() {
        val ctx = OfflineCopyRuleEvaluator.EvaluationContext(
            stockId = 1L,
            stockCode = "000001",
            year = 2026,
            month = 6,
            monthStemGod = TenGod.BI_JIAN,
            monthBranchMainQiGod = TenGod.ZHENG_CAI,
            strength = Strength.STRONG,
        )

        // 空规则集
        val resultEmpty = OfflineCopyRuleEvaluator.evaluate(ctx, emptyList())
        assertEquals("无规则命中时审核状态必须为 PENDING_REVIEW", ReviewStatus.PENDING_REVIEW, resultEmpty.reviewStatus)
        assertFalse("未审核文案禁止正式生产发布", resultEmpty.reviewStatus.isProductionAllowed)

        // 规则全部未命中
        val unmatchedRule = CopyRuleDefinition(
            ruleId = "UNMATCHED_RULE",
            section = CopySection.THEME,
            triggerDsl = "month.stem_god == \"七杀\"",
            priority = 100,
            conflictGroup = "THEME_MAIN",
            evidenceKeys = listOf("month.stem_god"),
            text = "七杀文案",
            ruleVersion = "v1.3",
            reviewStatus = ReviewStatus.APPROVED, // 即使规则定义本身写着 APPROVED，未命中整体也不得标为 APPROVED
        )
        val resultUnmatched = OfflineCopyRuleEvaluator.evaluate(ctx, listOf(unmatchedRule))
        assertEquals("无规则命中时整体绝对不得标记为 APPROVED", ReviewStatus.PENDING_REVIEW, resultUnmatched.reviewStatus)
    }

    @Test
    fun testProductionGateBlocksCandidateInProductionBuild() {
        val prodCtx = OfflineCopyRuleEvaluator.EvaluationContext(
            stockId = 1L,
            stockCode = "000001",
            year = 2026,
            month = 6,
            monthStemGod = TenGod.BI_JIAN,
            monthBranchMainQiGod = TenGod.ZHENG_CAI,
            strength = Strength.STRONG,
            isProductionBuild = true, // 正式生产发布构建
        )

        val candidateRule = CopyRuleDefinition(
            ruleId = "CANDIDATE_RULE",
            section = CopySection.THEME,
            triggerDsl = "month.stem_god == \"比肩\"",
            priority = 100,
            conflictGroup = "THEME_MAIN",
            evidenceKeys = listOf("month.stem_god"),
            text = "候审文案正文",
            ruleVersion = "v1.3",
            productionGate = ProductionGate.CANDIDATE_ONLY,
        )

        val blockedRule = CopyRuleDefinition(
            ruleId = "BLOCKED_RULE",
            section = CopySection.THEME,
            triggerDsl = "month.stem_god == \"比肩\"",
            priority = 90,
            conflictGroup = "THEME_MAIN",
            evidenceKeys = listOf("month.stem_god"),
            text = "生产阻断文案",
            ruleVersion = "v1.3",
            productionGate = ProductionGate.PRODUCTION_BLOCKED,
        )

        val releaseRule = CopyRuleDefinition(
            ruleId = "RELEASE_RULE",
            section = CopySection.THEME,
            triggerDsl = "month.stem_god == \"比肩\"",
            priority = 80,
            conflictGroup = "THEME_MAIN",
            evidenceKeys = listOf("month.stem_god"),
            text = "正式发布合格文案",
            ruleVersion = "v1.3",
            productionGate = ProductionGate.PRODUCTION_RELEASE,
            reviewStatus = ReviewStatus.APPROVED,
        )

        // 在生产构建中，候审和阻断规则被拦截，仅正式发布规则可输出
        val prodResult = OfflineCopyRuleEvaluator.evaluate(prodCtx, listOf(candidateRule, blockedRule, releaseRule))
        assertFalse("生产环境严禁输出候审文案", prodResult.hitRuleIds.contains("CANDIDATE_RULE"))
        assertFalse("生产环境严禁输出阻断文案", prodResult.hitRuleIds.contains("BLOCKED_RULE"))
        assertTrue("生产环境仅允许输出正式发布规则", prodResult.hitRuleIds.contains("RELEASE_RULE"))
        assertEquals("正式发布合格文案", prodResult.themeText)

        // 在内部测试构建 (isProductionBuild = false) 中，候审规则可供内部预览
        val devCtx = prodCtx.copy(isProductionBuild = false)
        val devResult = OfflineCopyRuleEvaluator.evaluate(devCtx, listOf(candidateRule, releaseRule))
        assertTrue("内部预览允许输出候审文案（按优先级最高者）", devResult.hitRuleIds.contains("CANDIDATE_RULE"))
        assertEquals("候审文案正文", devResult.themeText)
    }

    @Test
    fun testMockRulesIsolatedFromNormalContext() {
        val normalCtx = OfflineCopyRuleEvaluator.EvaluationContext(
            stockId = 1L,
            stockCode = "000001",
            year = 2026,
            month = 6,
            monthStemGod = TenGod.BI_JIAN,
            monthBranchMainQiGod = TenGod.ZHENG_CAI,
            strength = Strength.STRONG,
            isMockContext = false, // 正常股票上下文
        )

        val mockRule = CopyRuleDefinition(
            ruleId = "MOCK_TEST_RULE",
            section = CopySection.THEME,
            triggerDsl = "month.stem_god == \"比肩\"",
            priority = 200,
            conflictGroup = "THEME_MAIN",
            evidenceKeys = listOf("month.stem_god"),
            text = "模拟文案不得进入正常分析结果",
            ruleVersion = "v1.3",
        )

        val auditRule = CopyRuleDefinition(
            ruleId = "AUDIT_ONLY_RULE",
            section = CopySection.THEME,
            triggerDsl = "month.stem_god == \"比肩\"",
            priority = 150,
            conflictGroup = "THEME_MAIN",
            evidenceKeys = listOf("month.stem_god"),
            text = "仅供审计文案不得进入正常分析结果",
            ruleVersion = "v1.3",
            productionGate = ProductionGate.AUDIT_ONLY,
        )

        val normalResult = OfflineCopyRuleEvaluator.evaluate(normalCtx, listOf(mockRule, auditRule))
        assertFalse("正常股票严禁命中 MOCK 前缀规则", normalResult.hitRuleIds.contains("MOCK_TEST_RULE"))
        assertFalse("正常股票严禁命中 AUDIT_ONLY 规则", normalResult.hitRuleIds.contains("AUDIT_ONLY_RULE"))

        // 在显式标记为 mock 的上下文测试环境中允许
        val mockCtx = normalCtx.copy(isMockContext = true)
        val mockResult = OfflineCopyRuleEvaluator.evaluate(mockCtx, listOf(mockRule))
        assertTrue("Mock 环境允许测试 MOCK 规则", mockResult.hitRuleIds.contains("MOCK_TEST_RULE"))
    }

    @Test
    fun testAdvancedRulesDegradeWhenEvidenceMissing() {
        // 缺少大运、六合、喜用证据
        val missingEvidenceCtx = OfflineCopyRuleEvaluator.EvaluationContext(
            stockId = 1L,
            stockCode = "000001",
            year = 2026,
            month = 6,
            monthStemGod = TenGod.BI_JIAN,
            monthBranchMainQiGod = TenGod.ZHENG_CAI,
            strength = Strength.STRONG,
            dayunAvailability = AlgorithmAvailability.UNAVAILABLE,
            currentLuckPeriod = null,
            hitLiuhe = null,
            yongshenAvailability = AlgorithmAvailability.UNAVAILABLE,
            yongshen = null,
        )

        val dayunRule = CopyRuleDefinition(
            ruleId = "ADV_DY_TEST",
            section = CopySection.SYNTHESIS,
            triggerDsl = "{\"dayun.current_period_verified\": true}",
            priority = 100,
            conflictGroup = "DY_GOD_STRENGTH",
            evidenceKeys = listOf("dayun.period_ganzhi"),
            text = "大运综合解读",
            ruleVersion = "v1.3",
            module = "大运",
        )

        val liuheRule = CopyRuleDefinition(
            ruleId = "ADV_LH_TEST",
            section = CopySection.CONTRADICTION,
            triggerDsl = "{\"relation.type\": \"LIUHE\"}",
            priority = 100,
            conflictGroup = "LH_SCOPE_EVENT",
            evidenceKeys = listOf("relation.pair"),
            text = "六合潜在矛盾",
            ruleVersion = "v1.3",
            module = "六合",
        )

        val yongshenRule = CopyRuleDefinition(
            ruleId = "ADV_YS_TEST",
            section = CopySection.SYNTHESIS,
            triggerDsl = "{\"flow.loc\": \"干\", \"yongshen.role\": \"用\"}",
            priority = 100,
            conflictGroup = "YS_ROLE_STEM",
            evidenceKeys = listOf("yongshen.role"),
            text = "喜用综合解读",
            ruleVersion = "v1.3",
            module = "喜用",
        )

        val result = OfflineCopyRuleEvaluator.evaluate(missingEvidenceCtx, listOf(dayunRule, liuheRule, yongshenRule))
        assertFalse("缺少大运证据时严禁命中大运高级规则", result.hitRuleIds.contains("ADV_DY_TEST"))
        assertFalse("缺少六合证据时严禁命中六合高级规则", result.hitRuleIds.contains("ADV_LH_TEST"))
        assertFalse("缺少喜用证据时严禁命中喜用高级规则", result.hitRuleIds.contains("ADV_YS_TEST"))
    }
}

