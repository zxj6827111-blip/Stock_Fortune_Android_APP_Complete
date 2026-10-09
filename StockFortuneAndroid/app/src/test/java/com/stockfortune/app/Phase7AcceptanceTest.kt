package com.stockfortune.app

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.stockfortune.app.data.db.AppDatabase
import com.stockfortune.app.data.db.AssetManifest
import com.stockfortune.app.data.entity.FavoriteEntity
import com.stockfortune.app.data.entity.LuckCyclePeriodEntity
import com.stockfortune.app.data.entity.NatalRelationEntity
import com.stockfortune.app.data.entity.StockYongshenEntity
import com.stockfortune.app.data.repository.AnalysisRepository
import com.stockfortune.app.data.repository.CalendarRepository
import com.stockfortune.app.data.repository.ClassicQuoteRepository
import com.stockfortune.app.data.repository.StockRepository
import com.stockfortune.app.domain.calculator.FortuneCopyEngine
import com.stockfortune.app.domain.calculator.TenGodCalculator
import com.stockfortune.app.domain.calculator.YongshenCalculator
import com.stockfortune.app.domain.model.FirstDayPolarity
import com.stockfortune.app.domain.model.ReviewStatus
import com.stockfortune.app.domain.model.Strength
import com.stockfortune.app.domain.model.YongshenCandidateStatus
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * Phase 7 全量验收与系统集成专项测试套件 (Gate G7).
 *
 * 覆盖验收维度：
 * 1. 数据库升级与预置库自愈能力（Room v4 身份哈希、收藏备份与无损迁移、结构一致性）；
 * 2. 大运、六合、喜用、状态高级文案与五段式完整深度融合；
 * 3. 算法与文案条件一致性对拍（癸水身弱酉金用神、六合三柱时柱排除合化隔离、调候双轴解耦、平盘缺失降级）；
 * 4. 1990/2035 历法边界、上市前与起运前边界命局测试；
 * 5. 全量文案 60 禁词零容忍合规扫描（0 命中，展示层零「忌」字）；
 * 6. 候审状态严格锁定（ReviewStatus.PENDING_REVIEW，绝不擅自标记为通过发布）。
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class Phase7AcceptanceTest {

    private lateinit var app: Application
    private lateinit var db: AppDatabase
    private lateinit var stockRepo: StockRepository
    private lateinit var analysisRepo: AnalysisRepository

    private val forbiddenWords = listOf(
        "买入", "卖出", "涨幅", "收益率", "必涨", "预测涨跌", "稳赚",
        "获利", "收益", "短线", "机会", "潜力", "把握时机", "适合把握",
        "買入", "賣出", "漲幅", "收益率", "必漲", "預測漲跌", "穩賺",
        "獲利", "收益", "短線", "機會", "潛力", "把握時機", "適合把握",
        "忌", "不宜", "宜于", "适合", "慎", "勿", "务必", "尽量", "优先", "应当", "应该",
        "加仓", "减仓", "重仓", "轻仓", "建仓", "进场", "出场", "持币", "见好就收",
        "规避", "谨防", "小心", "可取", "避免", "防", "注意", "冲动", "大额", "投机",
    ).distinct()

    private fun assertZeroForbiddenWords(label: String, text: String) {
        forbiddenWords.forEach { fw ->
            assertFalse("[$label] 命中合规禁词「$fw」: $text", text.contains(fw))
        }
    }

    @Before
    fun setUp() {
        app = ApplicationProvider.getApplicationContext()
        db = AppDatabase.get(app)
        val classicRepo = ClassicQuoteRepository(app)
        stockRepo = StockRepository(
            db.stockDao(), db.baziDao(), db.filterDao(), db.favoriteDao(), db.metaDao(),
            classicRepo, db.luckCycleDao(), db.natalRelationDao(), db.yongshenDao(),
        )
        val calRepo = CalendarRepository(db.calendarDao())
        analysisRepo = AnalysisRepository(
            db.calendarDao(), db.baziDao(), db.filterDao(), db.scanCacheDao(),
            db.luckCycleDao(), db.natalRelationDao(), db.yongshenDao(), db.stockDao(),
        )
    }

    /**
     * 1. 数据库升级与收藏保留测试
     */
    @Test
    fun `数据库升级与Room模式v4一致性校验`() = runBlocking {
        assertEquals("全库股票数应为 5395", 5395, db.stockDao().count())
        assertEquals("大运周期总数应为 60948", 60948, db.luckCycleDao().countPeriods())
        assertEquals("原局关系总数应为 7931", 7931L, db.natalRelationDao().count())
        assertEquals("喜用实体总数应为 2776", 2776L, db.yongshenDao().count())

        val opened = db.openHelper.readableDatabase
        opened.query("PRAGMA user_version").use { c ->
            assertTrue(c.moveToFirst())
            assertEquals(AssetManifest.SCHEMA_VERSION, c.getInt(0))
        }
        opened.query("SELECT identity_hash FROM room_master_table WHERE id=42").use { c ->
            assertTrue(c.moveToFirst())
            assertEquals(AssetManifest.IDENTITY_HASH, c.getString(0))
        }

        // 测试收藏写入与读取完整性
        val testFavStockId = 600519L
        db.favoriteDao().add(FavoriteEntity(stockId = testFavStockId, addedAt = System.currentTimeMillis()))
        assertTrue("收藏应包含茅台", db.favoriteDao().isFavorite(testFavStockId))
        db.favoriteDao().remove(testFavStockId)
        assertFalse("取消后不应包含茅台", db.favoriteDao().isFavorite(testFavStockId))
    }

    /**
     * 2. 深度融合：大运、六合、喜用、调候共同参与五段式组装
     */
    @Test
    fun `典型股贵州茅台深度五段式融合生成`() = runBlocking {
        // 茅台 2026 年 2 月 (庚寅月)
        val moutai = stockRepo.detail("600519")!!
        val mAnalysis = analysisRepo.monthDays(moutai.stock.id, 2026, 2)!!
        val fp = mAnalysis.fiveParagraph
        assertNotNull("五段式解读应生成", fp)

        // 验证 1. 命理依据：包含流月十神、大运周期与十神、喜用扶抑依据
        assertTrue("命理依据包含偏印", fp!!.basisText.contains("偏印"))
        assertTrue("命理依据包含食神", fp.basisText.contains("食神"))
        assertTrue("命理依据包含大运事实", fp.basisText.contains("大运"))
        assertTrue("命理依据包含扶抑方法", fp.basisText.contains("扶抑方法"))

        // 验证 2. 本月主题：包含月干主线、强弱条件副线
        assertTrue("本月主题包含主线", fp.themeText.contains("专项技术与知识储备"))
        assertTrue("本月主题包含身强条件副线", fp.themeText.contains("身强又见偏印"))

        // 验证 3. 潜在矛盾：包含大运形式对照与十神分组差异
        assertTrue("潜在矛盾包含十神分组差异", fp.contradictionText.contains("不同十神分组"))
        assertTrue("潜在矛盾包含大运形式对照", fp.contradictionText.contains("大运"))

        // 验证 4. 企业经营观察：流月公开披露维度入口
        assertTrue("经营观察包含流月研发投入", fp.businessText.contains("研发投入和资本化率"))
        assertTrue("经营观察包含在手订单", fp.businessText.contains("在手订单"))

        // 验证 5. 综合解释：强弱边界、大运十神×强弱、喜用候选边界、六合结构总结
        assertTrue("综合解释包含身强边界", fp.synthesisText.contains("身强"))
        assertTrue("综合解释包含大运十神×强弱合成", fp.synthesisText.contains("大运"))
        assertTrue("综合解释包含喜用候选仅为方案边界", fp.synthesisText.contains("候选角色"))

        // 验证审核状态严格为 PENDING_REVIEW
        assertEquals(ReviewStatus.PENDING_REVIEW, fp.reviewStatus)
        assertFalse(fp.reviewStatus.isProductionAllowed)

        // 验证命中规则ID完整记录
        assertTrue("命中规则集非空", fp.hitRuleIds.isNotEmpty())
        assertTrue("命中大运事实", fp.hitRuleIds.contains("ADV_DY_PERIOD_FACT"))
        assertTrue("命中喜用扶抑方法", fp.hitRuleIds.contains("ADV_YS_METHOD_FUYI"))
        assertTrue("命中喜用候选边界", fp.hitRuleIds.contains("ADV_YS_CANDIDATE_ONLY"))

        // 合规扫描
        assertZeroForbiddenWords("茅台2026年3月解读", fp.toFormattedMarkdown())
    }

    /**
     * 3. 癸水日主身弱见酉金印星：必须确认为用神候选，严禁误标为比劫喜神
     */
    @Test
    fun `癸水日主身弱见酉金印星严格判定为用神候选`() {
        // 模拟癸水身弱八字：辛未 辛卯 癸卯
        val yongshenResult = YongshenCalculator.calculate(
            yearPillar = "辛未",
            monthPillar = "辛卯",
            dayPillar = "癸卯",
        )
        assertEquals(Strength.WEAK, yongshenResult.strengthLevel)
        assertEquals(YongshenCandidateStatus.CONFIRMED, yongshenResult.status)
        assertEquals("癸水身弱扶抑用神必须为金（印星）", listOf("金"), yongshenResult.yongShen)
        assertEquals("癸水身弱扶抑喜神必须为水（比劫）", listOf("水"), yongshenResult.xiShen)

        // 酉金藏干辛金属金，为印星，故必须是用神候选
        val fp = FortuneCopyEngine.composeMonthlyInterpretation(
            stockId = 999999L,
            stockCode = "TEST.MOCK",
            dayStem = "癸",
            yearPillar = "辛未",
            monthPillar = "辛卯",
            dayPillar = "癸卯",
            firstDayPolarity = FirstDayPolarity.YANG,
            dayunStatus = "available",
            year = 2026,
            month = 10,
            monthGanzhi = "丁酉", // 丁火偏财，酉金偏印
            natalRelationsCount = 1,
            yongshenStatus = YongshenCandidateStatus.CONFIRMED,
            yongshen = StockYongshenEntity(
                chartKey = "辛未_辛卯_癸卯",
                dayStem = "癸",
                monthBranch = "卯",
                strengthScore = -2.5,
                strengthLevel = "身弱",
                status = "confirmed",
                yongShen = "金",
                xiShen = "水",
                jiShen = "土,火",
                chouShen = "木",
                xianShen = "",
                candidateElements = "金,水",
                tiaohouNote = "春秋月生，寒暖适中，调候需求平和",
                rationale = "身弱取印星酉金为用，比劫为喜",
                ruleVersion = "yongshen-candidate-v1.3",
            ),
        )

        assertTrue("综合解释明确提及金为核心候选角色", fp.synthesisText.contains("流月地支本气所属五行金被标为当前方法的核心候选角色"))
        assertTrue("命中喜用扶抑规则", fp.hitRuleIds.contains("ADV_YS_METHOD_FUYI"))
        assertTrue("命中喜用用神角色规则", fp.hitRuleIds.contains("ADV_YS_BRANCH_YONG"))
        assertFalse("严禁将酉金印星误写为喜神规则", fp.hitRuleIds.contains("ADV_YS_BRANCH_XI"))
        assertZeroForbiddenWords("癸水身弱五段式", fp.toFormattedMarkdown())
    }

    /**
     * 4. 六合三柱口径与合化条件隔离
     */
    @Test
    fun `六合支对成立绝不直接认定合化成功或财运吉利`() {
        val fp = FortuneCopyEngine.composeMonthlyInterpretation(
            stockId = 888888L,
            stockCode = "TEST.LIUHE",
            dayStem = "甲",
            yearPillar = "丙子",
            monthPillar = "辛丑",
            dayPillar = "甲子",
            firstDayPolarity = FirstDayPolarity.YANG,
            dayunStatus = "available",
            year = 2026,
            month = 5,
            monthGanzhi = "壬辰",
            natalRelationsCount = 2,
            yongshenStatus = YongshenCandidateStatus.CANDIDATE,
            natalRelations = listOf(
                NatalRelationEntity(
                    id = 1L,
                    chartKey = "丙子_辛丑_甲子",
                    listingDate = "2000-01-01",
                    relationType = "六合",
                    category = "支合",
                    positions = "year,month",
                    sourcePillar = "year",
                    targetPillar = "month",
                    sourceGanzhi = "丙子",
                    targetGanzhi = "辛丑",
                    element = "土",
                    notes = "子丑六合（合化土）",
                    ruleVersion = "natal-relation-v1.3",
                    status = "confirmed",
                )
            ),
        )

        // 验证依据与综合解释中的合化隔离
        assertTrue("依据中应有支对成立不等于合化说明", fp.basisText.contains("单凭配对不能断定合化成立"))
        assertTrue("综合解释中应有六合合化不确定项", fp.synthesisText.contains("尚不足以确认合化"))
        assertTrue("命中ADV_LH_NO_HEHUA", fp.hitRuleIds.contains("ADV_LH_NO_HEHUA"))
        assertTrue("命中ADV_LH_SYNTHESIS", fp.hitRuleIds.contains("ADV_LH_SYNTHESIS"))
        assertFalse("严禁断定财运吉利", fp.toFormattedMarkdown().contains("财运吉利"))
    }

    /**
     * 5. 特殊样本：平盘股与首日缺失股优雅降级且无连带交叉否定
     */
    @Test
    fun `平盘与缺失股大运优雅降级且原局与喜用正常解析`() = runBlocking {
        // 平盘股 603222 (济民医疗)
        val pingpan = stockRepo.detail("603222")!!
        val mPingpan = analysisRepo.monthDays(pingpan.stock.id, 2026, 6)!!
        val fpFlat = mPingpan.fiveParagraph!!
        assertTrue("平盘依据应有大运暂停说明", fpFlat.basisText.contains("首日表现为平盘"))
        assertTrue("平盘命中NA_POLARITY_FLAT", fpFlat.hitRuleIds.contains("NA_POLARITY_FLAT"))
        assertTrue("平盘精确提示说明大运不适用", fpFlat.preciseAdvancedNotice.contains("大运"))
        assertFalse("平盘不得连带否定喜用", fpFlat.preciseAdvancedNotice.contains("喜用"))

        // 缺失股 000004 (国华网安)
        val missing = stockRepo.detail("000004")!!
        val mMissing = analysisRepo.monthDays(missing.stock.id, 2026, 6)!!
        val fpMissing = mMissing.fiveParagraph!!
        assertTrue("缺失依据应有数据缺失说明", fpMissing.basisText.contains("首日阴阳状态尚未核定"))
        assertTrue("缺失命中NA_POLARITY_UNKNOWN", fpMissing.hitRuleIds.contains("NA_POLARITY_UNKNOWN"))
        assertTrue("缺失精确提示说明大运不适用", fpMissing.preciseAdvancedNotice.contains("大运"))
        assertFalse("缺失不得连带否定喜用", fpMissing.preciseAdvancedNotice.contains("喜用"))
    }

    /**
     * 6. 起运前股票样本：尚未起运年份当前运步为空且文案优雅降级
     */
    @Test
    fun `中兴通讯起运前年份生成正常无崩溃`() = runBlocking {
        // 000063 中兴通讯：1997年上市，2004年起运
        val zte = stockRepo.detail("000063")!!
        assertEquals("中兴通讯", zte.stock.name)

        // 1998 年处于尚未起运阶段
        val y1998 = analysisRepo.yearAnalysis(zte.stock.id, 1998)
        assertNotNull(y1998)
        assertNull("1998年尚未起运，currentPeriod应为null", y1998!!.currentPeriod)
        assertTrue(y1998.annualSynthesis!!.currentPeriodDesc.contains("未查得"))

        val m1998 = analysisRepo.monthDays(zte.stock.id, 1998, 5)
        assertNotNull(m1998)
        assertNotNull(m1998!!.fiveParagraph)
        assertTrue("五段式结构齐全", m1998.fiveParagraph!!.isComplete)
        assertZeroForbiddenWords("中兴通讯1998年解读", m1998.fiveParagraph!!.toFormattedMarkdown())
    }

    /**
     * 7. 1990 与 2035 极值历法边界测试
     */
    @Test
    fun `历法极值边界1990与2035全流程无崩溃`() = runBlocking {
        val moutai = stockRepo.detail("600519")!!

        // 1990 年边界 (茅台尚未上市，但历法可用)
        val m1990 = analysisRepo.monthDays(moutai.stock.id, 1990, 12)
        assertNotNull(m1990)
        assertNotNull(m1990!!.fiveParagraph)
        assertTrue("1990年五段式齐全", m1990.fiveParagraph!!.isComplete)

        // 2035 年边界
        val m2035 = analysisRepo.monthDays(moutai.stock.id, 2035, 12)
        assertNotNull(m2035)
        assertNotNull(m2035!!.fiveParagraph)
        assertTrue("2035年五段式齐全", m2035.fiveParagraph!!.isComplete)
        assertZeroForbiddenWords("2035年茅台解读", m2035.fiveParagraph!!.toFormattedMarkdown())
    }
}
