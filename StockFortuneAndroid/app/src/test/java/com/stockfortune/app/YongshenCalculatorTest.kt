package com.stockfortune.app

import com.stockfortune.app.domain.calculator.TenGodCalculator
import com.stockfortune.app.domain.calculator.YongshenCalculator
import com.stockfortune.app.domain.model.Strength
import com.stockfortune.app.domain.model.YongshenCandidateStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class YongshenCalculatorTest {

    @Test
    fun testStrongChart_Moutai() {
        // 辛巳 丙申 壬戌 (贵州茅台)
        val res = YongshenCalculator.calculate("辛巳", "丙申", "壬戌")
        assertEquals(YongshenCandidateStatus.CONFIRMED, res.status)
        assertEquals("壬", res.dayStem)
        assertEquals(Strength.STRONG, res.strengthLevel)
        assertTrue(res.strengthScore >= 2.0)
        // 壬水身强: 克我者官杀(土)为用神, 我生者食伤(木)与我克者财星(火)为喜神
        assertEquals(listOf("土"), res.yongShen)
        assertEquals(listOf("木", "火"), res.xiShen)
        assertEquals(listOf("金", "水"), res.jiShen)
        assertEquals(listOf("金"), res.chouShen)
        assertEquals(listOf("土", "木", "火"), res.candidateElements)
        assertEquals("春秋月生，寒暖适中，调候需求平和", res.tiaohouNote)
        assertTrue(res.rationale.contains("官杀「土」为用神"))
        assertFalse(res.rationale.contains("忌"))
    }

    @Test
    fun testWeakChart_CATL() {
        // 戊戌 戊午 甲戌 (宁德时代)
        val res = YongshenCalculator.calculate("戊戌", "戊午", "甲戌")
        assertEquals(YongshenCandidateStatus.CONFIRMED, res.status)
        assertEquals("甲", res.dayStem)
        assertEquals(Strength.WEAK, res.strengthLevel)
        assertTrue(res.strengthScore <= -2.0)
        // 甲木身弱: 生我者印星(水)为用神, 同我者比劫(木)为喜神
        assertEquals(listOf("水"), res.yongShen)
        assertEquals(listOf("木"), res.xiShen)
        assertEquals(listOf("金", "土"), res.jiShen)
        assertEquals(listOf("火"), res.chouShen)
        assertEquals(listOf("水", "木"), res.candidateElements)
        assertEquals("夏月生，火燥水枯，调候宜见水（润局）", res.tiaohouNote)
        assertTrue(res.rationale.contains("印星「水」为用神"))
        assertFalse(res.rationale.contains("忌"))
    }

    @Test
    fun testBalancedChart() {
        // 丁丑 壬子 甲午
        val res = YongshenCalculator.calculate("丁丑", "壬子", "甲午")
        assertEquals(YongshenCandidateStatus.CANDIDATE, res.status)
        assertEquals(Strength.BALANCED, res.strengthLevel)
        assertEquals(0.75, res.strengthScore, 0.001)
        // 中和格局不强判单一用神
        assertTrue(res.yongShen.isEmpty())
        assertTrue(res.xiShen.isEmpty())
        assertTrue(res.jiShen.isEmpty())
        assertTrue(res.chouShen.isEmpty())
        assertEquals(listOf("木", "火", "土", "金", "水"), res.xianShen)
        assertEquals(3, res.candidateElements.size)
        assertTrue(res.rationale.contains("不设单一扶抑主轴"))
        assertFalse(res.rationale.contains("忌"))
    }

    @Test
    fun testBoundaryThresholds() {
        // 1. 恰好 +2.0: 丁酉 乙巳 戊戌 -> 身强 (CONFIRMED)
        val resPos = YongshenCalculator.calculate("丁酉", "乙巳", "戊戌")
        assertEquals(2.0, resPos.strengthScore, 0.001)
        assertEquals(Strength.STRONG, resPos.strengthLevel)
        assertEquals(YongshenCandidateStatus.CONFIRMED, resPos.status)
        assertEquals(listOf("木"), resPos.yongShen)

        // 2. 恰好 -2.0: 丁丑 癸丑 乙卯 -> 身弱 (CONFIRMED)
        val resNeg = YongshenCalculator.calculate("丁丑", "癸丑", "乙卯")
        assertEquals(-2.0, resNeg.strengthScore, 0.001)
        assertEquals(Strength.WEAK, resNeg.strengthLevel)
        assertEquals(YongshenCandidateStatus.CONFIRMED, resNeg.status)
        assertEquals(listOf("水"), resNeg.yongShen)

        // 3. 贴近下界 -1.9: 丁丑 乙巳 庚午 -> 中和 (CANDIDATE)
        val resNear = YongshenCalculator.calculate("丁丑", "乙巳", "庚午")
        assertEquals(-1.9, resNear.strengthScore, 0.001)
        assertEquals(Strength.BALANCED, resNear.strengthLevel)
        assertEquals(YongshenCandidateStatus.CANDIDATE, resNear.status)
        assertTrue(resNear.yongShen.isEmpty())

        // 4. 贴近上界 +1.65: 丁丑 丙午 丙午 -> 中和 (CANDIDATE)
        val resMid = YongshenCalculator.calculate("丁丑", "丙午", "丙午")
        assertEquals(1.65, resMid.strengthScore, 0.001)
        assertEquals(Strength.BALANCED, resMid.strengthLevel)
        assertEquals(YongshenCandidateStatus.CANDIDATE, resMid.status)
        assertTrue(resMid.yongShen.isEmpty())
    }

    @Test
    fun testTiaohouIndependence() {
        // 调候四时
        assertEquals("冬月生，天寒地冻，调候宜见火（暖局）", YongshenCalculator.computeTiaohouNote("子"))
        assertEquals("夏月生，火燥水枯，调候宜见水（润局）", YongshenCalculator.computeTiaohouNote("午"))
        assertEquals("季月土重，调候宜木疏土或水润泽", YongshenCalculator.computeTiaohouNote("辰"))
        assertEquals("春秋月生，寒暖适中，调候需求平和", YongshenCalculator.computeTiaohouNote("寅"))

        // 调候不污染扶抑五行集合
        val res = YongshenCalculator.calculate("戊戌", "戊午", "甲戌")
        assertEquals(listOf("水"), res.yongShen)
        assertEquals(listOf("木"), res.xiShen)
    }

    @Test
    fun testUnavailableFallback() {
        val resEmpty = YongshenCalculator.calculate("", "戊午", "甲戌")
        assertEquals(YongshenCandidateStatus.UNAVAILABLE, resEmpty.status)
        assertTrue(resEmpty.yongShen.isEmpty())
        assertTrue(resEmpty.rationale.contains("不完整"))
    }
}
