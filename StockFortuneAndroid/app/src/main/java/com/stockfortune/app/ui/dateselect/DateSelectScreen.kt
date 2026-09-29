package com.stockfortune.app.ui.dateselect

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Insights
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import com.stockfortune.app.R
import com.stockfortune.app.domain.calculator.GanzhiCalculator
import com.stockfortune.app.domain.model.DateSelectionRow
import com.stockfortune.app.ui.common.sfContainer
import com.stockfortune.app.ui.common.sfViewModel
import com.stockfortune.app.ui.components.SfCard
import com.stockfortune.app.ui.components.SfDatePickerDialog
import com.stockfortune.app.ui.components.SfDisclaimer
import com.stockfortune.app.ui.components.SfEmptyState
import com.stockfortune.app.ui.components.SfGoldButton
import com.stockfortune.app.ui.components.SfHero
import com.stockfortune.app.ui.components.SfSectionTitle
import com.stockfortune.app.ui.components.SfTableHeader
import com.stockfortune.app.ui.components.WealthTag
import com.stockfortune.app.ui.navigation.Routes
import com.stockfortune.app.ui.theme.SfColors
import com.stockfortune.app.ui.theme.SfDimens
import com.stockfortune.app.ui.vm.DateSelectViewModel
import java.time.LocalDate

private val HintBlueBg = Color(0xFFEAF2FE)
private val ErrorRed = Color(0xFFE5484D)
private const val MIN_YEAR = 1990
private const val MAX_YEAR = 2035

