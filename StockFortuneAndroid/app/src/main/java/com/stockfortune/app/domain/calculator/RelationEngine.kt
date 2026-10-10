package com.stockfortune.app.domain.calculator

import com.stockfortune.app.domain.model.Element
import com.stockfortune.app.domain.model.RelationCell
import com.stockfortune.app.domain.model.RelationEvent
import com.stockfortune.app.domain.model.RelationMatrix
import com.stockfortune.app.domain.model.RelationMatrixRow
import com.stockfortune.app.domain.model.RelationType

/**
 * 确定性干支关系与时间互动引擎（22 类关系目录）。
 * 与 Python 侧 `tools/relation_core.py` 严格对拍。
 */
object RelationEngine {

    const val RULE_VERSION = "natal-relation-v1.3"

    private val T = BaziTables

    private val STEM_FIVE_HARMONY = mapOf(
        pairKey("甲", "己") to "土",
        pairKey("乙", "庚") to "金",
        pairKey("丙", "辛") to "水",
        pairKey("丁", "壬") to "木",
        pairKey("戊", "癸") to "火",
    )

    private val STEM_CLASH_KEYS = setOf(
        pairKey("甲", "庚"),
        pairKey("乙", "辛"),
        pairKey("丙", "壬"),
        pairKey("丁", "癸"),
    )

    private val BRANCH_SIX_HARMONY = mapOf(
        pairKey("子", "丑") to "土",
        pairKey("寅", "亥") to "木",
        pairKey("卯", "戌") to "火",
        pairKey("辰", "酉") to "金",
        pairKey("巳", "申") to "水",
        pairKey("午", "未") to "土",
    )

    private val BRANCH_SIX_CLASH = setOf(
        pairKey("子", "午"),
        pairKey("丑", "未"),
        pairKey("寅", "申"),
        pairKey("卯", "酉"),
        pairKey("辰", "戌"),
        pairKey("巳", "亥"),
    )

    private val BRANCH_SIX_HARM = setOf(
        pairKey("子", "未"),
        pairKey("丑", "午"),
        pairKey("寅", "巳"),
        pairKey("卯", "辰"),
        pairKey("申", "亥"),
        pairKey("酉", "戌"),
    )

    private val BRANCH_SIX_BREAK = setOf(
        pairKey("子", "酉"),
        pairKey("丑", "辰"),
        pairKey("寅", "亥"),
        pairKey("卯", "午"),
        pairKey("巳", "申"),
        pairKey("未", "戌"),
    )

    private val BRANCH_TRIPLE_HARMONY = mapOf(
        listOf("申", "子", "辰") to "水",
        listOf("亥", "卯", "未") to "木",
        listOf("寅", "午", "戌") to "火",
        listOf("巳", "酉", "丑") to "金",
    )

    private val BRANCH_TRIPLE_MEETING = mapOf(
        listOf("寅", "卯", "辰") to "木",
        listOf("巳", "午", "未") to "火",
        listOf("申", "酉", "戌") to "金",
        listOf("亥", "子", "丑") to "水",
    )

    private val PUNISHMENT_GROUPS = listOf(
        setOf("寅", "巳", "申"),
        setOf("丑", "戌", "未"),
        setOf("子", "卯"),
    )

    private val SELF_PUNISHMENTS = setOf("辰", "午", "酉", "亥")

    fun pairKey(a: String, b: String): Pair<String, String> =
        if (a <= b) Pair(a, b) else Pair(b, a)

    private fun punishmentHit(a: String, b: String): String? {
        if (a == b && a in SELF_PUNISHMENTS) return "${a}${a}自刑"
        for (grp in PUNISHMENT_GROUPS) {
            if (a in grp && b in grp && a != b) {
                return grp.sortedBy { T.BRANCHES.indexOf(it) }.joinToString("")
            }
        }
        return null
    }

