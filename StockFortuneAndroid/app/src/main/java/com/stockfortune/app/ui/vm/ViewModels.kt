package com.stockfortune.app.ui.vm

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.stockfortune.app.AppContainer
import com.stockfortune.app.data.repository.AnalysisRepository
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
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * 全站「今天」的唯一来源。
 *
 * 可注入有两个原因：预置日历有截止日（当前 2035-12-31），跟随系统钟的断言会在换月、
 * 换数据快照时静默漂红 —— 提交态的 DetailViewModelTest 就是这样红了一次的
 * （`expected:<21> but was:<17>`）。测试固定日期应当走这里，而不是各自绕过默认值。
 */
internal object AppClock {
    @Volatile
    var fixedToday: LocalDate? = null

    fun today(): LocalDate = fixedToday ?: LocalDate.now()
}

private fun todayIso(): String = AppClock.today().toString()

private const val TAG = "SfViewModel"

/**
 * 后台取数外壳：成功、失败、抛错三条路径都必须把忙碌位写回 false。
 *
 * 此前每个 ViewModel 的协程都没有出口，任何一次跳异常（Room 读失败、日历越界、
 * 预置库与代码身份不符）都表现为永久转圈 —— 用户无法区分"在算"和"已经失败"。
 * 取消异常原样上抛，交给结构化并发处理，不当成失败吞掉。
 */
