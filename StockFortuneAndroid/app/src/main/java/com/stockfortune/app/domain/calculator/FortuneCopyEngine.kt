package com.stockfortune.app.domain.calculator

import com.stockfortune.app.data.entity.LuckCyclePeriodEntity
import com.stockfortune.app.data.entity.StockYongshenEntity
import com.stockfortune.app.domain.model.AlgorithmAvailability
import com.stockfortune.app.domain.model.AnnualSynthesis
import com.stockfortune.app.domain.model.CopyRuleDefinition
import com.stockfortune.app.domain.model.CopySection
import com.stockfortune.app.domain.model.FirstDayPolarity
import com.stockfortune.app.domain.model.FiveParagraphInterpretation
import com.stockfortune.app.domain.model.MonthLabel
import com.stockfortune.app.domain.model.PreciseAdvancedNotice
import com.stockfortune.app.domain.model.ProductionGate
import com.stockfortune.app.domain.model.ReviewStatus
import com.stockfortune.app.domain.model.Strength
import com.stockfortune.app.domain.model.TenGod
import com.stockfortune.app.domain.model.YongshenCandidateStatus

/**
 * 股运通 V1.3 Phase 5 离线自然语言组合引擎（FortuneCopyEngine）。
 *
 * 实施原则：
 * 1. 结构化分析：五段式独立组装（命理依据、本月主题、潜在矛盾、企业经营观察、综合解释）；
 * 2. 确定性组合：相同输入（股票八字、流月干支、大运、喜用、原局关系）输出绝对一致；
 * 3. 强弱差异性：同十神在身强/中和/身弱下生成具备命理依据的差异化解释；
 * 4. 阴阳命条件一致性：仅在产生真实大运时间条件变化时产生差异，不伪造命理结论；
 * 5. 分项精确提示：大运、原局、喜用三态独立输出，三项可用时提示为空串，不交叉否定；
 * 6. 零 LLM 与完全离线运行。
 */
object FortuneCopyEngine {

