package com.stockfortune.app.domain.model

/** 十神（子平口径）。DB 与界面统一使用中文标签，枚举仅用于类型安全。 */
enum class TenGod(val cn: String) {
    BI_JIAN("比肩"), JIE_CAI("劫财"), SHI_SHEN("食神"), SHANG_GUAN("伤官"),
    PIAN_CAI("偏财"), ZHENG_CAI("正财"), QI_SHA("七杀"), ZHENG_GUAN("正官"),
    PIAN_YIN("偏印"), ZHENG_YIN("正印");

    companion object {
        // 经典顺序，与效果图 07 的网格排布一致
        val ORDER = listOf(BI_JIAN, JIE_CAI, SHI_SHEN, SHANG_GUAN, ZHENG_CAI, PIAN_CAI, ZHENG_GUAN, QI_SHA, ZHENG_YIN, PIAN_YIN)
        fun fromCn(s: String?): TenGod? = entries.firstOrNull { it.cn == s }
    }

    val isWealth get() = this == ZHENG_CAI || this == PIAN_CAI
}

/** 财星判定结果。 */
enum class WealthType(val cn: String) {
    ZHENG_CAI("正财"), PIAN_CAI("偏财"), OTHER("其他"), NONE("无");

    val isWealth get() = this == ZHENG_CAI || this == PIAN_CAI

    companion object {
        fun fromCn(s: String?): WealthType = entries.firstOrNull { it.cn == s } ?: OTHER
    }
}

enum class Element(val cn: String) { WOOD("木"), FIRE("火"), EARTH("土"), METAL("金"), WATER("水") }

/** 股票基础信息 + 八字（预置库直读）。 */
data class StockInfo(
    val id: Long,
    val code: String,
    val symbol: String,
    val name: String,
    val exchange: String,
    val board: String,
    val listingDate: String,
    /** 一级行业，用于列表与标签 */
    val industry: String,
    /** 完整三级行业，如"食品饮料-饮料-白酒" */
    val industryFull: String,
    val stockNature: String,
    val firstDayFlag: String,
    val firstOpen: Double?,
    val firstClose: Double?,
    val firstChange: Double?,
)

data class BaziChart(
    val stockId: Long,
    val fullBazi: String,
    val yearPillar: String,
    val monthPillar: String,
    val dayPillar: String,
    val hourPillar: String,
    val dayMaster: String,
    val naYin: String,
) {
    val dayStem get() = dayPillar.takeIf { it.length == 2 }?.get(0)?.toString() ?: ""
    val dayBranch get() = dayPillar.takeIf { it.length == 2 }?.get(1)?.toString() ?: ""
    val monthBranch get() = monthPillar.takeIf { it.length == 2 }?.get(1)?.toString() ?: ""
}

data class PillarDisplay(val label: String, val ganzhi: String, val stemElement: String, val branchElement: String)

/** 某一天的干支快照（来自 ganzhi_calendar 表）。 */
data class GanzhiDay(
    val date: String,
    val yearGanzhi: String,
    val monthGanzhi: String,
    val dayGanzhi: String,
    val yearStem: String,
    val yearBranch: String,
    val monthStem: String,
    val monthBranch: String,
    val dayStem: String,
    val dayBranch: String,
    val monthBranchLabel: String,
    val solarTerm: String?,
)

data class DayAnalysis(
    val date: String,
    val weekday: Int,
    val isTradeDay: Boolean,
    val dayGanzhi: String,
    val dayStem: String,
    val dayBranch: String,
    /** 流日天干十神 = 每日分析页的"天干十神"列（不含地支本气，故可能与"财星"列不同） */
    val dayTenGod: TenGod,
    /** 流日地支本气十神 */
    val branchTenGod: TenGod,
    /** 财星判定（透干优先，其次本气） */
    val wealth: WealthType,
    val solarTerm: String?,
) {
    val wealthIsHidden get() = wealth.isWealth && dayTenGod.cn != wealth.cn
}

/** 流运维度（流年 / 流月 / 流日）：十神一律以该股日主为参照。 */
data class FlowPillar(
    val label: String,
    val ganzhi: String,
    val elements: String,
    /** 天干十神 */
    val stemGod: TenGod,
    /** 地支本气十神 */
    val branchGod: TenGod,
    val wealth: WealthType,
)

data class HiddenStemItem(val stem: String, val god: TenGod, val rank: String)

/** 本命四柱中一根地支的藏干十神。 */
data class PillarHidden(val label: String, val branch: String, val items: List<HiddenStemItem>)

/** 单日详情：股票 × 日期 的四维十神快照与财星判定推导。 */
data class DayDetail(
    val dayMaster: String,
    val dayMasterElement: String,
    val date: String,
    val isTradeDay: Boolean,
    val solarTerm: String?,
    val flows: List<FlowPillar>,
    val hidden: List<PillarHidden>,
    /** 四柱藏干去重后的十神集合，按经典顺序 */
    val hiddenGods: List<TenGod>,
    val wealth: WealthType,
    /** Rule v1.1 推导链，逐行展示"为什么是这个判定" */
    val basis: List<String>,
) {
    val dayFlow get() = flows.lastOrNull()
}

data class MonthLabel(
    val month: Int,
    val monthGanzhi: String,
    val branchLabel: String,
    val wealth: WealthType,
    val tenGod: TenGod,
)

data class YearAnalysis(
    val year: Int,
    val yearGanzhi: String,
    val yearWuxing: String,
    val wealthSummary: String,
    val industryNote: String,
    val advice: String,
    val months: List<MonthLabel>,
    val zhengCount: Int,
    val pianCount: Int,
)

data class MonthAnalysis(
    val year: Int,
    val month: Int,
    val monthGanzhi: String,
    val branchLabel: String,
    val monthStemTenGod: TenGod,
    val wuxingSummary: String,
    val summary: String,
    val days: List<DayAnalysis>,
    val tradeDays: List<DayAnalysis>,
    val zhengCount: Int,
    val pianCount: Int,
    val otherCount: Int,
    val tradeDayCount: Int,
    val tip: String,
    /** 公历月跨两个干支月时的口径说明（月初那几天属上一干支月） */
    val monthNote: String? = null,
)

data class ScanRow(
    val stockId: Long,
    val code: String,
    val symbol: String,
    val name: String,
    val industry: String,
    val wealth: WealthType,
    val dayTenGod: TenGod,
    val fromHiddenStem: Boolean,
    /** 十神筛选：本次命中用户所选的十神（扫描页为空） */
    val matchedGods: List<TenGod> = emptyList(),
)

data class ScanSummary(
    val date: String,
    val rows: List<ScanRow>,
    val zhengCount: Int,
    val pianCount: Int,
)

data class DateSelectionRow(
    val date: String,
    val weekday: Int,
    val isTradeDay: Boolean,
    val dayGanzhi: String,
    val wealth: WealthType,
    val dayTenGod: TenGod,
)

data class FilterQuery(
    val hiddenGods: Set<TenGod>,
    val yearGods: Set<TenGod>,
    val monthGods: Set<TenGod>,
    val dayGods: Set<TenGod>,
    val date: String,
) {
    val isEmpty get() = hiddenGods.isEmpty() && yearGods.isEmpty() && monthGods.isEmpty() && dayGods.isEmpty()
}

data class SearchHit(val stockId: Long, val code: String, val symbol: String, val name: String, val listingDate: String, val board: String)
