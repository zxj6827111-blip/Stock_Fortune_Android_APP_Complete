package com.stockfortune.app.domain.model

/**
 * 喜用候选状态枚举（V1.3 冻结契约 ADR-0006）。
 */
enum class YongshenCandidateStatus(val code: String, val label: String) {
    CONFIRMED("confirmed", "已确立"),     // 扶抑明确且有单一同党/异党主轴
    CANDIDATE("candidate", "候选待择"),   // 中和或多五行力量平衡，输出候选集
    UNAVAILABLE("unavailable", "暂不适用"); // 特殊从格、化气或六字未覆盖结构

    companion object {
        fun fromCode(code: String?): YongshenCandidateStatus =
            entries.firstOrNull { it.code == code } ?: UNAVAILABLE
    }
}

/**
 * 喜用候选与格局解释结果（扶抑主轴与调候双轴解耦）。
 */
data class YongshenCandidateResult(
    val chartKey: String,
    val dayStem: String,
    val monthBranch: String,
    val strengthScore: Double,
    val strengthLevel: Strength,
    val status: YongshenCandidateStatus,
    val yongShen: List<String>,
    val xiShen: List<String>,
    val jiShen: List<String>,
    val chouShen: List<String>,
    val xianShen: List<String>,
    val candidateElements: List<String>,
    val tiaohouNote: String,
    val rationale: String,
    val ruleVersion: String = "yongshen-candidate-v1.3",
)

/**
 * 典雅与合规术语映射器（ADR-0007）。
 * 在展示层完全剔除「忌」字等敏感字眼，将底层学术术语映射为合规典雅词汇。
 */
object LabelMapper {

    /** 术语标签合规映射 */
    fun mapShenLabel(rawShen: String): String = when (rawShen) {
        "用神" -> "用神"
        "喜神" -> "喜神"
        "忌神" -> "制衡之神"
        "仇神" -> "耗身之神"
        "闲神" -> "调和之神"
        else -> rawShen
    }

    /** 面向 UI 的安全典雅喜用概述文案（严格零禁词，绝不出现「忌」字） */
    fun formatDisplaySummary(result: YongshenCandidateResult): String {
        return when (result.status) {
            YongshenCandidateStatus.CONFIRMED -> {
                val yong = result.yongShen.joinToString("、")
                val xi = result.xiShen.joinToString("、")
                val zhiheng = result.jiShen.joinToString("、")
                val haoshen = result.chouShen.joinToString("、")
                buildString {
                    append("用神：$yong")
                    if (xi.isNotEmpty()) append(" | 喜神：$xi")
                    if (zhiheng.isNotEmpty()) append(" | 制衡：$zhiheng")
                    if (haoshen.isNotEmpty()) append(" | 耗身：$haoshen")
                }
            }
            YongshenCandidateStatus.CANDIDATE -> {
                val candidates = result.candidateElements.joinToString("、")
                "格局中和平衡，不设单一主轴，岁运调节候选：$candidates"
            }
            YongshenCandidateStatus.UNAVAILABLE -> {
                "暂不适用基础扶抑推导"
            }
        }
    }
}
