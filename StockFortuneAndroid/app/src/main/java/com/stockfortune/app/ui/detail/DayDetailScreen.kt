package com.stockfortune.app.ui.detail

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import com.stockfortune.app.R
import com.stockfortune.app.domain.calculator.GanzhiCalculator
import com.stockfortune.app.domain.model.DayDetail
import com.stockfortune.app.domain.model.FlowPillar
import com.stockfortune.app.domain.model.PillarHidden
import com.stockfortune.app.domain.model.TenGod
import com.stockfortune.app.domain.model.WealthType
import com.stockfortune.app.ui.common.sfContainer
import com.stockfortune.app.ui.common.sfViewModel
import com.stockfortune.app.ui.components.SfCard
import com.stockfortune.app.ui.components.SfDisclaimer
import com.stockfortune.app.ui.components.SfEmptyState
import com.stockfortune.app.ui.components.SfHero
import com.stockfortune.app.ui.components.SfInfoRow
import com.stockfortune.app.ui.components.SfLoading
import com.stockfortune.app.ui.components.SfSectionTitle
import com.stockfortune.app.ui.components.TenGodTag
import com.stockfortune.app.ui.components.WealthTag
import com.stockfortune.app.ui.theme.SfColors
import com.stockfortune.app.ui.theme.SfDimens
import com.stockfortune.app.ui.vm.DayDetailViewModel

/** 单日详情：某只股票在某一天的四维十神（流年 / 流月 / 流日 / 本命藏干）与财星判定快照。 */
@Composable
fun DayDetailScreen(nav: NavHostController, code: String, date: String) {
    val vm: DayDetailViewModel = sfViewModel { DayDetailViewModel.Factory(sfContainer()) }
    val state by vm.state.collectAsStateWithLifecycle()

    LaunchedEffect(code, date) { vm.load(code, date) }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .background(SfColors.PageBg),
    ) {
        val stock = state.stock
        val detail = state.detail
        SfHero(
            title = "单日详情",
            subtitle = if (date.isEmpty()) "" else "$date ${GanzhiCalculator.weekdayCn(date)}",
            height = 130.dp,
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
        )

        Column(
            Modifier
                .fillMaxWidth()
                .padding(SfDimens.PagePadding),
            verticalArrangement = Arrangement.spacedBy(SfDimens.CardGap),
        ) {
            if (state.loading) {
                SfLoading()
            } else if (stock == null || detail == null) {
                SfEmptyState("未找到该日数据", "请返回后重试；日期需在预置历法区间内")
            } else {
                DayInfoCard(stock.name, stock.code, date, detail)
                FourDimCard(detail)
                HiddenStemCard(detail.hidden)
                WealthCard(detail)
                SfDisclaimer(stringResource(R.string.disclaimer))
            }
        }
    }
}

@Composable
private fun DayInfoCard(name: String, code: String, date: String, d: DayDetail) {
    val day = d.dayFlow ?: return
    SfCard {
        SfSectionTitle("当日信息")
        Spacer(Modifier.height(10.dp))
        SfInfoRow(stringResource(R.string.field_name), name)
        SfInfoRow(stringResource(R.string.field_code), code)
        SfInfoRow("日期", "$date ${GanzhiCalculator.weekdayCn(date)}")
        SfInfoRow("流日干支", "${day.ganzhi}（${day.elements}）")
        SfInfoRow("该股日主", "${d.dayMaster}（${d.dayMasterElement}）· 十神皆以此为参照")
        SfInfoRow(
            "交易日",
            if (d.isTradeDay) "是" else "否",
            valueColor = if (d.isTradeDay) SfColors.TradeGreen else SfColors.TextSub,
        )
        d.solarTerm?.takeIf { it.isNotEmpty() }?.let { SfInfoRow("该日节气", it) }
    }
}

@Composable
private fun FourDimCard(d: DayDetail) {
    SfCard {
        SfSectionTitle("四维十神")
        Spacer(Modifier.height(6.dp))
        Text(
            "流年 / 流月 / 流日为外应，藏干为本命四柱所藏；均以日主${d.dayMaster}为参照，" +
                "干支分别取天干与地支本气。",
            style = MaterialTheme.typography.bodySmall,
            color = SfColors.TextSub,
        )
        Spacer(Modifier.height(10.dp))
        d.flows.forEach { FlowLine(it) }
        Divider()
        HiddenDimLine(d.hiddenGods)
    }
}

