package com.stockfortune.app.ui.scanner

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.WorkspacePremium
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.stockfortune.app.R
import com.stockfortune.app.domain.calculator.FortuneText
import com.stockfortune.app.domain.calculator.GanzhiCalculator
import com.stockfortune.app.domain.model.ScanRow
import com.stockfortune.app.ui.common.sfContainer
import com.stockfortune.app.ui.common.sfViewModel
import com.stockfortune.app.ui.components.SfDatePickerDialog
import com.stockfortune.app.ui.components.SfDisclaimer
import com.stockfortune.app.ui.components.SfEmptyState
import com.stockfortune.app.ui.components.SfHero
import com.stockfortune.app.ui.components.SfLoading
import com.stockfortune.app.ui.components.SfRowContainer
import com.stockfortune.app.ui.components.SfSegmented
import com.stockfortune.app.ui.components.SfStatCard
import com.stockfortune.app.ui.components.SfTableHeader
import com.stockfortune.app.ui.components.WealthTag
import com.stockfortune.app.ui.navigation.Routes
import com.stockfortune.app.ui.theme.SfColors
import com.stockfortune.app.ui.theme.SfDimens
import com.stockfortune.app.ui.vm.ScannerViewModel
import java.time.LocalDate

/** 表头列宽：与下方数据行共用同一组权重，保证列对齐。 */
private val SCAN_COLUMNS = listOf(
    "#" to 0.6f,
    "股票代码" to 1.5f,
    "股票名称" to 1.6f,
    "所属行业" to 1.5f,
    "财星判定" to 1.3f,
)

/**
 * 每日选股扫描：以某个交易日为基准，列出全市场命中正财 / 偏财十神的股票。
 * 结果可达上千行，整页用单层 LazyColumn（表头 / 行 / 表尾作为独立 item），
 * 避免"滚动页里嵌限高 LazyColumn"造成的手势冲突与小窗滚动。
 */
@Composable
fun ScannerScreen(nav: NavHostController, wealth: String) {
    val vm: ScannerViewModel = sfViewModel { ScannerViewModel.Factory(sfContainer()) }
    val st by vm.state.collectAsState()
    var picking by remember { mutableStateOf(false) }

    LaunchedEffect(wealth) {
        // 日期一律交给 ViewModel：init(null) 会取最近交易日
        vm.init(null)
        when (wealth) {
            "正财" -> vm.setTab(1)
            "偏财" -> vm.setTab(2)
        }
    }

    val summary = st.summary
    val rows = st.rows
    val total = summary?.rows?.size ?: 0
    val zheng = summary?.zhengCount ?: 0
    val pian = summary?.pianCount ?: 0
    val topCorners = RoundedCornerShape(topStart = SfDimens.CardRadius, topEnd = SfDimens.CardRadius)
    val bottomCorners = RoundedCornerShape(bottomStart = SfDimens.CardRadius, bottomEnd = SfDimens.CardRadius)

    if (picking) {
        SfDatePickerDialog(
            title = stringResource(R.string.picker_title_scan),
            initial = GanzhiCalculator.parse(st.date) ?: LocalDate.now(),
            onDismiss = { picking = false },
            onPick = {
                picking = false
                vm.setDate(GanzhiCalculator.iso(it))
            },
        )
    }

    LazyColumn(modifier = Modifier.fillMaxSize()) {
        item {
            SfHero(
                title = stringResource(R.string.scanner_title),
                subtitle = stringResource(R.string.scanner_subtitle),
                showLogo = false,
                height = 140.dp,
                trailing = {
                    ScanDatePill(
                        date = st.date,
                        onPrev = { vm.shiftDate(-1L) },
                        onNext = { vm.shiftDate(1L) },
                        onOpenPicker = { picking = true },
                    )
                },
            )
        }

        item {
            Column(
                modifier = Modifier.padding(
                    horizontal = SfDimens.PagePadding,
                    vertical = SfDimens.CardGap,
                ),
                verticalArrangement = Arrangement.spacedBy(SfDimens.CardGap),
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    SfStatCard(
                        label = stringResource(R.string.wealth_zheng),
                        value = zheng.toString(),
                        unit = stringResource(R.string.unit_only),
                        hint = FortuneText.SCAN_ZHENG_NOTE,
                        accent = SfColors.ZhengCai,
                        background = SfColors.ZhengCaiBg,
                        modifier = Modifier.weight(1f),
                        onClick = { vm.setTab(1) },
                    )
                    SfStatCard(
                        label = stringResource(R.string.wealth_pian),
                        value = pian.toString(),
                        unit = stringResource(R.string.unit_only),
                        hint = FortuneText.SCAN_PIAN_NOTE,
                        accent = SfColors.PianCai,
                        background = SfColors.PianCaiBg,
                        modifier = Modifier.weight(1f),
                        onClick = { vm.setTab(2) },
                    )
                }

                SfSegmented(
                    options = listOf(
                        "${stringResource(R.string.scan_tab_all)} ($total)",
                        "${stringResource(R.string.legend_zheng)} ($zheng)",
                        "${stringResource(R.string.legend_pian)} ($pian)",
                    ),
                    selected = st.tab,
                    onSelect = { vm.setTab(it) },
                    modifier = Modifier.align(Alignment.CenterHorizontally),
                )

                ScanDateNote(date = st.date, onTradeDay = { vm.useNearestTradeDay() })
                st.notice?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.labelMedium,
                        color = SfColors.PianCai,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }

        when {
            st.loading -> item {
                SfLoading(Modifier.padding(horizontal = SfDimens.PagePadding))
            }

            rows.isEmpty() -> item {
                Column(Modifier.padding(horizontal = SfDimens.PagePadding)) {
                    SfEmptyState(stringResource(R.string.empty_result), stringResource(R.string.empty_result_hint))
                    Text(
                        text = stringResource(R.string.scan_empty_note, cnDate(st.date)),
                        style = MaterialTheme.typography.bodySmall,
                        color = SfColors.TextSub,
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp),
                    )
                }
            }

            else -> {
                item {
                    Column(
                        Modifier
                            .padding(horizontal = SfDimens.PagePadding)
                            .clip(topCorners)
                            .background(SfColors.CardBg),
                    ) {
                        Spacer(Modifier.height(4.dp))
                        SfTableHeader(SCAN_COLUMNS)
                    }
                }
                itemsIndexed(rows, key = { _, row -> row.stockId }) { index, row ->
                    Column(
                        Modifier
                            .padding(horizontal = SfDimens.PagePadding)
                            .background(SfColors.CardBg),
                    ) {
                        ScanResultRow(
                            index = index,
                            row = row,
                            last = false,
                            onClick = { nav.navigate(Routes.stock(row.code)) },
                        )
                    }
                }
                item {
                    Box(
                        Modifier
                            .padding(horizontal = SfDimens.PagePadding)
                            .clip(bottomCorners)
                            .background(SfColors.CardBg)
                            .height(8.dp),
                    )
                }
            }
        }

        item {
            SfDisclaimer(
                stringResource(R.string.disclaimer),
                Modifier.padding(horizontal = SfDimens.PagePadding, vertical = SfDimens.CardGap),
            )
        }
    }
}

