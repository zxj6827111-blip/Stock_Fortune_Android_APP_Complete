package com.stockfortune.app

import com.stockfortune.app.domain.calculator.YongshenCalculator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.BufferedReader
import java.io.InputStreamReader

class YongshenParityTest {

    private fun parseCsvLine(line: String): List<String> {
        val result = mutableListOf<String>()
        val current = StringBuilder()
        var inQuotes = false
        for (char in line) {
            when {
                char == '\"' -> inQuotes = !inQuotes
                char == ',' && !inQuotes -> {
                    result.add(current.toString())
                    current.clear()
                }
                else -> current.append(char)
            }
        }
        result.add(current.toString())
        return result
    }

    @Test
    fun testYongshenParityWithPythonFixtures() {
        val stream = javaClass.getResourceAsStream("/parity/yongshen.csv")
            ?: throw IllegalStateException("缺少 parity/yongshen.csv 夹具")

        val reader = BufferedReader(InputStreamReader(stream, Charsets.UTF_8))
        val header = reader.readLine()
        assertTrue(header.startsWith("year_pillar,month_pillar,day_pillar"))

        var tested = 0
        reader.forEachLine { line ->
            if (line.isBlank()) return@forEachLine
            val cols = parseCsvLine(line)
            val yPillar = cols[0]
            val mPillar = cols[1]
            val dPillar = cols[2]
            val expDayStem = cols[3]
            val expMonthBranch = cols[4]
            val expScore = cols[5].toDouble()
            val expLevel = cols[6]
            val expStatus = cols[7]
            val expYong = cols[8]
            val expXi = cols[9]
            val expJi = cols[10]
            val expChou = cols[11]
            val expCandidates = cols[12]
            val expTiaohou = cols[13]
            val expRationale = cols[14]

            val res = YongshenCalculator.calculate(yPillar, mPillar, dPillar)

            val tag = "$yPillar $mPillar $dPillar"
            assertEquals("[$tag] 日干不符", expDayStem, res.dayStem)
            assertEquals("[$tag] 月支不符", expMonthBranch, res.monthBranch)
            assertEquals("[$tag] 得分不符", expScore, res.strengthScore, 0.01)
            assertEquals("[$tag] 强弱级别不符", expLevel, res.strengthLevel.cn)
            assertEquals("[$tag] 候选状态不符", expStatus, res.status.code)
            assertEquals("[$tag] 用神不符", expYong, res.yongShen.joinToString(","))
            assertEquals("[$tag] 喜神不符", expXi, res.xiShen.joinToString(","))
            assertEquals("[$tag] 忌神不符", expJi, res.jiShen.joinToString(","))
            assertEquals("[$tag] 仇神不符", expChou, res.chouShen.joinToString(","))
            assertEquals("[$tag] 候选五行不符", expCandidates, res.candidateElements.joinToString(","))
            assertEquals("[$tag] 调候提示不符", expTiaohou, res.tiaohouNote)
            assertEquals("[$tag] 理由不符", expRationale, res.rationale)

            tested++
        }

        assertTrue("至少应校验 40 组喜用对拍夹具", tested >= 40)
    }
}