    /**
     * 核心月度五段式解读装配入口。
     */
    fun composeMonthlyInterpretation(
        stockId: Long,
        stockCode: String,
        dayStem: String,
        yearPillar: String,
        monthPillar: String,
        dayPillar: String,
        firstDayPolarity: FirstDayPolarity,
        dayunStatus: String,
        year: Int,
        month: Int,
        monthGanzhi: String,
        natalRelationsCount: Int,
        yongshenStatus: YongshenCandidateStatus,
        currentLuckPeriod: LuckCyclePeriodEntity? = null,
        natalRelations: List<com.stockfortune.app.data.entity.NatalRelationEntity> = emptyList(),
        yongshen: StockYongshenEntity? = null,
        candidateRules: List<CopyRuleDefinition>? = null,
    ): FiveParagraphInterpretation {
        if (monthGanzhi.length < 2 || dayStem.isBlank()) {
            return fallbackUnavailable(stockId, stockCode, year, month)
        }

        val monthStem = monthGanzhi[0].toString()
        val monthBranch = monthGanzhi[1].toString()
        val branchMainStem = TenGodCalculator.mainQi(monthBranch)

        val stemGod = TenGodCalculator.tenGod(dayStem, monthStem)
        val branchGod = TenGodCalculator.tenGod(dayStem, branchMainStem)
        val strength = TenGodCalculator.dayMasterStrength(yearPillar, monthPillar, dayPillar)

        val dayunAvailability = when (dayunStatus.lowercase()) {
            "available" -> AlgorithmAvailability.AVAILABLE
            "unavailable_flat", "unavailable_missing", "unavailable_conflict", "unavailable" -> AlgorithmAvailability.UNAVAILABLE
            "pending" -> AlgorithmAvailability.PENDING
            else -> AlgorithmAvailability.UNAVAILABLE
        }

        val natalAvailability = if (natalRelationsCount >= 0) {
            AlgorithmAvailability.AVAILABLE
        } else {
            AlgorithmAvailability.UNAVAILABLE
        }

        val yongshenAvailability = when (yongshenStatus) {
            YongshenCandidateStatus.CONFIRMED, YongshenCandidateStatus.CANDIDATE -> AlgorithmAvailability.AVAILABLE
            YongshenCandidateStatus.UNAVAILABLE -> AlgorithmAvailability.UNAVAILABLE
        }

        val isDayunAvailable = dayunAvailability == AlgorithmAvailability.AVAILABLE &&
            firstDayPolarity != FirstDayPolarity.FLAT &&
            firstDayPolarity != FirstDayPolarity.MISSING &&
            firstDayPolarity != FirstDayPolarity.CONFLICT &&
            currentLuckPeriod != null

        val hitIds = mutableListOf<String>()

        // 识别六合支对（原局内六合 或 流月与原局三柱六合 或 大运与原局三柱六合）
        val hitLiuhe = findLiuheRelation(
            monthBranch = monthBranch,
            yearPillar = yearPillar,
            monthPillar = monthPillar,
            dayPillar = dayPillar,
            natalRelations = natalRelations,
            currentLuckPeriod = if (isDayunAvailable) currentLuckPeriod else null,
        )

        val context = OfflineCopyRuleEvaluator.EvaluationContext(
            stockId = stockId,
            stockCode = stockCode,
            year = year,
            month = month,
            dayStem = dayStem,
            monthStem = monthStem,
            monthBranch = monthBranch,
            monthStemGod = stemGod,
            monthBranchMainQiGod = branchGod,
            strength = strength,
            firstDayPolarity = firstDayPolarity,
            dayunAvailability = dayunAvailability,
            currentLuckPeriod = if (isDayunAvailable) currentLuckPeriod else null,
            natalAvailability = natalAvailability,
            natalRelations = natalRelations,
            hitLiuhe = hitLiuhe,
            yongshenAvailability = yongshenAvailability,
            yongshen = yongshen,
            isMockContext = false,
            isProductionBuild = false,
        )

        val candidateList = candidateRules ?: com.stockfortune.app.data.repository.CopyRuleRepository.getDefault().getRules()
        if (candidateList.isNotEmpty()) {
            return OfflineCopyRuleEvaluator.evaluate(context, candidateList)
        }

        // 1. 第一段：命理依据
        val basisText = buildBasisParagraph(
            firstDayPolarity = firstDayPolarity,
            monthStem = monthStem,
            stemGod = stemGod,
            monthBranch = monthBranch,
            branchGod = branchGod,
            currentLuckPeriod = if (isDayunAvailable) currentLuckPeriod else null,
            dayStem = dayStem,
            hitLiuhe = hitLiuhe,
            yongshen = yongshen,
            hitIds = hitIds,
        )

        // 2. 第二段：本月主题（主线 + 大运阶段背景 + 30条十神强弱条件解释副线）
        val themeText = buildThemeParagraph(
            stemGod = stemGod,
            strength = strength,
            currentLuckPeriod = if (isDayunAvailable) currentLuckPeriod else null,
            dayStem = dayStem,
            hitIds = hitIds,
        )

        // 3. 第三段：潜在矛盾（同神聚焦 vs 异神分类差异 + 大运形式对照 + 六合合化不确定项 + 调候扶抑分离）
        val contradictionText = buildContradictionParagraph(
            stemGod = stemGod,
            branchGod = branchGod,
            monthStem = monthStem,
            currentLuckPeriod = if (isDayunAvailable) currentLuckPeriod else null,
            dayStem = dayStem,
            hitLiuhe = hitLiuhe,
            yongshen = yongshen,
            hitIds = hitIds,
        )

        // 4. 第四段：企业经营观察（匹配财报与治理披露入口，结合流月与大运）
        val businessText = buildBusinessParagraph(
            stemGod = stemGod,
            branchGod = branchGod,
            currentLuckPeriod = if (isDayunAvailable) currentLuckPeriod else null,
            dayStem = dayStem,
            hitIds = hitIds,
        )

        // 5. 第五段：综合解释（归纳前四段：强弱解释边界 + 大运十神×强弱 + 喜用候选 + 六合结构总结）
        val synthesisText = buildSynthesisParagraph(
            strength = strength,
            stemGod = stemGod,
            branchGod = branchGod,
            currentLuckPeriod = if (isDayunAvailable) currentLuckPeriod else null,
            dayStem = dayStem,
            hitLiuhe = hitLiuhe,
            yongshen = yongshen,
            hitIds = hitIds,
        )

        // 6. 分项精确提示
        val notice = PreciseAdvancedNotice.formatNotice(
            dayunState = dayunAvailability,
            natalState = natalAvailability,
            yongshenState = yongshenAvailability,
        )

        return FiveParagraphInterpretation(
            stockId = stockId,
            stockCode = stockCode,
            year = year,
            month = month,
            basisText = basisText,
            themeText = themeText,
            contradictionText = contradictionText,
            businessText = businessText,
            synthesisText = synthesisText,
            preciseAdvancedNotice = notice,
            hitRuleIds = hitIds,
            reviewStatus = ReviewStatus.PENDING_REVIEW, // 严格保持待人工终审，未终审前不标记通过
            isMock = false,
        )
    }

    data class LiuheHit(
        val pairName: String,
        val pairCode: String,
        val scope: String,
        val branchA: String,
        val branchB: String,
        val posA: String,
        val posB: String,
    )

    private fun findLiuheRelation(
        monthBranch: String,
        yearPillar: String,
        monthPillar: String,
        dayPillar: String,
        natalRelations: List<com.stockfortune.app.data.entity.NatalRelationEntity>,
        currentLuckPeriod: LuckCyclePeriodEntity?,
    ): LiuheHit? {
        val natalPillars = listOf(
            "原局年支" to yearPillar.getOrNull(1)?.toString(),
            "原局月支" to monthPillar.getOrNull(1)?.toString(),
            "原局日支" to dayPillar.getOrNull(1)?.toString(),
        )

        // 1. 原局内部六合
        val natalLiuhe = natalRelations.firstOrNull { it.relationType == "六合" }
        if (natalLiuhe != null) {
            val bA = natalLiuhe.sourceGanzhi.takeLast(1)
            val bB = natalLiuhe.targetGanzhi.takeLast(1)
            val p = getLiuhePair(bA, bB)
            if (p != null) {
                return LiuheHit(
                    pairName = p.first,
                    pairCode = p.second,
                    scope = "NATAL_INTERNAL",
                    branchA = bA,
                    branchB = bB,
                    posA = "原局" + pillarCn(natalLiuhe.sourcePillar),
                    posB = "原局" + pillarCn(natalLiuhe.targetPillar),
                )
            }
        }

        // 2. 流月对原局三柱六合
        for ((posName, nb) in natalPillars) {
            if (!nb.isNullOrBlank()) {
                val p = getLiuhePair(monthBranch, nb)
                if (p != null) {
                    return LiuheHit(
                        pairName = p.first,
                        pairCode = p.second,
                        scope = "FLOW_MONTH_TO_NATAL",
                        branchA = monthBranch,
                        branchB = nb,
                        posA = "流月支",
                        posB = posName,
                    )
                }
            }
        }

        // 3. 大运对原局三柱六合
        if (currentLuckPeriod != null) {
            for ((posName, nb) in natalPillars) {
                if (!nb.isNullOrBlank()) {
                    val p = getLiuhePair(currentLuckPeriod.branch, nb)
                    if (p != null) {
                        return LiuheHit(
                            pairName = p.first,
                            pairCode = p.second,
                            scope = "DAYUN_TO_NATAL",
                            branchA = currentLuckPeriod.branch,
                            branchB = nb,
                            posA = "大运支",
                            posB = posName,
                        )
                    }
                }
            }
        }

        return null
    }