/** 八字择日：股票代码 + 日期区间 → 吉日列表（对齐效果图 08）。 */
@Composable
fun DateSelectScreen(nav: NavHostController, initialCode: String) {
    val vm: DateSelectViewModel = sfViewModel { DateSelectViewModel.Factory(sfContainer()) }
    val state by vm.state.collectAsStateWithLifecycle()
    // 0=不弹，1=改开始日期，2=改结束日期
    var picking by remember { mutableIntStateOf(0) }

    LaunchedEffect(initialCode) { vm.prefill(initialCode) }

    Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        SfHero(
            title = stringResource(R.string.date_select_title),
            subtitle = stringResource(R.string.date_select_subtitle),
            height = 150.dp,
            showLogo = false,
            leading = {
                IconButton(onClick = { nav.popBackStack() }) {
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = stringResource(R.string.back),
                        tint = Color.White,
                    )
                }
            },
            trailing = {
                IconButton(onClick = { nav.navigate(Routes.ALGORITHM) }) {
                    Icon(
                        Icons.Filled.Info,
                        contentDescription = stringResource(R.string.mine_algorithm),
                        tint = SfColors.GoldLight,
                    )
                }
            },
        )

        Spacer(Modifier.height(SfDimens.CardGap))

        // 表单卡片
        SfCard(modifier = Modifier.padding(horizontal = SfDimens.PagePadding)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                LeadingIcon(Icons.Filled.Search)
                Spacer(Modifier.width(10.dp))
                Text(
                    stringResource(R.string.date_select_code),
                    style = MaterialTheme.typography.titleSmall,
                    color = SfColors.TextMain,
                )
                Spacer(Modifier.width(10.dp))
                TextField(
                    value = state.code,
                    onValueChange = vm::setCode,
                    modifier = Modifier.weight(1f),
                    placeholder = { Text("如 600519", color = SfColors.TextSub) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text, imeAction = ImeAction.Done),
                    trailingIcon = {
                        if (state.code.isNotEmpty()) {
                            IconButton(onClick = { vm.setCode("") }) {
                                Icon(
                                    Icons.Filled.Close,
                                    contentDescription = stringResource(R.string.clear),
                                    tint = SfColors.TextSub,
                                    modifier = Modifier.size(16.dp),
                                )
                            }
                        }
                    },
                )
            }
            Text(
                "请输入A股股票代码，如 600519",
                style = MaterialTheme.typography.bodySmall,
                color = SfColors.TextSub,
                modifier = Modifier.padding(start = 46.dp, top = 2.dp, bottom = 4.dp),
            )

            FieldDivider()

            DateFieldRow(
                icon = Icons.Filled.CalendarMonth,
                label = stringResource(R.string.date_select_start),
                value = state.start,
                onClick = { picking = 1 },
            )

            FieldDivider()

            DateFieldRow(
                icon = Icons.Filled.CalendarMonth,
                label = stringResource(R.string.date_select_end),
                value = state.end,
                onClick = { picking = 2 },
            )

            Spacer(Modifier.height(10.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(checked = state.onlyTradeDays, onCheckedChange = vm::setOnlyTradeDays)
                Text(
                    stringResource(R.string.only_trade_days),
                    style = MaterialTheme.typography.bodyMedium,
                    color = SfColors.TextMain,
                    modifier = Modifier.clickable { vm.setOnlyTradeDays(!state.onlyTradeDays) },
                )
            }

            Spacer(Modifier.height(10.dp))
            state.error?.let {
                Text(it, color = ErrorRed, fontSize = 12.sp, modifier = Modifier.padding(bottom = 8.dp))
            }
            SfGoldButton(
                text = stringResource(R.string.date_select_run),
                onClick = { vm.run() },
                enabled = !state.running,
                icon = Icons.Filled.Insights,
            )
        }

        Spacer(Modifier.height(SfDimens.CardGap))

        // 提示条
        Row(
            modifier = Modifier
                .padding(horizontal = SfDimens.PagePadding)
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(HintBlueBg)
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("ⓘ", color = SfColors.DeepBlue, fontWeight = FontWeight.Bold)
            Spacer(Modifier.width(8.dp))
            Text(
                stringResource(R.string.date_select_hint),
                style = MaterialTheme.typography.bodySmall,
                color = SfColors.DeepBlue,
                modifier = Modifier.weight(1f),
            )
        }

        if (state.running) {
            Spacer(Modifier.height(SfDimens.CardGap))
            Box(Modifier.fillMaxWidth().height(120.dp), contentAlignment = Alignment.Center) {
                Text("正在推算吉日…", style = MaterialTheme.typography.bodySmall, color = SfColors.TextSub)
            }
        }

        // 结果区
        val rows = state.rows
        if (rows != null) {
            Spacer(Modifier.height(SfDimens.CardGap))
            SfCard(
                modifier = Modifier.padding(horizontal = SfDimens.PagePadding),
                padding = PaddingValues(vertical = 8.dp),
            ) {
                SfSectionTitle(
                    title = stringResource(R.string.date_select_result_title),
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                    trailing = {
                        Text(
                            stringResource(R.string.date_select_found, rows.size),
                            style = MaterialTheme.typography.labelMedium,
                            color = SfColors.DeepBlue,
                            fontWeight = FontWeight.SemiBold,
                        )
                    },
                )
                state.resolvedName?.let {
                    Text(
                        "$it · ${state.start} 至 ${state.end}",
                        style = MaterialTheme.typography.labelSmall,
                        color = SfColors.TextSub,
                        modifier = Modifier.padding(horizontal = 12.dp),
                    )
                }
                if (rows.isEmpty()) {
                    SfEmptyState(title = "区间内无合适吉日", hint = "换个日期区间试试")
                } else {
                    SfTableHeader(
                        listOf(
                            stringResource(R.string.daily_col_date) to 2.2f,
                            stringResource(R.string.daily_col_week) to 0.9f,
                            stringResource(R.string.col_wealth_type) to 1.3f,
                            stringResource(R.string.col_is_trade_day) to 1.2f,
                        ),
                    )
                    Box(Modifier.padding(horizontal = 12.dp).fillMaxWidth().height(0.5.dp).background(SfColors.Divider))
                    rows.forEach { DateResultRow(it) }
                }
            }
        }

        Spacer(Modifier.height(SfDimens.CardGap))
        SfDisclaimer(
            stringResource(R.string.disclaimer),
            modifier = Modifier.padding(horizontal = SfDimens.PagePadding),
        )
        Spacer(Modifier.height(SfDimens.CardGap))
    }

    if (picking != 0) {
        val current = if (picking == 1) state.start else state.end
        SfDatePickerDialog(
            title = if (picking == 1) stringResource(R.string.date_select_start) else stringResource(R.string.date_select_end),
            initial = GanzhiCalculator.parse(current) ?: LocalDate.now(),
            onDismiss = { picking = 0 },
            onPick = { picked ->
                val iso = GanzhiCalculator.iso(picked)
                if (picking == 1) vm.setStart(iso) else vm.setEnd(iso)
                picking = 0
            },
        )
    }
}

