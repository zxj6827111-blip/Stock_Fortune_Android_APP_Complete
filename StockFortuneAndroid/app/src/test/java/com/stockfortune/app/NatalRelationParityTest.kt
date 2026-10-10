package com.stockfortune.app

import com.stockfortune.app.domain.calculator.RelationEngine
import com.stockfortune.app.domain.model.RelationType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.BufferedReader
import java.io.InputStreamReader

class NatalRelationParityTest {

    private val structuralTypes = setOf(
        RelationType.TIANGAN_HE, RelationType.TIANGAN_CHONG,
        RelationType.DIZHI_LIUHE, RelationType.DIZHI_LIUCHONG,
        RelationType.DIZHI_SANHE, RelationType.DIZHI_BANHE, RelationType.DIZHI_SANHUI,
        RelationType.DIZHI_XING_SAN, RelationType.DIZHI_XING_XIANG, RelationType.DIZHI_XING_ZI,
        RelationType.DIZHI_XIANGHAI, RelationType.DIZHI_XIANGPO, RelationType.DIZHI_TONG_ZHI,
        RelationType.FUYIN, RelationType.FANYIN,
        RelationType.TIANHE_DIHE, RelationType.TIANKE_DICHONG,
    )

    @Test
    fun testNatalRelationsParityWithPythonFixtures() {
        val stream = javaClass.getResourceAsStream("/parity/natal_relation.csv")
            ?: throw IllegalStateException("缺少 parity/natal_relation.csv 夹具")

        val reader = BufferedReader(InputStreamReader(stream, Charsets.UTF_8))
        val header = reader.readLine()
        assertEquals("code,listing_date,year_pillar,month_pillar,day_pillar,relation_count,relation_types", header)

        var tested = 0
        reader.forEachLine { line ->
            if (line.isBlank()) return@forEachLine
            val cols = line.split(",")
            val code = cols[0]
            val listingDate = cols[1]
            val yPillar = cols[2]
            val mPillar = cols[3]
            val dPillar = cols[4]
            val expectedCount = cols[5].toInt()
            val expectedTypes = if (cols[6].isEmpty()) emptyList() else cols[6].split(";")

            val events = RelationEngine.computeNatalInternalRelations(yPillar, mPillar, dPillar)
            val structEvents = events.filter { it.relationType in structuralTypes }

            assertEquals("股票 $code ($listingDate) 关系数量不符", expectedCount, structEvents.size)
            val gotTypes = structEvents.map { it.relationType.cn }
            assertEquals("股票 $code ($listingDate) 关系类型序列不符", expectedTypes, gotTypes)
            tested++
        }

        assertTrue("至少应校验 40 组原局命盘夹具", tested >= 40)
    }

    @Test
    fun testMaotaiAndShenhuaGoldenNatalCases() {
        // 茅台（600519）：辛巳 丙申 壬戌
        val maotaiEvents = RelationEngine.computeNatalInternalRelations("辛巳", "丙申", "壬戌")
        val maotaiTypes = maotaiEvents.filter { it.relationType in structuralTypes }.map { it.relationType.cn }.toSet()
        assertTrue(maotaiTypes.contains("天干五合"))
        assertTrue(maotaiTypes.contains("六合"))
        assertTrue(maotaiTypes.contains("天合地合"))
        assertTrue(maotaiTypes.contains("六破"))
        assertTrue(maotaiTypes.contains("相刑"))

        // 中国神华（601088）：丁亥 庚戌 丙子 -> 原局无刑冲合害
        val shenhuaEvents = RelationEngine.computeNatalInternalRelations("丁亥", "庚戌", "丙子")
        val shenhuaStruct = shenhuaEvents.filter { it.relationType in structuralTypes }
        assertEquals(0, shenhuaStruct.size)
    }
}
