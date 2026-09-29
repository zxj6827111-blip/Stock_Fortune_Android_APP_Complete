package com.stockfortune.app.ui.vm

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.stockfortune.app.AppContainer
import com.stockfortune.app.data.repository.StockDetail
import com.stockfortune.app.domain.calculator.GanzhiCalculator
import com.stockfortune.app.domain.model.DateSelectionRow
import com.stockfortune.app.domain.model.DayDetail
import com.stockfortune.app.domain.model.FilterQuery
import com.stockfortune.app.domain.model.MonthAnalysis
import com.stockfortune.app.domain.model.ScanRow
import com.stockfortune.app.domain.model.ScanSummary
import com.stockfortune.app.domain.model.SearchHit
import com.stockfortune.app.domain.model.StockInfo
import com.stockfortune.app.domain.model.TenGod
import com.stockfortune.app.domain.model.WealthType
import com.stockfortune.app.domain.model.YearAnalysis
import com.stockfortune.app.ui.common.vmFactory
import java.time.LocalDate
import java.time.YearMonth
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

private fun todayIso(): String = LocalDate.now().toString()

/** 首页：今日概览（最近交易日的正/偏财数量）。 */
class HomeViewModel(private val container: AppContainer) : ViewModel() {
    data class State(
        val date: String = "",
        val zhengCount: Int = 0,
        val pianCount: Int = 0,
        val loading: Boolean = true,
    )

    private val _state = MutableStateFlow(State())
    val state: StateFlow<State> = _state.asStateFlow()

    init {
        viewModelScope.launch(Dispatchers.Default) {
            val date = container.calendarRepository.nearestTradeDayOnOrBefore(todayIso())
            val (zheng, pian) = container.analysisRepository.scanCounts(date)
            _state.update { it.copy(date = date, zhengCount = zheng, pianCount = pian, loading = false) }
        }
    }

    companion object {
        fun Factory(c: AppContainer) = vmFactory { HomeViewModel(c) }
    }
}

/** 搜索结果列表。 */
class SearchViewModel(private val container: AppContainer) : ViewModel() {
    data class State(val query: String = "", val hits: List<SearchHit> = emptyList(), val loading: Boolean = false)

    private val _state = MutableStateFlow(State())
    val state: StateFlow<State> = _state.asStateFlow()

    fun search(q: String) {
        _state.update { it.copy(query = q, loading = true) }
        viewModelScope.launch(Dispatchers.Default) {
            val hits = container.stockRepository.search(q).map {
                SearchHit(it.stockId, it.code, it.symbol, it.name, it.listingDate, it.board)
            }
            _state.update { cur ->
                if (cur.query != q) cur else cur.copy(hits = hits, loading = false)
            }
        }
    }

    companion object {
        fun Factory(c: AppContainer) = vmFactory { SearchViewModel(c) }
    }
}

/** 股票详情容器：4 个 Tab 的数据都挂在这里，切 Tab 不重复取数。 */
class StockDetailViewModel(private val container: AppContainer) : ViewModel() {
    data class State(
        val code: String = "",
        val detail: StockDetail? = null,
        val year: YearAnalysis? = null,
        val yearValue: Int = LocalDate.now().year,
        val month: MonthAnalysis? = null,
        val monthYear: Int = LocalDate.now().year,
        val monthValue: Int = LocalDate.now().monthValue,
        val daily: MonthAnalysis? = null,
        val dailyYear: Int = LocalDate.now().year,
        val dailyMonth: Int = LocalDate.now().monthValue,
        val loading: Boolean = true,
        val notFound: Boolean = false,
    )

    private val _state = MutableStateFlow(State())
    val state: StateFlow<State> = _state.asStateFlow()

    private var yearRange: Pair<Int, Int> = 1991 to 2035

    fun load(code: String, year: Int, monthYm: Pair<Int, Int>, dailyYm: Pair<Int, Int>) {
        viewModelScope.launch(Dispatchers.Default) {
            yearRange = container.calendarRepository.yearBounds()
            val detail = container.stockRepository.detail(code)
            if (detail == null) {
                _state.update { it.copy(loading = false, notFound = true, code = code) }
                return@launch
            }
            val y = year.coerceIn(yearRange.first, yearRange.second)
            _state.update {
                it.copy(
                    code = detail.stock.code, detail = detail, loading = false, notFound = false,
                    yearValue = y, monthYear = monthYm.first, monthValue = monthYm.second,
                    dailyYear = dailyYm.first, dailyMonth = dailyYm.second,
                )
            }
            loadYear(y)
            loadMonth(monthYm.first, monthYm.second)
            loadDaily(dailyYm.first, dailyYm.second)
        }
    }

