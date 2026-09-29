package com.stockfortune.app.domain.calculator

import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

/** 日柱与日期工具：日柱为 60 甲子连续推进，锚点 1949-10-01 = 甲子日。 */
object GanzhiCalculator {
    private val ANCHOR: LocalDate = LocalDate.of(1949, 10, 1)
    val ISO: DateTimeFormatter = DateTimeFormatter.ISO_LOCAL_DATE

    fun parse(iso: String): LocalDate? = runCatching { LocalDate.parse(iso, ISO) }.getOrNull()

    fun iso(date: LocalDate): String = date.format(ISO)

    fun dayGanzhi(date: LocalDate): String {
        val diff = ChronoUnit.DAYS.between(ANCHOR, date).toInt()
        return TenGodCalculator.ganzhiOfIndex(diff)
    }

    fun dayGanzhiIndex(date: LocalDate): Int {
        val diff = ChronoUnit.DAYS.between(ANCHOR, date).toInt()
        return ((diff % 60) + 60) % 60
    }

    fun weekdayCn(iso: String): String {
        val d = parse(iso) ?: return ""
        return listOf("周一", "周二", "周三", "周四", "周五", "周六", "周日")[d.dayOfWeek.value - 1]
    }

    fun weekdayShort(iso: String): String {
        val d = parse(iso) ?: return ""
        return listOf("一", "二", "三", "四", "五", "六", "日")[d.dayOfWeek.value - 1]
    }

    fun monthRange(year: Int, month: Int): Pair<LocalDate, LocalDate> {
        val first = LocalDate.of(year, month, 1)
        return first to first.withDayOfMonth(first.lengthOfMonth())
    }

    fun yearRange(year: Int): Pair<LocalDate, LocalDate> =
        LocalDate.of(year, 1, 1) to LocalDate.of(year, 12, 31)

    fun clampDate(date: LocalDate, minIso: String, maxIso: String): LocalDate {
        val min = parse(minIso) ?: date
        val max = parse(maxIso) ?: date
        return when {
            date.isBefore(min) -> min
            date.isAfter(max) -> max
            else -> date
        }
    }
}
