package com.stockfortune.app.ui.calendar

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
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import com.stockfortune.app.R
import com.stockfortune.app.domain.calculator.GanzhiCalculator
import com.stockfortune.app.ui.common.sfContainer
import com.stockfortune.app.ui.common.sfViewModel
import com.stockfortune.app.ui.components.SfCard
import com.stockfortune.app.ui.components.SfDatePickerDialog
import com.stockfortune.app.ui.components.SfDisclaimer
import com.stockfortune.app.ui.components.SfHero
import com.stockfortune.app.ui.components.SfInfoRow
import com.stockfortune.app.ui.components.SfSectionTitle
import com.stockfortune.app.ui.theme.SfColors
import com.stockfortune.app.ui.theme.SfDimens
import com.stockfortune.app.ui.vm.CalendarViewModel
import java.time.LocalDate

private val WEEK_HEADER = listOf("一", "二", "三", "四", "五", "六", "日")

/** 交易日历：月历网格 + 选中日干支与休市原因。 */
@Composable
fun CalendarScreen(nav: NavHostController) {
    val vm: CalendarViewModel = sfViewModel { CalendarViewModel.Factory(sfContainer()) }
    val state by vm.state.collectAsStateWithLifecycle()
    var picking by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) { vm.refresh() }

    Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        SfHero(
            title = stringResource(R.string.entry_trade_calendar),
            subtitle = "查看 A 股交易日与当日干支",
            height = 120.dp,
            showLogo = false,
        )

        Spacer(Modifier.height(SfDimens.CardGap))

        // 月份切换条：‹ › 逐月翻页，点月份标题可跳到任意年月
        val thisMonth = LocalDate.now()
        val atThisMonth = state.year == thisMonth.year && state.month == thisMonth.monthValue
        Row(
            modifier = Modifier
                .padding(horizontal = SfDimens.PagePadding)
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(SfColors.CardBg)
                .padding(horizontal = 10.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            MonthArrow("‹") { vm.shift(-1) }
            Column(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(12.dp))
                    .clickable(onClick = { picking = true })
                    .padding(vertical = 3.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Filled.CalendarMonth,
                        contentDescription = stringResource(R.string.picker_open_hint),
                        tint = SfColors.DeepBlue,
                        modifier = Modifier.size(16.dp),
                    )
                    Spacer(Modifier.width(5.dp))
                    Text(
                        "${state.year}年${state.month}月",
                        style = MaterialTheme.typography.titleMedium,
                        color = SfColors.TextMain,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
                Text(
                    stringResource(R.string.calendar_jump_month_hint),
                    style = MaterialTheme.typography.labelSmall,
                    color = SfColors.TextSub,
                )
            }
            MonthArrow("›") { vm.shift(1) }
            if (!atThisMonth) {
                Text(
                    stringResource(R.string.calendar_this_month),
                    style = MaterialTheme.typography.labelSmall,
                    color = SfColors.DeepBlue,
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .clickable { vm.backToThisMonth() }
                        .padding(horizontal = 7.dp, vertical = 6.dp),
                )
            }
        }

        state.notice?.let {
            Spacer(Modifier.height(8.dp))
            Text(
                text = it,
                style = MaterialTheme.typography.labelMedium,
                color = SfColors.PianCai,
                modifier = Modifier.padding(horizontal = SfDimens.PagePadding),
            )
        }

        Spacer(Modifier.height(SfDimens.CardGap))

        // 月历网格
        SfCard(modifier = Modifier.padding(horizontal = SfDimens.PagePadding)) {
            Row(Modifier.fillMaxWidth()) {
                WEEK_HEADER.forEach { w ->
                    Text(
                        w,
                        modifier = Modifier.weight(1f),
                        textAlign = TextAlign.Center,
                        style = MaterialTheme.typography.labelMedium,
                        color = SfColors.TextSub,
                    )
                }
            }
            Spacer(Modifier.height(6.dp))
            val cells: List<CalendarViewModel.DayCell?> =
                List(state.leadingBlanks) { null } + state.cells
            cells.chunked(7).forEach { week ->
                Row(Modifier.fillMaxWidth()) {
                    (week + List(7 - week.size) { null }).forEach { cell ->
                        Box(Modifier.weight(1f)) {
                            if (cell != null) {
                                DayCellBox(
                                    cell = cell,
                                    selected = cell.date == state.selected?.date,
                                    onClick = { vm.select(cell) },
                                )
                            }
                        }
                    }
                }
            }
            if (state.loading && state.cells.isEmpty()) {
                Spacer(Modifier.height(10.dp))
                Text("正在载入日历…", style = MaterialTheme.typography.bodySmall, color = SfColors.TextSub)
            }
            Spacer(Modifier.height(10.dp))
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(0.5.dp)
                    .background(SfColors.Divider),
            )
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Dot(SfColors.TradeGreen)
                Spacer(Modifier.width(5.dp))
                Text("交易日", style = MaterialTheme.typography.labelSmall, color = SfColors.TextSub)
                Spacer(Modifier.width(14.dp))
                Dot(SfColors.OtherTag)
                Spacer(Modifier.width(5.dp))
                Text("休市（含周末与节假日）", style = MaterialTheme.typography.labelSmall, color = SfColors.TextSub)
            }
        }

        Spacer(Modifier.height(SfDimens.CardGap))

        // 选中日信息
        state.selected?.let { cell ->
            SfCard(modifier = Modifier.padding(horizontal = SfDimens.PagePadding)) {
                SfSectionTitle(title = "当日信息")
                Spacer(Modifier.height(6.dp))
                SfInfoRow(label = stringResource(R.string.daily_col_date), value = cell.date)
                SfInfoRow(label = "星期", value = GanzhiCalculator.weekdayCn(cell.date))
                cell.ganzhi?.takeIf { it.isNotBlank() }?.let {
                    SfInfoRow(label = "干支", value = "${it}日")
                }
                SfInfoRow(
                    label = "状态",
                    value = if (cell.isTradeDay) stringResource(R.string.trade_day_yes)
                    else "休市：${cell.reason ?: "周末"}",
                    valueColor = if (cell.isTradeDay) SfColors.TradeGreen else SfColors.TextSub,
                )
                SfInfoRow(label = "数据可信度", value = confidenceLabel(cell.confidence, cell.isTradeDay))
            }
            Spacer(Modifier.height(SfDimens.CardGap))
        }

        // 本月统计
        SfCard(modifier = Modifier.padding(horizontal = SfDimens.PagePadding)) {
            SfSectionTitle(title = "本月统计")
            Spacer(Modifier.height(6.dp))
            SfInfoRow(label = "本月交易日", value = "${state.tradeDayCount} 天")
            SfInfoRow(label = "本月休市日", value = "${(state.cells.size - state.tradeDayCount).coerceAtLeast(0)} 天")
        }

        Spacer(Modifier.height(SfDimens.CardGap))
        if (picking) {
            val anchor = GanzhiCalculator.parse(state.selected?.date ?: "")
                ?: LocalDate.of(state.year, state.month, 1)
            SfDatePickerDialog(
                title = stringResource(R.string.calendar_picker_title),
                initial = anchor,
                onDismiss = { picking = false },
                onPick = {
                    picking = false
                    vm.jumpTo(it.year, it.monthValue, GanzhiCalculator.iso(it))
                },
            )
        }

        SfDisclaimer(
            stringResource(R.string.disclaimer),
            modifier = Modifier.padding(horizontal = SfDimens.PagePadding),
        )
        Spacer(Modifier.height(SfDimens.CardGap))
    }
}

