package com.stockfortune.app.ui.detail

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.stockfortune.app.R
import com.stockfortune.app.data.repository.ClassicQuote
import com.stockfortune.app.data.repository.ClassicQuoteKind
import com.stockfortune.app.data.repository.ClassicQuoteResult
import com.stockfortune.app.data.repository.StockDetail
import com.stockfortune.app.domain.calculator.GanzhiCalculator
import com.stockfortune.app.domain.calculator.TenGodCalculator
import com.stockfortune.app.domain.model.DayAnalysis
import com.stockfortune.app.domain.model.MonthAnalysis
import com.stockfortune.app.domain.model.MonthLabel
import com.stockfortune.app.domain.model.TenGod
import com.stockfortune.app.domain.model.WealthType
import com.stockfortune.app.domain.model.YearAnalysis
import com.stockfortune.app.ui.components.SfAmberCallout
import com.stockfortune.app.ui.components.SfCard
import com.stockfortune.app.ui.components.SfDisclaimer
import com.stockfortune.app.ui.components.SfEmptyState
import com.stockfortune.app.ui.components.SfInfoRow
import com.stockfortune.app.ui.components.SfRowContainer
import com.stockfortune.app.ui.components.SfSectionTitle
import com.stockfortune.app.ui.components.SfTableHeader
import com.stockfortune.app.ui.components.SfTag
import com.stockfortune.app.ui.components.TenGodTag
import com.stockfortune.app.ui.components.WealthTag
import com.stockfortune.app.ui.theme.SfColors
import com.stockfortune.app.ui.theme.SfDimens
import com.stockfortune.app.ui.theme.wealthColor
import com.stockfortune.app.ui.vm.AppClock
import java.time.LocalDate

/*
 * 详情页 4 个 Tab 的内容区。数据量小（12 个月 / 31 天 / 约 22 个交易日），
 * 统一用 Column + forEach 渲染，避免与外层 verticalScroll 嵌套冲突。
 * 效果图中的具体干支为示意值，此处只对齐布局、层级、配色与标签形态。
 */

// ---------------------------------------------------------------- 基本信息

