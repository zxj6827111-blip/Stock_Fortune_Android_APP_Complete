package com.stockfortune.app.data.repository

import android.util.Log
import com.stockfortune.app.data.dao.BaziDao
import com.stockfortune.app.data.dao.CalendarDao
import com.stockfortune.app.data.dao.FilterDao
import com.stockfortune.app.data.dao.LuckCycleDao
import com.stockfortune.app.data.dao.NatalRelationDao
import com.stockfortune.app.data.dao.ScanCacheDao
import com.stockfortune.app.data.dao.StockDao
import com.stockfortune.app.data.dao.StockYongshenDao
import com.stockfortune.app.data.entity.GanzhiCalendarEntity
import com.stockfortune.app.data.entity.ScanCacheEntity
import com.stockfortune.app.domain.calculator.BaziTables
import com.stockfortune.app.domain.calculator.FortuneCopyEngine
import com.stockfortune.app.domain.calculator.FortuneText
import com.stockfortune.app.domain.calculator.GanzhiCalculator
import com.stockfortune.app.domain.calculator.TenGodCalculator
import com.stockfortune.app.domain.model.DateSelectionRow
import com.stockfortune.app.domain.model.DayAnalysis
import com.stockfortune.app.domain.model.DayDetail
import com.stockfortune.app.domain.model.FilterQuery
import com.stockfortune.app.domain.model.FirstDayPolarity
import com.stockfortune.app.domain.model.FlowPillar
import com.stockfortune.app.domain.model.HiddenStemItem
import com.stockfortune.app.domain.model.MonthAnalysis
import com.stockfortune.app.domain.model.MonthLabel
import com.stockfortune.app.domain.model.PillarHidden
import com.stockfortune.app.domain.model.ScanRow
import com.stockfortune.app.domain.model.ScanSummary
import com.stockfortune.app.domain.model.Strength
import com.stockfortune.app.domain.model.TenGod
import com.stockfortune.app.domain.model.WealthType
import com.stockfortune.app.domain.model.YearAnalysis
import com.stockfortune.app.domain.model.YongshenCandidateStatus
import java.time.LocalDate

/**
 * 十神 / 财星分析的取数与组装。所有干支来自预置 `ganzhi_calendar`（日粒度口径），
 * 十神与财星由 [TenGodCalculator] 现算，判定规则见 PHASE0_DESIGN_REVIEW §8（Rule v1.1）。
 */
