package com.stockfortune.app.domain.model

/**
 * 22 类关系目录定义（bazi-relation-v3 / V1.3 冻结契约）。
 * 分为天干 7 类、地支 11 类、组合 4 类。
 */
enum class RelationType(val code: String, val cn: String, val category: String) {
    // 天干 7 类
    TIANGAN_HE("tiangan_he", "天干五合", "干合"),
    TIANGAN_CHONG("tiangan_chong", "天干相冲", "干冲"),
    TIANGAN_SHENG("tiangan_sheng", "天干生", "生克"),
    TIANGAN_SHOU_SHENG("tiangan_shou_sheng", "天干受生", "生克"),
    TIANGAN_KE("tiangan_ke", "天干克", "生克"),
    TIANGAN_SHOU_KE("tiangan_shou_ke", "天干受克", "生克"),
    TIANGAN_TONG_WUXING("tiangan_tong_wuxing", "天干同五行", "生克"),

    // 地支 11 类
    DIZHI_LIUHE("dizhi_liuhe", "六合", "支合"),
    DIZHI_SANHE("dizhi_sanhe", "三合", "支合"),
    DIZHI_BANHE("dizhi_banhe", "半合", "支合"),
    DIZHI_SANHUI("dizhi_sanhui", "三会", "支合"),
    DIZHI_LIUCHONG("dizhi_liuchong", "六冲", "支冲"),
    DIZHI_XING_SAN("dizhi_xing_san", "三刑", "支刑"),
    DIZHI_XING_XIANG("dizhi_xing_xiang", "相刑", "支刑"),
    DIZHI_XING_ZI("dizhi_xing_zi", "自刑", "支刑"),
    DIZHI_XIANGHAI("dizhi_xianghai", "相害", "支害"),
    DIZHI_XIANGPO("dizhi_xiangpo", "六破", "支破"),
    DIZHI_TONG_ZHI("dizhi_tong_zhi", "同支", "支同"),

    // 组合 4 类
    TIANHE_DIHE("tianhe_dihe", "天合地合", "复合"),
    TIANKE_DICHONG("tianke_dichong", "天克地冲", "复合"),
    FUYIN("fuyin", "伏吟", "特殊"),
    FANYIN("fanyin", "反吟", "特殊");

    companion object {
        fun fromCn(cn: String?): RelationType? = entries.firstOrNull { it.cn == cn }
        fun fromCode(code: String?): RelationType? = entries.firstOrNull { it.code == code }
    }
}

/**
 * 结构化关系事件对象。
 */
data class RelationEvent(
    val relationType: RelationType,
    val category: String,
    val sourcePillar: String,
    val targetPillar: String,
    val sourceGanzhi: String,
    val targetGanzhi: String,
    val element: String? = null,
    val notes: String = "",
    val ruleVersion: String = "natal-relation-v1.3",
    val status: String = "confirmed",
)

/**
 * 外部柱与原局单柱的交叉单元格。
 */
data class RelationCell(
    val sourcePillar: String,
    val targetPillar: String,
    val sourceGanzhi: String,
    val targetGanzhi: String,
    val events: List<RelationEvent> = emptyList(),
) {
    val relationTypes: List<String> get() = events.map { it.relationType.cn }.distinct()
}

/**
 * 关系矩阵的一行（如流年行、流月行、大运行）。
 */
data class RelationMatrixRow(
    val sourcePillar: String,
    val sourceGanzhi: String,
    val cells: List<RelationCell> = emptyList(),
) {
    val relationTypes: List<String> get() = cells.flatMap { it.events }.map { it.relationType.cn }.distinct()
}

/**
 * 严格 3×3 交互关系矩阵。
 */
data class RelationMatrix(
    val rows: List<RelationMatrixRow> = emptyList(),
    val columns: List<String> = listOf("year", "month", "day"),
    val ruleVersion: String = "natal-relation-v1.3",
)