@Composable
fun BasicTab(detail: StockDetail) {
    val stock = detail.stock
    Column(verticalArrangement = Arrangement.spacedBy(SfDimens.CardGap)) {
        SfCard {
            SfSectionTitle(stringResource(R.string.basic_info))
            Spacer(Modifier.height(10.dp))
            SfInfoRow(stringResource(R.string.field_code), stock.code)
            DividerLine()
            SfInfoRow(stringResource(R.string.field_name), stock.name)
            DividerLine()
            SfInfoRow(stringResource(R.string.field_listing_date), stock.listingDate)
            DividerLine()
            SfInfoRow(stringResource(R.string.field_industry), stock.industry)
            if (stock.industryFull.contains("-")) {
                DividerLine()
                SfInfoRow(stringResource(R.string.field_industry_full), shortIndustry(stock.industryFull))
            }
            DividerLine()
            SfInfoRow(stringResource(R.string.field_market), stock.board)
            DividerLine()
            SfInfoRow(stringResource(R.string.field_nature), stock.stockNature)
        }

        SfAmberCallout(
            text = detail.fateFeature,
            title = stringResource(R.string.overview_title),
            icon = Icons.Default.BarChart,
        )

        ClassicsCard(detail)

        SfCard {
            SfSectionTitle(stringResource(R.string.bazi_info))
            Spacer(Modifier.height(12.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                detail.pillars.forEach { p ->
                    PillarColumn(label = p.label, ganzhi = p.ganzhi, elements = p.stemElement + p.branchElement, modifier = Modifier.weight(1f))
                }
            }
            Spacer(Modifier.height(10.dp))
            Text(
                "时柱说明：因A股开盘多为09:30（巳时），时柱仅供排盘参考与多维筛选，命理强弱、喜用及大运均基于年月日三柱确立。",
                style = MaterialTheme.typography.labelSmall,
                color = SfColors.TextSub,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                "日历标准：依据公历日粒度日历排盘，节气交接以标准公历日为界；不依赖未公开算法。",
                style = MaterialTheme.typography.labelSmall,
                color = SfColors.TextSub,
            )
        }

        NatalRelationCard(detail)

        YongshenCard(detail)

        LuckCycleCard(detail)

        SfCard {
            SfSectionTitle("☯ " + stringResource(R.string.yinyang_info))
            Spacer(Modifier.height(10.dp))
            // 实测库内 first_day_flag 只有 阴 / 阳 / 数据缺失 三种取值。直接渲染会把内部
            // 哨兵暴露成"阴阳类型：数据缺失（首日涨跌）"，既泄漏实现细节又误导语义。
            val flag = stock.firstDayFlag
            val yinyangText = if (flag == "阴" || flag == "阳") {
                "$flag（${stringResource(R.string.field_first_day_flag)}）"
            } else {
                "—"
            }
            SfInfoRow(stringResource(R.string.field_yinyang), yinyangText)
            DividerLine()
            SfInfoRow(stringResource(R.string.field_wuxing), detail.seasonSummary)
            DividerLine()
            SfInfoRow(stringResource(R.string.field_nayin), detail.bazi.naYin)
            DividerLine()
            SfInfoRow(stringResource(R.string.field_strength_note), detail.bazi.strength)
            DividerLine()
            SfInfoRow(stringResource(R.string.field_fate_feature_note), detail.fateFeature)
        }

        SfDisclaimer(stringResource(R.string.disclaimer_short))
    }
}

/**
 * 典籍依据：按日主天干归类的原文参考。原文不设省略行数，
 * 由详情页已有的 verticalScroll 承载，这里不再套第二层滚动容器。
 */
@Composable
fun ClassicsCard(detail: StockDetail) {
    SfCard {
        SfSectionTitle(stringResource(R.string.classics_title))
        Spacer(Modifier.height(6.dp))
        Text(
            stringResource(R.string.classics_subtitle, detail.bazi.dayMaster),
            style = MaterialTheme.typography.labelSmall,
            color = SfColors.TextSub,
        )
        Spacer(Modifier.height(10.dp))
        when (val result = detail.classics) {
            is ClassicQuoteResult.Found -> {
                result.quotes.forEachIndexed { i, quote ->
                    if (i > 0) {
                        Spacer(Modifier.height(12.dp))
                        DividerLine()
                    }
                    QuoteBlock(quote)
                }
                Spacer(Modifier.height(12.dp))
                Text(
                    stringResource(R.string.classics_disclaimer),
                    style = MaterialTheme.typography.labelSmall,
                    color = SfColors.TextSub,
                )
            }
            ClassicQuoteResult.NoMatch -> StateLine(
                stringResource(R.string.classics_empty),
                stringResource(R.string.classics_empty_hint),
            )
            ClassicQuoteResult.LoadFailed -> StateLine(
                stringResource(R.string.classics_unavailable),
                stringResource(R.string.classics_unavailable_hint),
            )
        }
    }
}

@Composable
private fun StateLine(title: String, hint: String) {
    Column {
        Text(title, style = MaterialTheme.typography.bodyMedium, color = SfColors.TextMain)
        Text(hint, style = MaterialTheme.typography.labelSmall, color = SfColors.TextSub)
    }
}

@Composable
private fun QuoteBlock(quote: ClassicQuote) {
    val kindLabel = when (quote.kind) {
        ClassicQuoteKind.VERSE -> stringResource(R.string.classics_kind_verse)
        ClassicQuoteKind.ANNOTATION_EXCERPT -> stringResource(R.string.classics_kind_annotation)
    }
    Row(verticalAlignment = Alignment.Top) {
        SfTag(
            kindLabel,
            SfColors.DeepBlue,
            SfColors.OtherTagBg,
            fontSize = 10,
            bold = false,
        )
        Spacer(Modifier.width(8.dp))
        Text(
            quote.originalText,
            style = MaterialTheme.typography.bodyMedium,
            color = SfColors.TextMain,
            lineHeight = 22.sp,
            modifier = Modifier.weight(1f),
        )
    }
    Spacer(Modifier.height(4.dp))
    Text(
        stringResource(R.string.classics_source_line, quote.bookTitle, quote.edition),
        style = MaterialTheme.typography.labelSmall,
        color = SfColors.TextSub,
    )
    Text(
        stringResource(R.string.classics_meta_line, quote.chapter, quote.section, quote.scanPage),
        style = MaterialTheme.typography.labelSmall,
        color = SfColors.TextSub,
    )
    if (quote.kind == ClassicQuoteKind.ANNOTATION_EXCERPT) {
        Text(
            stringResource(R.string.classics_excerpt_note),
            style = MaterialTheme.typography.labelSmall,
            color = SfColors.OtherTag,
        )
    }
}

@Composable
private fun PillarColumn(label: String, ganzhi: String, elements: String, modifier: Modifier = Modifier) {
    val (fg, bg) = elementColorPair(elements.firstOrNull() ?: ' ')
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = SfColors.TextSub)
        Spacer(Modifier.height(6.dp))
        Box(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(bg)
                .border(0.8.dp, fg.copy(alpha = 0.25f), RoundedCornerShape(12.dp))
                .padding(vertical = 12.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(ganzhi, color = fg, fontSize = 18.sp, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.height(6.dp))
        Text(elements, style = MaterialTheme.typography.labelSmall, color = SfColors.TextSub)
    }
}

private fun elementColorPair(elementChar: Char): Pair<Color, Color> = when (elementChar) {
    '金' -> Color(0xFFC8942A) to Color(0xFFFFF9E6)
    '木' -> Color(0xFF2E9E66) to Color(0xFFE8F8F0)
    '水' -> Color(0xFF2570EB) to Color(0xFFEAF2FE)
    '火' -> Color(0xFFE54848) to Color(0xFFFDECEC)
    '土' -> Color(0xFFD97706) to Color(0xFFFFF4E5)
    else -> SfColors.DeepBlue to SfColors.OtherTagBg
}

// ---------------------------------------------------------------- 年度运势

@Composable
fun YearTab(
    year: YearAnalysis?,
    yearValue: Int,
    onPrev: () -> Unit,
    onNext: () -> Unit,
    onMonthClick: (MonthLabel) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(SfDimens.CardGap)) {
        PeriodSwitcher("${yearValue}年", onPrev, onNext)

        if (year == null) {
            SfEmptyState("暂无该年度数据", "请切换年份后重试")
            return@Column
        }

        SfCard {
            SfSectionTitle(stringResource(R.string.year_overview, yearValue))
            Spacer(Modifier.height(10.dp))
            SfInfoRow(stringResource(R.string.year_flow), "${year.yearGanzhi}年（$yearValue）")
            DividerLine()
            SfInfoRow("五行", year.yearWuxing, valueColor = SfColors.ZhengCai)
            DividerLine()
            if (year.currentPeriod != null) {
                SfInfoRow(
                    "流年所处大运",
                    "${year.currentPeriod.ganzhi}大运（${year.currentPeriod.startYear}–${year.currentPeriod.endYear}年）",
                    valueColor = SfColors.DeepBlue,
                )
                DividerLine()
            }
            SfInfoRow(stringResource(R.string.year_wealth_summary), year.wealthSummary)
            DividerLine()
            SfInfoRow(stringResource(R.string.year_industry), year.industryNote)
            DividerLine()
            SfInfoRow(stringResource(R.string.year_advice), year.advice)
        }

        AnnualSynthesisCard(year, yearValue)

        SfCard {
            SfSectionTitle(stringResource(R.string.month_ten_god_list))
            Spacer(Modifier.height(12.dp))
            Row(Modifier.fillMaxWidth()) {
                WealthLegend(
                    color = SfColors.ZhengCai, name = stringResource(R.string.legend_zheng),
                    hint = stringResource(R.string.legend_zheng_hint), modifier = Modifier.weight(1f),
                )
                WealthLegend(
                    color = SfColors.PianCai, name = stringResource(R.string.legend_pian),
                    hint = stringResource(R.string.legend_pian_hint), modifier = Modifier.weight(1f),
                )
                WealthLegend(
                    color = SfColors.OtherTag, name = stringResource(R.string.legend_other),
                    hint = stringResource(R.string.legend_other_hint), modifier = Modifier.weight(1f),
                )
            }
            Spacer(Modifier.height(6.dp))
            year.months.forEach { label ->
                Column(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .clickable { onMonthClick(label) }
                        .padding(vertical = 9.dp, horizontal = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            label.monthGanzhi,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = SfColors.TextMain,
                        )
                        Spacer(Modifier.width(7.dp))
                        // label.month 是流月序数（1 = 寅月），旧写法渲染成"1月 (寅月)"会被读成
                        // 公历 1 月：点进去却是公历 2 月的月度页，同一事物两套编号。
                        // 干支月序与公历月的对应是固定的：寅月起算，第 n 流月 ≈ 公历 n%12+1 月。
                        Text(
                            "${label.branchLabel} · 约${label.month % 12 + 1}月 · ${ganzhiMonthSpan(label.startDate, label.endDate)}",
                            style = MaterialTheme.typography.bodySmall, color = SfColors.TextSub,
                        )
                        Spacer(Modifier.weight(1f))
                        TenGodTag(label.tenGod)
                        Spacer(Modifier.width(5.dp))
                        WealthTag(label.wealth)
                    }
                    Text(
                        label.summary,
                        style = MaterialTheme.typography.bodySmall, color = SfColors.TextSub,
                    )
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                        Text(
                            "点击查看月度五段式解读 →",
                            fontSize = 11.sp,
                            color = SfColors.DeepBlue,
                            fontWeight = FontWeight.Medium,
                        )
                    }
                }
            }
        }
    }
}

