package com.stockfortune.app.ui.filter

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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.HelpOutline
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Autorenew
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Today
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.stockfortune.app.R
import com.stockfortune.app.domain.model.ScanRow
import com.stockfortune.app.domain.calculator.GanzhiCalculator
import java.time.LocalDate
import com.stockfortune.app.domain.model.TenGod
import com.stockfortune.app.ui.common.sfContainer
import com.stockfortune.app.ui.common.sfViewModel
import com.stockfortune.app.ui.components.SfCard
import com.stockfortune.app.ui.components.SfDatePickerDialog
import com.stockfortune.app.ui.components.TenGodTag
import com.stockfortune.app.ui.components.SfDisclaimer
import com.stockfortune.app.ui.components.SfEmptyState
import com.stockfortune.app.ui.components.SfGoldButton
import com.stockfortune.app.ui.components.SfHero
import com.stockfortune.app.ui.components.SfLoading
import com.stockfortune.app.ui.components.SfSectionTitle
import com.stockfortune.app.ui.components.WealthTag
import com.stockfortune.app.ui.navigation.Routes
import com.stockfortune.app.ui.theme.SfColors
import com.stockfortune.app.ui.theme.SfDimens
import com.stockfortune.app.ui.vm.FilterViewModel

/** 结果区最多展开的行数，避免整页滚动时一次性组合上千行。 */
private const val RESULT_LIMIT = 50

/**
 * 十神筛选：藏干 / 流年 / 流月 / 流日四个可折叠分组，组内 OR、组间 AND。
 */
@Composable
fun FilterScreen(nav: NavHostController) {
    val vm: FilterViewModel = sfViewModel { FilterViewModel.Factory(sfContainer()) }
    val st by vm.state.collectAsState()
    var picking by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) { vm.start() }

    val selectedCount = st.hidden.size + st.year.size + st.month.size + st.day.size

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
    ) {
        SfHero(
            title = stringResource(R.string.filter_title),
            subtitle = stringResource(R.string.filter_subtitle),
            slogan = stringResource(R.string.filter_desc),
            showLogo = false,
            height = 130.dp,
            trailing = {
                Box(
                    modifier = Modifier
                        .size(30.dp)
                        .clip(CircleShape)
                        .background(Color(0x33FFFFFF))
                        .clickable { nav.navigate(Routes.ALGORITHM) },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        Icons.AutoMirrored.Filled.HelpOutline,
                        contentDescription = stringResource(R.string.mine_algorithm),
                        tint = Color.White,
                        modifier = Modifier.size(17.dp),
                    )
                }
            },
        )

        Column(
            modifier = Modifier.padding(SfDimens.PagePadding),
            verticalArrangement = Arrangement.spacedBy(SfDimens.CardGap),
        ) {
            FilterDateBar(
                date = st.date,
                dayGanzhi = st.dayGanzhi,
                onPrev = { vm.shiftDate(-1) },
                onNext = { vm.shiftDate(1) },
                onTradeDay = { vm.useNearestTradeDay() },
                onOpenPicker = { picking = true },
            )

            st.notice?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.labelMedium,
                    color = SfColors.PianCai,
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            FilterGroup(
                title = stringResource(R.string.filter_group_hidden),
                desc = stringResource(R.string.filter_group_hidden_desc),
                icon = Icons.Filled.Layers,
                tint = SfColors.EntryBlue,
                selected = st.hidden,
                collapsed = "hidden" in st.collapsed,
                onToggleCollapse = { vm.toggleCollapse("hidden") },
                onToggleGod = { vm.toggle("hidden", it) },
            )
            FilterGroup(
                title = stringResource(R.string.filter_group_year) + ganzhiSuffix(st.yearGanzhi),
                desc = stringResource(R.string.filter_group_year_desc),
                icon = Icons.Filled.Autorenew,
                tint = SfColors.EntryTeal,
                selected = st.year,
                collapsed = "year" in st.collapsed,
                onToggleCollapse = { vm.toggleCollapse("year") },
                onToggleGod = { vm.toggle("year", it) },
            )
            FilterGroup(
                title = stringResource(R.string.filter_group_month) + ganzhiSuffix(st.monthGanzhi),
                desc = stringResource(R.string.filter_group_month_desc),
                icon = Icons.Filled.CalendarMonth,
                tint = SfColors.EntryOrange,
                selected = st.month,
                collapsed = "month" in st.collapsed,
                onToggleCollapse = { vm.toggleCollapse("month") },
                onToggleGod = { vm.toggle("month", it) },
            )
            FilterGroup(
                title = stringResource(R.string.filter_group_day) + ganzhiSuffix(st.dayGanzhi),
                desc = stringResource(R.string.filter_group_day_desc),
                icon = Icons.Filled.Today,
                tint = SfColors.EntryGold,
                selected = st.day,
                collapsed = "day" in st.collapsed,
                onToggleCollapse = { vm.toggleCollapse("day") },
                onToggleGod = { vm.toggle("day", it) },
            )

            Row(verticalAlignment = Alignment.CenterVertically) {
                SfGoldButton(
                    text = stringResource(R.string.filter_start),
                    onClick = { vm.runFilter() },
                    modifier = Modifier.weight(1f),
                    enabled = !st.searching,
                    icon = Icons.Filled.Search,
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = stringResource(R.string.filter_reset),
                    style = MaterialTheme.typography.labelLarge,
                    color = SfColors.DeepBlue,
                    modifier = Modifier
                        .clip(RoundedCornerShape(14.dp))
                        .clickable { vm.reset() }
                        .padding(horizontal = 14.dp, vertical = 12.dp),
                )
            }
            if (selectedCount == 0) {
                Text(
                    text = stringResource(R.string.filter_none_hint),
                    style = MaterialTheme.typography.labelSmall,
                    color = SfColors.TextSub,
                )
            }

            if (st.searching) {
                SfLoading()
            } else {
                st.result?.let { rows -> FilterResultCard(rows = rows, nav = nav) }
            }

            if (picking) {
                SfDatePickerDialog(
                    title = stringResource(R.string.picker_title),
                    initial = GanzhiCalculator.parse(st.date) ?: LocalDate.now(),
                    onDismiss = { picking = false },
                    onPick = {
                        picking = false
                        vm.setDate(GanzhiCalculator.iso(it))
                    },
                )
            }

            SfDisclaimer(stringResource(R.string.disclaimer))
        }
    }
}