    /**
     * 年 / 月 / 每日三个维度的取数都把目标值作为参数传入，写回时校验目标仍是当前值：
     * 快速连点 ‹ › 时后发先至的旧结果会被丢弃，避免页面年份回退。
     */
    private fun loadYear(y: Int) {
        viewModelScope.launch(Dispatchers.Default) {
            val id = _state.value.detail?.stock?.id ?: return@launch
            val data = container.analysisRepository.yearAnalysis(id, y)
            _state.update { cur -> if (cur.yearValue == y) cur.copy(year = data) else cur }
        }
    }

    fun changeYear(delta: Int) {
        val target = (_state.value.yearValue + delta).coerceIn(yearRange.first, yearRange.second)
        _state.update { it.copy(yearValue = target) }
        loadYear(target)
    }

    private fun loadMonth(year: Int, month: Int) {
        viewModelScope.launch(Dispatchers.Default) {
            val id = _state.value.detail?.stock?.id ?: return@launch
            val data = container.analysisRepository.monthDays(id, year, month)
            _state.update { cur ->
                if (cur.monthYear == year && cur.monthValue == month) cur.copy(month = data) else cur
            }
        }
    }

    fun changeMonth(delta: Int) {
        val s = _state.value
        val d = LocalDate.of(s.monthYear, s.monthValue, 1).plusMonths(delta.toLong())
        _state.update { it.copy(monthYear = d.year, monthValue = d.monthValue) }
        loadMonth(d.year, d.monthValue)
    }

    private fun loadDaily(year: Int, month: Int) {
        viewModelScope.launch(Dispatchers.Default) {
            val id = _state.value.detail?.stock?.id ?: return@launch
            val data = container.analysisRepository.monthDays(id, year, month)
            _state.update { cur ->
                if (cur.dailyYear == year && cur.dailyMonth == month) cur.copy(daily = data) else cur
            }
        }
    }

    fun changeDailyMonth(delta: Int) {
        val s = _state.value
        val d = LocalDate.of(s.dailyYear, s.dailyMonth, 1).plusMonths(delta.toLong())
        _state.update { it.copy(dailyYear = d.year, dailyMonth = d.monthValue) }
        loadDaily(d.year, d.monthValue)
    }

    fun toggleFavorite() {
        val id = _state.value.detail?.stock?.id ?: return
        viewModelScope.launch(Dispatchers.Default) {
            val nowFav = container.stockRepository.toggleFavorite(id)
            _state.update { it.copy(detail = it.detail?.copy(isFavorite = nowFav)) }
        }
    }

    companion object {
        fun Factory(c: AppContainer) = vmFactory { StockDetailViewModel(c) }
    }
}

/** 每日扫描页。 */
class ScannerViewModel(private val container: AppContainer) : ViewModel() {
    data class State(
        val date: String = "",
        val summary: ScanSummary? = null,
        val tab: Int = 0,
        val loading: Boolean = true,
        val notice: String? = null,
    ) {
        val rows: List<ScanRow>
            get() = when (tab) {
                1 -> summary?.rows?.filter { it.wealth == WealthType.ZHENG_CAI } ?: emptyList()
                2 -> summary?.rows?.filter { it.wealth == WealthType.PIAN_CAI } ?: emptyList()
                else -> summary?.rows ?: emptyList()
            }
    }

    private val _state = MutableStateFlow(State())
    val state: StateFlow<State> = _state.asStateFlow()

    fun init(date: String?) {
        if (_state.value.date.isNotEmpty() && date == null) return
        viewModelScope.launch(Dispatchers.Default) {
            val d = date?.takeIf { it.isNotBlank() }
                ?: container.calendarRepository.nearestTradeDayOnOrBefore(todayIso())
            if (container.calendarRepository.ganzhi(d) == null) {
                _state.update { it.copy(notice = outOfRangeNotice(container)) }
                return@launch
            }
            _state.update { it.copy(date = d, loading = true, notice = null) }
            val s = container.analysisRepository.scan(d)
            _state.update { cur -> if (cur.date != d) cur else cur.copy(summary = s, loading = false) }
        }
    }

    fun setTab(i: Int) { _state.update { it.copy(tab = i) } }

