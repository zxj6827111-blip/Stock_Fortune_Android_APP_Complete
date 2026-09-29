package com.stockfortune.app.domain.calculator

import com.stockfortune.app.domain.model.Element
import com.stockfortune.app.domain.model.TenGod
import com.stockfortune.app.domain.model.WealthType

/**
 * 十神 / 藏干 / 纳音 / 五行旺相内核。
 * 常量表与 `tools/bazi_core.py` 一一对应，任一侧修改必须同步，并由
 * `BaziCoreTest` 与 `tools/verify_database.py` 双向校验。
 */
object BaziTables {
    val STEMS = listOf("甲", "乙", "丙", "丁", "戊", "己", "庚", "辛", "壬", "癸")
    val BRANCHES = listOf("子", "丑", "寅", "卯", "辰", "巳", "午", "未", "申", "酉", "戌", "亥")

    val STEM_ELEMENT = mapOf(
        "甲" to Element.WOOD, "乙" to Element.WOOD, "丙" to Element.FIRE, "丁" to Element.FIRE,
        "戊" to Element.EARTH, "己" to Element.EARTH, "庚" to Element.METAL, "辛" to Element.METAL,
        "壬" to Element.WATER, "癸" to Element.WATER,
    )
    val BRANCH_ELEMENT = mapOf(
        "子" to Element.WATER, "丑" to Element.EARTH, "寅" to Element.WOOD, "卯" to Element.WOOD,
        "辰" to Element.EARTH, "巳" to Element.FIRE, "午" to Element.FIRE, "未" to Element.EARTH,
        "申" to Element.METAL, "酉" to Element.METAL, "戌" to Element.EARTH, "亥" to Element.WATER,
    )
    /** 甲丙戊庚壬为阳 */
    fun stemYang(stem: String) = STEMS.indexOf(stem) % 2 == 0

    val GENERATES = mapOf(
        Element.WOOD to Element.FIRE, Element.FIRE to Element.EARTH, Element.EARTH to Element.METAL,
        Element.METAL to Element.WATER, Element.WATER to Element.WOOD,
    )
    val OVERCOMES = mapOf(
        Element.WOOD to Element.EARTH, Element.EARTH to Element.WATER, Element.WATER to Element.FIRE,
        Element.FIRE to Element.METAL, Element.METAL to Element.WOOD,
    )

    /** 地支藏干（本气 / 中气 / 余气） */
    val HIDDEN_STEMS = mapOf(
        "子" to listOf("癸"), "丑" to listOf("己", "癸", "辛"), "寅" to listOf("甲", "丙", "戊"),
        "卯" to listOf("乙"), "辰" to listOf("戊", "乙", "癸"), "巳" to listOf("丙", "庚", "戊"),
        "午" to listOf("丁", "己"), "未" to listOf("己", "丁", "乙"), "申" to listOf("庚", "壬", "戊"),
        "酉" to listOf("辛"), "戌" to listOf("戊", "辛", "丁"), "亥" to listOf("壬", "甲"),
    )
    val HIDDEN_RANKS = listOf("本气", "中气", "余气")

    /** 60 甲子纳音，两柱共享一条 */
    val NA_YIN_PAIRS = listOf(
        "海中金", "炉中火", "大林木", "路旁土", "剑锋金", "山头火", "涧下水", "城头土", "白蜡金", "杨柳木",
        "泉中水", "屋上土", "霹雳火", "松柏木", "长流水", "沙中金", "山下火", "平地木", "壁上土", "金箔金",
        "覆灯火", "天河水", "大驿土", "钗钏金", "桑柘木", "大溪水", "沙中土", "天上火", "石榴木", "大海水",
    )

    /** 五鼠遁：日起时干 */
    val HOUR_START = mapOf(
        "甲" to 0, "己" to 0, "乙" to 2, "庚" to 2, "丙" to 4,
        "辛" to 4, "丁" to 6, "壬" to 6, "戊" to 8, "癸" to 8,
    )

    /** 五虎遁：年起月干（以寅月为正月） */
    val MONTH_START = mapOf(
        "甲" to 2, "己" to 2, "乙" to 4, "庚" to 4, "丙" to 6,
        "辛" to 6, "丁" to 8, "壬" to 8, "戊" to 0, "癸" to 0,
    )
}

private val T = BaziTables

object TenGodCalculator {

    fun sexagenaryIndex(ganzhi: String): Int {
        if (ganzhi.length != 2) return -1
        val s = T.STEMS.indexOf(ganzhi[0].toString())
        val b = T.BRANCHES.indexOf(ganzhi[1].toString())
        if (s < 0 || b < 0) return -1
        for (i in 0 until 60) if (i % 10 == s && i % 12 == b) return i
        return -1
    }