    private fun stemEvents(
        sourceGanzhi: String,
        targetGanzhi: String,
        sourcePillar: String,
        targetPillar: String,
    ): List<RelationEvent> {
        val events = mutableListOf<RelationEvent>()
        val sStem = sourceGanzhi.take(1)
        val tStem = targetGanzhi.take(1)
        val sElem = T.STEM_ELEMENT[sStem]?.cn ?: ""
        val tElem = T.STEM_ELEMENT[tStem]?.cn ?: ""
        val pkey = pairKey(sStem, tStem)

        val harmonyElem = STEM_FIVE_HARMONY[pkey]
        if (harmonyElem != null) {
            events.add(
                RelationEvent(
                    relationType = RelationType.TIANGAN_HE,
                    category = RelationType.TIANGAN_HE.category,
                    sourcePillar = sourcePillar,
                    targetPillar = targetPillar,
                    sourceGanzhi = sourceGanzhi,
                    targetGanzhi = targetGanzhi,
                    element = harmonyElem,
                    notes = "${sStem}${tStem}天干五合（合化${harmonyElem}）",
                    ruleVersion = RULE_VERSION,
                )
            )
        }

        if (pkey in STEM_CLASH_KEYS) {
            events.add(
                RelationEvent(
                    relationType = RelationType.TIANGAN_CHONG,
                    category = RelationType.TIANGAN_CHONG.category,
                    sourcePillar = sourcePillar,
                    targetPillar = targetPillar,
                    sourceGanzhi = sourceGanzhi,
                    targetGanzhi = targetGanzhi,
                    element = null,
                    notes = "${sStem}${tStem}天干相冲",
                    ruleVersion = RULE_VERSION,
                )
            )
        }

        val sElementObj = T.STEM_ELEMENT[sStem]
        val tElementObj = T.STEM_ELEMENT[tStem]
        if (sElem.isNotEmpty() && sElem == tElem) {
            events.add(
                RelationEvent(
                    relationType = RelationType.TIANGAN_TONG_WUXING,
                    category = RelationType.TIANGAN_TONG_WUXING.category,
                    sourcePillar = sourcePillar,
                    targetPillar = targetPillar,
                    sourceGanzhi = sourceGanzhi,
                    targetGanzhi = targetGanzhi,
                    element = sElem,
                    notes = "天干同属${sElem}",
                    ruleVersion = RULE_VERSION,
                )
            )
        } else if (sElementObj != null && tElementObj != null) {
            if (T.GENERATES[sElementObj] == tElementObj) {
                events.add(
                    RelationEvent(
                        relationType = RelationType.TIANGAN_SHENG,
                        category = RelationType.TIANGAN_SHENG.category,
                        sourcePillar = sourcePillar,
                        targetPillar = targetPillar,
                        sourceGanzhi = sourceGanzhi,
                        targetGanzhi = targetGanzhi,
                        element = tElem,
                        notes = "${sElem}生${tElem}",
                        ruleVersion = RULE_VERSION,
                    )
                )
            } else if (T.GENERATES[tElementObj] == sElementObj) {
                events.add(
                    RelationEvent(
                        relationType = RelationType.TIANGAN_SHOU_SHENG,
                        category = RelationType.TIANGAN_SHOU_SHENG.category,
                        sourcePillar = sourcePillar,
                        targetPillar = targetPillar,
                        sourceGanzhi = sourceGanzhi,
                        targetGanzhi = targetGanzhi,
                        element = sElem,
                        notes = "${tElem}生${sElem}",
                        ruleVersion = RULE_VERSION,
                    )
                )
            }

            if (T.OVERCOMES[sElementObj] == tElementObj) {
                events.add(
                    RelationEvent(
                        relationType = RelationType.TIANGAN_KE,
                        category = RelationType.TIANGAN_KE.category,
                        sourcePillar = sourcePillar,
                        targetPillar = targetPillar,
                        sourceGanzhi = sourceGanzhi,
                        targetGanzhi = targetGanzhi,
                        element = tElem,
                        notes = "${sElem}克${tElem}",
                        ruleVersion = RULE_VERSION,
                    )
                )
            } else if (T.OVERCOMES[tElementObj] == sElementObj) {
                events.add(
                    RelationEvent(
                        relationType = RelationType.TIANGAN_SHOU_KE,
                        category = RelationType.TIANGAN_SHOU_KE.category,
                        sourcePillar = sourcePillar,
                        targetPillar = targetPillar,
                        sourceGanzhi = sourceGanzhi,
                        targetGanzhi = targetGanzhi,
                        element = sElem,
                        notes = "${tElem}克${sElem}",
                        ruleVersion = RULE_VERSION,
                    )
                )
            }
        }

        return events
    }

