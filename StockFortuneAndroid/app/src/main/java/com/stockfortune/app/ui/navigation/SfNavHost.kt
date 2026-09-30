package com.stockfortune.app.ui.navigation

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.stockfortune.app.ui.calendar.CalendarScreen
import com.stockfortune.app.ui.dateselect.DateSelectScreen
import com.stockfortune.app.ui.detail.DayDetailScreen
import com.stockfortune.app.ui.detail.StockDetailScreen
import com.stockfortune.app.ui.filter.FilterScreen
import com.stockfortune.app.ui.home.HomeScreen
import com.stockfortune.app.ui.profile.AlgorithmDocScreen
import com.stockfortune.app.ui.profile.ProfileScreen
import com.stockfortune.app.ui.scanner.ScannerScreen
import com.stockfortune.app.ui.search.SearchScreen
import com.stockfortune.app.ui.theme.SfColors

private val TAB_PREFIXES = listOf(Routes.HOME, Routes.FILTER, Routes.SCANNER, Routes.CALENDAR, Routes.PROFILE)

@Composable
fun SfApp() {
    val nav = rememberNavController()
    val backStack by nav.currentBackStackEntryAsState()
    val current = backStack?.destination?.route.orEmpty()
    val showBar = current.isEmpty() || TAB_PREFIXES.any { current.startsWith(it) }

    Scaffold(
        containerColor = SfColors.PageBg,
        bottomBar = { if (showBar) SfBottomBar(current, nav) },
    ) { padding ->
        NavHost(
            navController = nav,
            startDestination = Routes.HOME,
            modifier = Modifier.padding(padding),
        ) {
            composable(Routes.HOME) { HomeScreen(nav) }
            composable(
                Routes.SEARCH_ROUTE,
                arguments = listOf(navArgument("query") { type = NavType.StringType; defaultValue = "" }),
            ) { entry ->
                SearchScreen(nav, entry.arguments?.getString("query").orEmpty())
            }
            composable(Routes.FILTER) { FilterScreen(nav) }
            composable(
                Routes.SCANNER_ROUTE,
                arguments = listOf(navArgument("wealth") { type = NavType.StringType; defaultValue = "" }),
            ) { entry ->
                ScannerScreen(nav, entry.arguments?.getString("wealth").orEmpty())
            }
            composable(Routes.CALENDAR) { CalendarScreen(nav) }
            composable(Routes.PROFILE) { ProfileScreen(nav) }
            composable(Routes.ALGORITHM) { AlgorithmDocScreen(onBack = { nav.popBackStack() }) }
            composable(
                Routes.STOCK_ROUTE,
                arguments = listOf(
                    navArgument("code") { type = NavType.StringType },
                    navArgument("tab") { type = NavType.StringType; defaultValue = "basic" },
                ),
            ) { entry ->
                StockDetailScreen(
                    nav,
                    code = entry.arguments?.getString("code").orEmpty(),
                    initialTab = entry.arguments?.getString("tab") ?: "basic",
                )
            }
            composable(
                Routes.DATE_SELECT_ROUTE,
                arguments = listOf(navArgument("code") { type = NavType.StringType; defaultValue = "" }),
            ) { entry ->
                DateSelectScreen(nav, initialCode = entry.arguments?.getString("code").orEmpty())
            }
            composable(
                Routes.DAY_DETAIL_ROUTE,
                arguments = listOf(
                    navArgument("code") { type = NavType.StringType; defaultValue = "" },
                    navArgument("date") { type = NavType.StringType; defaultValue = "" },
                ),
            ) { entry ->
                DayDetailScreen(
                    nav,
                    code = entry.arguments?.getString("code").orEmpty(),
                    date = entry.arguments?.getString("date").orEmpty(),
                )
            }
        }
    }
}

@Composable
private fun SfBottomBar(current: String, nav: androidx.navigation.NavHostController) {
    // targetSdk 35 强制 edge-to-edge：不主动吃系统导航条 inset 的话，
    // 页签文字会被手势条压住，点在文字上会落到系统层，表现为"点首页没反应"。
    Column(Modifier.windowInsetsPadding(WindowInsets.navigationBars)) {
        Box(Modifier.fillMaxWidth().height(0.5.dp).background(SfColors.Divider))
        Row(Modifier.fillMaxWidth().background(Color.White).padding(horizontal = 4.dp, vertical = 7.dp)) {
            BOTTOM_ITEMS.forEach { item ->
                val active = current.startsWith(item.match)
                Box(
                    Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(14.dp))
                        .clickable {
                            if (!active) {
                                // 起点目的地用 popUpTo+restoreState 会被 NavigationUI 忽略（已知行为），
                                // 因此"首页"走 popBackStack，其余页保留标准 bottom-nav 配方。
                                if (item.route == Routes.HOME) {
                                    if (!nav.popBackStack(Routes.HOME, inclusive = false)) {
                                        nav.navigate(Routes.HOME)
                                    }
                                } else {
                                    nav.navigate(item.route) {
                                        popUpTo(Routes.HOME) { saveState = true }
                                        launchSingleTop = true
                                        restoreState = true
                                    }
                                }
                            }
                        }
                        .padding(vertical = 6.dp, horizontal = 2.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            item.icon, contentDescription = stringResource(item.labelRes),
                            tint = if (active) SfColors.Gold else SfColors.TextSub,
                            modifier = Modifier.size(21.dp),
                        )
                        Spacer(Modifier.height(2.dp))
                        Text(
                            stringResource(item.labelRes),
                            color = if (active) SfColors.Gold else SfColors.TextSub,
                            fontSize = 11.sp,
                            fontWeight = if (active) FontWeight.Bold else FontWeight.Normal,
                        )
                        Spacer(Modifier.height(3.dp))
                        Box(
                            Modifier
                                .width(16.dp)
                                .height(2.5.dp)
                                .clip(RoundedCornerShape(1.5.dp))
                                .background(if (active) SfColors.Gold else Color.Transparent),
                        )
                    }
                }
            }
        }
    }
}