/** 干支月起止日（交节决定）压成「2.4–3.5」；跨年时省略起点年份。 */
private fun ganzhiMonthSpan(startIso: String, endIso: String): String {
    fun md(iso: String): String? = iso.split('-').getOrNull(1)?.let { m ->
        iso.split('-').getOrNull(2)?.let { d -> "${m.toInt()}.${d.toInt()}" }
    }
    val s = md(startIso) ?: return ""
    val e = md(endIso) ?: return s
    return if (s == e) s else "$s–$e"
}

@Composable
private fun WealthLegend(color: Color, name: String, hint: String, modifier: Modifier = Modifier) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(8.dp).clip(CircleShape).background(color))
        Spacer(Modifier.width(5.dp))
        Column {
            Text(name, style = MaterialTheme.typography.labelMedium, color = SfColors.TextMain)
            Text(hint, fontSize = 10.sp, color = SfColors.TextSub)
        }
    }
}

// ---------------------------------------------------------------- 月度运势

@Composable
fun MonthTab(
    m: MonthAnalysis?,
    onPrev: () -> Unit,
    onNext: () -> Unit,
    onDayClick: (DayAnalysis) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(SfDimens.CardGap)) {
        PeriodSwitcher(m?.let { "${it.year}年${it.month}月（${it.branchLabel}）" } ?: "—", onPrev, onNext)

        if (m == null) {
            SfEmptyState("暂无该月度数据", "请切换月份后重试")
            return@Column
        }

        SfCard {
            SfSectionTitle(stringResource(R.string.month_info))
            Spacer(Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.Top) {
                Box(
                    Modifier
                        .size(64.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(Brush.verticalGradient(listOf(SfColors.DeepBlue, SfColors.NavyDark)))
                        .border(1.2.dp, Brush.verticalGradient(listOf(Color(0xFFFFDF88), SfColors.Gold)), RoundedCornerShape(16.dp)),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = m.monthGanzhi.takeLast(1),
                        fontSize = 32.sp,
                        fontWeight = FontWeight.Bold,
                        style = TextStyle(brush = Brush.verticalGradient(SfColors.GoldGradient)),
                    )
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    TaggedInfoRow(
                        label = "月干支",
                        value = "${m.monthGanzhi}（${m.branchLabel}）",
                        tags = listOf(
                            "月干 " + TenGodCalculator.elementOf(m.monthGanzhi.take(1)) to (SfColors.DeepBlue to SfColors.OtherTagBg),
                            "月支 " + TenGodCalculator.elementOfBranch(m.monthGanzhi.takeLast(1)) to (SfColors.Gold to SfColors.PianCaiBg),
                        ),
                    )
                    SfInfoRow(stringResource(R.string.month_element), m.wuxingSummary)
                    TaggedInfoRow(label = stringResource(R.string.month_relation), value = null, god = m.monthStemTenGod)
                    SfInfoRow(stringResource(R.string.month_summary), m.summary)
                }
            }
            m.monthNote?.let {
                Spacer(Modifier.height(8.dp))
                Text(
                    text = it,
                    style = MaterialTheme.typography.labelSmall,
                    color = SfColors.TextSub,
                )
            }
        }

        MonthlyFiveParagraphCard(m)

        SfCard {
            SfSectionTitle(stringResource(R.string.month_trade_days))
            Spacer(Modifier.height(12.dp))
            Row(Modifier.fillMaxWidth()) {
                CountLegend(SfColors.ZhengCai, "${stringResource(R.string.legend_zheng)}（${m.zhengCount}天）", Modifier.weight(1f))
                CountLegend(SfColors.PianCai, "${stringResource(R.string.legend_pian)}（${m.pianCount}天）", Modifier.weight(1f))
                CountLegend(SfColors.OtherTag, "其他（${m.otherCount}天）", Modifier.weight(1f))
            }
            Spacer(Modifier.height(12.dp))
            MonthCalendar(m, onDayClick)
        }

        SfAmberCallout(
            title = stringResource(R.string.month_tip_title),
            text = m.tip,
        )
    }
}