    fun shiftDate(days: Long) {
        val cur = GanzhiCalculator.parse(_state.value.date) ?: return
        init(GanzhiCalculator.iso(cur.plusDays(days)))
    }

    fun setDate(iso: String) = init(iso)

    /** 手工选到周末 / 节假日时结果为空，给一个回到最近交易日的出口（与筛选页一致）。 */
    fun useNearestTradeDay() {
        viewModelScope.launch(Dispatchers.Default) {
            init(container.calendarRepository.nearestTradeDayOnOrBefore(todayIso()))
        }
    }

    companion object {
        fun Factory(c: AppContainer) = vmFactory { ScannerViewModel(c) }
    }
}

/** 日历边界提示：‹ › 越过预置日历范围时给用户明确反馈，而不是点了没反应。 */
internal suspend fun outOfRangeNotice(container: AppContainer): String {
    val (lo, hi) = container.calendarRepository.bounds()
    return "已超出预置日历范围（$lo ~ $hi）"
}

/** 十神筛选页：组内 OR、组间 AND，勾选状态持久化。 */
class FilterViewModel(private val container: AppContainer) : ViewModel() {
    data class State(
        val hidden: Set<TenGod> = emptySet(),
        val year: Set<TenGod> = emptySet(),
        val month: Set<TenGod> = emptySet(),
        val day: Set<TenGod> = emptySet(),
        val date: String = "",
        val yearGanzhi: String = "",
        val monthGanzhi: String = "",
        val dayGanzhi: String = "",
        val collapsed: Set<String> = emptySet(),
        val result: List<ScanRow>? = null,
        val searching: Boolean = false,
        val restored: Boolean = false,
        val notice: String? = null,
    )

    private val _state = MutableStateFlow(State())
    val state: StateFlow<State> = _state.asStateFlow()

    /** 请求序号：丢弃后发先至的旧结果，避免快速连点时旧筛选覆盖新筛选。 */
    private val filterReq = java.util.concurrent.atomic.AtomicInteger(0)

    fun start() {
        viewModelScope.launch(Dispatchers.Default) {
            val d = container.calendarRepository.nearestTradeDayOnOrBefore(todayIso())
            val sel = container.settingsRepository.restoreFilter()
            val gz = container.calendarRepository.ganzhi(d)
            _state.update {
                it.copy(
                    date = d,
                    yearGanzhi = gz?.yearGanzhi ?: "", monthGanzhi = gz?.monthGanzhi ?: "",
                    dayGanzhi = gz?.dayGanzhi ?: "",
                    hidden = sel?.hidden ?: it.hidden,
                    year = sel?.year ?: it.year,
                    month = sel?.month ?: it.month,
                    day = sel?.day ?: it.day,
                    restored = sel != null,
                )
            }
        }
    }

    /** 流年 / 流月 / 流日三个维度都相对基准日成立，因此必须能换日期 */
    fun setDate(iso: String) {
        val hadResult = _state.value.result != null
        viewModelScope.launch(Dispatchers.Default) {
            val gz = container.calendarRepository.ganzhi(iso)
            if (gz == null) {
                _state.update { it.copy(notice = outOfRangeNotice(container)) }
                return@launch
            }
            _state.update {
                it.copy(date = iso, yearGanzhi = gz.yearGanzhi, monthGanzhi = gz.monthGanzhi, dayGanzhi = gz.dayGanzhi, notice = null)
            }
            if (hadResult) runFilter()
        }
    }

    fun shiftDate(days: Long) {
        val cur = GanzhiCalculator.parse(_state.value.date) ?: return
        setDate(GanzhiCalculator.iso(cur.plusDays(days)))
    }

    fun useNearestTradeDay() {
        viewModelScope.launch(Dispatchers.Default) {
            setDate(container.calendarRepository.nearestTradeDayOnOrBefore(todayIso()))
        }
    }

    fun toggle(group: String, god: TenGod) {
        _state.update { s ->
            val next = if (god in s.groupOf(group)) s.groupOf(group) - god else s.groupOf(group) + god
            s.withGroup(group, next)
        }
    }

    private fun State.groupOf(group: String): Set<TenGod> = when (group) {
        "hidden" -> hidden
        "year" -> year
        "month" -> month
        else -> day
    }

    private fun State.withGroup(group: String, gods: Set<TenGod>): State = when (group) {
        "hidden" -> copy(hidden = gods)
        "year" -> copy(year = gods)
        "month" -> copy(month = gods)
        else -> copy(day = gods)
    }