    private fun getLiuhePair(a: String, b: String): Pair<String, String>? {
        val key = if (a <= b) "$a$b" else "$b$a"
        return when (key) {
            "丑子", "子丑" -> "子丑" to "ZICHOU"
            "亥寅", "寅亥" -> "寅亥" to "YINHAI"
            "卯戌", "戌卯" -> "卯戌" to "MAOXU"
            "酉辰", "辰酉" -> "辰酉" to "CHENYOU"
            "巳申", "申巳" -> "巳申" to "SISHEN"
            "午未", "未午" -> "午未" to "WUWEI"
            else -> null
        }
    }

    private fun pillarCn(pillar: String): String = when (pillar.lowercase()) {
        "year" -> "年支"
        "month" -> "月支"
        "day" -> "日支"
        else -> "支"
    }

    private fun strengthSuffix(s: Strength): String = when (s) {
        Strength.STRONG -> "STR"
        Strength.BALANCED -> "BAL"
        Strength.WEAK -> "WEK"
    }

    private fun buildBasisParagraph(
        firstDayPolarity: FirstDayPolarity,
        monthStem: String,
        stemGod: TenGod,
        monthBranch: String,
        branchGod: TenGod,
        currentLuckPeriod: LuckCyclePeriodEntity?,
        dayStem: String,
        hitLiuhe: LiuheHit?,
        yongshen: StockYongshenEntity?,
        hitIds: MutableList<String>,
    ): String {
        val parts = mutableListOf<String>()

        if (firstDayPolarity == FirstDayPolarity.FLAT) {
            parts.add("首日表现为平盘，不能直接沿用阳命标签推算方向；大运区间解释在此情形下暂停。")
            hitIds.add("NA_POLARITY_FLAT")
        } else if (firstDayPolarity == FirstDayPolarity.MISSING) {
            parts.add("首日行情数据缺失，大运方向未形成裁定；大运维度在此情形下暂停。")
            hitIds.add("NA_POLARITY_MISSING")
        } else if (currentLuckPeriod != null) {
            val dayunStemGod = TenGodCalculator.tenGod(dayStem, currentLuckPeriod.stem)
            parts.add("流年行入${currentLuckPeriod.ganzhi}大运（${currentLuckPeriod.startYear}–${currentLuckPeriod.endYear}年），运干对日主为${dayunStemGod.cn}；起运与大运周期已由干支历法独立推导核验。")
            hitIds.add("ADV_DY_PERIOD_FACT")
            hitIds.add("ADV_DY_BASE_${dayunStemGod.name}")
        }

        parts.add("流月天干对应${stemGod.cn}，仅表示该位置的十神分类；不等于公司已经出现某类经营事件。")
        hitIds.add("BAS_STEM_${stemGod.name}")

        parts.add("流月地支本气对应${branchGod.cn}；此处仅采用本气，不将中气和余气一并计入当月主题。")
        hitIds.add("BAS_BRANCH_${branchGod.name}")

        if (hitLiuhe != null) {
            parts.add("经核验，${hitLiuhe.posA}「${hitLiuhe.branchA}」与${hitLiuhe.posB}「${hitLiuhe.branchB}」符合${hitLiuhe.pairName}六合支对；此识别给出支位对应关系，单凭配对不能断定合化。")
            hitIds.add("ADV_LH_PAIR_${hitLiuhe.pairCode}")
            when (hitLiuhe.scope) {
                "NATAL_INTERNAL" -> hitIds.add("ADV_LH_NATAL_${hitLiuhe.pairCode}")
                "FLOW_MONTH_TO_NATAL" -> hitIds.add("ADV_LH_MONTH_${hitLiuhe.pairCode}")
                "DAYUN_TO_NATAL" -> hitIds.add("ADV_LH_DAYUN_${hitLiuhe.pairCode}")
            }
        }

        if (yongshen != null && yongshen.status == "confirmed") {
            parts.add("按已核验的扶抑方法，${yongshen.yongShen}为用神候选、${yongshen.xiShen}为喜神候选。")
            hitIds.add("ADV_YS_METHOD_FUYI")
            if (!yongshen.tiaohouNote.isNullOrBlank()) {
                parts.add("调候提示作为独立时令环境参考（${yongshen.tiaohouNote}），未经裁定不直接改写扶抑角色。")
                hitIds.add("ADV_YS_METHOD_TIAOHOU")
            }
        }

        return parts.joinToString(" ")
    }

