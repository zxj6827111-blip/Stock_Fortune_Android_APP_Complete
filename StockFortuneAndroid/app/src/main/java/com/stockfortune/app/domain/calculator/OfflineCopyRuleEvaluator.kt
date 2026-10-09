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
 * 离线文案规则求值与安全门禁引擎。
 *
 * 核心安全门禁守则：
 * 1. 未识别的 trigger 条件严格返回 false，严禁默认 true；
 * 2. 生产正式环境必须同时通过 reviewStatus == APPROVED 与 productionGate == PRODUCTION_RELEASE 双重授权；
 * 3. 历史禁止规则（HIST_BENCHMARK_PROHIBITED / isLegacyNoRender）绝不渲染；
 * 4. 缺少证据的高级规则强制降级阻断，不产生虚假命中 ID；
 * 5. MOCK 规则与普通股票分析结果严格隔离；
 * 6. 大运方向（dayun.direction）由实际预计算的四象限推步事实传递，严禁根据首日阴阳重新推断；
 * 7. 六合时间范围（relation.valid_from / valid_to）依据真实原局/流月节气月/大运区间分别设定，严禁将十年大运用于流月六合。
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
        val dayunDirection: String? = null, // 来自 StockLuckCycleEntity 预计算事实 ("FORWARD" / "REVERSE")
        val natalAvailability: AlgorithmAvailability = AlgorithmAvailability.AVAILABLE,
        val natalRelations: List<NatalRelationEntity> = emptyList(),
        val hitLiuhe: FortuneCopyEngine.LiuheHit? = null,
        val flowMonthStartDate: String? = null,
        val flowMonthEndDate: String? = null,
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
        // 门禁：生产构建环境必须严格校验生产授权
        val matchedRules = candidateRules.filter { rule ->
            // 门禁 1：历史旧通用句（NA_ADVANCED_MISSING）仅供审计，绝对禁止渲染
            if (rule.isLegacyNoRender) return@filter false

            // 门禁 2：生产阻断规则绝对禁止渲染
            if (rule.productionGate == ProductionGate.PRODUCTION_BLOCKED) return@filter false

            // 门禁 3：显式历史禁止规则拦截
            if (rule.ruleId == "HIST_BENCHMARK_PROHIBITED") return@filter false

            // 门禁 4：模拟文案严禁进入正常股票分析结果
            if ((rule.ruleId.startsWith("MOCK") || rule.productionGate == ProductionGate.AUDIT_ONLY) && !context.isMockContext) {
                return@filter false
            }

            // 门禁 5：正式生产发布双重门禁（reviewStatus 和 productionGate 均需通过）
            if (context.isProductionBuild) {
                if (rule.reviewStatus != ReviewStatus.APPROVED || rule.productionGate != ProductionGate.PRODUCTION_RELEASE) {
                    return@filter false
                }
            }

            // 门禁 6：页面级状态规则不作为五段式正文段落
            if (rule.section == CopySection.STATE) {
                return@filter false
            }

            // 门禁 7：缺少证据的高级规则必须正确降级阻断
            if (!hasRequiredEvidence(rule, context)) {
                return@filter false
            }

            // 门禁 8：触发条件匹配（未识别条件严格返回 false）
            matchesTrigger(rule.triggerDsl, context)
        }

        // 若正式发布生产环境下没有任何过审规则（当前280条均为候审状态），明确输出受控拦截保护，不伪造五段式内容
        if (context.isProductionBuild && matchedRules.isEmpty()) {
            return FiveParagraphInterpretation(
                stockId = context.stockId,
                stockCode = context.stockCode,
                year = context.year,
                month = context.month,
                basisText = "【候审保护】流月命理依据文案处于待终审状态，正式商用环境严格阻断未过审内容展示。",
                themeText = "【候审保护】流月十神主题文案处于待终审状态，正式商用环境严格阻断未过审内容展示。",
                contradictionText = "【候审保护】流月潜在矛盾文案处于待终审状态，正式商用环境严格阻断未过审内容展示。",
                businessText = "【候审保护】企业经营观察文案处于待终审状态，正式商用环境严格阻断未过审内容展示。",
                synthesisText = "【候审保护】当前文案规则处于待专家终审状态（PENDING_REVIEW），正式商用生产环境严格阻断未过审内容展示。请在内部审核版本（Debug/Internal）中进行预览验收。",
                preciseAdvancedNotice = "【生产门禁受控拦截】待审文案禁止在正式发布版本外溢",
                hitRuleIds = emptyList(),
                reviewStatus = ReviewStatus.PENDING_REVIEW,
                isMock = context.isMockContext,
            )
        }

        // 若没有任何规则命中（内部预览模式下输入异常等），明确不可用，不允许伪造
        if (matchedRules.isEmpty()) {
            return FiveParagraphInterpretation(
                stockId = context.stockId,
                stockCode = context.stockCode,
                year = context.year,
                month = context.month,
                basisText = "【受控不可用】未命中匹配的流月命理依据规则。",
                themeText = "【受控不可用】未命中匹配的流月十神主题规则。",
                contradictionText = "【受控不可用】未命中匹配的流月潜在矛盾规则。",
                businessText = "【受控不可用】未命中匹配的企业经营观察规则。",
                synthesisText = "【受控不可用】当前输入未命中符合条件的有效规则，系统拒绝伪造输出。",
                preciseAdvancedNotice = "【规则未命中】受控不可用状态",
                hitRuleIds = emptyList(),
                reviewStatus = ReviewStatus.PENDING_REVIEW,
                isMock = context.isMockContext,
            )
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
            } else {
                sectionTexts[section] = "【受控不可用】该段落未命中有效规则。"
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
            basisText = sectionTexts[CopySection.BASIS] ?: "【受控不可用】流月命理依据未就绪",
            themeText = sectionTexts[CopySection.THEME] ?: "【受控不可用】流月十神主题未就绪",
            contradictionText = sectionTexts[CopySection.CONTRADICTION] ?: "【受控不可用】流月潜在矛盾未就绪",
            businessText = sectionTexts[CopySection.BUSINESS] ?: "【受控不可用】企业经营观察未就绪",
            synthesisText = sectionTexts[CopySection.SYNTHESIS] ?: "【受控不可用】流月综合解释未就绪",
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
        } else {
            res = res.replace("{dayun.start_at}", "未起运")
                .replace("{dayun.start_age}", "--")
                .replace("{dayun.start_year}", "--")
                .replace("{dayun.end_year}", "--")
                .replace("{dayun.period_ganzhi}", "无大运")
                .replace("{dayun.period_start}", "无")
                .replace("{dayun.period_end}", "无")
                .replace("{dayun.ganzhi}", "无大运")
                .replace("{dayun.stem}", "")
                .replace("{dayun.branch}", "")
                .replace("{dayun.stem_god}", "无")
        }

        // 六合时间范围精准替换：依据命中六合的实际范围分别设定，严禁将十年大运用于流月六合
        val lh = ctx.hitLiuhe
        if (lh != null) {
            res = res.replace("{relation.pair}", lh.pairName)
                .replace("{relation.scope}", lh.scope)
                .replace("{relation.branch_a}", lh.branchA)
                .replace("{relation.branch_b}", lh.branchB)
                .replace("{relation.position_a}", lh.posA)
                .replace("{relation.position_b}", lh.posB)
                .replace("{relation.transformed_element}", "待核验")
                .replace("{relation.valid_from}", lh.validFrom)
                .replace("{relation.valid_to}", lh.validTo)
        } else {
            res = res.replace("{relation.pair}", "未命中六合")
                .replace("{relation.scope}", "无")
                .replace("{relation.branch_a}", "")
                .replace("{relation.branch_b}", "")
                .replace("{relation.position_a}", "")
                .replace("{relation.position_b}", "")
                .replace("{relation.transformed_element}", "无")
                .replace("{relation.valid_from}", "无")
                .replace("{relation.valid_to}", "无")
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

        // 喜用占位符
        val ys = ctx.yongshen
        if (ys != null) {
            res = res.replace("{ys_status}", ys.status)
                .replace("{ys_elements}", ys.candidateElements ?: ys.yongShen ?: "")
                .replace("{yongshen.yong_shen}", ys.yongShen ?: "")
                .replace("{yongshen.xi_shen}", ys.xiShen ?: "")
                .replace("{yongshen.candidate_elements}", ys.candidateElements ?: "")
        }

        // 基础股票及流月变量
        res = res.replace("{stock_name}", ctx.stockCode)
            .replace("{stock_code}", ctx.stockCode)
            .replace("{year}", ctx.year.toString())
            .replace("{month}", ctx.month.toString())
            .replace("{month_ganzhi}", "${ctx.monthStem}${ctx.monthBranch}")
            .replace("{day_master}", ctx.dayStem)
            .replace("{day_master_strength}", ctx.strength.cn)

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
                // 修复：大运方向必须由真实预计算事实 dayunDirection 判定，禁止仅凭首日阴阳推测；平盘/缺失/冲突绝不命中
                "dayun.direction" -> {
                    val exp = obj.getString(key)
                    if (ctx.dayunAvailability != AlgorithmAvailability.AVAILABLE ||
                        ctx.firstDayPolarity == FirstDayPolarity.FLAT ||
                        ctx.firstDayPolarity == FirstDayPolarity.MISSING ||
                        ctx.firstDayPolarity == FirstDayPolarity.CONFLICT ||
                        ctx.currentLuckPeriod == null) {
                        false
                    } else {
                        val dir = ctx.dayunDirection
                        dir != null && dir.equals(exp, ignoreCase = true)
                    }
                }
                "dayun.natal_relation_group" -> {
                    val exp = obj.getString(key)
                    if (ctx.currentLuckPeriod == null || ctx.dayStem.isBlank()) false
                    else {
                        val dayunGod = TenGodCalculator.tenGod(ctx.dayStem, ctx.currentLuckPeriod.stem)
                        val relGroup = if (dayunGod == ctx.monthStemGod) "同类"
                        else if (TenGodCalculator.elementOf(ctx.currentLuckPeriod.stem) == TenGodCalculator.elementOf(ctx.monthStem)) "五行同类异神"
                        else "异类"
                        relGroup.equals(exp, ignoreCase = true)
                    }
                }
                "dayun.base_ten_god", "dayun.stem_god" -> {
                    val exp = obj.getString(key)
                    if (ctx.currentLuckPeriod == null || ctx.dayStem.isBlank()) false
                    else {
                        val dayunGod = TenGodCalculator.tenGod(ctx.dayStem, ctx.currentLuckPeriod.stem)
                        dayunGod.cn == exp
                    }
                }
                "dayun.day_master_strength", "natal.strength_state" -> {
                    val exp = obj.getString(key)
                    ctx.strength.cn == exp
                }
                "relation.pair", "liuhe.pair" -> {
                    val exp = obj.getString(key)
                    ctx.hitLiuhe != null && (ctx.hitLiuhe.pairName == exp || ctx.hitLiuhe.pairCode.equals(exp, ignoreCase = true))
                }
                "relation.scope" -> {
                    val exp = obj.getString(key)
                    ctx.hitLiuhe != null && ctx.hitLiuhe.scope.equals(exp, ignoreCase = true)
                }
                "relation.transformation_status" -> {
                    val exp = obj.getString(key)
                    if (ctx.hitLiuhe == null) false
                    else exp.equals("NOT_VERIFIED", ignoreCase = true)
                }
                "relation.multiplicity" -> {
                    val exp = obj.getString(key)
                    if (exp.equals("MULTIPLE", ignoreCase = true)) {
                        ctx.natalRelations.count { it.relationType == "六合" } > 1
                    } else false
                }
                "relation.other_events_verified" -> {
                    val exp = obj.getBoolean(key)
                    val hasOthers = ctx.natalRelations.any { it.relationType != "六合" }
                    hasOthers == exp
                }
                "relation.type" -> {
                    val exp = obj.getString(key)
                    if (exp.equals("LIUHE", ignoreCase = true)) ctx.hitLiuhe != null else false
                }
                "relation_scan.completeness" -> {
                    val exp = obj.getString(key)
                    if (exp.equals("COMPLETE", ignoreCase = true)) ctx.natalAvailability == AlgorithmAvailability.AVAILABLE
                    else if (exp.equals("PARTIAL", ignoreCase = true)) ctx.natalAvailability != AlgorithmAvailability.AVAILABLE
                    else false
                }
                "relation_scan.result" -> {
                    val exp = obj.getString(key)
                    if (exp.equals("NONE", ignoreCase = true)) ctx.hitLiuhe == null else false
                }
                "relation_scan.availability" -> {
                    val exp = obj.getString(key)
                    ctx.natalAvailability.code.equals(exp, ignoreCase = true)
                }
                "flow.loc" -> {
                    val loc = obj.getString(key)
                    val elem = if (loc == "干") TenGodCalculator.elementOf(ctx.monthStem) else TenGodCalculator.elementOfBranch(ctx.monthBranch)
                    val actualRole = deriveYongshenRole(elem, ctx.yongshen)
                    if (obj.has("yongshen.role")) {
                        val expRole = obj.getString("yongshen.role")
                        actualRole == expRole
                    } else {
                        true
                    }
                }
                "yongshen.role" -> {
                    if (obj.has("flow.loc")) {
                        true // 已在 flow.loc 联合校验
                    } else {
                        val expRole = obj.getString(key)
                        val stemRole = deriveYongshenRole(TenGodCalculator.elementOf(ctx.monthStem), ctx.yongshen)
                        val branchRole = deriveYongshenRole(TenGodCalculator.elementOfBranch(ctx.monthBranch), ctx.yongshen)
                        expRole == stemRole || expRole == branchRole
                    }
                }
                "yongshen.availability" -> {
                    val exp = obj.getString(key)
                    ctx.yongshenAvailability.code.equals(exp, ignoreCase = true)
                }
                "yongshen.method" -> {
                    val exp = obj.getString(key)
                    if (ctx.yongshen == null || ctx.yongshenAvailability != AlgorithmAvailability.AVAILABLE) false
                    else exp.equals("FUYI", ignoreCase = true)
                }
                "yongshen.method_verified" -> {
                    val exp = obj.getBoolean(key)
                    (ctx.yongshen != null && ctx.yongshenAvailability == AlgorithmAvailability.AVAILABLE) == exp
                }
                "yongshen.role_comparison" -> {
                    val exp = obj.getString(key)
                    if (ctx.yongshen == null) false
                    else {
                        val stemRole = deriveYongshenRole(TenGodCalculator.elementOf(ctx.monthStem), ctx.yongshen)
                        val branchRole = deriveYongshenRole(TenGodCalculator.elementOfBranch(ctx.monthBranch), ctx.yongshen)
                        val comp = if (stemRole == branchRole) "SAME" else "DIFFERENT"
                        comp.equals(exp, ignoreCase = true)
                    }
                }
                "liuhe.month_involvement" -> {
                    val exp = obj.getBoolean(key)
                    (ctx.hitLiuhe != null && ctx.hitLiuhe.scope == "FLOW_MONTH_TO_NATAL") == exp
                }
                "liuhe.multiple_pairs" -> {
                    val exp = obj.getBoolean(key)
                    val count = ctx.natalRelations.count { it.relationType == "六合" }
                    (count > 1) == exp
                }
                "liuhe.hehua_judgement" -> {
                    val exp = obj.getString(key)
                    exp.equals("NO_HEHUA", ignoreCase = true)
                }
                "liuhe.other_events_coexist" -> {
                    val exp = obj.getBoolean(key)
                    val hasOthers = ctx.natalRelations.any { it.relationType != "六合" }
                    hasOthers == exp
                }
                "liuhe.synthesis_nature" -> {
                    val exp = obj.getString(key)
                    exp.equals("SYNTHESIS", ignoreCase = true)
                }
                "state.target" -> {
                    false
                }
                // 未识别的 JSON 属性严格返回 false
                else -> false
            }
            if (!matches) return false
        }
        return true
    }

    private fun deriveYongshenRole(elementCn: String, ys: StockYongshenEntity?): String {
        if (ys == null || elementCn.isBlank()) return "中性"
        val yongList = ys.yongShen?.split(",")?.map { it.trim() }?.filter { it.isNotBlank() } ?: emptyList()
        val xiList = ys.xiShen?.split(",")?.map { it.trim() }?.filter { it.isNotBlank() } ?: emptyList()
        val jiList = ys.jiShen?.split(",")?.map { it.trim() }?.filter { it.isNotBlank() } ?: emptyList()
        val chouList = ys.chouShen?.split(",")?.map { it.trim() }?.filter { it.isNotBlank() } ?: emptyList()
        val candList = ys.candidateElements?.split(",")?.map { it.trim() }?.filter { it.isNotBlank() } ?: emptyList()
        return when {
            elementCn in yongList -> "用"
            elementCn in xiList -> "喜"
            elementCn in jiList -> "制约"
            elementCn in chouList -> "消耗"
            elementCn in candList -> "用"
            else -> "中性"
        }
    }
}
