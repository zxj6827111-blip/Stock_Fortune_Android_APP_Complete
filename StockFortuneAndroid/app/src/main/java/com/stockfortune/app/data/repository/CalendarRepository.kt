package com.stockfortune.app.data.repository

import com.stockfortune.app.data.dao.CalendarDao
import com.stockfortune.app.data.entity.GanzhiCalendarEntity
import com.stockfortune.app.data.entity.TradeCalendarEntity
import java.time.LocalDate

class CalendarRepository(private val dao: CalendarDao) {

    suspend fun ganzhi(date: String): GanzhiCalendarEntity? = dao.ganzhi(date)

    suspend fun ganzhiRange(start: String, end: String): List<GanzhiCalendarEntity> = dao.ganzhiRange(start, end)

    suspend fun tradeDay(date: String): TradeCalendarEntity? = dao.tradeDay(date)

    suspend fun isTradeDay(date: String): Boolean = (dao.tradeDay(date)?.isTradeDay ?: 0) == 1

    suspend fun tradeRange(start: String, end: String): List<TradeCalendarEntity> = dao.tradeRange(start, end)

    /** 目标日或之前最近的交易日；日历缺失时退化为最近的工作日。 */
    suspend fun nearestTradeDayOnOrBefore(date: String): String {
        dao.lastTradeDayOnOrBefore(date)?.let { return it }
        var d = GanzhiCalculatorProxy.parse(date) ?: return date
        var guard = 0
        while (guard++ < 10) {
            if (d.dayOfWeek.value <= 5) return d.toString()
            d = d.minusDays(1)
        }
        return date
    }

    suspend fun bounds(): Pair<String, String> =
        (dao.minDate() ?: "1990-12-01") to (dao.maxDate() ?: LocalDate.now().toString())

    suspend fun yearBounds(): Pair<Int, Int> =
        (dao.minYear()?.toIntOrNull() ?: 1991) to (dao.maxYear()?.toIntOrNull() ?: 2035)

    /** 月历单元格数据：干支 + 是否交易日 + 休市原因 */
    data class MonthDay(
        val date: String,
        val isTradeDay: Boolean,
        val weekday: Int,
        val reason: String?,
        val dayGanzhi: String?,
        val solarTerm: String?,
        val confidence: String,
    )

    suspend fun monthDays(start: String, end: String): List<MonthDay> {
        val gz = dao.ganzhiRange(start, end).associateBy { it.date }
        return dao.tradeRange(start, end).map { t ->
            MonthDay(
                date = t.date, isTradeDay = t.isTradeDay == 1, weekday = t.weekday, reason = t.closedReason,
                dayGanzhi = gz[t.date]?.dayGanzhi, solarTerm = gz[t.date]?.solarTerm, confidence = t.confidence,
            )
        }
    }
}

/** 避免 repository 直接依赖 domain 计算器，做一层薄代理。 */
internal object GanzhiCalculatorProxy {
    fun parse(iso: String): LocalDate? = runCatching { LocalDate.parse(iso) }.getOrNull()
}
