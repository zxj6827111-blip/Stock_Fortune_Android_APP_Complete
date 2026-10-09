package com.stockfortune.app.domain.calculator

import com.stockfortune.app.data.entity.LuckCyclePeriodEntity
import com.stockfortune.app.data.entity.NatalRelationEntity
import com.stockfortune.app.data.entity.StockYongshenEntity
import com.stockfortune.app.domain.model.AlgorithmAvailability
import com.stockfortune.app.domain.model.CopyRuleDefinition
import com.stockfortune.app.domain.model.CopySection
import com.stockfortune.app.domain.model.FirstDayPolarity
import com.stockfortune.app.domain.model.FiveParagraphInterpretation
import com.stockfortune.app.domain.model.PreciseAdvancedNotice
import com.stockfortune.app.domain.model.ProductionGate
import com.stockfortune.app.domain.model.ReviewStatus
import com.stockfortune.app.domain.model.Strength
import com.stockfortune.app.domain.model.TenGod
import org.json.JSONObject

/**
 * 离线文案规则评估与触发匹配引擎（Rule Matcher Interface & Evaluator）。
 *
 * 安全门禁规范（根据 Phase 7 独立代码审查整改要求）：
 * 1. 严格离线计算，不调用网络与外部 LLM；
 * 2. 未识别的 trigger 条件严格返回 false，杜绝默认 true 漏洞；
 * 3. 无规则命中不得自动标记 APPROVED，严格维持 PENDING_REVIEW；
 * 4. productionGate 真实参与展示授权（正式发布包严禁未终审文案，内部测试预览允许候审标记）；
 * 5. 禁止展示的历史规则（isLegacyNoRender / PRODUCTION_BLOCKED）必须保持禁止；
 * 6. 缺少证据的高级规则必须正确降级阻断，不产生无依据断言；
 * 7. 模拟文案（MOCK 前缀 / AUDIT_ONLY）严禁进入正常股票分析结果；
 * 8. 规则优先级裁决：同段落且同冲突组（conflict_group）时，取 priority 最高者。
 */
object OfflineCopyRuleEvaluator {

    /** 评估输入上下文 */
    data class EvaluationContext(
        val stockId: Long,
        val stockCode: String,
        val year: Int,
        val month: Int,
        val dayStem: String = "",
        val monthStem: String = "",
        val monthBranch: String = "",
        val monthStemGod: TenGod,
        val monthBranchMainQiGod: TenGod,
        val strength: Strength,
        val firstDayPolarity: FirstDayPolarity = FirstDayPolarity.YANG,
        val dayunAvailability: AlgorithmAvailability = AlgorithmAvailability.AVAILABLE,
        val currentLuckPeriod: LuckCyclePeriodEntity? = null,
        val natalAvailability: AlgorithmAvailability = AlgorithmAvailability.AVAILABLE,
        val natalRelations: List<NatalRelationEntity> = emptyList(),
        val hitLiuhe: FortuneCopyEngine.LiuheHit? = null,
        val yongshenAvailability: AlgorithmAvailability = AlgorithmAvailability.AVAILABLE,
        val yongshen: StockYongshenEntity? = null,
        val isMockContext: Boolean = false,
        val isProductionBuild: Boolean = false,
    )

