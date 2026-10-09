package com.stockfortune.app.domain.calculator

import com.stockfortune.app.domain.model.Strength
import com.stockfortune.app.domain.model.TenGod
import com.stockfortune.app.domain.model.WealthType

/**
 * 静态命理文案表。产品边界明确"不做 AI 解释、不做涨跌预测"，
 * 因此全部文案为固定枚举映射，只描述五行关系与传统命理意象，不含任何收益或买卖表述。
 */
object FortuneText {

    /**
     * 日主命理特征（10 干）。每条只据《滴天髓輯要·天干論》对应小节的原注节选改写，
     * 选段见 tools/classics_selection.py，逐字出处见 docs/CLASSICS_AUDIT_ditiansui_v1.md。
     * 原注未说的喜忌（如「喜甲木疏」「得壬水淘」）一律不写；界面须标「本项目概述」，不挂书名。
     */
    private val FATE_FEATURE = mapOf(
        "甲" to "根干之木，纯阳之本，取参天雄壮之象",
        "乙" to "枝叶之木，质性柔如花卉",
        "丙" to "焚烈之火，取其纯阳之性",
        "丁" to "温煖之火，性虽烈而属阴，柔而得中，外柔顺而内文明",
        "戊" to "山冈之土，非城墙之谓，较己土高厚刚燥，为己土发源地",
        "己" to "田园之土，其性卑湿，为戊土枝叶之地，亦主中正、蓄藏万物",
        "庚" to "阳金，太白之精，带煞而刚健",
        "辛" to "阴金，非珠玉之谓，性温柔清润",
        "壬" to "癸水之源，有分有合，运行不息，为百川亦为雨露",
        "癸" to "纯阴而至弱，然能上达天津",
    )

    fun fateFeature(dayStem: String): String = FATE_FEATURE[dayStem] ?: "五行流转，气机中和"

    /**
     * 白话子句：流年 / 流月天干的十神 × 日主强弱，10 × 3 = 30 格。
     *
     * 拆成 30 格而不是 10 格，是因为同一个十神在身强身弱下读法相反 —— 比肩于身弱是帮身，
     * 于身强是分财；印于身弱是护身，于身旺是因循。这正是 Rule v1.2 那根强弱轴的用处。
     *
     * 尺度（与产品边界一致）：只讲机制与传统口径的两面性，不给操作建议、不说窗口期与排名。
     * 「ComplianceTextTest.advisoryBanned」会把「忌 / 不宜 / 适合 / 应 / 勿」这类指令句式拦下，
     * 新增格子时不必自己记着规避，写错了测试会红。
     *
     * 全部自写，只借子平通说词条（帮身 / 夺财 / 泄秀 / 制杀为权 / 富屋贫人 / 偏印夺食…），
     * 《滴天髓輯要》里没有对应原句，所以界面一律标「本项目概述」，不挂书名。
     */
    private val PLAIN: Map<Pair<TenGod, Strength>, String> = mapOf(
        TenGod.BI_JIAN to Strength.STRONG to
            "本已不弱又添一分，帮身与分财原是一件事的两面，此时分的一面更重",
        TenGod.BI_JIAN to Strength.BALANCED to
            "同辈并列，合作与竞争同时到位，传统口径既谓比肩帮身，也谓比肩分财",
        TenGod.BI_JIAN to Strength.WEAK to
            "同辈之力来帮身，助力之外仍带分财一层，得助与让利并行",
        TenGod.JIE_CAI to Strength.STRONG to
            "旺而又旺，传统口径谓劫财夺财，争竞之象重于帮身之益",
        TenGod.JIE_CAI to Strength.BALANCED to
            "主行动亦主争夺，人事往来密集，得力与破耗同在一处",
        TenGod.JIE_CAI to Strength.WEAK to
            "异类同我者来帮身，但劫财夺财之名未去，出力与分利相杂",
        TenGod.SHI_SHEN to Strength.STRONG to
            "旺气有了出口，传统口径谓食神生财，此时泄身反成泄秀",
        TenGod.SHI_SHEN to Strength.BALANCED to
            "主技艺、表达与生财之源，传统口径谓我生者为子孙",
        TenGod.SHI_SHEN to Strength.WEAK to
            "元气外泄，传统口径谓才华显而内气耗，多作多思愈见其劳",
        TenGod.SHANG_GUAN to Strength.STRONG to
            "泄身有力，传统口径既谓伤官生财，也谓伤官见官，锋芒与是非同来",
        TenGod.SHANG_GUAN to Strength.BALANCED to
            "主创意亦主变动，传统口径许其秀气流行，也以其不拘名分为病",
        TenGod.SHANG_GUAN to Strength.WEAK to
            "泄身更甚，传统口径谓聪明外露而内气先亏，多成多败皆起于动",
        TenGod.PIAN_CAI to Strength.STRONG to
            "我克者得力，传统口径谓身强能任财，偏财主流动、主众人之财",
        TenGod.PIAN_CAI to Strength.BALANCED to
            "财路活而不专一，传统口径谓偏财来去皆快、得之不以常",
        TenGod.PIAN_CAI to Strength.WEAK to
            "财在当前而身不任财，传统口径谓财多身弱，账面之旺与担荷之力两不相当",
        TenGod.ZHENG_CAI to Strength.STRONG to
            "身强能任财，主有常、以勤而得之入，传统口径谓正财为稳定之有",
        TenGod.ZHENG_CAI to Strength.BALANCED to
            "财路以常以勤为主，传统口径谓正财乃身外之有，得之以其分",
        TenGod.ZHENG_CAI to Strength.WEAK to
            "有财之名而无担之实，传统口径谓财多身弱、富屋贫人",
        TenGod.QI_SHA to Strength.STRONG to
            "传统口径谓身强制杀为权，压力可化为攻坚之力",
        TenGod.QI_SHA to Strength.BALANCED to
            "主魄力亦主耗神，传统口径谓制杀为权、遇杀为祸，只看担不担得住",
        TenGod.QI_SHA to Strength.WEAK to
            "压力与责任先于其用，传统口径谓杀重身轻",
        TenGod.ZHENG_GUAN to Strength.STRONG to
            "传统口径谓身强任官，名分与职分俱来，约束即是位置",
        TenGod.ZHENG_GUAN to Strength.BALANCED to
            "主名分、约束与秩序，传统口径谓官者管也，管人者亦被人管",
        TenGod.ZHENG_GUAN to Strength.WEAK to
            "约束之力大于受用之力，传统口径谓官重身轻",
        TenGod.ZHENG_YIN to Strength.STRONG to
            "身已不弱又得生，传统口径谓印旺身旺，反主因循少变",
        TenGod.ZHENG_YIN to Strength.BALANCED to
            "主资源、荫护与学业，传统口径谓印为我之所依",
        TenGod.ZHENG_YIN to Strength.WEAK to
            "得荫护身，传统口径亦谓印能代人作主，得助与让出主导并看",
        TenGod.PIAN_YIN to Strength.STRONG to
            "旺而又得生，传统口径谓偏印夺食，生身之力反碍生财之路",
        TenGod.PIAN_YIN to Strength.BALANCED to
            "主专长、冷门与多思，传统口径谓偏印所长在偏、所短亦在偏",
        TenGod.PIAN_YIN to Strength.WEAK to
            "偏印生身可济弱，传统口径亦谓其性孤而思虑多",
    )

