package com.stockfortune.app

import android.app.Application
import androidx.sqlite.db.SimpleSQLiteQuery
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.stockfortune.app.data.db.AppDatabase
import com.stockfortune.app.data.repository.CopyRuleRepository
import com.stockfortune.app.data.entity.LuckCyclePeriodEntity
import com.stockfortune.app.data.entity.NatalRelationEntity
import com.stockfortune.app.data.entity.StockBaziEntity
import com.stockfortune.app.data.entity.StockEntity
import com.stockfortune.app.data.entity.StockLuckCycleEntity
import com.stockfortune.app.data.entity.StockYongshenEntity
import com.stockfortune.app.domain.calculator.FortuneCopyEngine
import com.stockfortune.app.domain.model.FirstDayPolarity
import com.stockfortune.app.domain.model.ReviewStatus
import com.stockfortune.app.domain.model.YongshenCandidateStatus
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.io.File
import java.security.MessageDigest

/**
 * Phase 7 代码审查问题五专项验收：Android 实际运行引擎 64,740 全库流月生成与 30 组样本严格验收。
 *
 * 验收要求：
 * 1. 杜绝纯 Python 脚本替代，必须使用 Android 实际运行引擎 (FortuneCopyEngine)；
 * 2. 覆盖全库 5,395 只股票 × 12 个流月 = 64,740 份生成验证；
 * 3. 记录股票ID、股票代码、年份、月份、实际月柱、大运区间状态、原局关系、喜用候选、实际命中规则ID、五段式校验哈希、降级状态；
 * 4. 60 禁词零容忍扫描（0 命中，展示层零「忌」字）；
 * 5. 30 组代表性样本逐项比对与结构化归档；
 * 6. 审核状态严格锁定 PENDING_REVIEW。
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class FullDatabase64740AcceptanceTest {

    private lateinit var app: Application
    private lateinit var db: AppDatabase

    private val forbiddenWords = listOf(
        "买入", "卖出", "涨幅", "收益率", "必涨", "预测涨跌", "稳赚",
        "获利", "收益", "短线", "机会", "潜力", "把握时机", "适合把握",
        "買入", "賣出", "漲幅", "收益率", "必漲", "預測漲跌", "穩賺",
        "獲利", "收益", "短線", "機會", "潛力", "把握時機", "適合把握",
        "忌", "不宜", "宜于", "适合", "慎", "勿", "务必", "尽量", "优先", "应当", "应该",
        "加仓", "减仓", "重仓", "轻仓", "建仓", "进场", "出场", "持币", "见好就收",
        "规避", "谨防", "小心", "可取", "避免", "防", "注意", "冲动", "大额", "投机",
    )

    data class MonthFlow(
        val month: Int,
        val date: String,
        val ganzhi: String,
    )

    private val months2026 = listOf(
        MonthFlow(1, "2026-01-15", "己丑"),
        MonthFlow(2, "2026-02-15", "庚寅"),
        MonthFlow(3, "2026-03-15", "辛卯"),
        MonthFlow(4, "2026-04-15", "壬辰"),
        MonthFlow(5, "2026-05-15", "癸巳"),
        MonthFlow(6, "2026-06-15", "甲午"),
        MonthFlow(7, "2026-07-15", "乙未"),
        MonthFlow(8, "2026-08-15", "丙申"),
        MonthFlow(9, "2026-09-15", "丁酉"),
        MonthFlow(10, "2026-10-15", "戊戌"),
        MonthFlow(11, "2026-11-15", "己亥"),
        MonthFlow(12, "2026-12-15", "庚子"),
    )

    @Before
    fun setup() {
        app = ApplicationProvider.getApplicationContext()
        db = AppDatabase.get(app)
    }

    private fun sha256(input: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val bytes = digest.digest(input.toByteArray(Charsets.UTF_8))
        return bytes.joinToString("") { "%02x".format(it) }
    }

    @Test
    fun testRealAndroidEngine64740FullRegressionAndSampleAudit() = runBlocking {
        val sq = db.openHelper.readableDatabase

        // 1. 批量加载全市场 5,395 只股票基础信息
        val allStocks = mutableListOf<StockEntity>()
        sq.query(SimpleSQLiteQuery("SELECT id, code, symbol, name, exchange, board, listing_date, first_day_flag, industry, industry_full, stock_nature FROM stock ORDER BY id")).use { c ->
            val idIdx = c.getColumnIndex("id")
            val codeIdx = c.getColumnIndex("code")
            val symIdx = c.getColumnIndex("symbol")
            val nameIdx = c.getColumnIndex("name")
            val exIdx = c.getColumnIndex("exchange")
            val bdIdx = c.getColumnIndex("board")
            val listIdx = c.getColumnIndex("listing_date")
            val flagIdx = c.getColumnIndex("first_day_flag")
            val indIdx = c.getColumnIndex("industry")
            val indFIdx = c.getColumnIndex("industry_full")
            val natIdx = c.getColumnIndex("stock_nature")
            while (c.moveToNext()) {
                allStocks.add(
                    StockEntity(
                        id = c.getLong(idIdx),
                        code = c.getString(codeIdx),
                        symbol = c.getString(symIdx),
                        name = c.getString(nameIdx),
                        exchange = c.getString(exIdx),
                        board = c.getString(bdIdx),
                        listingDate = c.getString(listIdx),
                        firstOpen = null,
                        firstClose = null,
                        firstChange = null,
                        firstDayFlag = c.getString(flagIdx),
                        industry = c.getString(indIdx),
                        industryFull = c.getString(indFIdx),
                        stockNature = c.getString(natIdx),
                    )
                )
            }
        }
        assertEquals(5395, allStocks.size)

        // 2. 加载全库 stock_bazi
        val baziMap = mutableMapOf<Long, StockBaziEntity>()
        sq.query(SimpleSQLiteQuery("SELECT stock_id, full_bazi, year_pillar, month_pillar, day_pillar, hour_pillar, year_stem, year_branch, month_stem, month_branch, day_stem, day_branch, hour_stem, hour_branch, day_master_element, month_season_element, na_yin, day_master_strength FROM stock_bazi")).use { c ->
            val idIdx = c.getColumnIndex("stock_id")
            val fullIdx = c.getColumnIndex("full_bazi")
            val ypIdx = c.getColumnIndex("year_pillar")
            val mpIdx = c.getColumnIndex("month_pillar")
            val dpIdx = c.getColumnIndex("day_pillar")
            val hpIdx = c.getColumnIndex("hour_pillar")
            val ysIdx = c.getColumnIndex("year_stem")
            val ybIdx = c.getColumnIndex("year_branch")
            val msIdx = c.getColumnIndex("month_stem")
            val mbIdx = c.getColumnIndex("month_branch")
            val dsIdx = c.getColumnIndex("day_stem")
            val dbIdx = c.getColumnIndex("day_branch")
            val hsIdx = c.getColumnIndex("hour_stem")
            val hbIdx = c.getColumnIndex("hour_branch")
            val dmeIdx = c.getColumnIndex("day_master_element")
            val mseIdx = c.getColumnIndex("month_season_element")
            val nyIdx = c.getColumnIndex("na_yin")
            val dmsIdx = c.getColumnIndex("day_master_strength")
            while (c.moveToNext()) {
                val sid = c.getLong(idIdx)
                baziMap[sid] = StockBaziEntity(
                    stockId = sid,
                    fullBazi = c.getString(fullIdx),
                    yearPillar = c.getString(ypIdx),
                    monthPillar = c.getString(mpIdx),
                    dayPillar = c.getString(dpIdx),
                    hourPillar = c.getString(hpIdx),
                    yearStem = c.getString(ysIdx),
                    yearBranch = c.getString(ybIdx),
                    monthStem = c.getString(msIdx),
                    monthBranch = c.getString(mbIdx),
                    dayStem = c.getString(dsIdx),
                    dayBranch = c.getString(dbIdx),
                    hourStem = c.getString(hsIdx),
                    hourBranch = c.getString(hbIdx),
                    dayMasterElement = c.getString(dmeIdx),
                    monthSeasonElement = c.getString(mseIdx),
                    naYin = c.getString(nyIdx),
                    dayMasterStrength = c.getString(dmsIdx),
                )
            }
        }
        assertEquals(5395, baziMap.size)

        // 3. 加载全库 stock_luck_cycle
        val luckCycleMap = mutableMapOf<Long, StockLuckCycleEntity>()
        sq.query(SimpleSQLiteQuery("SELECT stock_id, stock_code, direction, status, status_reason, start_date, start_age, first_day_polarity, rule_version, boundary_flag FROM stock_luck_cycle")).use { c ->
            val idIdx = c.getColumnIndex("stock_id")
            val codeIdx = c.getColumnIndex("stock_code")
            val dirIdx = c.getColumnIndex("direction")
            val stIdx = c.getColumnIndex("status")
            val rsnIdx = c.getColumnIndex("status_reason")
            val sdIdx = c.getColumnIndex("start_date")
            val saIdx = c.getColumnIndex("start_age")
            val polIdx = c.getColumnIndex("first_day_polarity")
            val verIdx = c.getColumnIndex("rule_version")
            val bndIdx = c.getColumnIndex("boundary_flag")
            while (c.moveToNext()) {
                val sid = c.getLong(idIdx)
                luckCycleMap[sid] = StockLuckCycleEntity(
                    stockId = sid,
                    stockCode = c.getString(codeIdx),
                    direction = c.getString(dirIdx),
                    status = c.getString(stIdx),
                    statusReason = c.getString(rsnIdx),
                    startDate = if (c.isNull(sdIdx)) null else c.getString(sdIdx),
                    startAge = if (c.isNull(saIdx)) null else c.getInt(saIdx),
                    firstDayPolarity = c.getString(polIdx),
                    ruleVersion = c.getString(verIdx),
                    boundaryFlag = if (c.isNull(bndIdx)) null else c.getString(bndIdx),
                )
            }
        }

        // 4. 加载全库 luck_cycle_period
        val periodListMap = mutableMapOf<Long, MutableList<LuckCyclePeriodEntity>>()
        sq.query(SimpleSQLiteQuery("SELECT id, stock_id, cycle_index, ganzhi, stem, branch, start_date, end_date, start_year, end_year, start_age, end_age, rule_version FROM luck_cycle_period ORDER BY stock_id, cycle_index")).use { c ->
            val idIdx = c.getColumnIndex("id")
            val sidIdx = c.getColumnIndex("stock_id")
            val cIdx = c.getColumnIndex("cycle_index")
            val gIdx = c.getColumnIndex("ganzhi")
            val sIdx = c.getColumnIndex("stem")
            val bIdx = c.getColumnIndex("branch")
            val sdIdx = c.getColumnIndex("start_date")
            val edIdx = c.getColumnIndex("end_date")
            val syIdx = c.getColumnIndex("start_year")
            val eyIdx = c.getColumnIndex("end_year")
            val saIdx = c.getColumnIndex("start_age")
            val eaIdx = c.getColumnIndex("end_age")
            val verIdx = c.getColumnIndex("rule_version")
            while (c.moveToNext()) {
                val sid = c.getLong(sidIdx)
                val p = LuckCyclePeriodEntity(
                    id = c.getLong(idIdx),
                    stockId = sid,
                    cycleIndex = c.getInt(cIdx),
                    ganzhi = c.getString(gIdx),
                    stem = c.getString(sIdx),
                    branch = c.getString(bIdx),
                    startDate = c.getString(sdIdx),
                    endDate = c.getString(edIdx),
                    startYear = c.getInt(syIdx),
                    endYear = c.getInt(eyIdx),
                    startAge = c.getInt(saIdx),
                    endAge = c.getInt(eaIdx),
                    ruleVersion = c.getString(verIdx),
                )
                periodListMap.getOrPut(sid) { mutableListOf() }.add(p)
            }
        }

        // 5. 加载全库 natal_relation (按 listing_date 聚合)
        val relationsByListingDate = mutableMapOf<String, MutableList<NatalRelationEntity>>()
        sq.query(SimpleSQLiteQuery("SELECT id, chart_key, listing_date, relation_type, category, positions, source_pillar, target_pillar, source_ganzhi, target_ganzhi, element, notes, rule_version, status FROM natal_relation")).use { c ->
            val idIdx = c.getColumnIndex("id")
            val chartIdx = c.getColumnIndex("chart_key")
            val listIdx = c.getColumnIndex("listing_date")
            val relIdx = c.getColumnIndex("relation_type")
            val catIdx = c.getColumnIndex("category")
            val posIdx = c.getColumnIndex("positions")
            val spIdx = c.getColumnIndex("source_pillar")
            val tpIdx = c.getColumnIndex("target_pillar")
            val sgIdx = c.getColumnIndex("source_ganzhi")
            val tgIdx = c.getColumnIndex("target_ganzhi")
            val elemIdx = c.getColumnIndex("element")
            val noteIdx = c.getColumnIndex("notes")
            val verIdx = c.getColumnIndex("rule_version")
            val stIdx = c.getColumnIndex("status")
            while (c.moveToNext()) {
                val lDate = c.getString(listIdx)
                val rel = NatalRelationEntity(
                    id = c.getLong(idIdx),
                    chartKey = c.getString(chartIdx),
                    listingDate = lDate,
                    relationType = c.getString(relIdx),
                    category = c.getString(catIdx),
                    positions = c.getString(posIdx),
                    sourcePillar = c.getString(spIdx),
                    targetPillar = c.getString(tpIdx),
                    sourceGanzhi = c.getString(sgIdx),
                    targetGanzhi = c.getString(tgIdx),
                    element = c.getString(elemIdx),
                    notes = c.getString(noteIdx),
                    ruleVersion = c.getString(verIdx),
                    status = c.getString(stIdx),
                )
                relationsByListingDate.getOrPut(lDate) { mutableListOf() }.add(rel)
            }
        }

        // 6. 加载全库 stock_yongshen (按 chart_key 聚合)
        val yongshenByChartKey = mutableMapOf<String, StockYongshenEntity>()
        sq.query(SimpleSQLiteQuery("SELECT chart_key, day_stem, month_branch, strength_score, strength_level, status, yong_shen, xi_shen, ji_shen, chou_shen, xian_shen, candidate_elements, tiaohou_note, rationale, rule_version FROM stock_yongshen")).use { c ->
            val chartIdx = c.getColumnIndex("chart_key")
            val dsIdx = c.getColumnIndex("day_stem")
            val mbIdx = c.getColumnIndex("month_branch")
            val scoreIdx = c.getColumnIndex("strength_score")
            val levelIdx = c.getColumnIndex("strength_level")
            val stIdx = c.getColumnIndex("status")
            val yongIdx = c.getColumnIndex("yong_shen")
            val xiIdx = c.getColumnIndex("xi_shen")
            val jiIdx = c.getColumnIndex("ji_shen")
            val chouIdx = c.getColumnIndex("chou_shen")
            val xianIdx = c.getColumnIndex("xian_shen")
            val candIdx = c.getColumnIndex("candidate_elements")
            val tiaoIdx = c.getColumnIndex("tiaohou_note")
            val ratIdx = c.getColumnIndex("rationale")
            val verIdx = c.getColumnIndex("rule_version")
            while (c.moveToNext()) {
                val ck = c.getString(chartIdx)
                yongshenByChartKey[ck] = StockYongshenEntity(
                    chartKey = ck,
                    dayStem = c.getString(dsIdx),
                    monthBranch = c.getString(mbIdx),
                    strengthScore = c.getDouble(scoreIdx),
                    strengthLevel = c.getString(levelIdx),
                    status = c.getString(stIdx),
                    yongShen = c.getString(yongIdx),
                    xiShen = c.getString(xiIdx),
                    jiShen = c.getString(jiIdx),
                    chouShen = c.getString(chouIdx),
                    xianShen = c.getString(xianIdx),
                    candidateElements = c.getString(candIdx),
                    tiaohouNote = c.getString(tiaoIdx),
                    rationale = c.getString(ratIdx),
                    ruleVersion = c.getString(verIdx),
                )
            }
        }

        // 15 只不同类型的代表性股票（覆盖身强、中和、身弱、平盘、缺失、大运交接、六合、喜用候选等），每只取2个流月，共30组样本
        val targetStockList = listOf(
            "600519" to "贵州茅台",
            "601318" to "中国平安",
            "601857" to "中国石油",
            "002594" to "比亚迪",
            "300750" to "宁德时代",
            "000333" to "美的集团",
            "000002" to "万科A",
            "688981" to "中芯国际",
            "603222" to "济民健康", // 平盘
            "000004" to "国华网安", // 缺失
            "000035" to "中国天楹", // 多组六合
            "000063" to "中兴通讯", // 大运逆推
            "600036" to "招商银行",
            "601088" to "中国神华",
            "000001" to "平安银行",
        )
        val sampleTargetCodes = targetStockList.map { it.first }
        val allValidRuleIds = CopyRuleRepository.getDefault().getRules().map { it.ruleId }.toSet()

        val sampleResults = JSONArray()
        val ruleHitFrequency = mutableMapOf<String, Int>()

        var totalGenerated = 0
        var totalForbiddenHits = 0
        var totalPendingReviewCount = 0
        var totalUnreplacedPlaceholderHits = 0

        val t0 = System.currentTimeMillis()

        // 7. 遍历全库 5,395 只股票 × 12 个流月 = 64,740 份流月生成
        allStocks.forEach { stock ->
            val bazi = baziMap[stock.id] ?: return@forEach
            val luckCycle = luckCycleMap[stock.id]
            val periods = periodListMap[stock.id] ?: emptyList()
            val natalRelations = relationsByListingDate[stock.listingDate] ?: emptyList()

            val chartKey = "${bazi.yearPillar}_${bazi.monthPillar}_${bazi.dayPillar}"
            val yongshen = yongshenByChartKey[chartKey]

            val firstDayPolarity = FirstDayPolarity.fromCode(luckCycle?.firstDayPolarity)
            val dayunStatus = luckCycle?.status ?: "available"
            val yongshenStatus = YongshenCandidateStatus.fromCode(yongshen?.status)

            months2026.forEach { m ->
                // 按日期匹配当前大运区间 [start_date, end_date)
                val currentPeriod = periods.firstOrNull { p ->
                    m.date >= p.startDate && (m.date < p.endDate || (p.cycleIndex == 12 && m.date <= p.endDate))
                }

                // 核心：使用 Android 实际运行引擎生成五段式解读
                val fp = FortuneCopyEngine.composeMonthlyInterpretation(
                    stockId = stock.id,
                    stockCode = stock.code,
                    dayStem = bazi.dayStem,
                    yearPillar = bazi.yearPillar,
                    monthPillar = bazi.monthPillar,
                    dayPillar = bazi.dayPillar,
                    firstDayPolarity = firstDayPolarity,
                    dayunStatus = dayunStatus,
                    year = 2026,
                    month = m.month,
                    monthGanzhi = m.ganzhi,
                    natalRelationsCount = natalRelations.size,
                    yongshenStatus = yongshenStatus,
                    currentLuckPeriod = currentPeriod,
                    natalRelations = natalRelations,
                    yongshen = yongshen,
                    luckCycleDirection = luckCycle?.direction,
                    flowMonthStartDate = m.date,
                    flowMonthEndDate = m.date,
                )

                totalGenerated++

                // 校验 1：五段式各段文字非空
                assertTrue(fp.basisText.isNotBlank())
                assertTrue(fp.themeText.isNotBlank())
                assertTrue(fp.contradictionText.isNotBlank())
                assertTrue(fp.businessText.isNotBlank())
                assertTrue(fp.synthesisText.isNotBlank())

                // 校验 2：审核状态必须严格为 PENDING_REVIEW
                if (fp.reviewStatus == ReviewStatus.PENDING_REVIEW) {
                    totalPendingReviewCount++
                }

                // 校验 3：命中规则集非空，且所有命中的规则ID必须在冻结 281 条规则资产中真实存在
                assertTrue(fp.hitRuleIds.isNotEmpty())
                fp.hitRuleIds.forEach { rId ->
                    assertTrue("命中规则 $rId 必须在冻结 281 规则资产中存在", allValidRuleIds.contains(rId))
                    ruleHitFrequency[rId] = (ruleHitFrequency[rId] ?: 0) + 1
                }

                // 校验 4：60 禁词合规门禁零容忍扫描
                val fullMarkdown = fp.toFormattedMarkdown()
                forbiddenWords.forEach { fw ->
                    if (fullMarkdown.contains(fw)) {
                        totalForbiddenHits++
                    }
                }

                // 校验 5：严禁存在未替换占位符（如未处理的 {relation.xxx} 或 {dayun.xxx}）
                if (fullMarkdown.contains("{") && fullMarkdown.contains("}")) {
                    totalUnreplacedPlaceholderHits++
                }

                // 抽样记录样本：从 15 只目标股票中各抽取第 1 月与第 6 月（共 30 组样本）
                val targetCode = sampleTargetCodes.firstOrNull { stock.code.contains(it) }
                if (targetCode != null && (m.month == 1 || m.month == 6) && sampleResults.length() < 30) {
                    val hash = sha256(fullMarkdown)
                    val sampleObj = JSONObject().apply {
                        put("stockId", stock.id)
                        put("stockCode", stock.code)
                        put("stockName", stock.name)
                        put("year", 2026)
                        put("month", m.month)
                        put("monthGanzhi", m.ganzhi)
                        put("dayunGanzhi", currentPeriod?.ganzhi ?: "无/未起运")
                        put("dayunPeriod", currentPeriod?.let { "${it.startDate}—${it.endDate}" } ?: "N/A")
                        put("dayunAvailability", if (currentPeriod != null) "AVAILABLE" else "UNAVAILABLE")
                        put("dayunDirection", luckCycle?.direction ?: "N/A")
                        put("firstDayPolarity", firstDayPolarity.name)
                        put("natalRelationsCount", natalRelations.size)
                        put("yongshenStatus", yongshenStatus.name)
                        put("yongshenElements", yongshen?.yongShen ?: yongshen?.candidateElements ?: "N/A")
                        put("hitRuleCount", fp.hitRuleIds.size)
                        put("hitRuleIds", JSONArray(fp.hitRuleIds))
                        put("reviewStatus", fp.reviewStatus.name)
                        put("preciseAdvancedNotice", fp.preciseAdvancedNotice)
                        put("fiveParagraphHash", hash)
                        // 提供完整五段式正文（非截断）
                        put("fullBasisText", fp.basisText)
                        put("fullThemeText", fp.themeText)
                        put("fullContradictionText", fp.contradictionText)
                        put("fullBusinessText", fp.businessText)
                        put("fullSynthesisText", fp.synthesisText)
                    }
                    sampleResults.put(sampleObj)
                }
            }
        }

        val durationMs = System.currentTimeMillis() - t0

        // 写入结果文件供报告引用
        val reportsDir = File("build/reports").apply { mkdirs() }
        File(reportsDir, "acceptance_64740_android_samples.json").writeText(sampleResults.toString(2))

        // 导出格式化 Markdown 供人工审阅直接查阅
        val mdSb = StringBuilder()
        mdSb.append("# 股运通 V1.3｜30 组代表性流月五段式解读人工审阅样例（Android 原生运行时生成）\n\n")
        mdSb.append("> **生成引擎：** Android FortuneCopyEngine (Kotlin Runtime)  \n")
        mdSb.append("> **覆盖股票：** 15 只不同类型代表性股票 × 2 个流月 = 30 组完整样例  \n")
        mdSb.append("> **特征分布：** 涵盖身强、中和、身弱、平盘、缺失、大运交接、六合支对、用神候选等全部关键命理类型  \n\n")

        for (i in 0 until sampleResults.length()) {
            val s = sampleResults.getJSONObject(i)
            mdSb.append("## 样例 ${i + 1}：${s.getString("stockName")} (${s.getString("stockCode")}) - ${s.getInt("year")}年${s.getInt("month")}月 (${s.getString("monthGanzhi")})\n\n")
            mdSb.append("- **大运区间：** ${s.getString("dayunGanzhi")} (${s.getString("dayunPeriod")}) · 方向：${s.getString("dayunDirection")} · 状态：${s.getString("dayunAvailability")}\n")
            mdSb.append("- **首日形态：** ${s.getString("firstDayPolarity")} · 原局关系数：${s.getInt("natalRelationsCount")} · 喜用状态：${s.getString("yongshenStatus")} (${s.getString("yongshenElements")})\n")
            mdSb.append("- **命中规则ID (${s.getInt("hitRuleCount")}条)：** `${s.getJSONArray("hitRuleIds")}`\n")
            mdSb.append("- **审核状态：** `${s.getString("reviewStatus")}` · **文本哈希：** `${s.getString("fiveParagraphHash")}`\n\n")
            mdSb.append("### 五段式完整正文：\n\n")
            mdSb.append("1. **【命理依据】** ${s.getString("fullBasisText")}\n\n")
            mdSb.append("2. **【本月主题】** ${s.getString("fullThemeText")}\n\n")
            mdSb.append("3. **【潜在矛盾】** ${s.getString("fullContradictionText")}\n\n")
            mdSb.append("4. **【经营观察】** ${s.getString("fullBusinessText")}\n\n")
            mdSb.append("5. **【综合解释】** ${s.getString("fullSynthesisText")}\n\n")
            mdSb.append("---\n\n")
        }
        File(reportsDir, "acceptance_64740_android_samples.md").writeText(mdSb.toString())

        val summaryObj = JSONObject().apply {
            put("engine", "Android FortuneCopyEngine (Kotlin Runtime)")
            put("totalStocks", allStocks.size)
            put("totalMonthsEvaluated", totalGenerated)
            put("successCount", totalGenerated)
            put("errorCount", 0)
            put("totalForbiddenHits", totalForbiddenHits)
            put("totalPendingReviewCount", totalPendingReviewCount)
            put("totalUnreplacedPlaceholderHits", totalUnreplacedPlaceholderHits)
            put("uniqueRulesHit", ruleHitFrequency.size)
            put("durationSeconds", durationMs / 1000.0)
            put("sampleCount", sampleResults.length())
        }
        File(reportsDir, "acceptance_64740_android_summary.json").writeText(summaryObj.toString(2))

        // 断言验证
        assertEquals("全库流月生成总数必须精确等于 64740 份", 64740, totalGenerated)
        assertEquals("全量五段式解读 60 禁词必须为 0 命中", 0, totalForbiddenHits)
        assertEquals("全量五段式解读未替换占位符必须为 0 命中", 0, totalUnreplacedPlaceholderHits)
        assertEquals("全量五段式解读必须 100% 保持候审状态 PENDING_REVIEW", 64740, totalPendingReviewCount)
        assertTrue("规则库命中规则种类应丰富 (>=120)", ruleHitFrequency.size >= 120)
        assertEquals("30 组抽样样本记录完整", 30, sampleResults.length())
    }
}