    private fun buildThemeParagraph(
        stemGod: TenGod,
        strength: Strength,
        currentLuckPeriod: LuckCyclePeriodEntity?,
        dayStem: String,
        hitIds: MutableList<String>,
    ): String {
        val parts = mutableListOf<String>()

        // 主题主线
        val mainTheme = when (stemGod) {
            TenGod.BI_JIAN -> "本月主线落在团队协作与责任分工。月干比肩表示同类并行的传统意象；经营层面只把分工、协作、关联方披露列为核对方向，不据此推断协作结果。"
            TenGod.JIE_CAI -> "本月主线落在资源竞争与利益分配。月干劫财作为分夺和同业竞争的传统意象，提示关注资源分配边界，不能断定企业面临同业挤压。"
            TenGod.SHI_SHEN -> "本月主线落在业务培育与稳步产出。月干食神侧重于持续投入与自然转化的意象，不能等同于企业业绩已经兑现。"
            TenGod.SHANG_GUAN -> "本月主线落在模式突破与规制平衡。月干伤官强调打破常规与创新的传统意象，提示关注创新探索与合规要求的平衡。"
            TenGod.PIAN_CAI -> "本月主线落在资产交易与资金流转。月干偏财作为传统的流动性意象，只能提示核对披露口径，不能说明资产交易已发生。"
            TenGod.ZHENG_CAI -> "本月主线落在主营业务与现金回笼。月干正财作为传统的常态财务意象，不等于本月现金流真实改善或经营兑现。"
            TenGod.QI_SHA -> "本月主线落在履约约束与治理压力。月干七杀在传统解释中侧重约束强度，不能仅据此认定公司承受诉讼或债务事宜。"
            TenGod.ZHENG_GUAN -> "本月主线落在治理秩序与制度执行。月干正官作为规制与职责象义，只是讨论制度问题的入口，并非内控有效性的现实结论。"
            TenGod.PIAN_YIN -> "本月主线落在专项技术与知识储备。月干偏印强调专业性和内部积累的意象，不等于企业已经形成技术优势。"
            TenGod.ZHENG_YIN -> "本月主线落在资质信誉与政策支持。月干正印代表依托制度与支持体系的传统意象，不代表公司已经取得明确扶持。"
        }
        parts.add(mainTheme)
        hitIds.add("THM_${stemGod.name}")

        // 大运背景呼应（如果有效大运存在）
        if (currentLuckPeriod != null) {
            val dayunStemGod = TenGodCalculator.tenGod(dayStem, currentLuckPeriod.stem)
            val relationNote = if (stemGod == dayunStemGod) "形成同类十神呼应" else "相互补充"
            parts.add("大运呈现${dayunStemGod.cn}象义，阶段背景与流月${relationNote}，月干仍为当月解释主轴。")
        }

        // 30 条十神强弱条件解释（有依据的强弱差异副线）
        val strengthSupplement = getStrengthTenGodExplanation(stemGod, strength)
        parts.add(strengthSupplement)
        hitIds.add("SGS_${stemGod.name}_${strength.name}")

        return parts.joinToString(" ")
    }

    private fun buildContradictionParagraph(
        stemGod: TenGod,
        branchGod: TenGod,
        monthStem: String,
        currentLuckPeriod: LuckCyclePeriodEntity?,
        dayStem: String,
        hitLiuhe: LiuheHit?,
        yongshen: StockYongshenEntity?,
        hitIds: MutableList<String>,
    ): String {
        val parts = mutableListOf<String>()

        if (stemGod == branchGod) {
            hitIds.add("CNT_IDENTICAL")
            parts.add("月干与本气为同一十神，说明流月两处分类指向相近的象义；这是形式上的聚焦，不是已核验的原局关系事件。")
        } else {
            hitIds.add("CNT_DISTINCT")
            parts.add("月干与本气来自不同十神分组，形成主题与副线的分类差异；这并非已核验的相冲、相刑或经营矛盾。")
        }

        // 大运形式对照
        if (currentLuckPeriod != null) {
            val dayunStemGod = TenGodCalculator.tenGod(dayStem, currentLuckPeriod.stem)
            if (dayunStemGod == stemGod) {
                parts.add("大运天干与流月天干属于同类十神，形成形式上的主题聚焦；这并非原局已核验的相互转化，各自作用范围依然受限于时间层级。")
                hitIds.add("ADV_DY_NATAL_FORMAL_SAME")
            } else if (TenGodCalculator.elementOf(currentLuckPeriod.stem) == TenGodCalculator.elementOf(monthStem)) {
                parts.add("大运天干与流月天干五行同类异神，属于形式呼应，各方权责边界仍需独立审视。")
                hitIds.add("ADV_DY_NATAL_FORMAL_SAME_ELEMENT")
            } else {
                parts.add("大运与流月分属不同十神分组，反映阶段背景与当期关注的层次分工；二者并存并不构成已核验的干支刑冲克害。")
                hitIds.add("ADV_DY_NATAL_FORMAL_DISTINCT")
            }
        }

        // 六合合化不确定项
        if (hitLiuhe != null) {
            parts.add("六合支对成立不等于合化成功。未经独立证据确认合化条件时，不得判定合化成立，亦不作确定性偏向推断。")
            hitIds.add("ADV_LH_NO_HEHUA")
        }

        // 调候与扶抑分离
        if (yongshen != null && yongshen.status == "confirmed" && !yongshen.tiaohouNote.isNullOrBlank()) {
            parts.add("调候提示属于时令环境维度，不得自动覆盖已核验的扶抑用喜角色，双方未经共同裁定时分开展示。")
        }

        return parts.joinToString(" ")
    }