    fun ganzhiOfIndex(i: Int): String {
        val n = ((i % 60) + 60) % 60
        return T.STEMS[n % 10] + T.BRANCHES[n % 12]
    }

    fun naYin(ganzhi: String): String {
        val idx = sexagenaryIndex(ganzhi)
        if (idx < 0) return ""
        return T.NA_YIN_PAIRS[idx / 2]
    }

    /** otherStem 相对日主的十神 */
    fun tenGod(dayStem: String, otherStem: String): TenGod {
        val dw = T.STEM_ELEMENT[dayStem] ?: return TenGod.BI_JIAN
        val ow = T.STEM_ELEMENT[otherStem] ?: return TenGod.BI_JIAN
        val same = T.stemYang(dayStem) == T.stemYang(otherStem)
        return when {
            dw == ow -> if (same) TenGod.BI_JIAN else TenGod.JIE_CAI
            T.GENERATES[dw] == ow -> if (same) TenGod.SHI_SHEN else TenGod.SHANG_GUAN
            T.OVERCOMES[dw] == ow -> if (same) TenGod.PIAN_CAI else TenGod.ZHENG_CAI
            T.GENERATES[ow] == dw -> if (same) TenGod.PIAN_YIN else TenGod.ZHENG_YIN
            T.OVERCOMES[ow] == dw -> if (same) TenGod.QI_SHA else TenGod.ZHENG_GUAN
            else -> TenGod.BI_JIAN
        }
    }

    fun hiddenStems(branch: String): List<String> = T.HIDDEN_STEMS[branch] ?: emptyList()
    fun mainQi(branch: String): String = hiddenStems(branch).firstOrNull() ?: ""

    /** 藏干在该支中的气位：本气 / 中气 / 余气 */
    fun hiddenRank(branch: String, stem: String): String {
        val i = hiddenStems(branch).indexOf(stem)
        return if (i < 0) "" else T.HIDDEN_RANKS.getOrElse(i) { "余气" }
    }

    /** 日主的财星天干（我克者为财），如癸水 → 丙、丁 */
    fun wealthStems(dayStem: String): List<String> {
        val target = T.OVERCOMES[T.STEM_ELEMENT[dayStem]] ?: return emptyList()
        return T.STEMS.filter { T.STEM_ELEMENT[it] == target }
    }

    fun hiddenTenGods(dayStem: String, branches: List<String>): Set<TenGod> =
        branches.flatMap { hiddenStems(it) }.map { tenGod(dayStem, it) }.toSet()

    /**
     * 单柱财星判定（Rule v1.1，透干优先）：
     * 先看天干十神是否为财，再看地支本气十神。判定域不含全部藏干，
     * 藏干十神作为独立筛选维度。口径依据见 PHASE0_DESIGN_REVIEW §8。
     */
    fun wealthType(dayStem: String, stem: String, branch: String): WealthType {
        if (stem.isNotEmpty()) {
            val g = tenGod(dayStem, stem)
            if (g == TenGod.ZHENG_CAI || g == TenGod.PIAN_CAI) return if (g == TenGod.ZHENG_CAI) WealthType.ZHENG_CAI else WealthType.PIAN_CAI
        }
        if (branch.isNotEmpty()) {
            val g = tenGod(dayStem, mainQi(branch))
            if (g == TenGod.ZHENG_CAI || g == TenGod.PIAN_CAI) return if (g == TenGod.ZHENG_CAI) WealthType.ZHENG_CAI else WealthType.PIAN_CAI
        }
        return WealthType.OTHER
    }

    /** 按"旺相休囚死"顺序输出的月令五行描述，如"金旺·水相·土休·火囚·木死" */
    fun seasonSummary(monthBranch: String): String {
        val season = T.BRANCH_ELEMENT[monthBranch] ?: return ""
        val wang = season
        val xiang = T.GENERATES[season]!!
        val xiu = T.GENERATES.entries.first { it.value == season }.key
        val qiu = T.OVERCOMES.entries.first { it.value == season }.key
        val si = T.OVERCOMES[season]!!
        return listOf(wang to "旺", xiang to "相", xiu to "休", qiu to "囚", si to "死")
            .joinToString("·") { "${it.first.cn}${it.second}" }
    }

    fun hourStem(dayStem: String, branchIndex: Int): String {
        val start = T.HOUR_START[dayStem] ?: return ""
        return T.STEMS[(start + branchIndex) % 10]
    }

    fun monthStem(yearStem: String, branchIndex: Int): String {
        val start = T.MONTH_START[yearStem] ?: return ""
        return T.STEMS[(start + ((branchIndex - 2 + 12) % 12)) % 10]
    }

    fun elementOf(stem: String) = T.STEM_ELEMENT[stem]?.cn ?: ""
    fun elementOfBranch(branch: String) = T.BRANCH_ELEMENT[branch]?.cn ?: ""
}