@Composable
private fun LeadingIcon(icon: ImageVector, tint: Color = SfColors.DeepBlue) {
    Box(
        modifier = Modifier
            .size(34.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(tint.copy(alpha = 0.12f)),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(18.dp))
    }
}

@Composable
private fun FieldDivider() {
    Box(
        Modifier
            .padding(vertical = 6.dp)
            .fillMaxWidth()
            .height(0.5.dp)
            .background(SfColors.Divider),
    )
}

@Composable
private fun DateFieldRow(icon: ImageVector, label: String, value: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        LeadingIcon(icon)
        Spacer(Modifier.width(10.dp))
        Text(label, style = MaterialTheme.typography.titleSmall, color = SfColors.TextMain)
        Spacer(Modifier.weight(1f))
        Text(
            value.ifEmpty { "—" },
            style = MaterialTheme.typography.bodyLarge,
            color = SfColors.TextMain,
            textAlign = TextAlign.End,
        )
        Spacer(Modifier.width(6.dp))
        Icon(
            Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = SfColors.OtherTag,
            modifier = Modifier.size(18.dp),
        )
    }
}

@Composable
private fun DateResultRow(row: DateSelectionRow) {
    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(2.2f)) {
                Text(row.date, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = SfColors.TextMain)
                Text(
                    "（${GanzhiCalculator.weekdayCn(row.date)}）",
                    style = MaterialTheme.typography.labelSmall,
                    color = SfColors.TextSub,
                )
            }
            Text(
                GanzhiCalculator.weekdayShort(row.date),
                modifier = Modifier.weight(0.9f),
                style = MaterialTheme.typography.labelMedium,
                color = SfColors.TextSub,
            )
            Box(Modifier.weight(1.3f)) {
                WealthTag(row.wealth, hidden = row.wealth.isWealth && row.dayTenGod.cn != row.wealth.cn)
            }
            TradeFlag(row.isTradeDay, Modifier.weight(1.2f))
        }
        Box(
            Modifier
                .padding(horizontal = 12.dp)
                .fillMaxWidth()
                .height(0.5.dp)
                .background(SfColors.Divider),
        )
    }
}

@Composable
private fun TradeFlag(trade: Boolean, modifier: Modifier = Modifier) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier
                .size(7.dp)
                .clip(CircleShape)
                .background(if (trade) SfColors.TradeGreen else SfColors.OtherTag),
        )
        Spacer(Modifier.width(5.dp))
        Text(
            stringResource(if (trade) R.string.trade_day_yes else R.string.trade_day_no),
            fontSize = 11.sp,
            color = if (trade) SfColors.TradeGreen else SfColors.TextSub,
        )
    }
}


@Composable
private fun StepperRow(label: String, value: String, onDec: () -> Unit, onInc: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = SfColors.TextSub, modifier = Modifier.width(34.dp))
        Spacer(Modifier.weight(1f))
        StepButton("−", onDec)
        Box(
            Modifier
                .padding(horizontal = 10.dp)
                .width(72.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(SfColors.OtherTagBg)
                .padding(vertical = 8.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(value, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = SfColors.TextMain)
        }
        StepButton("＋", onInc)
    }
}

@Composable
private fun StepButton(glyph: String, onClick: () -> Unit) {
    Box(
        Modifier
            .size(34.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(SfColors.DeepBlue.copy(alpha = 0.10f))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(glyph, fontSize = 18.sp, color = SfColors.DeepBlue, fontWeight = FontWeight.Bold)
    }
}

private fun clampDate(year: Int, month: Int, day: Int): LocalDate {
    val y = year.coerceIn(MIN_YEAR, MAX_YEAR)
    val m = month.coerceIn(1, 12)
    val last = LocalDate.of(y, m, 1).lengthOfMonth()
    return LocalDate.of(y, m, day.coerceIn(1, last))
}

private fun shiftMonth(date: LocalDate, delta: Int): LocalDate {
    val idx = date.year * 12 + (date.monthValue - 1) + delta
    return clampDate(Math.floorDiv(idx, 12), Math.floorMod(idx, 12) + 1, date.dayOfMonth)
}