    fun toggleCollapse(group: String) {
        _state.update { s -> s.copy(collapsed = if (group in s.collapsed) s.collapsed - group else s.collapsed + group) }
    }

    fun reset() {
        _state.update { it.copy(hidden = emptySet(), year = emptySet(), month = emptySet(), day = emptySet(), result = null) }
        viewModelScope.launch(Dispatchers.Default) { container.settingsRepository.clearFilter() }
    }

    fun runFilter() {
        val s = _state.value
        val req = filterReq.incrementAndGet()
        viewModelScope.launch(Dispatchers.Default) {
            _state.update { it.copy(searching = true) }
            val rows = container.analysisRepository.filter(FilterQuery(s.hidden, s.year, s.month, s.day, s.date))
            container.settingsRepository.saveFilter(s.hidden, s.year, s.month, s.day)
            _state.update { cur -> if (filterReq.get() != req) cur else cur.copy(result = rows, searching = false) }
        }
    }

    companion object {
        fun Factory(c: AppContainer) = vmFactory { FilterViewModel(c) }
    }
}

/** 八字择日页。 */
class DateSelectViewModel(private val container: AppContainer) : ViewModel() {
    data class State(
        val code: String = "",
        val start: String = LocalDate.now().withDayOfMonth(1).toString(),
        val end: String = LocalDate.now().toString(),
        val onlyTradeDays: Boolean = false,
        val rows: List<DateSelectionRow>? = null,
        val resolvedName: String? = null,
        val error: String? = null,
        val running: Boolean = false,
    )

    private val _state = MutableStateFlow(State())
    val state: StateFlow<State> = _state.asStateFlow()

    fun prefill(code: String) {
        if (code.isNotBlank()) _state.update { it.copy(code = code) }
    }

    fun setCode(v: String) { _state.update { it.copy(code = v, error = null) } }
    fun setStart(v: String) { _state.update { it.copy(start = v, error = null) } }
    fun setEnd(v: String) { _state.update { it.copy(end = v, error = null) } }
    fun setOnlyTradeDays(v: Boolean) { _state.update { it.copy(onlyTradeDays = v) } }

    private val runReq = java.util.concurrent.atomic.AtomicInteger(0)

    fun run() {
        val s = _state.value
        val req = runReq.incrementAndGet()
        viewModelScope.launch(Dispatchers.Default) {
            _state.update { it.copy(running = true, error = null) }
            val detail = container.stockRepository.detail(s.code)
            if (detail == null) {
                _state.update { cur -> if (runReq.get() != req) cur else cur.copy(running = false, error = "未找到该股票，请检查股票代码") }
                return@launch
            }
            if (s.start > s.end) {
                _state.update { cur -> if (runReq.get() != req) cur else cur.copy(running = false, error = "开始日期不能晚于结束日期") }
                return@launch
            }
            val rows = container.analysisRepository.dateSelect(detail.stock.id, s.start, s.end, s.onlyTradeDays)
            _state.update { cur ->
                if (runReq.get() != req) cur else cur.copy(running = false, rows = rows, resolvedName = detail.stock.name)
            }
        }
    }

    companion object {
        fun Factory(c: AppContainer) = vmFactory { DateSelectViewModel(c) }
    }
}

/** 交易日历页（全局，无股票维度）。 */
class CalendarViewModel(private val container: AppContainer) : ViewModel() {
    data class DayCell(
        val date: String,
        val isTradeDay: Boolean,
        val weekday: Int,
        val reason: String?,
        val ganzhi: String?,
        val confidence: String,
    )

    data class State(
        val year: Int = LocalDate.now().year,
        val month: Int = LocalDate.now().monthValue,
        val cells: List<DayCell> = emptyList(),
        val leadingBlanks: Int = 0,
        val tradeDayCount: Int = 0,
        val selected: DayCell? = null,
        val loading: Boolean = true,
        val notice: String? = null,
    )

    private val _state = MutableStateFlow(State())
    val state: StateFlow<State> = _state.asStateFlow()