private fun <S> MutableStateFlow<S>.launchLoad(
    scope: CoroutineScope,
    markIdle: (S) -> S,
    block: suspend () -> Unit,
) = scope.launch(Dispatchers.Default) {
    try {
        block()
    } catch (ce: CancellationException) {
        throw ce
    } catch (e: Exception) {
        Log.e(TAG, "后台取数失败，已落回空闲态", e)
    } finally {
        update { markIdle(it) }
    }
}

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
        _state.launchLoad(viewModelScope, { it.copy(loading = false) }) {
            val date = container.calendarRepository.nearestTradeDayOnOrBefore(todayIso())
            val (zheng, pian) = container.analysisRepository.scanCounts(date)
            _state.update { it.copy(date = date, zhengCount = zheng, pianCount = pian) }
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
    private var searchJob: kotlinx.coroutines.Job? = null

    fun search(q: String) {
        _state.update { it.copy(query = q, loading = true) }
        // 每个按键都会发一条"前导通配 LIKE"的全表查询，且结果只会被最新输入覆盖：
        // 先取消上一个，别让它们并行排队打 SQLite（旧实现既不取消也不复位忙碌位）。
        searchJob?.cancel()
        searchJob = _state.launchLoad(viewModelScope, { cur ->
            if (cur.query != q) cur else cur.copy(loading = false)
        }) {
            val hits = container.stockRepository.search(q).map {
                SearchHit(it.stockId, it.code, it.symbol, it.name, it.listingDate, it.board)
            }
            _state.update { cur -> if (cur.query != q) cur else cur.copy(hits = hits) }
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
        val yearValue: Int = AppClock.today().year,
        val month: MonthAnalysis? = null,
        val monthYear: Int = AppClock.today().year,
        val monthValue: Int = AppClock.today().monthValue,
        val daily: MonthAnalysis? = null,
        val dailyYear: Int = AppClock.today().year,
        val dailyMonth: Int = AppClock.today().monthValue,
        val loading: Boolean = true,
        val notFound: Boolean = false,
    )

    private val _state = MutableStateFlow(State())
    val state: StateFlow<State> = _state.asStateFlow()

    private var yearRange: Pair<Int, Int> = 1991 to 2035

    fun load(code: String, year: Int, monthYm: Pair<Int, Int>, dailyYm: Pair<Int, Int>) {
        // 同一只股已经装载过就不再重置年 / 月 / 每日三个锚点：从单日详情返回时本屏组合会被
        // 销毁重建，旧实现在这里把锚点打回"今天"，用户翻到的 2018 年随之丢失，还白跑三遍全量推算
        // —— 与本文件开头"切 Tab 不重复取数"的意图相反。
        val cur = _state.value
        if (cur.code == code && cur.detail != null) return
        _state.launchLoad(viewModelScope, { it.copy(loading = false) }) {
            yearRange = container.calendarRepository.yearBounds()
            val detail = container.stockRepository.detail(code)
            if (detail == null) {
                _state.update {
                    it.copy(
                        code = code,
                        detail = null,
                        year = null,
                        month = null,
                        daily = null,
                        notFound = true,
                        loading = false,
                    )
                }
                return@launchLoad
            }
            val y = year.coerceIn(yearRange.first, yearRange.second)
            _state.update {
                it.copy(
                    code = detail.stock.code,
                    detail = detail,
                    year = null,
                    month = null,
                    daily = null,
                    notFound = false,
                    yearValue = y,
                    monthYear = monthYm.first,
                    monthValue = monthYm.second,
                    dailyYear = dailyYm.first,
                    dailyMonth = dailyYm.second,
                )
            }
            loadYear(y)
            loadMonth(monthYm.first, monthYm.second)
            loadDaily(dailyYm.first, dailyYm.second)
        }
    }

    /**
     * 年 / 月 / 每日三个维度的取数都把目标值作为参数传入，写回时校验目标仍是当前值：
     * 快速连点 ‹ › 时后发先至的旧结果会被丢弃，避免页面年份回退；
     * 同时核对股票ID未改变，杜绝切股票时旧股数据泄漏。
     */
    private fun loadYear(y: Int) {
        val targetStockId = _state.value.detail?.stock?.id ?: return
        _state.launchLoad(viewModelScope, { it }) {
            val data = container.analysisRepository.yearAnalysis(targetStockId, y)
            _state.update { cur ->
                if (cur.yearValue == y && cur.detail?.stock?.id == targetStockId) cur.copy(year = data) else cur
            }
        }
    }

    fun changeYear(delta: Int) {
        val target = (_state.value.yearValue + delta).coerceIn(yearRange.first, yearRange.second)
        _state.update { it.copy(yearValue = target) }
        loadYear(target)
    }

    private fun loadMonth(year: Int, month: Int) {
        val targetStockId = _state.value.detail?.stock?.id ?: return
        _state.launchLoad(viewModelScope, { it }) {
            val data = container.analysisRepository.monthDays(targetStockId, year, month)
            _state.update { cur ->
                if (cur.monthYear == year && cur.monthValue == month && cur.detail?.stock?.id == targetStockId) cur.copy(month = data) else cur
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
        val targetStockId = _state.value.detail?.stock?.id ?: return
        _state.launchLoad(viewModelScope, { it }) {
            val data = container.analysisRepository.monthDays(targetStockId, year, month)
            _state.update { cur ->
                if (cur.dailyYear == year && cur.dailyMonth == month && cur.detail?.stock?.id == targetStockId) cur.copy(daily = data) else cur
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
        _state.launchLoad(viewModelScope, { it }) {
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
        _state.launchLoad(viewModelScope, { it.copy(loading = false) }) {
            val d = date?.takeIf { it.isNotBlank() }
                ?: container.calendarRepository.nearestTradeDayOnOrBefore(todayIso())
            if (container.calendarRepository.ganzhi(d) == null) {
                // 旧实现在这里只写 notice 就返回，loading 仍是 true —— 首启即越界时页面会同时
                // 显示"已超出预置日历范围"和永久转圈，用户没有任何出口。
                _state.update { it.copy(notice = outOfRangeNotice(container)) }
                return@launchLoad
            }
            _state.update { it.copy(date = d, loading = true, notice = null) }
            val s = container.analysisRepository.scan(d)
            _state.update { cur -> if (cur.date != d) cur else cur.copy(summary = s) }
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
        // 幂等：从结果行进入个股详情再返回时本屏组合重建，旧实现无条件把基准日打回最近
        // 交易日、并用已落库的旧勾选覆盖尚未筛选的那一组勾选，用户两处改动都静默丢失。
        if (_state.value.date.isNotEmpty()) return
        _state.launchLoad(viewModelScope, { it }) {
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
        _state.launchLoad(viewModelScope, { it.copy(searching = false) }) {
            val gz = container.calendarRepository.ganzhi(iso)
            if (gz == null) {
                _state.update { it.copy(notice = outOfRangeNotice(container)) }
                return@launchLoad
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
        _state.launchLoad(viewModelScope, { it }) { container.settingsRepository.clearFilter() }
    }

    fun runFilter() {
        val s = _state.value
        val req = filterReq.incrementAndGet()
        _state.launchLoad(viewModelScope, { it.copy(searching = false) }) {
            _state.update { it.copy(searching = true) }
            val rows = container.analysisRepository.filter(FilterQuery(s.hidden, s.year, s.month, s.day, s.date))
            container.settingsRepository.saveFilter(s.hidden, s.year, s.month, s.day)
            _state.update { cur -> if (filterReq.get() != req) cur else cur.copy(result = rows) }
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
        val start: String = AppClock.today().withDayOfMonth(1).toString(),
        val end: String = AppClock.today().toString(),
        val onlyTradeDays: Boolean = false,
        val rows: List<DateSelectionRow>? = null,
        val resolvedName: String? = null,
        val error: String? = null,
        val running: Boolean = false,
        /** 副标题要说"实际算过的区间"，不能说当前表单值 —— 后者会在用户改完还没重跑时展示假信息 */
        val ranStart: String = "",
        val ranEnd: String = "",
        val truncated: Boolean = false,
    )

    private val _state = MutableStateFlow(State())
    val state: StateFlow<State> = _state.asStateFlow()

    fun prefill(code: String) {
        if (code.isNotBlank()) _state.update { it.copy(code = code) }
    }

    // 表单一变，上次结果就不再成立：清掉 rows，避免"标题是新区间、列表是旧结果"
    fun setCode(v: String) { _state.update { it.copy(code = v, error = null, rows = null) } }
    fun setStart(v: String) { _state.update { it.copy(start = v, error = null, rows = null) } }
    fun setEnd(v: String) { _state.update { it.copy(end = v, error = null, rows = null) } }
    fun setOnlyTradeDays(v: Boolean) { _state.update { it.copy(onlyTradeDays = v, error = null, rows = null) } }

    private val runReq = java.util.concurrent.atomic.AtomicInteger(0)

    fun run() {
        val s = _state.value
        val req = runReq.incrementAndGet()
        _state.launchLoad(viewModelScope, { it.copy(running = false) }) {
            _state.update { it.copy(running = true, error = null) }
            // 日期是用户可编辑的文本；字符串比较与 SQL 的 BETWEEN 都按字典序走，
            // 格式不对时不报错、只会给出一个看起来"无吉日"的空结果。
            val malformed = listOf("开始日期" to s.start, "结束日期" to s.end)
                .firstOrNull { GanzhiCalculator.parse(it.second) == null }
            if (malformed != null) {
                fail(req, "${malformed.first}不是合法日期，应为 YYYY-MM-DD")
                return@launchLoad
            }
            if (s.start > s.end) {
                fail(req, "开始日期不能晚于结束日期")
                return@launchLoad
            }
            val detail = container.stockRepository.detail(s.code)
            if (detail == null) {
                fail(req, "未找到该股票，请检查股票代码")
                return@launchLoad
            }
            val rows = container.analysisRepository.dateSelect(
                detail.stock.id, s.start, s.end, s.onlyTradeDays, DATE_LIMIT,
            )
            _state.update { cur ->
                if (runReq.get() != req) cur else cur.copy(
                    rows = rows, resolvedName = detail.stock.name,
                    ranStart = s.start, ranEnd = s.end, truncated = rows.size >= DATE_LIMIT,
                )
            }
        }
    }

    private fun fail(req: Int, message: String) {
        _state.update { cur -> if (runReq.get() != req) cur else cur.copy(error = message) }
    }

    companion object {
        private const val DATE_LIMIT = AnalysisRepository.DATE_SELECT_LIMIT

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
        val year: Int = AppClock.today().year,
        val month: Int = AppClock.today().monthValue,
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
        _state.launchLoad(
            viewModelScope,
            { cur -> if (cur.year != year || cur.month != month) cur else cur.copy(loading = false) },
        ) {
            val (first, last) = GanzhiCalculator.monthRange(year, month)
            val days = container.calendarRepository.monthDays(first.toString(), last.toString())
            val cells = days.map { DayCell(it.date, it.isTradeDay, it.weekday, it.reason, it.dayGanzhi, it.confidence) }
            _state.update { cur ->
                if (cur.year != year || cur.month != month) cur else cur.copy(
                    cells = cells, leadingBlanks = first.dayOfWeek.value - 1,
                    tradeDayCount = cells.count { c -> c.isTradeDay },
                    selected = cells.firstOrNull { c -> c.date == selectDate }
                        ?: cells.firstOrNull { c -> c.date == todayIso() }
                        ?: cells.lastOrNull { c -> c.isTradeDay },
                )
            }
        }
    }

    /** 切到指定年月：按预置日历边界夹取，越界时给出提示而不是让按钮失去响应。 */
    private val jumpReq = java.util.concurrent.atomic.AtomicInteger(0)

    fun jumpTo(year: Int, month: Int, selectDate: String? = null) {
        val req = jumpReq.incrementAndGet()
        _state.launchLoad(viewModelScope, { it.copy(loading = false) }) {
            val (loStr, hiStr) = container.calendarRepository.bounds()
            val lo = YearMonth.from(GanzhiCalculator.parse(loStr) ?: LocalDate.of(1990, 12, 1))
            val hi = YearMonth.from(GanzhiCalculator.parse(hiStr) ?: AppClock.today())
            val asked = YearMonth.of(year, month)
            val target = when {
                asked.isBefore(lo) -> lo
                asked.isAfter(hi) -> hi
                else -> asked
            }
            // 连点 ‹ › 时会有多个 jumpTo 并行等 bounds()：没有请求号的话，后回来的旧请求
            // 会把年月写歪，refresh 的守卫随即把正确月份的数据整批丢掉。
            if (jumpReq.get() != req) return@launchLoad
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
        val now = AppClock.today()
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
        _state.launchLoad(viewModelScope, { it.copy(loading = false) }) {
            val info = container.stockRepository.detail(code)
            val detail = info?.let { container.analysisRepository.dayDetail(it.stock.id, date) }
            _state.update { it.copy(stock = info?.stock, detail = detail) }
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
        _state.launchLoad(viewModelScope, { it }) {
            val meta = container.stockRepository.meta()
            val stockCount = container.stockRepository.stockCount()
            val cacheCount = container.analysisRepository.cacheSize()
            val favorites = container.stockRepository.favoriteStocks()
            _state.update { it.copy(meta = meta, stockCount = stockCount, cacheCount = cacheCount, favorites = favorites) }
        }
    }

    fun clearCache() {
        _state.launchLoad(viewModelScope, { it }) {
            container.analysisRepository.clearCache()
            _state.update { it.copy(cacheCount = 0) }
        }
    }

    companion object {
        fun Factory(c: AppContainer) = vmFactory { ProfileViewModel(c) }
    }
}
