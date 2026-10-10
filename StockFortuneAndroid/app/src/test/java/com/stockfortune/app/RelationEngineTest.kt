package com.stockfortune.app

import com.stockfortune.app.domain.calculator.BaziTables
import com.stockfortune.app.domain.calculator.RelationEngine
import com.stockfortune.app.domain.model.RelationType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RelationEngineTest {

    @Test
    fun testCatalogHas22Types() {
        assertEquals(22, RelationType.entries.size)
    }

    @Test
    fun test120x120ExhaustiveEmittedCatalog() {
        // 120 外部干支 × 120 原局干支
        val allPillars = mutableListOf<String>()
        for (s in BaziTables.STEMS) {
            for (b in BaziTables.BRANCHES) {
                allPillars.add("${s}${b}")
            }
        }
        assertEquals(120, allPillars.size)

        val emitted = mutableSetOf<RelationType>()

        // 1. 两两单元格穷尽
        for (ext in allPillars) {
            for (nat in allPillars) {
                val events = RelationEngine.computePairRelations(ext, nat, "external", "day")
                for (ev in events) {
                    emitted.add(ev.relationType)
                }
            }
        }

        // 2. 外部柱 + 原局两柱触发三合、三会、三刑
        val testNatals = listOf(
            Pair("申", "辰"), // + 子 -> 三合水
            Pair("寅", "辰"), // + 卯 -> 三会木
            Pair("巳", "申"), // + 寅 -> 三刑
            Pair("戌", "未"), // + 丑 -> 三刑
        )
        for ((b1, b2) in testNatals) {
            for (ext in allPillars) {
                val interEvents = RelationEngine.computeExternalInteraction(
                    ext, "flow_year", "甲$b1", "乙$b2", "丙午"
                )
                for (ev in interEvents) {
                    emitted.add(ev.relationType)
                }
            }
        }

        // 验证 22 种类型全部可达且无非法类型
        assertEquals(RelationType.entries.toSet(), emitted)
    }

    @Test
    fun testTianheDiheCompound() {
        // 辛巳 与 丙申：丙辛合水 + 巳申合水 -> 天合地合
        val events = RelationEngine.computePairRelations("辛巳", "丙申", "year", "month")
        val types = events.map { it.relationType }.toSet()
        assertTrue(types.contains(RelationType.TIANGAN_HE))
        assertTrue(types.contains(RelationType.DIZHI_LIUHE))
        assertTrue(types.contains(RelationType.TIANHE_DIHE))
    }

    @Test
    fun testTiankeDichongAndFanyin() {
        // 庚午 与 甲子：庚克甲 + 子午冲 -> 天克地冲 + 反吟
        val events = RelationEngine.computePairRelations("庚午", "甲子", "year", "month")
        val types = events.map { it.relationType }.toSet()
        assertTrue(types.contains(RelationType.TIANGAN_KE))
        assertTrue(types.contains(RelationType.DIZHI_LIUCHONG))
        assertTrue(types.contains(RelationType.TIANKE_DICHONG))
        assertTrue(types.contains(RelationType.FANYIN))
    }

    @Test
    fun testFuyin() {
        val events = RelationEngine.computePairRelations("甲子", "甲子", "year", "month")
        val types = events.map { it.relationType }.toSet()
        assertTrue(types.contains(RelationType.FUYIN))
        assertTrue(types.contains(RelationType.DIZHI_TONG_ZHI))
        assertTrue(types.contains(RelationType.TIANGAN_TONG_WUXING))
    }

    @Test
    fun testTripleHarmonyAndHalfHarmony() {
        // 三合完整：申子辰三合水局
        val eventsFull = RelationEngine.computeNatalInternalRelations("庚申", "戊子", "壬辰")
        val typesFull = eventsFull.map { it.relationType }.toSet()
        assertTrue(typesFull.contains(RelationType.DIZHI_SANHE))
        val sanhe = eventsFull.first { it.relationType == RelationType.DIZHI_SANHE }
        assertEquals("水", sanhe.element)

        // 半合（缺一）：申子半合水局
        val eventsHalf = RelationEngine.computeNatalInternalRelations("庚申", "戊子", "壬午")
        val typesHalf = eventsHalf.map { it.relationType }.toSet()
        assertTrue(typesHalf.contains(RelationType.DIZHI_BANHE))
        assertTrue(!typesHalf.contains(RelationType.DIZHI_SANHE))
        val banhe = eventsHalf.first { it.relationType == RelationType.DIZHI_BANHE }
        assertEquals("水", banhe.element)
    }

    @Test
    fun testTripleMeeting() {
        // 寅卯辰三会木方
        val events = RelationEngine.computeNatalInternalRelations("甲寅", "乙卯", "丙辰")
        val types = events.map { it.relationType }.toSet()
        assertTrue(types.contains(RelationType.DIZHI_SANHUI))
        val sanhui = events.first { it.relationType == RelationType.DIZHI_SANHUI }
        assertEquals("木", sanhui.element)
    }

    @Test
    fun testTriplePunishment() {
        // 寅巳申三刑
        val events = RelationEngine.computeNatalInternalRelations("甲寅", "乙巳", "丙申")
        val types = events.map { it.relationType }.toSet()
        assertTrue(types.contains(RelationType.DIZHI_XING_SAN))
        assertTrue(types.contains(RelationType.DIZHI_XING_XIANG))
    }

    @Test
    fun testSelfPunishment() {
        for (branch in listOf("辰", "午", "酉", "亥")) {
            val events = RelationEngine.computePairRelations("甲$branch", "丙$branch", "year", "month")
            val types = events.map { it.relationType }.toSet()
            assertTrue("$branch 应触发自刑", types.contains(RelationType.DIZHI_XING_ZI))
            assertTrue(types.contains(RelationType.DIZHI_TONG_ZHI))
        }
    }

    @Test
    fun testMaotaiGoldenNatalRelations() {
        // 茅台：辛巳, 丙申, 壬戌
        val events = RelationEngine.computeNatalInternalRelations("辛巳", "丙申", "壬戌")
        val types = events.map { it.relationType }.toSet()
        assertTrue(types.contains(RelationType.TIANGAN_HE))
        assertTrue(types.contains(RelationType.DIZHI_LIUHE))
        assertTrue(types.contains(RelationType.TIANHE_DIHE))
        assertTrue(types.contains(RelationType.DIZHI_XIANGPO))
        assertTrue(types.contains(RelationType.DIZHI_XING_XIANG))
    }

    @Test
    fun testRelationMatrixStructure() {
        val external = mapOf("year" to "甲辰", "month" to "丁卯", "day" to "己亥")
        val natal = mapOf("year" to "辛巳", "month" to "丙申", "day" to "壬戌")
        val matrix = RelationEngine.buildRelationMatrix(external, natal)

        assertEquals(3, matrix.rows.size)
        assertEquals(listOf("year", "month", "day"), matrix.columns)
        for (row in matrix.rows) {
            assertEquals(3, row.cells.size)
            assertNotNull(row.relationTypes)
        }
    }
}
