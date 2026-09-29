package com.stockfortune.app.domain.calculator

import com.stockfortune.app.domain.model.TenGod
import com.stockfortune.app.domain.model.WealthType

/**
 * 静态命理文案表。产品边界明确"不做 AI 解释、不做涨跌预测"，
 * 因此全部文案为固定枚举映射，只描述五行关系与传统命理意象，不含任何收益或买卖表述。
 */
object FortuneText {

    /** 日主命理特征（10 干） */
    private val FATE_FEATURE = mapOf(
        "甲" to "参天大木，性直主进，喜土培根、火暖其性",
        "乙" to "藤萝系甲，柔韧善攀，得丙火照、壬水润则荣",
        "丙" to "太阳之火，光明外达，喜壬水既济、金水相涵",
        "丁" to "灯烛之火，内明而细，遇甲木引、壬水护则久",
        "戊" to "城墙厚土，承载万物，喜甲木疏、丙火暖、癸水润",
        "己" to "田园之土，卑湿涵养，得丙火暖、甲木疏则活",
        "庚" to "刀剑之金，刚锐主断，遇丁火炼、甲木成则器",
        "辛" to "珠玉之金，清润喜洁，得壬水淘、丙火照则明",
        "壬" to "江河之水，周流不滞，喜戊土堤、庚金发源",
        "癸" to "雨露之水，滋润无声，得庚辛源、丙火暖则生",
    )

    fun fateFeature(dayStem: String): String = FATE_FEATURE[dayStem] ?: "五行流转，气机中和"

    /** 年度概览：整体财运 */
    fun yearWealthSummary(wealth: WealthType, yearGanzhi: String): String = when (wealth) {
        WealthType.ZHENG_CAI -> "${yearGanzhi}年财星透干，正财为主，传统口径视为稳健之象"
        WealthType.PIAN_CAI -> "${yearGanzhi}年财星透干，偏财为主，传统口径视为流动之象"
        WealthType.OTHER -> "${yearGanzhi}年干支以它星当值，无明显财星透干"
        WealthType.NONE -> "${yearGanzhi}年干支与日主无直接财星关系"
    }

    private val GEN = mapOf("木" to "火", "火" to "土", "土" to "金", "金" to "水", "水" to "木")
    private val OVER = mapOf("木" to "土", "土" to "水", "水" to "火", "火" to "金", "金" to "木")

    fun yearIndustryNote(dayStem: String, yearBranch: String): String {
        val dayEl = BaziTables.STEM_ELEMENT[dayStem]?.cn ?: return "流年气机与日主关系平和"
        val yearEl = BaziTables.BRANCH_ELEMENT[yearBranch]?.cn ?: return "流年气机与日主关系平和"
        return when {
            dayEl == yearEl -> "流年$yearEl 与日主同类比和，气机同频"
            GEN[yearEl] == dayEl -> "流年$yearEl 生日主$dayEl，相生得助之象"
            GEN[dayEl] == yearEl -> "日主$dayEl 生流年$yearEl，气机外泄之象"
            OVER[dayEl] == yearEl -> "日主$dayEl 克流年$yearEl，我克为财、主动求财之象"
            OVER[yearEl] == dayEl -> "流年$yearEl 克日主$dayEl，克我为官杀、受制之象"
            else -> "流年$yearEl 与日主$dayEl 关系平和"
        }
    }

    fun yearAdvice(wealth: WealthType): String = when (wealth) {
        WealthType.ZHENG_CAI -> "正财当值之岁，传统研究口径重节奏与守成"
        WealthType.PIAN_CAI -> "偏财当值之岁，传统研究口径重机动与取舍"
        else -> "无明显财星之年，传统研究口径以观察为主"
    }

    fun monthSummary(monthGanzhi: String, tenGod: TenGod, wealth: WealthType, monthBranch: String): String {
        val season = TenGodCalculator.seasonSummary(monthBranch)
        val rel = when (wealth) {
            WealthType.ZHENG_CAI -> "${monthGanzhi}透正财，财星显象，传统口径主稳健"
            WealthType.PIAN_CAI -> "${monthGanzhi}透偏财，财星显象，传统口径主机动"
            else -> "${monthGanzhi}以${tenGod.cn} 当值，无财星透干"
        }
        return "$rel；月令$season"
    }

    fun monthTip(zhengDays: Int, pianDays: Int): String =
        if (zhengDays == 0 && pianDays == 0) {
            "本月无明显财日，传统口径以观察为主。"
        } else {
            "本月正财日 $zhengDays 天、偏财日 $pianDays 天；传统口径中正财主守成、偏财主流动，仅供研究参考。"
        }

    /**
     * 单日财星判定的推导链（Rule v1.1：透干优先，判定域 = 天干 + 地支本气）。
     * 只陈述干支与十神关系，不含涨跌或买卖表述。口径依据见 PHASE0_DESIGN_REVIEW §8。
     */
    fun wealthBasis(
        dayStem: String,
        ganzhi: String,
        stem: String,
        branch: String,
        wealth: WealthType,
        isTradeDay: Boolean,
    ): List<String> {
        if (!isTradeDay) return listOf("该日非交易日，按产品口径不作财星判定")
        val out = mutableListOf<String>()
        val wealthStems = TenGodCalculator.wealthStems(dayStem)
            .joinToString("、") { "$it（${TenGodCalculator.tenGod(dayStem, it).cn}）" }
        out += "日主 $dayStem${TenGodCalculator.elementOf(dayStem)}，我克者为财 → 财星为 $wealthStems"
        val stemGod = TenGodCalculator.tenGod(dayStem, stem)
        val main = TenGodCalculator.mainQi(branch)
        val branchGod = TenGodCalculator.tenGod(dayStem, main)
        out += "该日 $ganzhi：天干 $stem 为${stemGod.cn}，地支 $branch 本气 $main 为${branchGod.cn}"
        if (wealth.isWealth) {
            out += if (stemGod.isWealth) "→ 财星透于天干，判定为${wealth.cn}"
            else "→ 天干不透，地支本气 $main 为${wealth.cn}，判定为${wealth.cn}"
        } else {
            out += "→ 天干与地支本气均不见财星，判定为其他"
            val hiddenWealth = TenGodCalculator.hiddenStems(branch).drop(1)
                .map { it to TenGodCalculator.tenGod(dayStem, it) }
                .filter { it.second.isWealth }
                .joinToString("、") { "${it.first}（${TenGodCalculator.hiddenRank(branch, it.first)}·${it.second.cn}）" }
            if (hiddenWealth.isNotEmpty()) out += "$branch 中另藏 $hiddenWealth，藏而不透，按口径不计入判定"
        }
        return out
    }

    /** 统计卡副标题：只描述传统命理意象，不含收益或操作暗示（见 ComplianceTextTest 门禁）。 */
    val SCAN_ZHENG_NOTE = "正财 · 传统口径主守成"
    val SCAN_PIAN_NOTE = "偏财 · 传统口径主流动"
}