/** 单个十神分组：头部常驻，展开后显示 10 个十神复选框（每行 4 个）。 */
@Composable
private fun FilterGroup(
    title: String,
    desc: String,
    icon: ImageVector,
    tint: Color,
    selected: Set<TenGod>,
    collapsed: Boolean,
    onToggleCollapse: () -> Unit,
    onToggleGod: (TenGod) -> Unit,
) {
    SfCard {
        Row(
            modifier = Modifier.fillMaxWidth().clickable(onClick = onToggleCollapse),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(RoundedCornerShape(11.dp))
                    .background(tint.copy(alpha = 0.14f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(19.dp))
            }
            Spacer(Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium, color = SfColors.TextMain)
                Text(
                    text = desc,
                    style = MaterialTheme.typography.labelSmall,
                    color = SfColors.TextSub,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(Modifier.width(8.dp))
            Text(
                text = if (selected.isEmpty()) "" else "${selected.size}",
                style = MaterialTheme.typography.labelMedium,
                color = SfColors.DeepBlue,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.width(6.dp))
            Icon(
                imageVector = if (collapsed) Icons.Filled.ExpandMore else Icons.Filled.ExpandLess,
                contentDescription = if (collapsed) "展开 $title" else "收起 $title",
                tint = SfColors.DeepBlue,
                modifier = Modifier.size(20.dp),
            )
        }

        if (!collapsed) {
            Spacer(Modifier.height(10.dp))
            TenGodCheckGrid(selected = selected, onToggleGod = onToggleGod)
        }
    }
}

@Composable
private fun TenGodCheckGrid(selected: Set<TenGod>, onToggleGod: (TenGod) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        TenGod.ORDER.chunked(4).forEach { line ->
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                line.forEach { god ->
                    TenGodCheckItem(
                        god = god,
                        checked = god in selected,
                        modifier = Modifier.weight(1f),
                        onClick = { onToggleGod(god) },
                    )
                }
                repeat(4 - line.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

@Composable
private fun TenGodCheckItem(god: TenGod, checked: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .clickable(onClick = onClick),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(
            checked = checked,
            onCheckedChange = { onClick() },
            modifier = Modifier.size(26.dp),
            colors = CheckboxDefaults.colors(
                checkedColor = SfColors.DeepBlue,
                uncheckedColor = SfColors.OtherTag,
                checkmarkColor = Color.White,
            ),
        )
        Text(
            text = god.cn,
            style = MaterialTheme.typography.labelMedium,
            color = if (checked) SfColors.DeepBlue else SfColors.TextMain,
            fontWeight = if (checked) FontWeight.SemiBold else FontWeight.Medium,
            maxLines = 1,
        )
    }
}

/** 筛选结果：最多展开前 50 条，其余提示收紧条件。 */
@Composable
private fun FilterResultCard(rows: List<ScanRow>, nav: NavHostController) {
    SfCard(padding = PaddingValues(top = 14.dp, bottom = 8.dp)) {
        SfSectionTitle(
            title = stringResource(R.string.filter_result_title),
            trailing = {
                Text(
                    text = stringResource(R.string.filter_result_total, rows.size),
                    style = MaterialTheme.typography.labelMedium,
                    color = SfColors.TextSub,
                )
            },
        )
        Spacer(Modifier.height(8.dp))
        if (rows.isEmpty()) {
            SfEmptyState(stringResource(R.string.empty_result), stringResource(R.string.empty_result_hint))
        } else {
            rows.take(RESULT_LIMIT).forEachIndexed { index, row ->
                FilterResultRow(
                    row = row,
                    last = index == minOf(rows.size, RESULT_LIMIT) - 1,
                    onClick = { nav.navigate(Routes.stock(row.code)) },
                )
            }
            if (rows.size > RESULT_LIMIT) {
                Text(
                    text = stringResource(R.string.filter_result_limit, RESULT_LIMIT),
                    style = MaterialTheme.typography.labelSmall,
                    color = SfColors.TextSub,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
                )
            }
        }
    }
}

@Composable
private fun FilterResultRow(row: ScanRow, last: Boolean, onClick: () -> Unit) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick)
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = row.symbol,
                style = MaterialTheme.typography.bodyMedium,
                color = SfColors.TextMain,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.weight(1.2f),
                maxLines = 1,
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = row.name,
                style = MaterialTheme.typography.bodyMedium,
                color = SfColors.TextMain,
                modifier = Modifier.weight(1.2f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = row.industry,
                style = MaterialTheme.typography.bodySmall,
                color = SfColors.TextSub,
                modifier = Modifier.weight(1.2f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.width(8.dp))
            if (row.matchedGods.isEmpty()) {
                WealthTag(row.wealth, hidden = row.fromHiddenStem)
            } else {
                row.matchedGods.take(2).forEachIndexed { i, god ->
                    if (i > 0) Spacer(Modifier.width(4.dp))
                    TenGodTag(god)
                }
                if (row.matchedGods.size > 2) {
                    Spacer(Modifier.width(4.dp))
                    Text("+${row.matchedGods.size - 2}", style = MaterialTheme.typography.labelSmall, color = SfColors.TextSub)
                }
            }
            Spacer(Modifier.width(6.dp))
            Icon(
                Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = SfColors.OtherTag,
                modifier = Modifier.size(14.dp),
            )
        }
        if (!last) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp)
                    .height(0.5.dp)
                    .background(SfColors.Divider),
            )
        }
    }
}


