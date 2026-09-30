package com.stockfortune.app.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.Scanner
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.stockfortune.app.R
import com.stockfortune.app.domain.calculator.FortuneText
import com.stockfortune.app.domain.calculator.GanzhiCalculator
import com.stockfortune.app.ui.common.sfContainer
import com.stockfortune.app.ui.common.sfViewModel
import com.stockfortune.app.ui.components.SfCard
import com.stockfortune.app.ui.components.SfDisclaimer
import com.stockfortune.app.ui.components.SfEntryTile
import com.stockfortune.app.ui.components.SfHero
import com.stockfortune.app.ui.components.SfLoading
import com.stockfortune.app.ui.components.SfSectionTitle
import com.stockfortune.app.ui.components.SfStatCard
import com.stockfortune.app.ui.navigation.Routes
import com.stockfortune.app.ui.theme.SfColors
import com.stockfortune.app.ui.theme.SfDimens
import com.stockfortune.app.ui.vm.HomeViewModel

@Composable
fun HomeScreen(nav: NavHostController) {
    val vm: HomeViewModel = sfViewModel { HomeViewModel.Factory(sfContainer()) }
    val st by vm.state.collectAsState()
    var keyword by remember { mutableStateOf("") }

    val onSubmit: (String) -> Unit = { q ->
        if (q.isNotBlank()) nav.navigate(Routes.search(q.trim()))
    }

    Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        SfHero(
            title = stringResource(R.string.app_name),
            subtitle = stringResource(R.string.app_subtitle),
            slogan = stringResource(R.string.app_slogan),
            showLogo = true,
            trailing = {
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .clip(CircleShape)
                        .background(Color(0x24FFFFFF))
                        .border(0.8.dp, Color(0x33FFFFFF), CircleShape)
                        .clickable { nav.navigate(Routes.ALGORITHM) },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Filled.Settings, contentDescription = stringResource(R.string.mine_algorithm), tint = Color.White, modifier = Modifier.size(19.dp))
                }
            },
        )

        Column(
            modifier = Modifier.padding(SfDimens.PagePadding),
            verticalArrangement = Arrangement.spacedBy(SfDimens.CardGap),
        ) {
            HomeSearchField(keyword = keyword, onKeywordChange = { keyword = it }, onSubmit = onSubmit)
            HomeEntryGrid(nav = nav)
            TodayOverview(st = st, nav = nav)
            SfDisclaimer(stringResource(R.string.disclaimer_short))
        }
    }
}

@Composable
private fun HomeSearchField(keyword: String, onKeywordChange: (String) -> Unit, onSubmit: (String) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(2.dp, RoundedCornerShape(22.dp), ambientColor = Color(0x0A0D2743), spotColor = Color(0x140D2743))
            .clip(RoundedCornerShape(22.dp))
            .background(Color.White)
            .border(0.8.dp, SfColors.CardBorder, RoundedCornerShape(22.dp))
            .padding(start = 4.dp, end = 5.dp, top = 4.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TextField(
            value = keyword,
            onValueChange = onKeywordChange,
            modifier = Modifier.weight(1f),
            placeholder = { Text(stringResource(R.string.search_hint), style = MaterialTheme.typography.bodyMedium, color = SfColors.TextSub) },
            leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null, tint = SfColors.TextSub, modifier = Modifier.size(20.dp)) },
            singleLine = true,
            shape = RoundedCornerShape(18.dp),
            textStyle = MaterialTheme.typography.bodyMedium,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { onSubmit(keyword) }),
            colors = TextFieldDefaults.colors(
                focusedContainerColor = Color.Transparent,
                unfocusedContainerColor = Color.Transparent,
                focusedTextColor = SfColors.TextMain,
                unfocusedTextColor = SfColors.TextMain,
                cursorColor = SfColors.DeepBlue,
                focusedIndicatorColor = Color.Transparent,
                unfocusedIndicatorColor = Color.Transparent,
                focusedPlaceholderColor = SfColors.TextSub,
                unfocusedPlaceholderColor = SfColors.TextSub,
            ),
        )
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(Brush.verticalGradient(listOf(SfColors.DeepBlue, SfColors.NavyDark)))
                .clickable { onSubmit(keyword) },
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Filled.Search, contentDescription = null, tint = Color.White, modifier = Modifier.size(20.dp))
        }
    }
}

@Composable
private fun HomeEntryGrid(nav: NavHostController) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            SfEntryTile(
                label = stringResource(R.string.entry_stock_query),
                sub = stringResource(R.string.entry_stock_query_sub),
                icon = Icons.AutoMirrored.Filled.TrendingUp,
                tint = SfColors.EntryBlue,
                modifier = Modifier.weight(1f),
                onClick = { nav.navigate(Routes.search("")) },
            )
            SfEntryTile(
                label = stringResource(R.string.entry_daily_scan),
                sub = stringResource(R.string.entry_daily_scan_sub),
                icon = Icons.Filled.Scanner,
                tint = SfColors.EntryOrange,
                modifier = Modifier.weight(1f),
                onClick = { nav.navigate(Routes.scanner()) },
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            SfEntryTile(
                label = stringResource(R.string.entry_trade_calendar),
                sub = stringResource(R.string.entry_trade_calendar_sub),
                icon = Icons.Filled.CalendarMonth,
                tint = SfColors.EntryTeal,
                modifier = Modifier.weight(1f),
                onClick = { nav.navigate(Routes.CALENDAR) },
            )
            SfEntryTile(
                label = stringResource(R.string.entry_date_select),
                sub = stringResource(R.string.entry_date_select_sub),
                icon = Icons.Filled.Explore,
                tint = SfColors.EntryGold,
                modifier = Modifier.weight(1f),
                onClick = { nav.navigate(Routes.dateSelect()) },
            )
        }
    }
}

@Composable
private fun TodayOverview(st: HomeViewModel.State, nav: NavHostController) {
    SfCard {
        SfSectionTitle(
            title = stringResource(R.string.today_overview),
            trailing = {
                if (!st.loading) {
                    Text(cnDate(st.date), style = MaterialTheme.typography.labelMedium, color = SfColors.TextSub)
                }
            },
        )
        Spacer(Modifier.size(12.dp))
        if (st.loading) {
            SfLoading()
        } else {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                SfStatCard(
                    label = stringResource(R.string.wealth_zheng),
                    value = st.zhengCount.toString(),
                    unit = stringResource(R.string.unit_only),
                    hint = FortuneText.SCAN_ZHENG_NOTE,
                    accent = SfColors.ZhengCai,
                    background = SfColors.ZhengCaiBg,
                    modifier = Modifier.weight(1f),
                    onClick = { nav.navigate(Routes.scanner("正财")) },
                )
                SfStatCard(
                    label = stringResource(R.string.wealth_pian),
                    value = st.pianCount.toString(),
                    unit = stringResource(R.string.unit_only),
                    hint = FortuneText.SCAN_PIAN_NOTE,
                    accent = SfColors.PianCai,
                    background = SfColors.PianCaiBg,
                    modifier = Modifier.weight(1f),
                    onClick = { nav.navigate(Routes.scanner("偏财")) },
                )
            }
        }
    }
}

/** weekdayCn 已自带「周」前缀，直接拼接避免出现「周周二」。 */
private fun cnDate(iso: String): String {
    val d = GanzhiCalculator.parse(iso) ?: return iso
    return "${d.year}年${d.monthValue}月${d.dayOfMonth}日（${GanzhiCalculator.weekdayCn(iso)}）"
}