class AnalysisRepository(
    private val calendarDao: CalendarDao,
    private val baziDao: BaziDao,
    private val filterDao: FilterDao,
    private val scanCacheDao: ScanCacheDao,
    private val luckCycleDao: LuckCycleDao? = null,
    private val natalRelationDao: NatalRelationDao? = null,
    private val stockYongshenDao: StockYongshenDao? = null,
    private val stockDao: StockDao? = null,
    private val copyRuleRepository: CopyRuleRepository? = null,
) {

    companion object {
        /** 扫描缓存保留的最近扫描日数，防止缓存表无界增长。 */
        const val CACHE_KEEP_DAYS = 30

        /** 单次八字择日最多返回的命中日期；超出时截断并在区间提示里说明。 */
        const val DATE_SELECT_LIMIT = 400

        private const val TAG = "AnalysisRepository"
    }

    private suspend fun day(date: String): GanzhiCalendarEntity? = calendarDao.ganzhi(date)

    private suspend fun tradeDayFlags(start: String, end: String): Map<String, Int> =
        calendarDao.tradeRange(start, end).associate { it.date to it.isTradeDay }

    fun dayAnalysis(dayStem: String, gz: GanzhiCalendarEntity, isTradeDay: Boolean): DayAnalysis {
        val stemGod = TenGodCalculator.tenGod(dayStem, gz.dayStem)
        val branchGod = TenGodCalculator.tenGod(dayStem, TenGodCalculator.mainQi(gz.dayBranch))
        val wealth = if (!isTradeDay) WealthType.NONE else TenGodCalculator.wealthType(dayStem, gz.dayStem, gz.dayBranch)
        return DayAnalysis(
            date = gz.date, weekday = LocalDate.parse(gz.date).dayOfWeek.value, isTradeDay = isTradeDay,
            dayGanzhi = gz.dayGanzhi, dayStem = gz.dayStem, dayBranch = gz.dayBranch,
            dayTenGod = stemGod, branchTenGod = branchGod, wealth = wealth, solarTerm = gz.solarTerm,
        )
    }

    private val pillarLabels = listOf("year" to "年柱", "month" to "月柱", "day" to "日柱", "hour" to "时柱")
    private val rankOrder = listOf("本气", "中气", "余气")

    /** 单日详情：流年 / 流月 / 流日 / 本命藏干四个维度 + 流日财星判定推导 */
    suspend fun dayDetail(stockId: Long, date: String): DayDetail? {
        val bazi = baziDao.findByStockId(stockId) ?: return null
        val gz = day(date) ?: return null
        val ds = bazi.dayStem
        val trade = (calendarDao.tradeDay(date)?.isTradeDay ?: 0) == 1
        fun flow(label: String, ganzhi: String, stem: String, branch: String) = FlowPillar(
            label = label, ganzhi = ganzhi,
            elements = TenGodCalculator.elementOf(stem) + "·" + TenGodCalculator.elementOfBranch(branch),
            stemGod = TenGodCalculator.tenGod(ds, stem),
            branchGod = TenGodCalculator.tenGod(ds, TenGodCalculator.mainQi(branch)),
            wealth = if (!trade) WealthType.NONE else TenGodCalculator.wealthType(ds, stem, branch),
        )
        val rows = filterDao.hiddenOfStock(stockId)
        val hidden = pillarLabels.mapNotNull { (key, label) ->
            val cells = rows.filter { it.pillar == key }.sortedBy { rankOrder.indexOf(it.rank).coerceAtLeast(0) }
            cells.firstOrNull()?.let { first ->
                // 认不出的十神标签直接跳过该行，而不是静默渲染成"比肩"掩盖脏数据
                val items = cells.mapNotNull { cell ->
                    TenGod.fromCn(cell.tenGod)?.let { HiddenStemItem(cell.hiddenStem, it, cell.rank) }
                }
                PillarHidden(label = label, branch = first.branch, items = items)
            }
        }
        val w = if (!trade) WealthType.NONE else TenGodCalculator.wealthType(ds, gz.dayStem, gz.dayBranch)
        return DayDetail(
            dayMaster = ds, dayMasterElement = TenGodCalculator.elementOf(ds), date = date, isTradeDay = trade,
            solarTerm = gz.solarTerm,
            flows = listOf(
                flow("流年", gz.yearGanzhi, gz.yearStem, gz.yearBranch),
                flow("流月", gz.monthGanzhi, gz.monthStem, gz.monthBranch),
                flow("流日", gz.dayGanzhi, gz.dayStem, gz.dayBranch),
            ),
            hidden = hidden,
            hiddenGods = TenGod.ORDER.filter { g -> rows.any { it.tenGod == g.cn } },
            wealth = w,
            basis = FortuneText.wealthBasis(ds, gz.dayGanzhi, gz.dayStem, gz.dayBranch, w, trade),
        )
    }

    /** 每日分析页：整月，仅交易日 */
    suspend fun monthDays(stockId: Long, year: Int, month: Int): MonthAnalysis? {
        val bazi = baziDao.findByStockId(stockId) ?: return null
        val (first, last) = GanzhiCalculator.monthRange(year, month)
        val start = GanzhiCalculator.iso(first)
        val end = GanzhiCalculator.iso(last)
        val gzRows = calendarDao.ganzhiRange(start, end)
        if (gzRows.isEmpty()) return null
        val flags = tradeDayFlags(start, end)
        val all = gzRows.map { dayAnalysis(bazi.dayStem, it, (flags[it.date] ?: 0) == 1) }
        val trade = all.filter { it.isTradeDay }
        val midGz = gzRows[minOf(gzRows.size / 2, gzRows.size - 1)]
        val monthGod = TenGodCalculator.tenGod(bazi.dayStem, midGz.monthStem)
        val strength = strengthOf(bazi.dayMasterStrength)
        val zheng = trade.count { it.wealth == WealthType.ZHENG_CAI }
        val pian = trade.count { it.wealth == WealthType.PIAN_CAI }

        val luckCycle = luckCycleDao?.findByStockId(stockId)
        val firstDayPolarity = FirstDayPolarity.fromCode(luckCycle?.firstDayPolarity)
        val dayunStatus = luckCycle?.status ?: "available"
        val natalRelations = natalRelationDao?.getByStockId(stockId) ?: emptyList()
        val natalRelationsCount = natalRelations.size
        val yongshen = stockYongshenDao?.getByStockId(stockId)
        val yongshenStatus = YongshenCandidateStatus.fromCode(yongshen?.status)
        val stockCode = stockDao?.findById(stockId)?.code ?: ""
        val currentPeriod = luckCycleDao?.currentPeriodForDate(stockId, midGz.date)

        val midDate = LocalDate.parse(midGz.date)
        val wideGzRows = calendarDao.ganzhiRange(
            GanzhiCalculator.iso(midDate.minusDays(35)),
            GanzhiCalculator.iso(midDate.plusDays(35)),
        )
        val solarTermDays = wideGzRows.filter { it.monthGanzhi == midGz.monthGanzhi }
        val flowMonthStartDate = solarTermDays.minOfOrNull { it.date } ?: start
        val flowMonthEndDate = solarTermDays.maxOfOrNull { it.date } ?: end

        val candidateRules = copyRuleRepository?.getRules() ?: CopyRuleRepository.getDefault().getRules()
        val fiveParagraph = FortuneCopyEngine.composeMonthlyInterpretation(
            stockId = stockId,
            stockCode = stockCode,
            dayStem = bazi.dayStem,
            yearPillar = bazi.yearPillar,
            monthPillar = bazi.monthPillar,
            dayPillar = bazi.dayPillar,
            firstDayPolarity = firstDayPolarity,
            dayunStatus = dayunStatus,
            year = year,
            month = month,
            monthGanzhi = midGz.monthGanzhi,
            natalRelationsCount = natalRelationsCount,
            yongshenStatus = yongshenStatus,
            currentLuckPeriod = currentPeriod,
            natalRelations = natalRelations,
            yongshen = yongshen,
            candidateRules = candidateRules,
            luckCycleDirection = luckCycle?.direction,
            flowMonthStartDate = flowMonthStartDate,
            flowMonthEndDate = flowMonthEndDate,
        )

        return MonthAnalysis(
            year = year, month = month, monthGanzhi = midGz.monthGanzhi,
            branchLabel = midGz.monthBranchLabel, monthStemTenGod = monthGod,
            wuxingSummary = TenGodCalculator.seasonSummary(midGz.monthBranch),
            summary = FortuneText.monthSummary(midGz.monthGanzhi, monthGod, TenGodCalculator.wealthType(bazi.dayStem, midGz.monthStem, midGz.monthBranch), midGz.monthBranch, strength),
            days = all, tradeDays = trade, zhengCount = zheng, pianCount = pian,
            otherCount = trade.size - zheng - pian, tradeDayCount = trade.size,
            tip = FortuneText.monthTip(zheng, pian),
            monthNote = monthSpanNote(gzRows, midGz),
            fiveParagraph = fiveParagraph,
            currentPeriod = currentPeriod,
        )
    }

    /**
     * 预置库由同一 rule_version 产出，认不出强弱标签就是库与代码不匹配 ——
     * 不能像 WealthType.fromCn 那样兜底成某个默认值，那会把损坏洗白成「中和」。
     */
    private fun strengthOf(raw: String): Strength =
        Strength.fromCn(raw)
            ?: error("stock_bazi.day_master_strength 出现未知标签「$raw」，预置库与代码 rule_version 不匹配")

    /** 公历月若跨两个干支月，给出"哪一天交节、之前属何月"的口径说明。 */
    private fun monthSpanNote(rows: List<GanzhiCalendarEntity>, midGz: GanzhiCalendarEntity): String? {
        val first = rows.first()
        if (first.monthGanzhi == midGz.monthGanzhi) return null
        val idx = rows.indexOfFirst { it.monthGanzhi != first.monthGanzhi }
        if (idx <= 0) return null
        val d = LocalDate.parse(rows[idx].date)
        return "本月 ${d.monthValue}月${d.dayOfMonth}日交节入${midGz.monthGanzhi}（${midGz.monthBranchLabel}）；" +
            "此前 $idx 天仍属${first.monthGanzhi}，月令与月运简述按交节后的干支月计。"
    }

    /** 年度运势页：按干支月（寅月=1月 … 丑月=12月）列出财星标签 */
    suspend fun yearAnalysis(stockId: Long, year: Int): YearAnalysis? {
        val bazi = baziDao.findByStockId(stockId) ?: return null
        val (yStart, yEnd) = GanzhiCalculator.yearRange(year)
        val wideStart = GanzhiCalculator.iso(yStart.minusDays(45))
        val wideEnd = GanzhiCalculator.iso(yEnd.plusDays(45))
        val rows = calendarDao.ganzhiRange(wideStart, wideEnd)
        if (rows.isEmpty()) return null
        val yearPillar = rows.filter { it.date.startsWith(year.toString()) }
            .groupingBy { it.yearGanzhi }.eachCount().maxByOrNull { it.value }?.key ?: return null
        val inYear = rows.filter { it.yearGanzhi == yearPillar }
        if (inYear.isEmpty()) return null
        val strength = strengthOf(bazi.dayMasterStrength)
        val months = inYear.groupBy { it.monthGanzhi }.toList()
            .sortedBy { (_, g) -> g.first().date }
            .mapIndexed { idx, (ganzhi, days) ->
                val sample = days.first()
                val wealth = TenGodCalculator.wealthType(bazi.dayStem, sample.monthStem, sample.monthBranch)
                val god = TenGodCalculator.tenGod(bazi.dayStem, sample.monthStem)
                MonthLabel(
                    month = idx + 1, monthGanzhi = ganzhi, branchLabel = sample.monthBranchLabel,
                    monthBranch = sample.monthBranch,
                    wealth = wealth,
                    tenGod = god,
                    startDate = days.minOf { it.date }, endDate = days.maxOf { it.date },
                    summary = FortuneText.monthSummary(ganzhi, god, wealth, sample.monthBranch, strength),
                )
            }

        val luckCycle = luckCycleDao?.findByStockId(stockId)
        val firstDayPolarity = FirstDayPolarity.fromCode(luckCycle?.firstDayPolarity)
        val dayunStatus = luckCycle?.status ?: "available"
        val natalRelations = natalRelationDao?.getByStockId(stockId) ?: emptyList()
        val natalRelationsCount = natalRelations.size
        val yongshen = stockYongshenDao?.getByStockId(stockId)
        val yongshenStatus = YongshenCandidateStatus.fromCode(yongshen?.status)
        val stockCode = stockDao?.findById(stockId)?.code ?: ""
        val yearStartDate = inYear.firstOrNull()?.date ?: "${year}-02-04"
        val currentPeriod = luckCycleDao?.currentPeriodForDate(stockId, yearStartDate)

        val candidateRules = copyRuleRepository?.getRules() ?: CopyRuleRepository.getDefault().getRules()
        val monthlyInterpretations = months.map { mLabel ->
            val monthPeriod = luckCycleDao?.currentPeriodForDate(stockId, mLabel.startDate)
            FortuneCopyEngine.composeMonthlyInterpretation(
                stockId = stockId,
                stockCode = stockCode,
                dayStem = bazi.dayStem,
                yearPillar = bazi.yearPillar,
                monthPillar = bazi.monthPillar,
                dayPillar = bazi.dayPillar,
                firstDayPolarity = firstDayPolarity,
                dayunStatus = dayunStatus,
                year = year,
                month = mLabel.month,
                monthGanzhi = mLabel.monthGanzhi,
                natalRelationsCount = natalRelationsCount,
                yongshenStatus = yongshenStatus,
                currentLuckPeriod = monthPeriod,
                natalRelations = natalRelations,
                yongshen = yongshen,
                candidateRules = candidateRules,
                luckCycleDirection = luckCycle?.direction,
                flowMonthStartDate = mLabel.startDate,
                flowMonthEndDate = mLabel.endDate,
            )
        }

        val annualSynthesis = FortuneCopyEngine.composeAnnualSynthesis(
            stockId = stockId,
            stockCode = stockCode,
            dayStem = bazi.dayStem,
            year = year,
            yearGanzhi = yearPillar,
            strength = strength,
            currentPeriod = currentPeriod,
            monthlyInterpretations = monthlyInterpretations,
            months = months,
            yongshen = yongshen,
            natalRelationsCount = natalRelationsCount,
        )

        val yearSample = inYear.first()
        val yearWealth = TenGodCalculator.wealthType(bazi.dayStem, yearSample.yearStem, yearSample.yearBranch)
        return YearAnalysis(
            year = year, yearGanzhi = yearPillar,
            yearWuxing = TenGodCalculator.elementOf(yearSample.yearStem) + TenGodCalculator.elementOfBranch(yearSample.yearBranch),
            wealthSummary = FortuneText.yearWealthSummary(
                yearWealth, yearPillar, TenGodCalculator.tenGod(bazi.dayStem, yearSample.yearStem), strength,
            ),
            industryNote = FortuneText.yearIndustryNote(bazi.dayStem, yearSample.yearBranch),
            advice = FortuneText.yearAdvice(yearWealth),
            months = months,
            zhengCount = months.count { it.wealth == WealthType.ZHENG_CAI },
            pianCount = months.count { it.wealth == WealthType.PIAN_CAI },
            currentPeriod = currentPeriod,
            annualSynthesis = annualSynthesis,
        )
    }

    /** 10 个日主对某一流日干支的判定结果（全市场只需算 10 次）。 */
    private data class StemVerdict(val wealth: WealthType, val dayGod: TenGod)

    private fun verdicts(stem: String, branch: String): Map<String, StemVerdict> =
        BaziTables.STEMS.associateWith { ds ->
            StemVerdict(TenGodCalculator.wealthType(ds, stem, branch), TenGodCalculator.tenGod(ds, stem))
        }

    /** 首页概览：只要计数，不物化任何股票行。 */
    suspend fun scanCounts(date: String): Pair<Int, Int> {
        val gz = day(date) ?: return 0 to 0
        if (!isTrade(date)) return 0 to 0
        val v = verdicts(gz.dayStem, gz.dayBranch)
        val counts = baziDao.countByDayStem().associate { it.dayStem to it.n }
        fun total(vararg wealth: WealthType) = v.filterValues { it.wealth in wealth }.keys.sumOf { counts[it] ?: 0 }
        return total(WealthType.ZHENG_CAI) to total(WealthType.PIAN_CAI)
    }

    /** 每日扫描：全市场某日财星分布。同日重复扫描直接读缓存，不再重算。 */
    suspend fun scan(date: String, persist: Boolean = true): ScanSummary {
        val gz = day(date) ?: return ScanSummary(date, emptyList(), 0, 0)
        if (!isTrade(date)) return ScanSummary(date, emptyList(), 0, 0)
        val cached = scanCacheDao.rowsWithStock(date)
        if (cached.isNotEmpty()) {
            val rows = cached.mapNotNull { c ->
                val god = TenGod.fromCn(c.dayTenGod) ?: return@mapNotNull null
                val w = WealthType.fromCnStrict(c.wealthType) ?: return@mapNotNull null
                ScanRow(
                    stockId = c.stockId, code = c.code, symbol = c.symbol, name = c.name,
                    industry = c.industry, wealth = w, dayTenGod = god,
                    fromHiddenStem = !god.isWealth,
                )
            }
            if (rows.size == cached.size) {
                return ScanSummary(
                    date = date, rows = rows,
                    zhengCount = rows.count { it.wealth == WealthType.ZHENG_CAI },
                    pianCount = rows.count { it.wealth == WealthType.PIAN_CAI },
                )
            }
            // 出现认不出的标签 = 这批缓存不是按当前口径算出来的（例如预置库换版后的残留）。
            // 整批作废重算，而不是把脏标签静默渲染成"比肩"掩盖它 —— 后者与本文件
            // dayDetail 里已声明的口径相悖，且会让计数悄悄错掉。
            Log.w(TAG, "扫描缓存有 ${cached.size - rows.size} 条标签无法识别，作废重算: $date")
            scanCacheDao.clear()
        }
        val v = verdicts(gz.dayStem, gz.dayBranch)
        val wealthOf = v.mapValues { it.value.wealth }
        val hitStems = wealthOf.filterValues { it.isWealth }.keys.toList()
        if (hitStems.isEmpty()) return ScanSummary(date, emptyList(), 0, 0)
        val hits = baziDao.byDayStems(hitStems)
        val rows = hits.map { s ->
            ScanRow(
                stockId = s.stockId, code = s.code, symbol = s.symbol, name = s.name,
                industry = s.industry, wealth = wealthOf.getValue(s.dayStem),
                dayTenGod = v.getValue(s.dayStem).dayGod,
                fromHiddenStem = !v.getValue(s.dayStem).dayGod.isWealth,
            )
        }.sortedWith(compareBy({ it.wealth != WealthType.ZHENG_CAI }, { it.symbol }))
        if (persist) {
            val now = System.currentTimeMillis()
            scanCacheDao.upsertAll(hits.map { s ->
                ScanCacheEntity(
                    stockId = s.stockId, date = date,
                    yearTenGod = TenGodCalculator.tenGod(s.dayStem, gz.yearStem).cn,
                    monthTenGod = TenGodCalculator.tenGod(s.dayStem, gz.monthStem).cn,
                    dayTenGod = v.getValue(s.dayStem).dayGod.cn,
                    wealthType = wealthOf.getValue(s.dayStem).cn,
                    isTradeDay = 1, computedAt = now,
                )
            })
            scanCacheDao.evictOld(CACHE_KEEP_DAYS)
        }
        return ScanSummary(
            date = date, rows = rows,
            zhengCount = rows.count { it.wealth == WealthType.ZHENG_CAI },
            pianCount = rows.count { it.wealth == WealthType.PIAN_CAI },
        )
    }

    private suspend fun isTrade(date: String): Boolean = (calendarDao.tradeDay(date)?.isTradeDay ?: 0) == 1

    /** 十神筛选：组内 OR、组间 AND；时间维度以所选日期为准 */
    suspend fun filter(query: FilterQuery): List<ScanRow> {
        val gz = day(query.date) ?: return emptyList()
        val trade = isTrade(query.date)
        // 时间三维的十神只由日主决定：先用 10 个日主筛掉不可能的日主，再取候选股票
        val allowedStems = BaziTables.STEMS.filter { ds ->
            temporalMatch(query.yearGods, ds, gz.yearStem, gz.yearBranch) &&
                temporalMatch(query.monthGods, ds, gz.monthStem, gz.monthBranch) &&
                temporalMatch(query.dayGods, ds, gz.dayStem, gz.dayBranch)
        }
        if (allowedStems.isEmpty()) return emptyList()
        // 藏干维度一次取回"哪些股票命中 + 各命中了哪些十神"。先前还额外跑了一遍
        // stockIdsByHiddenGods，取的正是同一个集合的键，等于把 stock_hidden_ten_god 白扫两次。
        val hiddenGodOf: Map<Long, List<TenGod>> =
            if (query.hiddenGods.isEmpty()) emptyMap() else dbHiddenGodsByStock(query.hiddenGods.toList())
        val out = baziDao.byDayStems(allowedStems).mapNotNull { s ->
            if (hiddenGodOf.isNotEmpty() && s.stockId !in hiddenGodOf) return@mapNotNull null
            val hits = LinkedHashSet<TenGod>()
            if (query.hiddenGods.isNotEmpty()) hits += hiddenGodOf[s.stockId].orEmpty()
            temporalHits(query.yearGods, s.dayStem, gz.yearStem, gz.yearBranch)?.let { hits += it }
            temporalHits(query.monthGods, s.dayStem, gz.monthStem, gz.monthBranch)?.let { hits += it }
            temporalHits(query.dayGods, s.dayStem, gz.dayStem, gz.dayBranch)?.let { hits += it }
            ScanRow(
                stockId = s.stockId, code = s.code, symbol = s.symbol, name = s.name, industry = s.industry,
                wealth = TenGodCalculator.wealthType(s.dayStem, gz.dayStem, gz.dayBranch)
                    .let { if (!trade) WealthType.NONE else it },
                dayTenGod = TenGodCalculator.tenGod(s.dayStem, gz.dayStem),
                fromHiddenStem = false,
                matchedGods = hits.toList(),
            )
        }
        // 命中维度多的排前面；再按代码。旧版按"正财优先+代码"排序，会让任意条件的
        // 前 50 条几乎相同，用户看起来就是"选什么都没变化"。
        return out.sortedWith(compareByDescending<ScanRow> { it.matchedGods.size }.thenBy { it.symbol })
    }

    /** 时间维度匹配：天干十神或地支本气十神命中即可（空集合 = 不限） */
    private fun temporalMatch(gods: Set<TenGod>, dayStem: String, stem: String, branch: String): Boolean =
        temporalHits(gods, dayStem, stem, branch) != null || gods.isEmpty()

    /** 返回该时间维度命中的十神（未命中返回 null）；空集合 = 不参与筛选 */
    private fun temporalHits(gods: Set<TenGod>, dayStem: String, stem: String, branch: String): List<TenGod>? {
        if (gods.isEmpty()) return null
        val candidates = listOf(
            TenGodCalculator.tenGod(dayStem, stem),
            TenGodCalculator.tenGod(dayStem, TenGodCalculator.mainQi(branch)),
        )
        val hit = candidates.filter { it in gods }.distinct()
        return hit.ifEmpty { null }
    }

    /** 每只股票在"藏干十神"维度上命中的十神集合 */
    private suspend fun dbHiddenGodsByStock(gods: List<TenGod>): Map<Long, List<TenGod>> {
        val map = mutableMapOf<Long, MutableList<TenGod>>()
        filterDao.hiddenGodPairs(gods.map { it.cn }).forEach { row ->
            TenGod.fromCn(row.tenGod)?.let { map.getOrPut(row.stockId) { mutableListOf() }.add(it) }
        }
        return map.mapValues { (_, v) -> v.distinct().sortedBy { TenGod.ORDER.indexOf(it) } }
    }

    /**
     * 八字择日：区间内命中财星的日期。
     *
     * `limit` 是必需的：选择器放开 1990-12-01~2035-12-31 全域，木日主（土为财）在整区间
     * 约有 7684 个命中日，不限量会把这一页的整页滚动列表撑爆。返回条数等于 limit 时
     * 说明被截断，调用方须如实告知用户。
     */
    suspend fun dateSelect(
        stockId: Long,
        start: String,
        end: String,
        onlyTradeDays: Boolean = false,
        limit: Int = DATE_SELECT_LIMIT,
    ): List<DateSelectionRow> {
        val bazi = baziDao.findByStockId(stockId) ?: return emptyList()
        val s = maxOf(start, calendarDao.minDate() ?: start)
        val e = minOf(end, calendarDao.maxDate() ?: end)
        if (s > e) return emptyList()
        val rows = calendarDao.ganzhiRange(s, e)
        val flags = tradeDayFlags(s, e)
        return rows.mapNotNull { gz ->
            val trade = (flags[gz.date] ?: 0) == 1
            if (onlyTradeDays && !trade) return@mapNotNull null
            val w = TenGodCalculator.wealthType(bazi.dayStem, gz.dayStem, gz.dayBranch)
            if (w == WealthType.OTHER) return@mapNotNull null
            DateSelectionRow(
                date = gz.date, weekday = LocalDate.parse(gz.date).dayOfWeek.value, isTradeDay = trade,
                dayGanzhi = gz.dayGanzhi, wealth = w, dayTenGod = TenGodCalculator.tenGod(bazi.dayStem, gz.dayStem),
            )
        }.take(limit)
    }

    suspend fun clearCache() = scanCacheDao.clear()

    suspend fun cacheSize(): Int = scanCacheDao.count()

}
