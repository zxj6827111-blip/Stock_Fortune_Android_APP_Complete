package com.stockfortune.app.domain.calculator

import com.stockfortune.app.domain.model.Element
import com.stockfortune.app.domain.model.Strength
import com.stockfortune.app.domain.model.YongshenCandidateResult
import com.stockfortune.app.domain.model.YongshenCandidateStatus

/**
 * 喜用候选与格局解释计算器（Kotlin 生产实现）。
 * 与 `tools/yongshen_core.py` 严格保持同源与双向测试对拍。
 */
object YongshenCalculator {

    const val RULE_VERSION = "yongshen-candidate-v1.3"

    private val WU_XING_ORDER = listOf(Element.WOOD, Element.FIRE, Element.EARTH, Element.METAL, Element.WATER)

    private val GENERATED_BY = mapOf(
        Element.WOOD to Element.WATER,
        Element.FIRE to Element.WOOD,
        Element.EARTH to Element.FIRE,
        Element.METAL to Element.EARTH,
        Element.WATER to Element.METAL,
    )

    private val OVERCOME_BY = mapOf(
        Element.WOOD to Element.METAL,
        Element.FIRE to Element.WATER,
        Element.EARTH to Element.WOOD,
        Element.METAL to Element.FIRE,
        Element.WATER to Element.EARTH,
    )

    /**
     * 调候环境观察（独立双轴，仅作为环境描述，不直接污染扶抑五行集合）。
     */
    fun computeTiaohouNote(monthBranch: String): String {
        if (monthBranch.length != 1) return ""
        return when (monthBranch) {
            "亥", "子", "丑" -> "冬月生，天寒地冻，调候宜见火（暖局）"
            "巳", "午", "未" -> "夏月生，火燥水枯，调候宜见水（润局）"
            "辰", "戌" -> "季月土重，调候宜木疏土或水润泽"
            "寅", "卯", "申", "酉" -> "春秋月生，寒暖适中，调候需求平和"
            else -> ""
        }
    }

    /**
     * 基于年月日三柱推导喜用候选结果。
     */
    fun calculate(
        yearPillar: String,
        monthPillar: String,
        dayPillar: String,
    ): YongshenCandidateResult {
        val chartKey = "${yearPillar}_${monthPillar}_${dayPillar}"

        if (yearPillar.length != 2 || monthPillar.length != 2 || dayPillar.length != 2) {
            return YongshenCandidateResult(
                chartKey = chartKey,
                dayStem = dayPillar.firstOrNull()?.toString() ?: "",
                monthBranch = monthPillar.getOrNull(1)?.toString() ?: "",
                strengthScore = 0.0,
                strengthLevel = Strength.BALANCED,
                status = YongshenCandidateStatus.UNAVAILABLE,
                yongShen = emptyList(),
                xiShen = emptyList(),
                jiShen = emptyList(),
                chouShen = emptyList(),
                xianShen = emptyList(),
                candidateElements = emptyList(),
                tiaohouNote = "",
                rationale = "原局柱位数据不完整，暂不适用基础扶抑推导",
                ruleVersion = RULE_VERSION,
            )
        }

        val dayStem = dayPillar[0].toString()
        val monthBranch = monthPillar[1].toString()
        val dmElement = BaziTables.STEM_ELEMENT[dayStem]

        if (dmElement == null) {
            return YongshenCandidateResult(
                chartKey = chartKey,
                dayStem = dayStem,
                monthBranch = monthBranch,
                strengthScore = 0.0,
                strengthLevel = Strength.BALANCED,
                status = YongshenCandidateStatus.UNAVAILABLE,
                yongShen = emptyList(),
                xiShen = emptyList(),
                jiShen = emptyList(),
                chouShen = emptyList(),
                xianShen = emptyList(),
                candidateElements = emptyList(),
                tiaohouNote = "",
                rationale = "未知日干「$dayStem」，暂不适用基础扶抑推导",
                ruleVersion = RULE_VERSION,
            )
        }

        val sameWx = dmElement.cn
        val resourceWx = (GENERATED_BY[dmElement] ?: dmElement).cn
        val outputWx = (BaziTables.GENERATES[dmElement] ?: dmElement).cn
        val wealthWx = (BaziTables.OVERCOMES[dmElement] ?: dmElement).cn
        val officerWx = (OVERCOME_BY[dmElement] ?: dmElement).cn

        val score = TenGodCalculator.strengthScore(yearPillar, monthPillar, dayPillar)
        val level = TenGodCalculator.dayMasterStrength(yearPillar, monthPillar, dayPillar)
        val tiaohou = computeTiaohouNote(monthBranch)

        val status: YongshenCandidateStatus
        val yongShen: List<String>
        val xiShen: List<String>
        val jiShen: List<String>
        val chouShen: List<String>
        val xianShen: List<String>
        val candidates: List<String>
        val rationale: String

        val scoreFormatted = String.format(java.util.Locale.US, "%+.2f", score)

        when (level) {
            Strength.STRONG -> {
                status = YongshenCandidateStatus.CONFIRMED
                yongShen = listOf(officerWx)
                xiShen = listOf(outputWx, wealthWx)
                jiShen = listOf(resourceWx, sameWx)
                chouShen = listOf(resourceWx)
                xianShen = emptyList()
                candidates = listOf(officerWx, outputWx, wealthWx)
                rationale = "日主身强（得分 $scoreFormatted）→ 力量充沛，宜克泄耗：" +
                    "取官杀「$officerWx」为用神，食伤「$outputWx」与财星「$wealthWx」为喜神"
            }
            Strength.WEAK -> {
                status = YongshenCandidateStatus.CONFIRMED
                yongShen = listOf(resourceWx)
                xiShen = listOf(sameWx)
                jiShen = listOf(officerWx, wealthWx)
                chouShen = listOf(outputWx)
                xianShen = emptyList()
                candidates = listOf(resourceWx, sameWx)
                rationale = "日主身弱（得分 $scoreFormatted）→ 力量偏弱，宜生助扶持：" +
                    "取印星「$resourceWx」为用神，比劫「$sameWx」为喜神"
            }
            Strength.BALANCED -> {
                status = YongshenCandidateStatus.CANDIDATE
                yongShen = emptyList()
                xiShen = emptyList()
                jiShen = emptyList()
                chouShen = emptyList()
                xianShen = WU_XING_ORDER.map { it.cn }
                candidates = listOf(outputWx, wealthWx, officerWx)
                rationale = "日主中和（得分 $scoreFormatted）→ 力量均衡无明显偏枯，" +
                    "不设单一扶抑主轴，以岁运顺畅流通为主"
            }
        }

        return YongshenCandidateResult(
            chartKey = chartKey,
            dayStem = dayStem,
            monthBranch = monthBranch,
            strengthScore = score,
            strengthLevel = level,
            status = status,
            yongShen = yongShen,
            xiShen = xiShen,
            jiShen = jiShen,
            chouShen = chouShen,
            xianShen = xianShen,
            candidateElements = candidates,
            tiaohouNote = tiaohou,
            rationale = rationale,
            ruleVersion = RULE_VERSION,
        )
    }
}