    /**
     * 对给定的规则列表进行过滤与冲突裁决，按段落聚合输出五段式文本。
     */
    fun evaluate(
        context: EvaluationContext,
        candidateRules: List<CopyRuleDefinition>,
    ): FiveParagraphInterpretation {
        // 1. 规则匹配过滤与安全门禁
        val matchedRules = candidateRules.filter { rule ->
            // 门禁 1：历史旧通用句（NA_ADVANCED_MISSING）仅供审计，绝对禁止渲染
            if (rule.isLegacyNoRender) return@filter false

            // 门禁 2：生产阻断规则绝对禁止渲染
            if (rule.productionGate == ProductionGate.PRODUCTION_BLOCKED) return@filter false

            // 门禁 3：模拟文案严禁进入正常股票分析结果
            if ((rule.ruleId.startsWith("MOCK") || rule.productionGate == ProductionGate.AUDIT_ONLY) && !context.isMockContext) {
                return@filter false
            }

            // 门禁 4：正式生产发布门禁校验
            if (context.isProductionBuild && rule.productionGate != ProductionGate.PRODUCTION_RELEASE) {
                return@filter false
            }

            // 门禁 5：页面级状态规则不作为五段式正文段落
            if (rule.section == CopySection.STATE) {
                return@filter false
            }

            // 门禁 6：缺少证据的高级规则必须正确降级阻断
            if (!hasRequiredEvidence(rule, context)) {
                return@filter false
            }

            // 门禁 7：触发条件匹配（未识别条件严格返回 false）
            matchesTrigger(rule.triggerDsl, context)
        }

        // 2. 按段落分组并按冲突组消解（取最高 priority）
        val sectionTexts = mutableMapOf<CopySection, String>()
        val hitRuleIds = mutableListOf<String>()

        listOf(
            CopySection.BASIS,
            CopySection.THEME,
            CopySection.CONTRADICTION,
            CopySection.BUSINESS,
            CopySection.SYNTHESIS,
        ).forEach { section ->
            val sectionRules = matchedRules.filter { it.section == section }
            if (sectionRules.isNotEmpty()) {
                val resolvedRules = sectionRules
                    .groupBy { it.conflictGroup }
                    .values
                    .map { group -> group.maxByOrNull { it.priority }!! }
                    .sortedByDescending { it.priority }

                val text = resolvedRules.joinToString(" ") { substitutePlaceholders(it.text, context) }
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

        // 4. 判定整体审核状态：
        // 门禁规定：无规则命中不得自动标记 APPROVED！只有命中规则且全部为 APPROVED 时才为 APPROVED。
        val overallReviewStatus = if (matchedRules.isEmpty()) {
            ReviewStatus.PENDING_REVIEW
        } else if (matchedRules.any { it.reviewStatus != ReviewStatus.APPROVED }) {
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
     * 检查高级规则所依赖的证据链条是否完备。缺少证据时严禁命中，强制降级。
     */
    private fun hasRequiredEvidence(rule: CopyRuleDefinition, ctx: EvaluationContext): Boolean {
        // 大运模块规则必须具有有效的大运信息
        if (rule.module == "大运" || rule.conflictGroup.startsWith("DY_")) {
            if (ctx.dayunAvailability != AlgorithmAvailability.AVAILABLE || ctx.currentLuckPeriod == null) {
                return false
            }
        }
        // 六合模块规则必须具有实际命中的六合关系
        if (rule.module == "六合" || rule.conflictGroup.startsWith("LH_")) {
            if (ctx.hitLiuhe == null) {
                return false
            }
        }
        // 喜用模块规则必须具有喜用实体
        if (rule.module == "喜用" || rule.conflictGroup.startsWith("YS_")) {
            if (ctx.yongshenAvailability != AlgorithmAvailability.AVAILABLE || ctx.yongshen == null) {
                return false
            }
        }
        return true
    }

    /**
     * 替换规则正文中的占位符变量
     */
    fun substitutePlaceholders(text: String, ctx: EvaluationContext): String {
        var res = text
        val p = ctx.currentLuckPeriod
        if (p != null) {
            res = res.replace("{dayun.start_at}", p.startDate)
                .replace("{dayun.start_age}", p.startAge.toString())
                .replace("{dayun.start_year}", p.startYear.toString())
                .replace("{dayun.end_year}", p.endYear.toString())
                .replace("{dayun.period_ganzhi}", p.ganzhi)
                .replace("{dayun.period_start}", p.startDate)
                .replace("{dayun.period_end}", p.endDate)
                .replace("{dayun.ganzhi}", p.ganzhi)
                .replace("{dayun.stem}", p.stem)
                .replace("{dayun.branch}", p.branch)
            if (ctx.dayStem.isNotBlank()) {
                val dayunGod = TenGodCalculator.tenGod(ctx.dayStem, p.stem)
                res = res.replace("{dayun.stem_god}", dayunGod.cn)
            }
        }
        val lh = ctx.hitLiuhe
        if (lh != null) {
            res = res.replace("{relation.pair}", lh.pairName)
                .replace("{relation.scope}", lh.scope)
                .replace("{relation.branch_a}", lh.branchA)
                .replace("{relation.branch_b}", lh.branchB)
                .replace("{relation.position_a}", lh.posA)
                .replace("{relation.position_b}", lh.posB)
                .replace("{relation.transformed_element}", "待核验")
                .replace("{relation.valid_from}", p?.startDate ?: "当月")
                .replace("{relation.valid_to}", p?.endDate ?: "当月")
        }
        if (ctx.monthStem.isNotBlank()) {
            res = res.replace("{flow.month_stem}", ctx.monthStem)
        }
        if (ctx.monthBranch.isNotBlank()) {
            res = res.replace("{flow.month_branch}", ctx.monthBranch)
        }
        if (res.contains("{flow.element}")) {
            val stemElem = if (ctx.monthStem.isNotBlank()) TenGodCalculator.elementOf(ctx.monthStem) else ""
            val branchElem = if (ctx.monthBranch.isNotBlank()) TenGodCalculator.elementOfBranch(ctx.monthBranch) else ""
            val elem = if (res.contains("地支") || res.contains("本气")) branchElem else stemElem
            res = res.replace("{flow.element}", elem)
        }
        if (res.contains("{yongshen.method}")) {
            res = res.replace("{yongshen.method}", "扶抑")
        }
        return res
    }

    /**
     * 触发器匹配总入口（支持 DSL 与 JSON 两种触发条件格式）。
     * 未识别的 trigger 条件严格返回 false，杜绝默认 true 漏洞。
     */
    fun matchesTrigger(dsl: String, ctx: EvaluationContext): Boolean {
        val trimmed = dsl.trim()
        if (trimmed.isEmpty()) return false
        return if (trimmed.startsWith("{")) {
            matchesJsonTrigger(trimmed, ctx)
        } else {
            matchesDslTrigger(trimmed, ctx)
        }
    }

    private fun matchesDslTrigger(dsl: String, ctx: EvaluationContext): Boolean {
        val conditions = dsl.split("&&").map { it.trim() }
        if (conditions.isEmpty()) return false
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
            cond.startsWith("pair.god_group == ") -> {
                val expected = cond.substringAfter("pair.god_group == ").trim().trim('"', '\'')
                derivePairGodGroup(ctx.monthStemGod, ctx.monthBranchMainQiGod) == expected
            }
            cond.startsWith("first_day_polarity.state == ") -> {
                val expected = cond.substringAfter("first_day_polarity.state == ").trim().trim('"', '\'')
                ctx.firstDayPolarity.name.equals(expected, ignoreCase = true) ||
                    ctx.firstDayPolarity.code.equals(expected, ignoreCase = true) ||
                    (expected.equals("UNKNOWN", ignoreCase = true) && ctx.firstDayPolarity == FirstDayPolarity.MISSING)
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
            // 未识别的 trigger 条件严格返回 false，杜绝默认 true 漏洞！
            else -> false
        }
    }

    private fun derivePairGodGroup(sg: TenGod, bg: TenGod): String {
        if (sg == bg) return "同神"
        val sameGroup = when (sg) {
            TenGod.BI_JIAN, TenGod.JIE_CAI -> bg in setOf(TenGod.BI_JIAN, TenGod.JIE_CAI)
            TenGod.SHI_SHEN, TenGod.SHANG_GUAN -> bg in setOf(TenGod.SHI_SHEN, TenGod.SHANG_GUAN)
            TenGod.PIAN_CAI, TenGod.ZHENG_CAI -> bg in setOf(TenGod.PIAN_CAI, TenGod.ZHENG_CAI)
            TenGod.QI_SHA, TenGod.ZHENG_GUAN -> bg in setOf(TenGod.QI_SHA, TenGod.ZHENG_GUAN)
            TenGod.PIAN_YIN, TenGod.ZHENG_YIN -> bg in setOf(TenGod.PIAN_YIN, TenGod.ZHENG_YIN)
        }
        return if (sameGroup) "同五行异神" else "不同类十神"
    }

    private fun matchesJsonTrigger(jsonStr: String, ctx: EvaluationContext): Boolean {
        val obj = try {
            JSONObject(jsonStr)
        } catch (_: Throwable) {
            return false
        }
        val keys = try {
            obj.keys()
        } catch (_: Throwable) {
            null
        } ?: return false
        if (!keys.hasNext()) return false

        while (keys.hasNext()) {
            val key = keys.next()
            val matches = when (key) {
                "dayun.availability" -> {
                    ctx.dayunAvailability.code.equals(obj.getString(key), ignoreCase = true)
                }
                "dayun.current_period_verified" -> {
                    val exp = obj.getBoolean(key)
                    (ctx.currentLuckPeriod != null && ctx.dayunAvailability == AlgorithmAvailability.AVAILABLE) == exp
                }
                "dayun.start_verified" -> {
                    val exp = obj.getBoolean(key)
                    (ctx.currentLuckPeriod != null) == exp
                }
                "dayun.direction" -> {
                    val exp = obj.getString(key)
                    val dir = if (ctx.firstDayPolarity == FirstDayPolarity.YANG) "FORWARD" else "REVERSE"
                    dir.equals(exp, ignoreCase = true)
                }
                "dayun.natal_relation_group" -> {
                    val exp = obj.getString(key)
                    if (ctx.currentLuckPeriod == null || ctx.dayStem.isBlank()) false
                    else {
                        val dayunStemGod = TenGodCalculator.tenGod(ctx.dayStem, ctx.currentLuckPeriod.stem)
                        val rel = derivePairGodGroup(ctx.monthStemGod, dayunStemGod)
                        when (exp) {
                            "同类" -> rel == "同神"
                            "五行同类异神" -> rel == "同五行异神"
                            "异类" -> rel == "不同类十神"
                            else -> false
                        }
                    }
                }
                "dayun.stem_god" -> {
                    if (ctx.currentLuckPeriod == null || ctx.dayStem.isBlank()) false
                    else {
                        val dayunStemGod = TenGodCalculator.tenGod(ctx.dayStem, ctx.currentLuckPeriod.stem)
                        dayunStemGod.cn == obj.getString(key)
                    }
                }
                "natal.strength_state" -> {
                    ctx.strength.cn == obj.getString(key)
                }
                "relation.pair" -> {
                    ctx.hitLiuhe?.pairName == obj.getString(key)
                }
                "relation.scope" -> {
                    ctx.hitLiuhe?.scope == obj.getString(key)
                }
                "relation.type" -> {
                    obj.getString(key) == "LIUHE" && ctx.hitLiuhe != null
                }
                "relation.transformation_status" -> {
                    // V1.3 契约规定：支对成立不等于合化成功，状态均为 NOT_VERIFIED
                    obj.getString(key) == "NOT_VERIFIED" && ctx.hitLiuhe != null
                }
                "relation.multiplicity" -> {
                    obj.getString(key) == "MULTIPLE" && ctx.natalRelations.size > 1
                }
                "relation.other_events_verified" -> {
                    val exp = obj.getBoolean(key)
                    ctx.natalRelations.isNotEmpty() == exp
                }
                "relation_scan.availability" -> {
                    ctx.natalAvailability.code.equals(obj.getString(key), ignoreCase = true)
                }
                "relation_scan.completeness" -> {
                    val exp = obj.getString(key)
                    if (exp == "COMPLETE") ctx.natalAvailability == AlgorithmAvailability.AVAILABLE else false
                }
                "relation_scan.result" -> {
                    val exp = obj.getString(key)
                    if (exp == "NONE") ctx.natalRelations.isEmpty() else false
                }
                "yongshen.availability" -> {
                    ctx.yongshenAvailability.code.equals(obj.getString(key), ignoreCase = true)
                }
                "yongshen.method" -> {
                    val exp = obj.getString(key)
                    when (exp) {
                        "FUYI" -> ctx.yongshen != null && (ctx.yongshen.status == "confirmed" || ctx.yongshen.status == "candidate")
                        "TIAOHOU" -> ctx.yongshen != null && !ctx.yongshen.tiaohouNote.isNullOrBlank()
                        else -> false
                    }
                }
                "yongshen.method_verified" -> {
                    val exp = obj.getBoolean(key)
                    (ctx.yongshen?.status == "confirmed") == exp
                }
                "flow.loc" -> {
                    val loc = obj.getString(key)
                    val expectedRole = obj.optString("yongshen.role")
                    if (expectedRole.isNullOrEmpty() || ctx.yongshen == null) false
                    else {
                        val elem = if (loc == "干") {
                            TenGodCalculator.elementOf(ctx.monthStem)
                        } else {
                            TenGodCalculator.elementOfBranch(ctx.monthBranch)
                        }
                        val ys = ctx.yongshen
                        val actualRole = when {
                            ys.yongShen.contains(elem) -> "用"
                            ys.xiShen.contains(elem) -> "喜"
                            ys.jiShen.contains(elem) -> "制约"
                            ys.chouShen.contains(elem) -> "消耗"
                            else -> "中性"
                        }
                        actualRole == expectedRole
                    }
                }
                "yongshen.role" -> true // 已在 flow.loc 中联合处理
                "yongshen.role_comparison" -> {
                    if (ctx.yongshen == null) false
                    else {
                        val stemElem = TenGodCalculator.elementOf(ctx.monthStem)
                        val branchElem = TenGodCalculator.elementOfBranch(ctx.monthBranch)
                        val ys = ctx.yongshen
                        val stemRole = if (ys.yongShen.contains(stemElem)) "用" else if (ys.xiShen.contains(stemElem)) "喜" else "其他"
                        val branchRole = if (ys.yongShen.contains(branchElem)) "用" else if (ys.xiShen.contains(branchElem)) "喜" else "其他"
                        val comp = if (stemRole == branchRole) "SAME" else "DIFFERENT"
                        comp == obj.getString(key)
                    }
                }
                // 未识别的 JSON 属性严格返回 false，杜绝未知条件放行！
                else -> false
            }
            if (!matches) return false
        }
        return true
    }
}