@Composable
private fun CountLegend(color: Color, text: String, modifier: Modifier = Modifier) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(8.dp).clip(CircleShape).background(color))
        Spacer(Modifier.width(5.dp))
        Text(text, style = MaterialTheme.typography.labelMedium, color = SfColors.TextMain, maxLines = 1)
    }
}

/** 月历网格：表头 一~日 + 前导空格 + 每日格（数字 + 财星圆点）。 */
@Composable
private fun MonthCalendar(m: MonthAnalysis, onDayClick: (DayAnalysis) -> Unit) {
    val header = listOf("一", "二", "三", "四", "五", "六", "日")
    Row(Modifier.fillMaxWidth()) {
        header.forEach {
            Text(it, Modifier.weight(1f), textAlign = TextAlign.Center, style = MaterialTheme.typography.labelSmall, color = SfColors.TextSub)
        }
    }
    Spacer(Modifier.height(6.dp))
    val first = LocalDate.of(m.year, m.month, 1)
    val cells = ArrayList<DayAnalysis?>()
    repeat(first.dayOfWeek.value - 1) { cells.add(null) }
    cells.addAll(m.days)
    while (cells.size % 7 != 0) cells.add(null)
    cells.chunked(7).forEach { week ->
        Row(Modifier.fillMaxWidth()) {
            week.forEach { day ->
                Box(Modifier.weight(1f).height(42.dp), contentAlignment = Alignment.Center) {
                    if (day != null) DayCell(day, onDayClick)
                }
            }
        }
    }
}

@Composable
private fun DayCell(day: DayAnalysis, onDayClick: (DayAnalysis) -> Unit) {
    val isZheng = day.wealth == WealthType.ZHENG_CAI && day.isTradeDay
    val isPian = day.wealth == WealthType.PIAN_CAI && day.isTradeDay
    val haloBg = when {
        isZheng -> Color(0xFFFFECEE)
        isPian -> Color(0xFFFFF2DC)
        else -> Color.Transparent
    }
    val digitColor = when {
        !day.isTradeDay -> Color(0xFFA0AEC0)
        isZheng -> SfColors.ZhengCai
        isPian -> SfColors.PianCai
        else -> SfColors.TextMain
    }
    val dotColor = when {
        isZheng -> SfColors.ZhengCai
        isPian -> SfColors.PianCai
        day.isTradeDay -> Color(0xFFCBD5E1)
        else -> Color(0xFFE2E8F0)
    }

    Column(
        Modifier
            .size(38.dp)
            .clip(CircleShape)
            .background(haloBg)
            .clickable(enabled = day.isTradeDay) { onDayClick(day) },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = GanzhiCalculator.parse(day.date)?.dayOfMonth?.toString() ?: "",
            fontSize = 13.sp,
            color = digitColor,
            fontWeight = if (day.wealth.isWealth && day.isTradeDay) FontWeight.Bold else FontWeight.Medium,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(2.dp))
        Box(
            Modifier
                .size(4.dp)
                .clip(CircleShape)
                .background(dotColor),
        )
    }
}

// ---------------------------------------------------------------- 每日分析

@Composable
fun DailyTab(
    m: MonthAnalysis?,
    onPrev: () -> Unit,
    onNext: () -> Unit,
    onDayClick: (DayAnalysis) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(SfDimens.CardGap)) {
        PeriodSwitcher(m?.let { "${it.year}年${it.month}月（${it.branchLabel}）" } ?: "—", onPrev, onNext)

        if (m == null) {
            SfEmptyState("暂无该月度数据", "请切换月份后重试")
            return@Column
        }

        GoldHintBar(icon = Icons.Default.Info, text = stringResource(R.string.only_trade_days_hint, m.tradeDayCount))

        SfCard(padding = PaddingValues(vertical = 6.dp)) {
            // 权重与 DailyRow 必须成对修改：表头原先是 1.9/0.8/1.0/1.3/1.2，数据行是
            // 2.2/0.9/1.1/1.3/1.2 再加一个不参与加权的 18dp 箭头，两套宽度让列逐列右偏。
            SfTableHeader(
                listOf(
                    stringResource(R.string.daily_col_date) to 2.2f,
                    stringResource(R.string.daily_col_week) to 0.9f,
                    stringResource(R.string.daily_col_ganzhi) to 1.1f,
                    stringResource(R.string.daily_col_ten_god) to 1.3f,
                    stringResource(R.string.daily_col_wealth) to 1.2f,
                ),
                trailingWidth = 18.dp,
            )
            Text(
                text = stringResource(R.string.daily_col_hint),
                style = MaterialTheme.typography.labelSmall,
                color = SfColors.TextSub,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
            )
            m.tradeDays.forEachIndexed { i, day ->
                if (i > 0) DividerLine()
                DailyRow(day, onDayClick)
            }
        }
    }
}

