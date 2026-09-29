package com.stockfortune.app.ui.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.QueryStats
import androidx.compose.material.icons.filled.Scanner
import androidx.compose.ui.graphics.vector.ImageVector
import com.stockfortune.app.R

object Routes {
    const val HOME = "home"
    const val FILTER = "filter"
    const val SCANNER = "scanner"
    const val CALENDAR = "calendar"
    const val PROFILE = "profile"
    const val SEARCH = "search"
    const val STOCK = "stock"
    const val DATE_SELECT = "dateSelect"
    const val DAY_DETAIL = "dayDetail"
    const val ALGORITHM = "algorithmDoc"

    /** 带可选参数的目的地模板 */
    const val SEARCH_ROUTE = "search?query={query}"
    const val SCANNER_ROUTE = "scanner?wealth={wealth}"
    const val STOCK_ROUTE = "stock/{code}?tab={tab}"
    const val DATE_SELECT_ROUTE = "dateSelect?code={code}"
    const val DAY_DETAIL_ROUTE = "dayDetail?code={code}&date={date}"

    fun search(query: String) = "search?query=${enc(query)}"
    fun stock(code: String, tab: String = "basic") = "stock/${enc(code)}?tab=$tab"
    fun scanner(wealth: String = "") = "scanner?wealth=${enc(wealth)}"
    fun dateSelect(code: String = "") = "dateSelect?code=${enc(code)}"
    fun dayDetail(code: String, date: String) = "dayDetail?code=${enc(code)}&date=${enc(date)}"

    private fun enc(v: String) = android.net.Uri.encode(v)

}

data class BottomItem(val route: String, val match: String, @androidx.annotation.StringRes val labelRes: Int, val icon: ImageVector)

val BOTTOM_ITEMS: List<BottomItem> = listOf(
    BottomItem(Routes.HOME, Routes.HOME, R.string.nav_home, Icons.Filled.Home),
    BottomItem(Routes.FILTER, Routes.FILTER, R.string.nav_pick, Icons.Filled.QueryStats),
    BottomItem(Routes.scanner(), Routes.SCANNER, R.string.nav_scan, Icons.Filled.Scanner),
    BottomItem(Routes.CALENDAR, Routes.CALENDAR, R.string.nav_calendar, Icons.Filled.CalendarMonth),
    BottomItem(Routes.PROFILE, Routes.PROFILE, R.string.nav_mine, Icons.Filled.Person),
)