    private fun branchEvents(
        sourceGanzhi: String,
        targetGanzhi: String,
        sourcePillar: String,
        targetPillar: String,
    ): List<RelationEvent> {
        val events = mutableListOf<RelationEvent>()
        val a = sourceGanzhi.substring(1, 2)
        val b = targetGanzhi.substring(1, 2)
        val pkey = pairKey(a, b)

        if (a == b) {
            events.add(
                RelationEvent(
                    relationType = RelationType.DIZHI_TONG_ZHI,
                    category = RelationType.DIZHI_TONG_ZHI.category,
                    sourcePillar = sourcePillar,
                    targetPillar = targetPillar,
                    sourceGanzhi = sourceGanzhi,
                    targetGanzhi = targetGanzhi,
                    element = T.BRANCH_ELEMENT[a]?.cn,
                    notes = "${a}${b}同支",
                    ruleVersion = RULE_VERSION,
                )
            )
            if (a in SELF_PUNISHMENTS) {
                events.add(
                    RelationEvent(
                        relationType = RelationType.DIZHI_XING_ZI,
                        category = RelationType.DIZHI_XING_ZI.category,
                        sourcePillar = sourcePillar,
                        targetPillar = targetPillar,
                        sourceGanzhi = sourceGanzhi,
                        targetGanzhi = targetGanzhi,
                        element = null,
                        notes = "${a}${b}自刑",
                        ruleVersion = RULE_VERSION,
                    )
                )
            }
        }

        val harmonyElem = BRANCH_SIX_HARMONY[pkey]
        if (harmonyElem != null) {
            events.add(
                RelationEvent(
                    relationType = RelationType.DIZHI_LIUHE,
                    category = RelationType.DIZHI_LIUHE.category,
                    sourcePillar = sourcePillar,
                    targetPillar = targetPillar,
                    sourceGanzhi = sourceGanzhi,
                    targetGanzhi = targetGanzhi,
                    element = harmonyElem,
                    notes = "${a}${b}六合（合化${harmonyElem}）",
                    ruleVersion = RULE_VERSION,
                )
            )
        }

        if (pkey in BRANCH_SIX_CLASH) {
            events.add(
                RelationEvent(
                    relationType = RelationType.DIZHI_LIUCHONG,
                    category = RelationType.DIZHI_LIUCHONG.category,
                    sourcePillar = sourcePillar,
                    targetPillar = targetPillar,
                    sourceGanzhi = sourceGanzhi,
                    targetGanzhi = targetGanzhi,
                    element = null,
                    notes = "${a}${b}六冲",
                    ruleVersion = RULE_VERSION,
                )
            )
        }

        if (pkey in BRANCH_SIX_HARM) {
            events.add(
                RelationEvent(
                    relationType = RelationType.DIZHI_XIANGHAI,
                    category = RelationType.DIZHI_XIANGHAI.category,
                    sourcePillar = sourcePillar,
                    targetPillar = targetPillar,
                    sourceGanzhi = sourceGanzhi,
                    targetGanzhi = targetGanzhi,
                    element = null,
                    notes = "${a}${b}相害",
                    ruleVersion = RULE_VERSION,
                )
            )
        }

        if (pkey in BRANCH_SIX_BREAK) {
            events.add(
                RelationEvent(
                    relationType = RelationType.DIZHI_XIANGPO,
                    category = RelationType.DIZHI_XIANGPO.category,
                    sourcePillar = sourcePillar,
                    targetPillar = targetPillar,
                    sourceGanzhi = sourceGanzhi,
                    targetGanzhi = targetGanzhi,
                    element = null,
                    notes = "${a}${b}六破",
                    ruleVersion = RULE_VERSION,
                )
            )
        }

        val pun = punishmentHit(a, b)
        if (pun != null && a != b) {
            events.add(
                RelationEvent(
                    relationType = RelationType.DIZHI_XING_XIANG,
                    category = RelationType.DIZHI_XING_XIANG.category,
                    sourcePillar = sourcePillar,
                    targetPillar = targetPillar,
                    sourceGanzhi = sourceGanzhi,
                    targetGanzhi = targetGanzhi,
                    element = null,
                    notes = "${a}${b}相刑（${pun}）",
                    ruleVersion = RULE_VERSION,
                )
            )
        }

        return events
    }