@Composable
private fun DailyRow(day: DayAnalysis, onDayClick: (DayAnalysis) -> Unit) {
    val d = GanzhiCalculator.parse(day.date)
    SfRowContainer(onClick = { onDayClick(day) }) {
        Column(Modifier.weight(2.2f)) {
            Text(d?.let { "${it.monthValue}月${it.dayOfMonth}日" } ?: day.date, style = MaterialTheme.typography.titleSmall, color = SfColors.TextMain)
            Text(day.date, style = MaterialTheme.typography.labelSmall, color = SfColors.TextSub)
        }
        Text(GanzhiCalculator.weekdayCn(day.date), Modifier.weight(0.9f), style = MaterialTheme.typography.bodyMedium, color = SfColors.TextMain)
        Column(Modifier.weight(1.1f)) {
            Text(day.dayGanzhi, style = MaterialTheme.typography.titleSmall, color = SfColors.TextMain)
            Text(
                TenGodCalculator.elementOf(day.dayStem) + TenGodCalculator.elementOfBranch(day.dayBranch),
                style = MaterialTheme.typography.labelSmall, color = SfColors.TextSub,
            )
        }
        Box(Modifier.weight(1.3f)) { TenGodTag(day.dayTenGod) }
        Box(Modifier.weight(1.2f)) {
            if (day.wealth.isWealth) WealthTag(day.wealth, hidden = day.wealthIsHidden)
            else SfTag(day.wealth.cn, SfColors.TextSub, SfColors.OtherTagBg)
        }
        Icon(
            Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null,
            tint = SfColors.OtherTag, modifier = Modifier.size(18.dp),
        )
    }
}

// ---------------------------------------------------------------- 共用小块

/** 年 / 月切换条：白色圆角卡片内 ‹ 文案 ›。 */
@Composable
private fun PeriodSwitcher(text: String, onPrev: () -> Unit, onNext: () -> Unit) {
    Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(SfColors.CardBg)) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 6.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size(38.dp)
                    .clip(CircleShape)
                    .clickable(onClick = onPrev),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, stringResource(R.string.prev), tint = SfColors.DeepBlue, modifier = Modifier.size(22.dp))
            }
            Spacer(Modifier.weight(1f))
            Text(text, style = MaterialTheme.typography.titleMedium, color = SfColors.TextMain, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
            Spacer(Modifier.weight(1f))
            Box(
                Modifier
                    .size(38.dp)
                    .clip(CircleShape)
                    .clickable(onClick = onNext),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, stringResource(R.string.next), tint = SfColors.DeepBlue, modifier = Modifier.size(22.dp))
            }
        }
    }
}

/** 浅金底提示条。 */
@Composable
private fun GoldHintBar(icon: ImageVector, text: String) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(SfColors.PianCaiBg)
            .padding(14.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Box(
            Modifier
                .size(26.dp)
                .clip(CircleShape)
                .background(SfColors.Gold.copy(alpha = 0.18f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, contentDescription = null, tint = SfColors.Gold, modifier = Modifier.size(15.dp))
        }
        Spacer(Modifier.width(10.dp))
        Text(text, style = MaterialTheme.typography.bodyMedium, color = SfColors.TextMain, modifier = Modifier.weight(1f))
    }
}

/** 左标签 + 右值 + 值后追加小标签（月干支、十神行用）。 */
@Composable
private fun TaggedInfoRow(
    label: String,
    value: String?,
    tags: List<Pair<String, Pair<Color, Color>>> = emptyList(),
    god: TenGod? = null,
) {
    Row(Modifier.fillMaxWidth().padding(vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = SfColors.TextSub)
        Spacer(Modifier.width(12.dp))
        Row(
            Modifier.weight(1f),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (value != null) {
                Text(value, style = MaterialTheme.typography.bodyMedium, color = SfColors.TextMain)
            }
            if (god != null) {
                TenGodTag(god)
            }
            tags.forEach { (text, colors) ->
                Spacer(Modifier.width(4.dp))
                SfTag(text, colors.first, colors.second, fontSize = 10)
            }
        }
    }
}

@Composable
private fun DividerLine() {
    Box(Modifier.fillMaxWidth().height(0.5.dp).background(SfColors.Divider))
}

/** 二/三级行业同名时只保留一次，避免出现"房地产服务-房地产服务"。 */
private fun shortIndustry(full: String): String {
    val seg = full.split("-").filter { it.isNotBlank() }.drop(1)
    return seg.filterIndexed { i, v -> i == 0 || v != seg[i - 1] }.joinToString("·")
}

// ---------------------------------------------------------------- Phase 6 融合卡片

/**
 * 原局综合解读卡片：年月日三柱 3×3 离线关系矩阵（22 类刑冲合害）。
 */
