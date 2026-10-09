package com.stockfortune.app.domain.calculator

import com.stockfortune.app.domain.model.AlgorithmAvailability
import com.stockfortune.app.domain.model.CopyRuleDefinition
import com.stockfortune.app.domain.model.CopySection
import com.stockfortune.app.domain.model.FiveParagraphInterpretation
import com.stockfortune.app.domain.model.PreciseAdvancedNotice
import com.stockfortune.app.domain.model.ProductionGate
import com.stockfortune.app.domain.model.ReviewStatus
import com.stockfortune.app.domain.model.Strength
import com.stockfortune.app.domain.model.TenGod

/**
 * 离线文案规则评估与触发匹配引擎（Rule Matcher Interface & Evaluator）。
 *
 * 实施约束：
 * 1. 严格离线计算，不调用网络与外部 LLM；
 * 2. 规则优先级裁决：同段落且同冲突组（conflict_group）时，取 priority 最高者；
 * 3. 严格遵循 E-01/E-02 勘误：历史旧通用句（NA_ADVANCED_MISSING）仅供审计，不予最终渲染；
 * 4. 分项提示采用 SLOT_PRECISE_ADVANCED_STATUS：三项皆可用时返回空串，不进行连带否定。
 */
object OfflineCopyRuleEvaluator {

    /** 评估输入上下文 */
    data class EvaluationContext(
        val stockId: Long,
        val stockCode: String,
        val year: Int,
        val month: Int,
        val monthStemGod: TenGod,
        val monthBranchMainQiGod: TenGod,
        val strength: Strength,
        val dayunAvailability: AlgorithmAvailability = AlgorithmAvailability.AVAILABLE,
        val natalAvailability: AlgorithmAvailability = AlgorithmAvailability.AVAILABLE,
        val yongshenAvailability: AlgorithmAvailability = AlgorithmAvailability.AVAILABLE,
        val isMockContext: Boolean = false,
    )

    /**
     * 对给定的规则列表进行过滤与冲突裁决，按段落聚合输出五段式文本。
     */
    fun evaluate(
        context: EvaluationContext,
        candidateRules: List<CopyRuleDefinition>,
    ): FiveParagraphInterpretation {
        // 1. 规则匹配过滤
        val matchedRules = candidateRules.filter { rule ->
            if (rule.isLegacyNoRender) return@filter false
            matchesTrigger(rule.triggerDsl, context)
        }

        // 2. 按段落分组并按冲突组消解（取最高 priority）
        val sectionTexts = mutableMapOf<CopySection, String>()
        val hitRuleIds = mutableListOf<String>()

        CopySection.entries.forEach { section ->
            val sectionRules = matchedRules.filter { it.section == section }
            if (sectionRules.isNotEmpty()) {
                // 按 conflict_group 分组，每组选 priority 最高的一条
                val resolvedRules = sectionRules
                    .groupBy { it.conflictGroup }
                    .values
                    .map { group -> group.maxByOrNull { it.priority }!! }
                    .sortedByDescending { it.priority }

                val text = resolvedRules.joinToString(" ") { it.text }
                sectionTexts[section] = text
                hitRuleIds.addAll(resolvedRules.map { it.ruleId })
            }
        }

        // 3. 计算独立分项提示
        val notice = PreciseAdvancedNotice.formatNotice(
            context.dayunAvailability,
            context.natalAvailability,
            context.yongshenAvailability,
        )

        // 4. 判定整体审核状态（只要有一条待人工终审，整体即为待终审）
        val overallReviewStatus = if (matchedRules.any { it.reviewStatus != ReviewStatus.APPROVED }) {
            ReviewStatus.PENDING_REVIEW
        } else {
            ReviewStatus.APPROVED
        }

        return FiveParagraphInterpretation(
            stockId = context.stockId,
            stockCode = context.stockCode,
            year = context.year,
            month = context.month,
            basisText = sectionTexts[CopySection.BASIS] ?: "流月命理依据正在核算",
            themeText = sectionTexts[CopySection.THEME] ?: "流月十神主题正在配置",
            contradictionText = sectionTexts[CopySection.CONTRADICTION] ?: "流月潜在矛盾正在核对",
            businessText = sectionTexts[CopySection.BUSINESS] ?: "企业经营观察维度正在匹配",
            synthesisText = sectionTexts[CopySection.SYNTHESIS] ?: "流月综合解释正在归纳",
            preciseAdvancedNotice = notice,
            hitRuleIds = hitRuleIds,
            reviewStatus = overallReviewStatus,
            isMock = context.isMockContext,
        )
    }

    /**
     * 基础 DSL 条件匹配器（支持月干十神、月支十神、强弱及高级状态）。
     */
    fun matchesTrigger(dsl: String, ctx: EvaluationContext): Boolean {
        if (dsl.isBlank()) return true
        val conditions = dsl.split("&&").map { it.trim() }
        return conditions.all { cond -> evaluateSingleCondition(cond, ctx) }
    }

    private fun evaluateSingleCondition(cond: String, ctx: EvaluationContext): Boolean {
        return when {
            cond.startsWith("month.stem_god == ") -> {
                val expected = cond.substringAfter("month.stem_god == ").trim().trim('"', '\'')
                ctx.monthStemGod.cn == expected
            }
            cond.startsWith("month.branch_main_qi_god == ") -> {
                val expected = cond.substringAfter("month.branch_main_qi_god == ").trim().trim('"', '\'')
                ctx.monthBranchMainQiGod.cn == expected
            }
            cond.startsWith("natal.strength_state == ") -> {
                val expected = cond.substringAfter("natal.strength_state == ").trim().trim('"', '\'')
                ctx.strength.cn == expected
            }
            cond.startsWith("dayun.availability == ") -> {
                val expected = cond.substringAfter("dayun.availability == ").trim().trim('"', '\'')
                ctx.dayunAvailability.code.equals(expected, ignoreCase = true)
            }
            cond.startsWith("natal_relation.availability == ") -> {
                val expected = cond.substringAfter("natal_relation.availability == ").trim().trim('"', '\'')
                ctx.natalAvailability.code.equals(expected, ignoreCase = true)
            }
            cond.startsWith("yongshen.availability == ") -> {
                val expected = cond.substringAfter("yongshen.availability == ").trim().trim('"', '\'')
                ctx.yongshenAvailability.code.equals(expected, ignoreCase = true)
            }
            else -> true
        }
    }
}