    private fun plainClause(god: TenGod, strength: Strength): String =
        PLAIN[god to strength] ?: error("PLAIN 缺 $god × $strength 格，30 格必须写全")

    /** 年度概览：整体财运。「透干」只在天干本身为财时成立，否则按 Rule v1.1 记「藏支」。 */
    fun yearWealthSummary(
        wealth: WealthType, yearGanzhi: String, yearStemGod: TenGod, strength: Strength,
    ): String {
        val head = when (wealth) {
            WealthType.ZHENG_CAI, WealthType.PIAN_CAI ->
                "${yearGanzhi}年" + (if (yearStemGod.isWealth) "财星透干" else "财星藏支") +
                    "，${wealth.cn}为主，传统口径视为" +
                    (if (wealth == WealthType.ZHENG_CAI) "稳健" else "流动") + "之象"
            WealthType.OTHER -> "${yearGanzhi}年以${yearStemGod.cn}当值，干支皆非财星"
            WealthType.NONE -> "${yearGanzhi}年干支与日主无直接财星关系"
        }
        return "$head；${plainClause(yearStemGod, strength)}"
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

    fun monthSummary(
        monthGanzhi: String, tenGod: TenGod, wealth: WealthType, monthBranch: String,
        strength: Strength,
    ): String {
        val season = TenGodCalculator.seasonSummary(monthBranch)
        val tone = if (wealth == WealthType.ZHENG_CAI) "稳健" else "机动"
        val rel = when (wealth) {
            // tenGod 是月干的十神：它本身为财才叫「透」，否则财只藏在地支本气里。
            WealthType.ZHENG_CAI, WealthType.PIAN_CAI ->
                if (tenGod.isWealth) "${monthGanzhi}透${wealth.cn}，财星显象，传统口径主$tone"
                else "${monthGanzhi}${wealth.cn}藏支，传统口径主$tone"
            else -> "${monthGanzhi}以${tenGod.cn}当值，干支皆非财星"
        }
        return "$rel；${plainClause(tenGod, strength)}；月令$season"
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
