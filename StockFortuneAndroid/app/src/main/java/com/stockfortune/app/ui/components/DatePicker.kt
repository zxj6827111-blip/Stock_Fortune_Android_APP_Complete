package com.stockfortune.app.ui.components

import androidx.compose.foundation.background
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
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.stockfortune.app.ui.theme.SfColors
import java.time.LocalDate
import java.time.YearMonth

/**
 * 月历式日期选择对话框。筛选页与择日页共用，避免两套交互不一致。
 * 只允许选 [minDate, maxDate] 区间内的日期（与预置干支/交易日历覆盖范围一致）。
 */
@Composable
fun SfDatePickerDialog(
    title: String,
    initial: LocalDate,
    minDate: LocalDate = LocalDate.of(1990, 12, 1),
    maxDate: LocalDate = LocalDate.of(2035, 12, 31),
    onDismiss: () -> Unit,
    onPick: (LocalDate) -> Unit,
) {
    val clamped = initial.coerceIn(minDate, maxDate)
    var picked by remember { mutableStateOf(clamped) }
    var shownMonth by remember { mutableStateOf(YearMonth.from(clamped)) }
    // 翻月不许越过可选区间，否则用户会停在一个月里全灰的月份上无从返回。
    val loMonth = YearMonth.from(minDate)
    val hiMonth = YearMonth.from(maxDate)

    Dialog(onDismissRequest = onDismiss) {
        Surface(shape = RoundedCornerShape(22.dp), color = SfColors.CardBg) {
            Column(Modifier.padding(18.dp)) {
                Text(title, style = MaterialTheme.typography.titleMedium, color = SfColors.TextMain)
                Spacer(Modifier.height(12.dp))

                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    NavArrow(
                        Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                        "上一月",
                        enabled = shownMonth.isAfter(loMonth),
                    ) { shownMonth = shownMonth.minusMonths(1) }
                    Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                        Text(
                            "${shownMonth.year}年${shownMonth.monthValue}月",
                            style = MaterialTheme.typography.titleSmall,
                            color = SfColors.TextMain,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                    NavArrow(
                        Icons.AutoMirrored.Filled.KeyboardArrowRight,
                        "下一月",
                        enabled = shownMonth.isBefore(hiMonth),
                    ) { shownMonth = shownMonth.plusMonths(1) }
                }
                Spacer(Modifier.height(10.dp))

                Row(Modifier.fillMaxWidth()) {
                    listOf("一", "二", "三", "四", "五", "六", "日").forEach {
                        Text(
                            it, modifier = Modifier.weight(1f),
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                            style = MaterialTheme.typography.labelSmall, color = SfColors.TextSub,
                        )
                    }
                }
                Spacer(Modifier.height(6.dp))

                val first = shownMonth.atDay(1)
                val lead = first.dayOfWeek.value - 1
                val days = shownMonth.lengthOfMonth()
                val cells = List(lead) { null } + (1..days).map { shownMonth.atDay(it) }
                cells.chunked(7).forEach { week ->
                    Row(Modifier.fillMaxWidth()) {
                        week.forEach { day ->
                            Box(Modifier.weight(1f).height(40.dp), contentAlignment = Alignment.Center) {
                                if (day != null) {
                                    val enabled = !day.isBefore(minDate) && !day.isAfter(maxDate)
                                    val selected = day == picked
                                    Box(
                                        Modifier
                                            .size(34.dp)
                                            .clip(CircleShape)
                                            .background(if (selected) SfColors.DeepBlue else Color.Transparent)
                                            .clickable(enabled = enabled) { picked = day },
                                        contentAlignment = Alignment.Center,
                                    ) {
                                        Text(
                                            "${day.dayOfMonth}",
                                            style = MaterialTheme.typography.labelMedium,
                                            color = when {
                                                selected -> Color.White
                                                !enabled -> SfColors.OtherTag
                                                day.dayOfWeek.value >= 6 -> SfColors.TextSub
                                                else -> SfColors.TextMain
                                            },
                                            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                                        )
                                    }
                                }
                            }
                        }
                        repeat(7 - week.size) { Box(Modifier.weight(1f).height(40.dp)) }
                    }
                }

                Spacer(Modifier.height(10.dp))
                Text(
                    "已选 ${picked.year}年${picked.monthValue}月${picked.dayOfMonth}日",
                    style = MaterialTheme.typography.labelSmall, color = SfColors.TextSub,
                )
                Spacer(Modifier.height(12.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Box(
                        Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(14.dp))
                            .background(SfColors.OtherTagBg)
                            .clickable(onClick = onDismiss)
                            .padding(vertical = 12.dp),
                        contentAlignment = Alignment.Center,
                    ) { Text("取消", color = SfColors.TextSub, style = MaterialTheme.typography.labelLarge) }
                    Box(
                        Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(14.dp))
                            .background(SfColors.DeepBlue)
                            .clickable { onPick(picked) }
                            .padding(vertical = 12.dp),
                        contentAlignment = Alignment.Center,
                    ) { Text("确定", color = Color.White, style = MaterialTheme.typography.labelLarge) }
                }
            }
        }
    }
}

@Composable
private fun NavArrow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    desc: String,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    Box(
        Modifier
            .size(36.dp)
            .clip(CircleShape)
            .background(SfColors.OtherTagBg)
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            icon,
            contentDescription = desc,
            tint = if (enabled) SfColors.DeepBlue else SfColors.OtherTag,
            modifier = Modifier.size(20.dp),
        )
    }
}