/** 顶部日期切换胶囊：‹ 📅 2026年9月29日（周二） ›，中间日期可点击展开月历跳到任意一天。 */
@Composable
private fun ScanDatePill(
    date: String,
    onPrev: () -> Unit,
    onNext: () -> Unit,
    onOpenPicker: () -> Unit,
) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(SfDimens.TagRadius))
            .background(Color(0x33FFFFFF))
            .padding(horizontal = 2.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(26.dp)
                .clip(CircleShape)
                .clickable(onClick = onPrev),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                contentDescription = stringResource(R.string.day_prev),
                tint = Color.White,
                modifier = Modifier.size(18.dp),
            )
        }
        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(9.dp))
                .clickable(onClick = onOpenPicker)
                .padding(horizontal = 4.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                Icons.Filled.CalendarMonth,
                contentDescription = stringResource(R.string.picker_open_hint),
                tint = Color.White,
                modifier = Modifier.size(14.dp),
            )
            Spacer(Modifier.width(4.dp))
            Text(
                text = if (date.isEmpty()) stringResource(R.string.loading) else cnDate(date),
                color = Color.White,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
            )
        }
        Box(
            modifier = Modifier
                .size(26.dp)
                .clip(CircleShape)
                .clickable(onClick = onNext),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = stringResource(R.string.day_next),
                tint = Color.White,
                modifier = Modifier.size(18.dp),
            )
        }
    }
}

/** 列表上方的小字：扫描日期 + 仅统计交易日 + 回到最近交易日。 */
@Composable
private fun ScanDateNote(date: String, onTradeDay: () -> Unit) {
    Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = stringResource(R.string.scan_date_prefix) + cnDate(date),
            style = MaterialTheme.typography.labelMedium,
            color = SfColors.TextSub,
        )
        Spacer(Modifier.width(6.dp))
        Text(
            text = stringResource(R.string.scan_trade_only),
            style = MaterialTheme.typography.labelSmall,
            color = SfColors.OtherTag,
        )
        Spacer(Modifier.weight(1f))
        Text(
            text = stringResource(R.string.filter_nearest_trade),
            style = MaterialTheme.typography.labelSmall,
            color = SfColors.DeepBlue,
            modifier = Modifier
                .clip(RoundedCornerShape(9.dp))
                .clickable(onClick = onTradeDay)
                .padding(horizontal = 6.dp, vertical = 4.dp),
        )
    }
}

/** 单行扫描结果：前三名用金色徽标代替序号。 */
@Composable
private fun ScanResultRow(index: Int, row: ScanRow, last: Boolean, onClick: () -> Unit) {
    Column(modifier = Modifier.fillMaxWidth()) {
        SfRowContainer(onClick = onClick) {
            Box(modifier = Modifier.weight(0.6f), contentAlignment = Alignment.CenterStart) {
                if (index < 3) {
                    Icon(
                        Icons.Filled.WorkspacePremium,
                        contentDescription = stringResource(R.string.rank_nth, index + 1),
                        tint = SfColors.Gold,
                        modifier = Modifier.size(16.dp),
                    )
                } else {
                    Text(
                        text = (index + 1).toString(),
                        style = MaterialTheme.typography.bodyMedium,
                        color = SfColors.TextSub,
                    )
                }
            }
            Text(
                text = row.symbol,
                style = MaterialTheme.typography.bodyMedium,
                color = SfColors.TextMain,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.weight(1.5f),
                maxLines = 1,
            )
            Text(
                text = row.name,
                style = MaterialTheme.typography.bodyMedium,
                color = SfColors.TextMain,
                modifier = Modifier.weight(1.6f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = row.industry,
                style = MaterialTheme.typography.bodySmall,
                color = SfColors.TextSub,
                modifier = Modifier.weight(1.5f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Box(modifier = Modifier.weight(1.3f), contentAlignment = Alignment.CenterStart) {
                WealthTag(row.wealth, hidden = row.fromHiddenStem)
            }
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

/** weekdayCn 已自带「周」前缀，直接拼接避免出现「周周二」。 */
private fun cnDate(iso: String): String {
    val d = GanzhiCalculator.parse(iso) ?: return iso
    return "${d.year}年${d.monthValue}月${d.dayOfMonth}日（${GanzhiCalculator.weekdayCn(iso)}）"
}