/** 流年 / 流月 / 流日中的一行。 */
@Composable
private fun FlowLine(p: FlowPillar) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.width(78.dp)) {
            Text(p.label, style = MaterialTheme.typography.labelMedium, color = SfColors.TextSub)
            Text("${p.ganzhi} ${p.elements}", style = MaterialTheme.typography.titleSmall, color = SfColors.TextMain)
        }
        GodCell("干", p.stemGod)
        Spacer(Modifier.width(10.dp))
        GodCell("支", p.branchGod)
        Spacer(Modifier.weight(1f))
        if (p.wealth.isWealth) {
            Text("财", style = MaterialTheme.typography.labelSmall, color = SfColors.TextSub)
            Spacer(Modifier.width(3.dp))
            WealthTag(p.wealth)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun HiddenDimLine(gods: List<TenGod>) {
    if (gods.isEmpty()) {
        Text("本命四柱地支无藏干记录", style = MaterialTheme.typography.bodyMedium, color = SfColors.TextSub)
        return
    }
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Column(Modifier.width(78.dp)) {
            Text("藏干", style = MaterialTheme.typography.labelMedium, color = SfColors.TextSub)
            Text("本命四柱", style = MaterialTheme.typography.titleSmall, color = SfColors.TextMain)
        }
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            gods.forEach { TenGodTag(it) }
        }
    }
}

@Composable
private fun HiddenStemCard(groups: List<PillarHidden>) {
    val total = groups.sumOf { it.items.size }
    SfCard {
        SfSectionTitle("本命藏干明细")
        Spacer(Modifier.height(6.dp))
        if (groups.isEmpty()) {
            Text("无藏干记录", style = MaterialTheme.typography.bodyMedium, color = SfColors.TextSub)
            return@SfCard
        }
        Text(
            "四柱地支共藏 $total 个天干，同气位重复的十神在「藏干」维度里只归一次，" +
                "所以标签数少于明细行数。明细如下。",
            style = MaterialTheme.typography.bodySmall,
            color = SfColors.TextSub,
        )
        Spacer(Modifier.height(10.dp))
        Row(Modifier.fillMaxWidth()) {
            Text("柱", style = MaterialTheme.typography.labelSmall, color = SfColors.TextSub, modifier = Modifier.width(76.dp))
            Text("藏干", style = MaterialTheme.typography.labelSmall, color = SfColors.TextSub, modifier = Modifier.width(52.dp))
            Text("十神", style = MaterialTheme.typography.labelSmall, color = SfColors.TextSub, modifier = Modifier.width(72.dp))
            Text("气位", style = MaterialTheme.typography.labelSmall, color = SfColors.TextSub)
        }
        groups.forEach { g ->
            g.items.forEachIndexed { i, item ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(vertical = 5.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        if (i == 0) "${g.label} · ${g.branch}" else " ",
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (i == 0) SfColors.TextMain else SfColors.TextSub,
                        modifier = Modifier.width(76.dp),
                    )
                    Text(item.stem, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.width(52.dp))
                    Box(Modifier.width(72.dp)) { TenGodTag(item.god) }
                    Text(item.rank, style = MaterialTheme.typography.bodySmall, color = SfColors.TextSub)
                }
            }
        }
    }
}

@Composable
private fun WealthCard(d: DayDetail) {
    SfCard {
        SfSectionTitle("流日财星判定")
        Spacer(Modifier.height(10.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            when {
                !d.isTradeDay -> GrayPillTag(stringResource(R.string.trade_day_no))
                d.wealth == WealthType.OTHER -> GrayPillTag("其他 · 非财星日")
                else -> {
                    val day = d.dayFlow
                    WealthTag(d.wealth, hidden = day != null && !day.stemGod.isWealth)
                }
            }
            Spacer(Modifier.weight(1f))
            Text("口径 Rule v1.1", style = MaterialTheme.typography.labelSmall, color = SfColors.TextSub)
        }
        Spacer(Modifier.height(10.dp))
        d.basis.forEach {
            Text(it, style = MaterialTheme.typography.bodyMedium, color = SfColors.TextMain, modifier = Modifier.padding(vertical = 2.dp))
        }
    }
}

@Composable
private fun GodCell(prefix: String, god: TenGod) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(prefix, style = MaterialTheme.typography.labelSmall, color = SfColors.TextSub)
        Spacer(Modifier.width(3.dp))
        TenGodTag(god)
    }
}

@Composable
private fun Divider() {
    Box(Modifier.fillMaxWidth().height(1.dp).background(SfColors.Divider))
}

@Composable
private fun GrayPillTag(text: String) {
    Box(
        Modifier
            .clip(CircleShape)
            .background(SfColors.OtherTagBg)
            .padding(horizontal = 10.dp, vertical = 4.dp),
    ) {
        Text(text, color = SfColors.TextSub, fontSize = 12.sp)
    }
}