    /**
     * 目标年月作为参数传入，写回时校验目标仍是当前值：快速连点 ‹ › 时后发先至的旧结果会被丢弃。
     * 因此调用方必须先经 [jumpTo] 把年月写回状态，否则守卫会把当月以外的结果全部丢掉。
     */
    fun refresh(year: Int = _state.value.year, month: Int = _state.value.month, selectDate: String? = null) {
        viewModelScope.launch(Dispatchers.Default) {
            val (first, last) = GanzhiCalculator.monthRange(year, month)
            val days = container.calendarRepository.monthDays(first.toString(), last.toString())
            val cells = days.map { DayCell(it.date, it.isTradeDay, it.weekday, it.reason, it.dayGanzhi, it.confidence) }
            _state.update { cur ->
                if (cur.year != year || cur.month != month) cur else cur.copy(
                    cells = cells, leadingBlanks = first.dayOfWeek.value - 1,
                    tradeDayCount = cells.count { c -> c.isTradeDay }, loading = false,
                    selected = cells.firstOrNull { c -> c.date == selectDate }
                        ?: cells.firstOrNull { c -> c.date == todayIso() }
                        ?: cells.lastOrNull { c -> c.isTradeDay },
                )
            }
        }
    }

    /** 切到指定年月：按预置日历边界夹取，越界时给出提示而不是让按钮失去响应。 */
    fun jumpTo(year: Int, month: Int, selectDate: String? = null) {
        viewModelScope.launch(Dispatchers.Default) {
            val (loStr, hiStr) = container.calendarRepository.bounds()
            val lo = YearMonth.from(GanzhiCalculator.parse(loStr) ?: LocalDate.of(1990, 12, 1))
            val hi = YearMonth.from(GanzhiCalculator.parse(hiStr) ?: LocalDate.now())
            val asked = YearMonth.of(year, month)
            val target = when {
                asked.isBefore(lo) -> lo
                asked.isAfter(hi) -> hi
                else -> asked
            }
            _state.update {
                it.copy(
                    year = target.year, month = target.monthValue,
                    cells = emptyList(), selected = null, tradeDayCount = 0, loading = true,
                    notice = if (target != asked) "已到预置日历边界（$loStr ~ $hiStr）" else null,
                )
            }
            refresh(target.year, target.monthValue, selectDate)
        }
    }

    fun shift(delta: Int) {
        val s = _state.value
        val target = YearMonth.of(s.year, s.month).plusMonths(delta.toLong())
        jumpTo(target.year, target.monthValue)
    }

    fun backToThisMonth() {
        val now = LocalDate.now()
        jumpTo(now.year, now.monthValue, todayIso())
    }

    fun select(cell: DayCell) { _state.update { it.copy(selected = cell) } }

    companion object {
        fun Factory(c: AppContainer) = vmFactory { CalendarViewModel(c) }
    }
}

/** 单日详情。 */
class DayDetailViewModel(private val container: AppContainer) : ViewModel() {
    data class State(
        val stock: StockInfo? = null,
        val detail: DayDetail? = null,
        val loading: Boolean = true,
    )

    private val _state = MutableStateFlow(State())
    val state: StateFlow<State> = _state.asStateFlow()

    fun load(code: String, date: String) {
        viewModelScope.launch(Dispatchers.Default) {
            val info = container.stockRepository.detail(code)
            val detail = info?.let { container.analysisRepository.dayDetail(it.stock.id, date) }
            _state.update {
                it.copy(stock = info?.stock, detail = detail, loading = false)
            }
        }
    }

    companion object {
        fun Factory(c: AppContainer) = vmFactory { DayDetailViewModel(c) }
    }
}

/** 我的 / 设置。 */
class ProfileViewModel(private val container: AppContainer) : ViewModel() {
    data class State(
        val meta: Map<String, String> = emptyMap(),
        val stockCount: Int = 0,
        val cacheCount: Int = 0,
        val favorites: List<StockInfo> = emptyList(),
    )

    private val _state = MutableStateFlow(State())
    val state: StateFlow<State> = _state.asStateFlow()

    fun refresh() {
        viewModelScope.launch(Dispatchers.Default) {
            val meta = container.stockRepository.meta()
            val stockCount = container.stockRepository.stockCount()
            val cacheCount = container.analysisRepository.cacheSize()
            val favorites = container.stockRepository.favoriteStocks()
            _state.update { State(meta = meta, stockCount = stockCount, cacheCount = cacheCount, favorites = favorites) }
        }
    }

    fun clearCache() {
        viewModelScope.launch(Dispatchers.Default) {
            container.analysisRepository.clearCache()
            _state.update { it.copy(cacheCount = 0) }
        }
    }

    companion object {
        fun Factory(c: AppContainer) = vmFactory { ProfileViewModel(c) }
    }
}