    private fun buildBusinessParagraph(
        stemGod: TenGod,
        branchGod: TenGod,
        currentLuckPeriod: LuckCyclePeriodEntity?,
        dayStem: String,
        hitIds: MutableList<String>,
    ): String {
        val entry1 = getBusinessEntry(stemGod)
        val entry2 = getBusinessEntry(branchGod)
        hitIds.add("ENT_${stemGod.name}_${branchGod.name}")
        return if (currentLuckPeriod != null) {
            val dayunStemGod = TenGodCalculator.tenGod(dayStem, currentLuckPeriod.stem)
            val entryDayun = getBusinessEntry(dayunStemGod)
            "经营观察可对照公开披露中的「$entry1」与「$entry2」，并结合大运阶段观察「$entryDayun」。这些项目仅作为信息核对入口，不能据此认定公司已发生对应事项。"
        } else {
            "经营观察可对照公开披露中的「$entry1」与「$entry2」。这两个项目仅作为信息核对入口，不能据此认定公司已发生对应事项。"
        }
    }

    private fun buildSynthesisParagraph(
        strength: Strength,
        stemGod: TenGod,
        branchGod: TenGod,
        currentLuckPeriod: LuckCyclePeriodEntity?,
        dayStem: String,
        hitLiuhe: LiuheHit?,
        yongshen: StockYongshenEntity?,
        hitIds: MutableList<String>,
    ): String {
        val parts = mutableListOf<String>()

        hitIds.add("SUM_${strength.name}")
        val strengthBase = when (strength) {
            Strength.STRONG -> "原局强弱按现行三柱六字口径标为身强。这里讨论的是传统分类对本月主题的解释边界，不代表公司具备现实经营承载能力。"
            Strength.BALANCED -> "原局强弱按现行三柱六字口径标为中和。主题与副线是否相互呼应不能仅凭本月十神或一个强弱标签判定。"
            Strength.WEAK -> "原局强弱按现行三柱六字口径标为身弱。该强弱归类不等于公司实际财务承压，仍须另行核对经营信息。"
        }
        parts.add(strengthBase)

        // 大运十神×强弱
        if (currentLuckPeriod != null) {
            val dayunStemGod = TenGodCalculator.tenGod(dayStem, currentLuckPeriod.stem)
            parts.add(getDayunStrengthExplanation(dayunStemGod, strength))
            hitIds.add("ADV_DY_${dayunStemGod.name}_${strengthSuffix(strength)}")
        }

        // 喜用候选总结
        if (yongshen != null) {
            if (yongshen.status == "confirmed") {
                parts.add("流月五行角色结合原局扶抑主轴审视，此处是附有方法版本及来源的候选角色，不是唯一用神裁断；若扶抑与调候意见不同，分别保留依据，等待独立裁决。")
                hitIds.add("ADV_YS_CANDIDATE_ONLY")
            } else if (yongshen.status == "candidate") {
                parts.add("原局处于中和平衡，多五行流通候选不强加单一主轴，依岁运流动调节。")
                hitIds.add("ADV_YS_CANDIDATE_ONLY")
            }
        }

        // 六合结构总结
        if (hitLiuhe != null) {
            parts.add("六合支对补充了结构位置关系，在合化与最终喜用未裁定前，不给出单一吉凶或确定性财运结论。")
            hitIds.add("ADV_LH_SYNTHESIS")
        }

        return parts.joinToString(" ")
    }

