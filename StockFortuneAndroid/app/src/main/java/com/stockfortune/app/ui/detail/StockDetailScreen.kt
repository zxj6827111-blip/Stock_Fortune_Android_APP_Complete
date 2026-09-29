package com.stockfortune.app.ui.detail

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import com.stockfortune.app.R
import com.stockfortune.app.ui.common.sfContainer
import com.stockfortune.app.ui.common.sfViewModel
import com.stockfortune.app.ui.components.SfEmptyState
import com.stockfortune.app.ui.components.SfHero
import com.stockfortune.app.ui.components.SfLoading
import com.stockfortune.app.ui.navigation.Routes
import com.stockfortune.app.ui.theme.SfColors
import com.stockfortune.app.ui.theme.SfDimens
import com.stockfortune.app.ui.vm.StockDetailViewModel
import java.time.LocalDate

/**
 * 股票详情容器页：紧凑 Hero + 4 个 Tab（基本信息 / 年度运势 / 月度运势 / 每日分析）。
 * 数据全部挂在 [StockDetailViewModel]，切 Tab 不重复取数；翻页只影响对应维度。
 */
@Composable
fun StockDetailScreen(nav: NavHostController, code: String, initialTab: String) {
    val vm: StockDetailViewModel = sfViewModel { StockDetailViewModel.Factory(sfContainer()) }
    val state by vm.state.collectAsStateWithLifecycle()
    var tab by rememberSaveable { mutableIntStateOf(tabIndex(initialTab)) }

    // 进入页面时以"当前日期"初始化一次年 / 月 / 每日锚点
    val initial = remember(code) {
        val today = LocalDate.now()
        Triple(today.year, today.year to today.monthValue, today.year to today.monthValue)
    }
    LaunchedEffect(code) {
        vm.load(code, initial.first, initial.second, initial.third)
    }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .background(SfColors.PageBg),
    ) {
        val detail = state.detail
        SfHero(
            title = detail?.let { "${it.stock.name} (${it.stock.symbol})" } ?: stringResource(R.string.app_name),
            subtitle = detail?.let { "上市日 ${it.stock.listingDate}" } ?: "",
            height = 150.dp,
            showLogo = false,
            leading = {
                Box(
                    Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .clickable { nav.popBackStack() },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = stringResource(R.string.back),
                        tint = Color.White,
                        modifier = Modifier.size(22.dp),
                    )
                }
            },
            trailing = {
                Box(
                    Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .clickable { vm.toggleFavorite() },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        if (detail?.isFavorite == true) Icons.Default.Star else Icons.Default.StarBorder,
                        contentDescription = stringResource(R.string.favorites),
                        tint = SfColors.GoldLight,
                        modifier = Modifier.size(24.dp),
                    )
                }
            },
        )

        SfDetailTabs(tab) { tab = it }

        val contentModifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = SfDimens.PagePadding, vertical = SfDimens.CardGap)
        when {
            state.loading -> SfLoading(contentModifier)
            state.notFound -> SfEmptyState(
                title = "未找到该股票",
                hint = "请返回后重试",
                modifier = contentModifier,
            )
            else -> when (tab) {
                1 -> YearTab(
                    year = state.year,
                    yearValue = state.yearValue,
                    onPrev = { vm.changeYear(-1) },
                    onNext = { vm.changeYear(1) },
                    onMonthClick = { label ->
                        tab = 2
                        // 干支月序：1=寅月(约 2 月) … 11=子月(12 月)、12=丑月(次年 1 月)
                        val targetYear = if (label.month == 12) state.yearValue + 1 else state.yearValue
                        val targetMonth = if (label.month == 12) 1 else label.month + 1
                        val delta = (targetYear - state.monthYear) * 12 + (targetMonth - state.monthValue)
                        if (delta != 0) vm.changeMonth(delta)
                    },
                )
                2 -> MonthTab(
                    m = state.month,
                    onPrev = { vm.changeMonth(-1) },
                    onNext = { vm.changeMonth(1) },
                    onDayClick = { day -> nav.navigate(Routes.dayDetail(code, day.date)) },
                )
                3 -> DailyTab(
                    m = state.daily,
                    onPrev = { vm.changeDailyMonth(-1) },
                    onNext = { vm.changeDailyMonth(1) },
                    onDayClick = { day -> nav.navigate(Routes.dayDetail(code, day.date)) },
                )
                else -> detail?.let { BasicTab(it) }
            }
        }
    }
}

private fun tabIndex(initialTab: String): Int = when (initialTab) {
    "year" -> 1
    "month" -> 2
    "daily" -> 3
    else -> 0
}

/** 自定义 TabRow：文字 + 下划线指示条（选中深蓝、未选中次级灰）。 */
@Composable
private fun SfDetailTabs(selected: Int, onSelect: (Int) -> Unit) {
    val labels = listOf(
        stringResource(R.string.tab_basic),
        stringResource(R.string.tab_year),
        stringResource(R.string.tab_month),
        stringResource(R.string.tab_daily),
    )
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp))
            .background(SfColors.CardBg),
    ) {
        Row(Modifier.fillMaxWidth()) {
            labels.forEachIndexed { i, label ->
                val active = i == selected
                Column(
                    Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp))
                        .clickable { onSelect(i) }
                        .padding(top = 12.dp, bottom = 8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        label,
                        fontSize = 14.sp,
                        color = if (active) SfColors.DeepBlue else SfColors.TextSub,
                        fontWeight = if (active) FontWeight.SemiBold else FontWeight.Medium,
                    )
                    Spacer(Modifier.height(8.dp))
                    Box(
                        Modifier
                            .width(28.dp)
                            .height(3.dp)
                            .clip(RoundedCornerShape(2.dp))
                            .background(if (active) SfColors.DeepBlue else Color.Transparent),
                    )
                }
            }
        }
        Box(Modifier.fillMaxWidth().height(0.5.dp).background(SfColors.Divider))
    }
}