@Composable
fun NatalRelationCard(detail: StockDetail) {
    SfCard {
        SfSectionTitle("原局综合解读")
        Spacer(Modifier.height(4.dp))
        Text(
            "年月日三柱 3×3 离线关系矩阵 · 排除时柱口径",
            style = MaterialTheme.typography.labelSmall,
            color = SfColors.TextSub,
        )
        Spacer(Modifier.height(10.dp))
        if (detail.natalRelations.isNotEmpty()) {
            detail.natalRelations.forEachIndexed { i, rel ->
                if (i > 0) {
                    Spacer(Modifier.height(8.dp))
                    DividerLine()
                    Spacer(Modifier.height(8.dp))
                }
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
                    SfTag(
                        text = rel.relationType,
                        foreground = SfColors.DeepBlue,
                        background = SfColors.OtherTagBg,
                        fontSize = 11,
                    )
                    Spacer(Modifier.width(8.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            text = "${rel.positions}（${rel.sourceGanzhi} ↔ ${rel.targetGanzhi}）",
                            style = MaterialTheme.typography.titleSmall,
                            color = SfColors.TextMain,
                        )
                        if (rel.notes.isNotBlank()) {
                            Spacer(Modifier.height(2.dp))
                            Text(
                                text = rel.notes,
                                style = MaterialTheme.typography.bodySmall,
                                color = SfColors.TextSub,
                                lineHeight = 18.sp,
                            )
                        }
                    }
                }
            }
        } else {
            Text(
                text = "原局六字清透纯和，年月日三柱无刑冲合害交加。",
                style = MaterialTheme.typography.bodyMedium,
                color = SfColors.TextMain,
            )
        }
        Spacer(Modifier.height(10.dp))
        Text(
            text = "依据 ADR-0004 与 ADR-0008，原局分析严格基于年月日三柱，排除时柱干扰；规则版本：natal-relation-v1.3",
            style = MaterialTheme.typography.labelSmall,
            color = SfColors.TextSub,
        )
    }
}

/**
 * 喜用候选卡片：三柱六字同源喜用，扶抑调候双轴解耦，展示合规术语。
 */
@Composable
fun YongshenCard(detail: StockDetail) {
    val y = detail.yongshen
    SfCard {
        SfSectionTitle("喜用格局与流通候选")
        Spacer(Modifier.height(4.dp))
        Text(
            "三柱六字同源喜用 · 扶抑调候双轴解耦",
            style = MaterialTheme.typography.labelSmall,
            color = SfColors.TextSub,
        )
        Spacer(Modifier.height(10.dp))

        if (y != null) {
            val statusTag = when (y.status) {
                "confirmed" -> "扶抑已确立" to (Color(0xFF2E9E66) to Color(0xFFE8F8F0))
                "candidate" -> "中和流通候选" to (SfColors.Gold to SfColors.PianCaiBg)
                else -> "暂不适用" to (SfColors.TextSub to SfColors.OtherTagBg)
            }
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("推导状态", style = MaterialTheme.typography.bodyMedium, color = SfColors.TextSub)
                Spacer(Modifier.weight(1f))
                SfTag(statusTag.first, statusTag.second.first, statusTag.second.second)
            }
            DividerLine()

            if (y.status == "confirmed") {
                if (y.yongShen.isNotBlank()) {
                    SfInfoRow("扶抑用神", y.yongShen, valueColor = SfColors.DeepBlue)
                    DividerLine()
                }
                if (y.xiShen.isNotBlank()) {
                    SfInfoRow("扶抑喜神", y.xiShen, valueColor = Color(0xFF2E9E66))
                    DividerLine()
                }
                if (y.jiShen.isNotBlank()) {
                    SfInfoRow("制衡之神", y.jiShen, valueColor = SfColors.ZhengCai)
                    DividerLine()
                }
                if (y.chouShen.isNotBlank()) {
                    SfInfoRow("耗身之神", y.chouShen, valueColor = SfColors.PianCai)
                    DividerLine()
                }
            } else if (y.status == "candidate") {
                if (y.candidateElements.isNotBlank()) {
                    SfInfoRow("流通候选五行", y.candidateElements, valueColor = SfColors.Gold)
                    DividerLine()
                }
                Text(
                    text = "格局中和平衡，不设单一主轴，依岁运流年五行顺次调节。",
                    style = MaterialTheme.typography.bodySmall,
                    color = SfColors.TextSub,
                    modifier = Modifier.padding(vertical = 4.dp),
                )
                DividerLine()
            }

            if (y.tiaohouNote.isNotBlank()) {
                SfInfoRow("调候环境观察", y.tiaohouNote)
                DividerLine()
            }

            if (y.rationale.isNotBlank()) {
                Spacer(Modifier.height(4.dp))
                Text(
                    text = y.rationale,
                    style = MaterialTheme.typography.bodySmall,
                    color = SfColors.TextMain,
                    lineHeight = 20.sp,
                )
            }
        } else {
            Text(
                text = "暂无喜用候选预计算数据",
                style = MaterialTheme.typography.bodyMedium,
                color = SfColors.TextSub,
            )
        }

        Spacer(Modifier.height(8.dp))
        Text(
            text = "依据 ADR-0006 与 ADR-0007，全库喜用推导与强弱同源，无强加唯一喜用神；展示文案遵循合规映射。",
            style = MaterialTheme.typography.labelSmall,
            color = SfColors.TextSub,
        )
    }
}

/**
 * 大运区块卡片：展示顺逆方向、起运岁数与12步大运周期排盘。
 */