    private fun getStrengthTenGodExplanation(god: TenGod, strength: Strength): String {
        return when (god) {
            TenGod.BI_JIAN -> when (strength) {
                Strength.STRONG -> "原局已归身强，同类之气再见时，传统解释会把分担与同业并列放在同一幅图中：协作可能更密集，权责边界也更需要辨认。"
                Strength.BALANCED -> "原局归为中和，比肩既可读作同辈之间的支持，也可读作责任与资源的共同承担；两面并陈，比偏向哪一面并无单一答案。"
                Strength.WEAK -> "原局归为身弱，比肩的同类属性在传统扶身口径下更容易被理解为补入助力；但增加同伴力量与共同分配资源是两个同时成立的议题。"
            }
            TenGod.JIE_CAI -> when (strength) {
                Strength.STRONG -> "身强见劫财，分夺与同业竞争的象义在传统解释中被加重，关注资源被分流或成本上升的倾向。"
                Strength.BALANCED -> "中和见劫财，同业之间的博弈与合作并存，需结合实际经营判断主次。"
                Strength.WEAK -> "身弱见劫财，虽然借力帮身的意象仍然存在，但同伴参与分润的代价也需一并衡量。"
            }
            TenGod.SHI_SHEN -> when (strength) {
                Strength.STRONG -> "身强遇食神，我生之物能够顺畅排泄充沛力量，传统谓之秀气流行，产出与变现能力更受关注。"
                Strength.BALANCED -> "中和遇食神，业务培育与稳健产出保持相对均衡，投入节奏的动态把控更受重视。"
                Strength.WEAK -> "身弱遇食神，自身力量本已偏弱，再行泄耗易增加负担，传统强调关照力量收放。"
            }
            TenGod.SHANG_GUAN -> when (strength) {
                Strength.STRONG -> "身强见伤官，创新与突破意愿强烈，力量充裕下能承受探索风险，但合规边界需严格守护。"
                Strength.BALANCED -> "中和见伤官，求变与守规兼具，制度执行力与业务灵活性需要细致权衡。"
                Strength.WEAK -> "身弱见伤官，泄力过甚易导致内控或财务资源承压，稳健推进更契合当下格局。"
            }
            TenGod.PIAN_CAI -> when (strength) {
                Strength.STRONG -> "身强遇偏财，传统用“我克”讨论对流动资源的驾驭可能，主线是资源关系较多变动而非固定回路。"
                Strength.BALANCED -> "中和遇偏财，外部流动资源的调度空间适中，客观评估资产转化的实际效率。"
                Strength.WEAK -> "身弱遇偏财，“我克”的财星虽可成为主题，但传统口径会同时询问原局是否有足够力量承载；对象出现与承载能力是两件事。"
            }
            TenGod.ZHENG_CAI -> when (strength) {
                Strength.STRONG -> "身强遇正财，在“我克”的框架中，传统更强调职责内的资源配置与持续承接，稳定性的象义因此被放大。"
                Strength.BALANCED -> "中和遇正财，常态资源与持续任务构成解释主轴，传统的关键不只是财星显现，还要看各方关系是否能够衔接。"
                Strength.WEAK -> "身弱遇正财，传统“财多身弱”的疑问来自承载与对象之间的条件差，不是财星必为负担；更应同时看可扶助的印比。"
            }
            TenGod.QI_SHA -> when (strength) {
                Strength.STRONG -> "身强见七杀，克我者反成锤炼，传统所谓身强任杀为权，外部约束常能化为攻坚与治理提升的动能。"
                Strength.BALANCED -> "中和见七杀，压力与应对相对对等，在制度约束与风险管控中寻求动态平稳。"
                Strength.WEAK -> "身弱见七杀，外部规制与履约压力较为显著，传统强调护身为先，重点应对外部履约考验。"
            }
            TenGod.ZHENG_GUAN -> when (strength) {
                Strength.STRONG -> "身强任正官，约束即是定位与责任，组织治理规范化能有效约束并凝聚企业力量。"
                Strength.BALANCED -> "中和遇正官，规范与运作相得益彰，制度建设与日常运营协调推进。"
                Strength.WEAK -> "身弱遇正官，严格的管理约束可能带来运营弹性不足的感受，需印星化解压力。"
            }
            TenGod.PIAN_YIN -> when (strength) {
                Strength.STRONG -> "身强又见偏印，生我之力被进一步强调，传统会讨论专业积累是否过度内向以及与产出侧能否衔接。"
                Strength.BALANCED -> "中和见偏印，技术与知识储备稳步推进，内向积累与外向转化相对平衡。"
                Strength.WEAK -> "身弱见偏印，生我扶身之神在位，传统谓之绝处逢生，有助于恢复和筑牢核心能力底座。"
            }
            TenGod.ZHENG_YIN -> when (strength) {
                Strength.STRONG -> "身强见正印，传统认为生扶过重容易带来依赖或决策偏缓，需关注效率与活力。"
                Strength.BALANCED -> "中和遇正印，政策支持与资质声誉平稳赋能，为常规运营提供坚实背书。"
                Strength.WEAK -> "身弱遇正印，印星护身极为关键，传统解释视为雪中送炭，能有效提升抗风险与承接能力。"
            }
        }
    }

    private fun getBusinessEntry(god: TenGod): String {
        return when (god) {
            TenGod.BI_JIAN -> "关联交易及定价"
            TenGod.JIE_CAI -> "合并报表范围变动"
            TenGod.SHI_SHEN -> "在手订单（如披露）"
            TenGod.SHANG_GUAN -> "重大诉讼与仲裁进展"
            TenGod.PIAN_CAI -> "非经常性损益明细"
            TenGod.ZHENG_CAI -> "主营收入构成"
            TenGod.QI_SHA -> "有息负债到期结构"
            TenGod.ZHENG_GUAN -> "审计意见类型"
            TenGod.PIAN_YIN -> "研发投入和资本化率"
            TenGod.ZHENG_YIN -> "资质续期公告（如适用）"
        }
    }