@Composable
private fun MonthArrow(glyph: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(34.dp)
            .clip(CircleShape)
            .background(SfColors.OtherTagBg)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(glyph, fontSize = 20.sp, color = SfColors.DeepBlue, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun DayCellBox(cell: CalendarViewModel.DayCell, selected: Boolean, onClick: () -> Unit) {
    val dayNumber = GanzhiCalculator.parse(cell.date)?.dayOfMonth?.toString() ?: cell.date
    Box(
        modifier = Modifier
            .padding(2.dp)
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(if (selected) SfColors.Gold.copy(alpha = 0.20f) else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(vertical = 6.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                dayNumber,
                fontSize = 14.sp,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                color = if (cell.isTradeDay) SfColors.TextMain else SfColors.TextSub,
            )
            Spacer(Modifier.height(3.dp))
            Dot(if (cell.isTradeDay) SfColors.TradeGreen else SfColors.OtherTag)
        }
    }
}

@Composable
private fun Dot(color: Color) {
    Box(
        Modifier
            .size(6.dp)
            .clip(CircleShape)
            .background(color),
    )
}

private fun confidenceLabel(confidence: String, isTradeDay: Boolean): String = when {
    isTradeDay && confidence == "rule" -> "工作日开市（无休市安排）"
    confidence == "rule" -> "按规则推算（该年放假安排未逐条公布）"
    confidence == "curated" -> if (isTradeDay) "工作日开市" else "已公布休市安排"
    else -> confidence
}