@Composable
fun LuckCycleCard(detail: StockDetail) {
    val lc = detail.luckCycle
    var expanded by rememberSaveable { mutableStateOf(false) }

    SfCard {
        SfSectionTitle("当前大运与起运说明")
        Spacer(Modifier.height(4.dp))
        Text(
            "年干与有效首日涨跌阴阳联合裁定 · 离线排运",
            style = MaterialTheme.typography.labelSmall,
            color = SfColors.TextSub,
        )
        Spacer(Modifier.height(10.dp))

        if (lc != null) {
            val dirText = when (lc.direction) {
                "forward" -> "顺行"
                "reverse" -> "逆行"
                else -> "不适用"
            }
            val polText = when (lc.firstDayPolarity.lowercase()) {
                "yang" -> "阳命（首日收阳）"
                "yin" -> "阴命（首日收阴）"
                "flat" -> "平盘（不适用）"
                "missing" -> "缺失（不适用）"
                else -> lc.firstDayPolarity
            }
            SfInfoRow("首日命别", polText)
            DividerLine()
            SfInfoRow("大运方向", dirText)
            DividerLine()

            if (lc.startAge != null && lc.startDate != null) {
                SfInfoRow("起运岁数", "约 ${lc.startAge} 岁")
                DividerLine()
                SfInfoRow("首运日期", lc.startDate)
                DividerLine()
            } else {
                SfInfoRow("状态说明", lc.statusReason)
                DividerLine()
            }

            if (detail.luckPeriods.isNotEmpty()) {
                Spacer(Modifier.height(6.dp))
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text("12步大运周期排盘", style = MaterialTheme.typography.titleSmall, color = SfColors.TextMain)
                    Spacer(Modifier.weight(1f))
                    Text(
                        if (expanded) "收起 ▴" else "展开全部 ▾",
                        style = MaterialTheme.typography.labelSmall,
                        color = SfColors.DeepBlue,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.clip(RoundedCornerShape(4.dp)).clickable { expanded = !expanded }.padding(4.dp),
                    )
                }
                Spacer(Modifier.height(6.dp))

                val periodsToShow = if (expanded) detail.luckPeriods else detail.luckPeriods.take(3)
                periodsToShow.forEachIndexed { i, p ->
                    if (i > 0) DividerLine()
                    val stemGod = TenGodCalculator.tenGod(detail.bazi.dayStem, p.stem)
                    val branchGod = TenGodCalculator.tenGod(detail.bazi.dayStem, TenGodCalculator.mainQi(p.branch))
                    val currentYear = AppClock.today().year
                    val isCurrent = currentYear in p.startYear..p.endYear
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .padding(vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            "第${p.cycleIndex}步",
                            style = MaterialTheme.typography.labelSmall,
                            color = SfColors.TextSub,
                            modifier = Modifier.width(42.dp),
                        )
                        Text(
                            p.ganzhi,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold,
                            color = if (isCurrent) SfColors.Gold else SfColors.TextMain,
                            modifier = Modifier.width(44.dp),
                        )
                        Text(
                            "${p.startYear}–${p.endYear}（${p.startAge}–${p.endAge}岁）",
                            style = MaterialTheme.typography.bodySmall,
                            color = SfColors.TextSub,
                            modifier = Modifier.weight(1f),
                        )
                        TenGodTag(stemGod)
                        Spacer(Modifier.width(4.dp))
                        TenGodTag(branchGod)
                        if (isCurrent) {
                            Spacer(Modifier.width(4.dp))
                            SfTag("当前", SfColors.Gold, SfColors.PianCaiBg, fontSize = 10)
                        }
                    }
                }
            } else if (lc.status.startsWith("unavailable") || lc.status.contains("not_applicable")) {
                Spacer(Modifier.height(6.dp))
                SfAmberCallout(
                    text = "因上市首日处于平盘或行情缺失，依据 ADR-0001 与 ADR-0002，大运方向未形成裁定，不伪造顺逆周期。",
                    title = "大运不适用说明",
                )
            }
        } else {
            Text("暂无大运数据", style = MaterialTheme.typography.bodyMedium, color = SfColors.TextSub)
        }

        Spacer(Modifier.height(8.dp))
        Text(
            text = "依据 ADR-0001 与 ADR-0002，大运方向由年干与首日涨跌阴阳联合裁定；平盘及缺失显式标为不适用。",
            style = MaterialTheme.typography.labelSmall,
            color = SfColors.TextSub,
        )
    }
}

/**
 * 年度综合命理解释卡片：聚合实际 12 个月月度五段式分析与十神分布。
 */
@Composable
fun AnnualSynthesisCard(year: YearAnalysis, yearValue: Int) {
    val syn = year.annualSynthesis
    SfCard {
        SfSectionTitle("年度综合命理解释")
        Spacer(Modifier.height(4.dp))
        Text(
            "${yearValue}年（${year.yearGanzhi}）· 聚合 12 个月离线分析",
            style = MaterialTheme.typography.labelSmall,
            color = SfColors.TextSub,
        )
        Spacer(Modifier.height(10.dp))

        if (syn != null) {
            GoldHintBar(
                icon = Icons.Default.Info,
                text = syn.currentPeriodDesc,
            )
            Spacer(Modifier.height(10.dp))

            SfInfoRow("流月十神分布", syn.tenGodDistributionSummary)
            DividerLine()

            Spacer(Modifier.height(6.dp))
            Text("四季阶段演进", style = MaterialTheme.typography.titleSmall, color = SfColors.TextMain)
            Spacer(Modifier.height(6.dp))
            syn.seasonalThemes.forEach { (season, theme) ->
                Row(Modifier.fillMaxWidth().padding(vertical = 3.dp), verticalAlignment = Alignment.Top) {
                    SfTag(season, SfColors.DeepBlue, SfColors.OtherTagBg, fontSize = 10, bold = false)
                    Spacer(Modifier.width(8.dp))
                    Text(theme, style = MaterialTheme.typography.bodySmall, color = SfColors.TextSub, modifier = Modifier.weight(1f))
                }
            }
            DividerLine()
            Spacer(Modifier.height(6.dp))

            Text("综合解读归纳", style = MaterialTheme.typography.titleSmall, color = SfColors.TextMain)
            Spacer(Modifier.height(4.dp))
            Text(
                syn.structuredSummary,
                style = MaterialTheme.typography.bodyMedium,
                color = SfColors.TextMain,
                lineHeight = 22.sp,
            )

            Spacer(Modifier.height(10.dp))
            Text(
                syn.complianceNotice,
                style = MaterialTheme.typography.labelSmall,
                color = SfColors.TextSub,
            )
            Text(
                syn.hitRuleSummary,
                style = MaterialTheme.typography.labelSmall,
                color = SfColors.TextSub,
            )
        } else {
            Text("暂无该年度综合解读数据", style = MaterialTheme.typography.bodyMedium, color = SfColors.TextSub)
        }
    }
}