    private fun getDayunStrengthExplanation(god: TenGod, strength: Strength): String {
        return when (god) {
            TenGod.BI_JIAN -> when (strength) {
                Strength.STRONG -> "身强时遇比肩大运，同类之气与原局既有支撑并列，传统解释更重视参与主体增加以后，职责与资源配置是否均衡；这不是合作效率变化的事实判断。"
                Strength.BALANCED -> "中和遇比肩大运，可以同时讨论相互支持与责任分配，原局其他十神的参与程度才决定论述偏向，单凭此格不作有利或不利的定论。"
                Strength.WEAK -> "身弱遇比肩大运，同类元素具有补入支持的结构可能，但扶助是否落实仍取决于根气、透藏与其他干支；不能直接断言合作带来实际改善。"
            }
            TenGod.JIE_CAI -> when (strength) {
                Strength.STRONG -> "身强遇劫财大运，原局同类力量与阶段性的分配主题叠加，解释侧重权属安排和分配边界；这只是传统结构对照，并非企业发生权益争议。"
                Strength.BALANCED -> "中和遇劫财大运，同类支持与资源分担两面同时存在，仍须观察财星和印星等条件才可能判断其在命局中的作用。"
                Strength.WEAK -> "身弱遇劫财大运，劫财的同类属性可纳入扶身讨论，但资源共同承担与分配也是独立议题，不得把扶身直接说成资源增多。"
            }
            TenGod.SHI_SHEN -> when (strength) {
                Strength.STRONG -> "身强遇食神大运，向外表达可作为结构中的一个出口，但是否形成有效流通仍取决于原局组合，不能由食神一项认定已经产生成果。"
                Strength.BALANCED -> "中和遇食神大运，成果表达与日主消耗两面需同时考察，原局的根气和其他十神决定其作用条件。"
                Strength.WEAK -> "身弱遇食神大运，日主生出的具体五行取决于日主属性，传统解释会额外检视持续输出与原局承接的关系，不作单向吉凶判断。"
            }
            TenGod.SHANG_GUAN -> when (strength) {
                Strength.STRONG -> "身强遇伤官大运，表达和调整主题可能更加突出，但不能直接推出与正官形成具体制约，更不对应现实监管或经营事件。"
                Strength.BALANCED -> "中和遇伤官大运，创新表达与既定秩序之间是否形成张力，还需要原局官印等因素来确认；伤官标签本身不足以裁断。"
                Strength.WEAK -> "身弱遇伤官大运，向外表达伴随泄身的传统解释，需要进一步核对印比是否构成支持；不能将结构上的消耗写成现实能力不足。"
            }
            TenGod.PIAN_CAI -> when (strength) {
                Strength.STRONG -> "身强遇偏财大运，日主对财星的承接条件成为一个可讨论的结构点，但财星位置、透藏和流月关系尚需验证，不等于资源已经实现转化。"
                Strength.BALANCED -> "中和遇偏财大运，资源对象出现与实际承接程度仍是两层问题；阶段主题成立不意味着偏财作用已经确定。"
                Strength.WEAK -> "身弱遇偏财大运，财星作为日主所克的一类关系，其对象性可被描述，承接条件则必须连同印比、根气和岁月事件核对。"
            }
            TenGod.ZHENG_CAI -> when (strength) {
                Strength.STRONG -> "身强遇正财大运，可讨论原局对财星的承载与管理主题，但持续性资源是否形成真实兑现不由十神决定。"
                Strength.BALANCED -> "中和遇正财大运，资源持续与分配秩序之间的解释仍需其他十神和相应位置参与，不能仅凭中和断定财星作用。"
                Strength.WEAK -> "身弱遇正财大运，传统解释会把常态资源主题与承接能力分开：有财星类别不代表有足够生扶条件，也不代表现实资源增减。"
            }
            TenGod.QI_SHA -> when (strength) {
                Strength.STRONG -> "身强遇七杀大运，可讨论约束与日主承载之间的互动可能，但是否出现有效制化需要另有经核验的事件和位置证据。"
                Strength.BALANCED -> "中和遇七杀大运，约束强度与承担能力并未因“中和”而自动匹配；仍须核对根气、印星及其排列。"
                Strength.WEAK -> "身弱遇七杀大运，克制日主的结构更需要检视是否存在生扶和转化依据；不能把七杀理解为现实企业已经承受外部处罚。"
            }
            TenGod.ZHENG_GUAN -> when (strength) {
                Strength.STRONG -> "身强遇正官大运，约束与承接可作为一组结构议题，但是否形成有效官印关系，仍须进一步确认印星的作用位置。"
                Strength.BALANCED -> "中和遇正官大运，规则主题存在而实际协调方向未定，需查看官星力量与其他原局因素，不能用“中和”替代全盘论断。"
                Strength.WEAK -> "身弱遇正官大运，官星约束的传统象义需要与原局支持条件并读，是否有印星生扶及有效配合尚待关系验证。"
            }
            TenGod.PIAN_YIN -> when (strength) {
                Strength.STRONG -> "身强遇偏印大运，同类支持已经较多的情况下，偏印是否继续形成有意义的支持，需要考察食伤等其他因素，不作“印多必好”结论。"
                Strength.BALANCED -> "中和遇偏印大运，知识积累与向外表达能否协调取决于具体结构，单独的偏印标签不能决定任何压制或提升关系。"
                Strength.WEAK -> "身弱遇偏印大运，偏印具有生日主的传统象义，但是否获得实际扶助，仍需结合位置、根气与月令来源核对。"
            }
            TenGod.ZHENG_YIN -> when (strength) {
                Strength.STRONG -> "身强遇正印大运，生扶元素与已有力量并列，解释焦点转向支持是否与输出相协调，不能仅用印星认定结构趋于完善。"
                Strength.BALANCED -> "中和遇正印大运，稳定支撑和消耗输出两端均需兼顾，正印是否构成全盘关键条件还要看其他五行和生克位置。"
                Strength.WEAK -> "身弱遇正印大运，正印具有生日主的传统可能，但实际支持是否成立仍须核对根气、透藏和其他层次，不能将候选扶助写成确定结论。"
            }
        }
    }

    private fun fallbackUnavailable(
        stockId: Long,
        stockCode: String,
        year: Int,
        month: Int,
    ): FiveParagraphInterpretation {
        return FiveParagraphInterpretation(
            stockId = stockId,
            stockCode = stockCode,
            year = year,
            month = month,
            basisText = "原局或流月八字数据不完整，命理依据暂不适用",
            themeText = "流月十神条件缺失，本月主题暂不展开",
            contradictionText = "干支条件不充分，潜在矛盾暂不展开",
            businessText = "信息不完备，企业经营观察暂不展开",
            synthesisText = "本月命理分析暂不可用",
            preciseAdvancedNotice = "柱位数据不完整，相关推演暂行隔离",
            hitRuleIds = listOf("FALLBACK_UNAVAILABLE"),
            reviewStatus = ReviewStatus.PENDING_REVIEW,
            isMock = false,
        )
    }