/** 基准日期条：流年 / 流月 / 流日三个维度都相对这一天成立，必须可切换。 */
@Composable
private fun FilterDateBar(
    date: String,
    dayGanzhi: String,
    onPrev: () -> Unit,
    onNext: () -> Unit,
    onTradeDay: () -> Unit,
    onOpenPicker: () -> Unit,
) {
    SfCard(padding = PaddingValues(horizontal = 10.dp, vertical = 8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size(34.dp)
                    .clip(CircleShape)
                    .background(SfColors.OtherTagBg)
                    .clickable(onClick = onPrev),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                    contentDescription = stringResource(R.string.prev),
                    tint = SfColors.DeepBlue, modifier = Modifier.size(20.dp),
                )
            }
            Column(
                Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(12.dp))
                    .clickable(onClick = onOpenPicker)
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    dateLabel(date),
                    style = MaterialTheme.typography.titleSmall,
                    color = SfColors.TextMain,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    stringResource(R.string.filter_base_day, dayGanzhi),
                    style = MaterialTheme.typography.labelSmall,
                    color = SfColors.TextSub,
                )
            }
            Box(
                Modifier
                    .size(34.dp)
                    .clip(CircleShape)
                    .background(SfColors.OtherTagBg)
                    .clickable(onClick = onNext),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = stringResource(R.string.next),
                    tint = SfColors.DeepBlue, modifier = Modifier.size(20.dp),
                )
            }
            Spacer(Modifier.width(8.dp))
            Text(
                stringResource(R.string.filter_nearest_trade),
                style = MaterialTheme.typography.labelSmall,
                color = SfColors.DeepBlue,
                modifier = Modifier
                    .clip(RoundedCornerShape(10.dp))
                    .clickable(onClick = onTradeDay)
                    .padding(horizontal = 8.dp, vertical = 6.dp),
            )
        }
    }
}

private fun dateLabel(iso: String): String {
    val d = GanzhiCalculator.parse(iso) ?: return iso
    return "${d.year}年${d.monthValue}月${d.dayOfMonth}日 ${GanzhiCalculator.weekdayCn(iso)}"
}

private fun ganzhiSuffix(gz: String): String = if (gz.isBlank()) "" else " · $gz"