/**
 * 月度五段式详细解读卡片：命理依据（可展开）、本月主题、潜在矛盾、企业经营观察、综合解释。
 */
@Composable
fun MonthlyFiveParagraphCard(m: MonthAnalysis) {
    val fp = m.fiveParagraph
    var basisExpanded by rememberSaveable { mutableStateOf(false) }

    SfCard {
        SfSectionTitle("月度五段式解读")
        Spacer(Modifier.height(4.dp))
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                "${m.year}年${m.month}月（${m.monthGanzhi} · ${m.branchLabel}）",
                style = MaterialTheme.typography.labelSmall,
                color = SfColors.TextSub,
            )
            Spacer(Modifier.weight(1f))
            if (fp != null) {
                SfTag(fp.reviewStatus.cn, SfColors.DeepBlue, SfColors.OtherTagBg, fontSize = 10)
                Spacer(Modifier.width(4.dp))
                SfTag("离线确定性生成", SfColors.Gold, SfColors.PianCaiBg, fontSize = 10)
            }
        }
        Spacer(Modifier.height(10.dp))

        if (fp != null) {
            if (fp.preciseAdvancedNotice.isNotBlank()) {
                SfAmberCallout(
                    text = fp.preciseAdvancedNotice,
                    title = "分项可用性提示",
                )
                Spacer(Modifier.height(10.dp))
            }

            if (m.currentPeriod != null) {
                Row(Modifier.fillMaxWidth().padding(bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("当前运步", style = MaterialTheme.typography.labelSmall, color = SfColors.TextSub)
                    Spacer(Modifier.width(8.dp))
                    Text(
                        "${m.currentPeriod.ganzhi}大运（${m.currentPeriod.startYear}–${m.currentPeriod.endYear}年）",
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.SemiBold,
                        color = SfColors.DeepBlue,
                    )
                }
                DividerLine()
                Spacer(Modifier.height(6.dp))
            }

            // A. 命理依据
            Column(Modifier.fillMaxWidth()) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text("A. 命理依据", style = MaterialTheme.typography.titleSmall, color = SfColors.TextMain, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.weight(1f))
                    Text(
                        if (basisExpanded) "收起依据 ▴" else "展开查看依据 ▾",
                        style = MaterialTheme.typography.labelSmall,
                        color = SfColors.DeepBlue,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.clip(RoundedCornerShape(4.dp)).clickable { basisExpanded = !basisExpanded }.padding(4.dp),
                    )
                }
                Spacer(Modifier.height(4.dp))
                Text(fp.basisText, style = MaterialTheme.typography.bodyMedium, color = SfColors.TextMain, lineHeight = 20.sp)
                if (basisExpanded) {
                    Spacer(Modifier.height(6.dp))
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(SfColors.OtherTagBg)
                            .padding(8.dp),
                    ) {
                        Text(
                            "命中规则依据：${fp.hitRuleIds.joinToString(" · ")}",
                            style = MaterialTheme.typography.labelSmall,
                            color = SfColors.TextSub,
                        )
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
            DividerLine()
            Spacer(Modifier.height(8.dp))

            // B. 本月主题
            Column(Modifier.fillMaxWidth()) {
                Text("B. 本月主题", style = MaterialTheme.typography.titleSmall, color = SfColors.TextMain, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(4.dp))
                Text(fp.themeText, style = MaterialTheme.typography.bodyMedium, color = SfColors.TextMain, lineHeight = 22.sp)
            }
            Spacer(Modifier.height(8.dp))
            DividerLine()
            Spacer(Modifier.height(8.dp))

            // C. 潜在矛盾
            Column(Modifier.fillMaxWidth()) {
                Text("C. 潜在矛盾", style = MaterialTheme.typography.titleSmall, color = SfColors.TextMain, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(4.dp))
                Text(fp.contradictionText, style = MaterialTheme.typography.bodyMedium, color = SfColors.TextMain, lineHeight = 22.sp)
            }
            Spacer(Modifier.height(8.dp))
            DividerLine()
            Spacer(Modifier.height(8.dp))

            // D. 企业经营观察
            Column(Modifier.fillMaxWidth()) {
                Text("D. 企业经营观察", style = MaterialTheme.typography.titleSmall, color = SfColors.TextMain, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(4.dp))
                Text(fp.businessText, style = MaterialTheme.typography.bodyMedium, color = SfColors.TextMain, lineHeight = 22.sp)
            }
            Spacer(Modifier.height(8.dp))
            DividerLine()
            Spacer(Modifier.height(8.dp))

            // E. 综合解释
            Column(Modifier.fillMaxWidth()) {
                Text("E. 综合解释", style = MaterialTheme.typography.titleSmall, color = SfColors.TextMain, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(4.dp))
                Text(fp.synthesisText, style = MaterialTheme.typography.bodyMedium, color = SfColors.TextMain, lineHeight = 22.sp)
            }

            Spacer(Modifier.height(10.dp))
            Text(
                "命理学术分析仅供参考，不构成任何投资建议。",
                style = MaterialTheme.typography.labelSmall,
                color = SfColors.TextSub,
            )
        } else {
            Text("暂无当月五段式解读数据", style = MaterialTheme.typography.bodyMedium, color = SfColors.TextSub)
        }
    }
}