    /**
     * 核心年度综合总结装配入口（聚合 12 个月离线结构化分析结果）。
     */
    fun composeAnnualSynthesis(
        stockId: Long,
        stockCode: String,
        dayStem: String,
        year: Int,
        yearGanzhi: String,
        strength: Strength,
        currentPeriod: LuckCyclePeriodEntity?,
        monthlyInterpretations: List<FiveParagraphInterpretation>,
        months: List<MonthLabel>,
        yongshen: StockYongshenEntity?,
        natalRelationsCount: Int,
    ): AnnualSynthesis {
        val periodDesc = if (currentPeriod != null) {
            val stemGod = TenGodCalculator.tenGod(dayStem, currentPeriod.stem)
            val branchGod = TenGodCalculator.tenGod(dayStem, TenGodCalculator.mainQi(currentPeriod.branch))
            "流年行入 ${currentPeriod.ganzhi}大运（${currentPeriod.startYear}–${currentPeriod.endYear}年，${currentPeriod.startAge}–${currentPeriod.endAge}岁），运干${stemGod.cn}，运支${branchGod.cn}。"
        } else {
            "未查得对应年份大运区间，或首日平盘/缺失标为不适用。"
        }

        val godCounts = months.groupingBy { it.tenGod.cn }.eachCount().entries
            .sortedByDescending { it.value }
            .joinToString("、") { "${it.key}${it.value}个月" }
        val distSummary = "全年12个月流月主星分布：$godCounts。"

        fun seasonSummary(startIdx: Int): String {
            val sliceMonths = months.drop(startIdx).take(3)
            val sliceInterps = monthlyInterpretations.drop(startIdx).take(3)
            val dominantInSeason = sliceMonths.map { it.tenGod.cn }.distinct().joinToString("、")

            // 深度消费该季度 3 个月的实际月度解读核心观点与经营观察
            val themeHighlights = sliceInterps.mapNotNull { interp ->
                interp.themeText.split("。", "；").firstOrNull { it.isNotBlank() }?.trim()
            }.distinct()

            val combinedThemes = if (themeHighlights.isNotEmpty()) {
                themeHighlights.joinToString("；")
            } else {
                when (startIdx) {
                    0 -> "立春至季春之规划蓄势与基础梳理"
                    3 -> "立夏至季夏之协同发展与业务推进"
                    6 -> "立秋至季秋之规范治理与平稳运行"
                    else -> "立冬至季冬之回顾审视与跨年承接"
                }
            }
            return "主临${dominantInSeason}。该季度月度解读显示：${combinedThemes}。"
        }

        val seasonalThemes = listOf(
            "春季阶段" to seasonSummary(0),
            "夏季阶段" to seasonSummary(3),
            "秋季阶段" to seasonSummary(6),
            "冬季阶段" to seasonSummary(9),
        )

        val summaryParts = mutableListOf<String>()
        summaryParts.add("${year}年岁在${yearGanzhi}，原局日主属${dayStem}，三柱六字口径归为${strength.cn}。")
        val dominantGod = months.groupingBy { it.tenGod.cn }.eachCount().maxByOrNull { it.value }?.key ?: "常态"
        summaryParts.add("全年在十神分布上以${dominantGod}为主轴，12个月五段式分析显示：阶段转换体现从前期投入培育，到中期协同推进，再到后期治理规范的递进过程。")

        val allHitIds = monthlyInterpretations.flatMap { it.hitRuleIds }.toSet()
        val liuheHits = allHitIds.filter { it.startsWith("ADV_LH_") }
        val yongshenHits = allHitIds.filter { it.startsWith("ADV_YS_") }
        if (liuheHits.isNotEmpty()) {
            summaryParts.add("全年中流月与原局/大运形成六合支对互动（命中规则${liuheHits.take(2).joinToString("、")}等），为特定月份提供了结构对照与节奏考量。")
        }
        if (yongshenHits.isNotEmpty()) {
            summaryParts.add("在喜用候选维度，12个月实际推演深度结合了扶抑平衡取向（命中规则${yongshenHits.take(2).joinToString("、")}等），在对应流月形成了要素响应。")
        }

        if (yongshen != null && yongshen.status == "confirmed") {
            summaryParts.add("结合原局已确立扶抑主轴（以${yongshen.yongShen}为用神、${yongshen.xiShen}为喜神），流年岁运在对应五行当值之月形成助益呼应。")
        } else if (yongshen != null && yongshen.status == "candidate") {
            summaryParts.add("结合原局中和平衡格局，流年各月依流通五行（${yongshen.candidateElements}）顺次调节，不执于单一主轴。")
        } else {
            summaryParts.add("原局喜用格局依现行规则保持客观观察，结合各流月干支独立推导。")
        }

        summaryParts.add("年度综合命理分析基于实际12个月离线分析聚合而成，反映传统天干地支符号分类演进，不代表公司实际财务经营或市场走势。")

        return AnnualSynthesis(
            yearGanzhi = yearGanzhi,
            currentPeriodDesc = periodDesc,
            tenGodDistributionSummary = distSummary,
            seasonalThemes = seasonalThemes,
            structuredSummary = summaryParts.joinToString(" "),
            complianceNotice = "命理学术分析仅供参考，不构成任何操作建议。",
            hitRuleSummary = "FortuneCopyEngine-Annual-v1.3 · 真正消费12个月实际五段式分析 (${allHitIds.size}条规则命中参与装配) · 零LLM确定性生成",
        )
    }
}