    private fun compoundEvents(
        sourceGanzhi: String,
        targetGanzhi: String,
        sourcePillar: String,
        targetPillar: String,
        existing: List<RelationEvent>,
    ): List<RelationEvent> {
        val events = mutableListOf<RelationEvent>()
        val types = existing.map { it.relationType }.toSet()

        if (sourceGanzhi == targetGanzhi) {
            events.add(
                RelationEvent(
                    relationType = RelationType.FUYIN,
                    category = RelationType.FUYIN.category,
                    sourcePillar = sourcePillar,
                    targetPillar = targetPillar,
                    sourceGanzhi = sourceGanzhi,
                    targetGanzhi = targetGanzhi,
                    element = null,
                    notes = "${sourceGanzhi}柱位相同伏吟",
                    ruleVersion = RULE_VERSION,
                )
            )
        }

        if (RelationType.TIANGAN_HE in types && RelationType.DIZHI_LIUHE in types) {
            events.add(
                RelationEvent(
                    relationType = RelationType.TIANHE_DIHE,
                    category = RelationType.TIANHE_DIHE.category,
                    sourcePillar = sourcePillar,
                    targetPillar = targetPillar,
                    sourceGanzhi = sourceGanzhi,
                    targetGanzhi = targetGanzhi,
                    element = null,
                    notes = "${sourceGanzhi}与${targetGanzhi}天合地合",
                    ruleVersion = RULE_VERSION,
                )
            )
        }

        if (RelationType.TIANGAN_KE in types && RelationType.DIZHI_LIUCHONG in types) {
            events.add(
                RelationEvent(
                    relationType = RelationType.TIANKE_DICHONG,
                    category = RelationType.TIANKE_DICHONG.category,
                    sourcePillar = sourcePillar,
                    targetPillar = targetPillar,
                    sourceGanzhi = sourceGanzhi,
                    targetGanzhi = targetGanzhi,
                    element = null,
                    notes = "${sourceGanzhi}与${targetGanzhi}天克地冲",
                    ruleVersion = RULE_VERSION,
                )
            )
        }

        if ((RelationType.TIANGAN_KE in types || RelationType.TIANGAN_CHONG in types) &&
            RelationType.DIZHI_LIUCHONG in types
        ) {
            events.add(
                RelationEvent(
                    relationType = RelationType.FANYIN,
                    category = RelationType.FANYIN.category,
                    sourcePillar = sourcePillar,
                    targetPillar = targetPillar,
                    sourceGanzhi = sourceGanzhi,
                    targetGanzhi = targetGanzhi,
                    element = null,
                    notes = "${sourceGanzhi}与${targetGanzhi}反吟（干冲克且支相冲）",
                    ruleVersion = RULE_VERSION,
                )
            )
        }

        return events
    }

    /**
     * 计算两柱之间的全部两两关系。
     */
    fun computePairRelations(
        sourceGanzhi: String,
        targetGanzhi: String,
        sourcePillar: String,
        targetPillar: String,
    ): List<RelationEvent> {
        val events = mutableListOf<RelationEvent>()
        events.addAll(stemEvents(sourceGanzhi, targetGanzhi, sourcePillar, targetPillar))
        events.addAll(branchEvents(sourceGanzhi, targetGanzhi, sourcePillar, targetPillar))
        events.addAll(compoundEvents(sourceGanzhi, targetGanzhi, sourcePillar, targetPillar, events))
        return events
    }

    /**
     * 原局三柱内部关系识别（年/月/日 3×3）。
     */
    fun computeNatalInternalRelations(
        yearGanzhi: String,
        monthGanzhi: String,
        dayGanzhi: String,
    ): List<RelationEvent> {
        val pillars = listOf(
            "year" to yearGanzhi,
            "month" to monthGanzhi,
            "day" to dayGanzhi,
        )
        val allEvents = mutableListOf<RelationEvent>()

        // 1. 两两关系
        for (i in 0 until pillars.size) {
            for (j in i + 1 until pillars.size) {
                val (posA, gzA) = pillars[i]
                val (posB, gzB) = pillars[j]
                allEvents.addAll(computePairRelations(gzA, gzB, posA, posB))
            }
        }

        // 2. 多支复合结构
        val branchesMap = pillars.associate { it.first to it.second.substring(1, 2) }

        // 三合与半合
        for ((combo, elem) in BRANCH_TRIPLE_HARMONY) {
            val matchedPos = branchesMap.filter { it.value in combo }.map { it.key }
            val matchedBranches = matchedPos.map { branchesMap.getValue(it) }.distinct()
            if (matchedBranches.size == 3) {
                allEvents.add(
                    RelationEvent(
                        relationType = RelationType.DIZHI_SANHE,
                        category = RelationType.DIZHI_SANHE.category,
                        sourcePillar = matchedPos.joinToString(","),
                        targetPillar = matchedPos.joinToString(","),
                        sourceGanzhi = matchedPos.joinToString(",") { pos -> pillars.first { it.first == pos }.second },
                        targetGanzhi = matchedPos.joinToString(",") { branchesMap.getValue(it) },
                        element = elem,
                        notes = "${combo.joinToString("")}原局三合${elem}局",
                        ruleVersion = RULE_VERSION,
                    )
                )
            } else if (matchedBranches.size == 2) {
                val p1 = matchedPos.first { branchesMap[it] == matchedBranches[0] }
                val p2 = matchedPos.first { branchesMap[it] == matchedBranches[1] }
                val b1 = branchesMap.getValue(p1)
                val b2 = branchesMap.getValue(p2)
                allEvents.add(
                    RelationEvent(
                        relationType = RelationType.DIZHI_BANHE,
                        category = RelationType.DIZHI_BANHE.category,
                        sourcePillar = p1,
                        targetPillar = p2,
                        sourceGanzhi = pillars.first { it.first == p1 }.second,
                        targetGanzhi = pillars.first { it.first == p2 }.second,
                        element = elem,
                        notes = "${b1}${b2}半合${elem}局（缺一）",
                        ruleVersion = RULE_VERSION,
                    )
                )
            }
        }

        // 三会方
        for ((combo, elem) in BRANCH_TRIPLE_MEETING) {
            val matchedPos = branchesMap.filter { it.value in combo }.map { it.key }
            val matchedBranches = matchedPos.map { branchesMap.getValue(it) }.distinct()
            if (matchedBranches.size == 3) {
                allEvents.add(
                    RelationEvent(
                        relationType = RelationType.DIZHI_SANHUI,
                        category = RelationType.DIZHI_SANHUI.category,
                        sourcePillar = matchedPos.joinToString(","),
                        targetPillar = matchedPos.joinToString(","),
                        sourceGanzhi = matchedPos.joinToString(",") { pos -> pillars.first { it.first == pos }.second },
                        targetGanzhi = matchedPos.joinToString(",") { branchesMap.getValue(it) },
                        element = elem,
                        notes = "${combo.joinToString("")}原局三会${elem}方",
                        ruleVersion = RULE_VERSION,
                    )
                )
            }
        }

        // 三刑
        for (combo in listOf(listOf("寅", "巳", "申"), listOf("丑", "戌", "未"))) {
            val matchedPos = branchesMap.filter { it.value in combo }.map { it.key }
            val matchedBranches = matchedPos.map { branchesMap.getValue(it) }.distinct()
            if (matchedBranches.size == 3) {
                allEvents.add(
                    RelationEvent(
                        relationType = RelationType.DIZHI_XING_SAN,
                        category = RelationType.DIZHI_XING_SAN.category,
                        sourcePillar = matchedPos.joinToString(","),
                        targetPillar = matchedPos.joinToString(","),
                        sourceGanzhi = matchedPos.joinToString(",") { pos -> pillars.first { it.first == pos }.second },
                        targetGanzhi = matchedPos.joinToString(",") { branchesMap.getValue(it) },
                        element = null,
                        notes = "${combo.joinToString("")}原局三刑",
                        ruleVersion = RULE_VERSION,
                    )
                )
            }
        }

        return allEvents
    }

    /**
     * 单个外部柱（流年/流月/大运）与原局三柱的关系互动。
     */
    fun computeExternalInteraction(
        sourceGanzhi: String,
        sourcePillar: String,
        natalYear: String,
        natalMonth: String,
        natalDay: String,
    ): List<RelationEvent> {
        val natalPillars = listOf(
            "year" to natalYear,
            "month" to natalMonth,
            "day" to natalDay,
        )
        val events = mutableListOf<RelationEvent>()

        for ((targetPillar, targetGanzhi) in natalPillars) {
            events.addAll(computePairRelations(sourceGanzhi, targetGanzhi, sourcePillar, targetPillar))
        }

        val sBranch = sourceGanzhi.substring(1, 2)
        val branchesMap = natalPillars.associate { it.first to it.second.substring(1, 2) }

        // 三合与半合
        for ((combo, elem) in BRANCH_TRIPLE_HARMONY) {
            if (sBranch !in combo) continue
            val others = combo.filter { it != sBranch }
            val matchedPos = branchesMap.filter { it.value in others }.map { it.key }
            val matchedBranches = matchedPos.map { branchesMap.getValue(it) }.distinct()
            if (matchedBranches.size >= 2) {
                for (targetP in matchedPos.take(2)) {
                    events.add(
                        RelationEvent(
                            relationType = RelationType.DIZHI_SANHE,
                            category = RelationType.DIZHI_SANHE.category,
                            sourcePillar = sourcePillar,
                            targetPillar = targetP,
                            sourceGanzhi = sourceGanzhi,
                            targetGanzhi = natalPillars.first { it.first == targetP }.second,
                            element = elem,
                            notes = "${combo.joinToString("")}三合${elem}局",
                            ruleVersion = RULE_VERSION,
                        )
                    )
                }
            } else if (matchedBranches.size == 1) {
                val targetP = matchedPos.first()
                events.add(
                    RelationEvent(
                        relationType = RelationType.DIZHI_BANHE,
                        category = RelationType.DIZHI_BANHE.category,
                        sourcePillar = sourcePillar,
                        targetPillar = targetP,
                        sourceGanzhi = sourceGanzhi,
                        targetGanzhi = natalPillars.first { it.first == targetP }.second,
                        element = elem,
                        notes = "${sBranch}${branchesMap.getValue(targetP)}半合${elem}局（缺一）",
                        ruleVersion = RULE_VERSION,
                    )
                )
            }
        }

        // 三会方
        for ((combo, elem) in BRANCH_TRIPLE_MEETING) {
            if (sBranch !in combo) continue
            val others = combo.filter { it != sBranch }
            val matchedPos = branchesMap.filter { it.value in others }.map { it.key }
            val matchedBranches = matchedPos.map { branchesMap.getValue(it) }.distinct()
            if (matchedBranches.size >= 2) {
                for (targetP in matchedPos.take(2)) {
                    events.add(
                        RelationEvent(
                            relationType = RelationType.DIZHI_SANHUI,
                            category = RelationType.DIZHI_SANHUI.category,
                            sourcePillar = sourcePillar,
                            targetPillar = targetP,
                            sourceGanzhi = sourceGanzhi,
                            targetGanzhi = natalPillars.first { it.first == targetP }.second,
                            element = elem,
                            notes = "${combo.joinToString("")}三会${elem}方",
                            ruleVersion = RULE_VERSION,
                        )
                    )
                }
            }
        }

        // 三刑
        for (combo in listOf(listOf("寅", "巳", "申"), listOf("丑", "戌", "未"))) {
            if (sBranch !in combo) continue
            val others = combo.filter { it != sBranch }
            val matchedPos = branchesMap.filter { it.value in others }.map { it.key }
            val matchedBranches = matchedPos.map { branchesMap.getValue(it) }.distinct()
            if (matchedBranches.size >= 2) {
                for (targetP in matchedPos.take(2)) {
                    events.add(
                        RelationEvent(
                            relationType = RelationType.DIZHI_XING_SAN,
                            category = RelationType.DIZHI_XING_SAN.category,
                            sourcePillar = sourcePillar,
                            targetPillar = targetP,
                            sourceGanzhi = sourceGanzhi,
                            targetGanzhi = natalPillars.first { it.first == targetP }.second,
                            element = null,
                            notes = "${combo.joinToString("")}三刑",
                            ruleVersion = RULE_VERSION,
                        )
                    )
                }
            }
        }

        return events
    }

    /**
     * 构造流运外部柱与原局的 3×3 交互关系矩阵。
     */
    fun buildRelationMatrix(
        externalPillars: Map<String, String>,
        natalPillars: Map<String, String>,
    ): RelationMatrix {
        val natalCols = listOf("year", "month", "day")
        val externalRows = externalPillars.keys.toList()

        val rows = externalRows.map { sourcePillar ->
            val sourceGanzhi = externalPillars.getValue(sourcePillar)
            val cells = natalCols.map { targetPillar ->
                val targetGanzhi = natalPillars.getValue(targetPillar)
                val cellEvents = computePairRelations(sourceGanzhi, targetGanzhi, sourcePillar, targetPillar)
                RelationCell(
                    sourcePillar = sourcePillar,
                    targetPillar = targetPillar,
                    sourceGanzhi = sourceGanzhi,
                    targetGanzhi = targetGanzhi,
                    events = cellEvents,
                )
            }
            RelationMatrixRow(
                sourcePillar = sourcePillar,
                sourceGanzhi = sourceGanzhi,
                cells = cells,
            )
        }

        return RelationMatrix(
            rows = rows,
            columns = natalCols,
            ruleVersion = RULE_VERSION,
        )
    }
}
