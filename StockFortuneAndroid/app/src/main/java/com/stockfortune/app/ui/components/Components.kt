package com.stockfortune.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.stockfortune.app.R
import com.stockfortune.app.domain.model.WealthType
import com.stockfortune.app.ui.theme.SfColors
import com.stockfortune.app.ui.theme.SfDimens
import com.stockfortune.app.ui.theme.tenGodColor
import com.stockfortune.app.ui.theme.wealthBg
import com.stockfortune.app.ui.theme.wealthColor
import com.stockfortune.app.domain.model.TenGod

/** 白色大圆角卡片。 */
@Composable
fun SfCard(
    modifier: Modifier = Modifier,
    padding: androidx.compose.foundation.layout.PaddingValues = androidx.compose.foundation.layout.PaddingValues(SfDimens.CardPadding),
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScopeAlias.() -> Unit,
) {
    val shape = RoundedCornerShape(SfDimens.CardRadius)
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .background(SfColors.CardBg)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(padding),
        content = content,
    )
}

typealias ColumnScopeAlias = androidx.compose.foundation.layout.ColumnScope

@Composable
fun SfSectionTitle(title: String, modifier: Modifier = Modifier, trailing: @Composable (() -> Unit)? = null) {
    Row(modifier = modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.width(4.dp).height(16.dp).clip(RoundedCornerShape(2.dp)).background(SfColors.Gold))
        Spacer(Modifier.width(8.dp))
        Text(title, style = MaterialTheme.typography.titleMedium, color = SfColors.TextMain)
        Spacer(Modifier.weight(1f))
        trailing?.invoke()
    }
}

@Composable
fun SfTag(
    text: String,
    foreground: Color,
    background: Color,
    modifier: Modifier = Modifier,
    fontSize: Int = 12,
    bold: Boolean = true,
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(SfDimens.TagRadius))
            .background(background)
            .padding(horizontal = 10.dp, vertical = 4.dp),
    ) {
        Text(text, color = foreground, fontSize = fontSize.sp, fontWeight = if (bold) FontWeight.SemiBold else FontWeight.Medium)
    }
}

@Composable
fun WealthTag(wealth: WealthType, modifier: Modifier = Modifier, hidden: Boolean = false) {
    val label = wealth.cn + if (hidden && wealth.isWealth) "·藏" else ""
    SfTag(label, wealthColor(wealth), wealthBg(wealth), modifier)
}

@Composable
fun TenGodTag(god: TenGod, modifier: Modifier = Modifier) {
    val (fg, bg) = tenGodColor(god)
    SfTag(god.cn, fg, bg, modifier)
}

/** 顶部主视觉：深蓝夜空山脉 + 金月 + 品牌信息。 */
@Composable
fun SfHero(
    title: String,
    subtitle: String,
    modifier: Modifier = Modifier,
    slogan: String? = null,
    showLogo: Boolean = true,
    height: androidx.compose.ui.unit.Dp = SfDimens.HeroHeight,
    leading: @Composable (() -> Unit)? = null,
    trailing: @Composable (() -> Unit)? = null,
    bottom: @Composable (() -> Unit)? = null,
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(height)
            .clip(RoundedCornerShape(bottomStart = 26.dp, bottomEnd = 26.dp))
            .background(SfColors.DeepBlue),
    ) {
        androidx.compose.foundation.Image(
            painter = painterResource(id = R.drawable.hero_night),
            contentDescription = null,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop,
        )
        Box(
            Modifier
                .fillMaxSize()
                .background(Brush.verticalGradient(listOf(Color(0x660D2743), Color(0x220D2743), Color(0x990D2743)))),
        )
        Column(Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                leading?.invoke()
                Spacer(Modifier.weight(1f))
                trailing?.invoke()
            }
            Spacer(Modifier.height(6.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (showLogo) {
                    // Hero 用裁掉文字后的徽标：整张 brand_logo 含"股运通"字样，会与标题重复
                    androidx.compose.foundation.Image(
                        painter = painterResource(id = R.drawable.hero_emblem),
                        contentDescription = null,
                        modifier = Modifier.size(54.dp).clip(RoundedCornerShape(14.dp)),
                        contentScale = ContentScale.Fit,
                    )
                    Spacer(Modifier.width(12.dp))
                }
                Column {
                    Text(title, color = Color.White, fontSize = 26.sp, fontWeight = FontWeight.Bold)
                    Text(subtitle, color = SfColors.GoldLight, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                    if (slogan != null) {
                        Text(slogan, color = Color(0xB3FFFFFF), fontSize = 11.sp, modifier = Modifier.padding(top = 2.dp))
                    }
                }
            }
            Spacer(Modifier.weight(1f))
            bottom?.invoke()
        }
    }
}

/** 首页四宫格入口。 */
@Composable
fun SfEntryTile(
    label: String,
    sub: String,
    icon: ImageVector,
    tint: Color,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(18.dp))
            .background(SfColors.CardBg)
            .clickable(onClick = onClick)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(40.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(tint.copy(alpha = 0.16f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(22.dp))
        }
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.titleSmall, color = SfColors.TextMain)
            Text(sub, style = MaterialTheme.typography.labelSmall, color = SfColors.TextSub, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = SfColors.OtherTag, modifier = Modifier.size(18.dp))
    }
}

/** 统计卡：数字 + 单位 + 说明。 */
@Composable
fun SfStatCard(
    label: String,
    value: String,
    unit: String,
    hint: String,
    accent: Color,
    background: Color,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(18.dp))
            .background(background)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(26.dp).clip(RoundedCornerShape(9.dp)).background(accent.copy(alpha = 0.18f)), contentAlignment = Alignment.Center) {
                Box(Modifier.size(10.dp).clip(CircleShape).background(accent))
            }
            Spacer(Modifier.width(8.dp))
            Text(label, style = MaterialTheme.typography.titleSmall, color = SfColors.TextMain)
        }
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.Bottom) {
            Text(value, color = accent, fontSize = 26.sp, fontWeight = FontWeight.Bold)
            Text(unit, color = SfColors.TextSub, fontSize = 12.sp, modifier = Modifier.padding(start = 3.dp, bottom = 4.dp))
        }
        Text(hint, style = MaterialTheme.typography.labelSmall, color = SfColors.TextSub)
    }
}

/** 分段切换（全部 / 正财 / 偏财）。 */
@Composable
fun SfSegmented(options: List<String>, selected: Int, onSelect: (Int) -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(SfDimens.TagRadius))
            .background(SfColors.OtherTagBg)
            .padding(3.dp),
        horizontalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        options.forEachIndexed { i, label ->
            val active = i == selected
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(SfDimens.TagRadius))
                    .background(if (active) SfColors.DeepBlue else Color.Transparent)
                    .clickable { onSelect(i) }
                    .padding(horizontal = 18.dp, vertical = 7.dp),
            ) {
                Text(
                    label,
                    color = if (active) Color.White else SfColors.TextSub,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = if (active) FontWeight.SemiBold else FontWeight.Medium,
                )
            }
        }
    }
}

/** 信息行：左标签右值。 */
@Composable
fun SfInfoRow(label: String, value: String, modifier: Modifier = Modifier, valueColor: Color = SfColors.TextMain) {
    Row(modifier = modifier.fillMaxWidth().padding(vertical = 5.dp), verticalAlignment = Alignment.Top) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = SfColors.TextSub)
        Spacer(Modifier.width(12.dp))
        Text(value, style = MaterialTheme.typography.bodyMedium, color = valueColor, modifier = Modifier.weight(1f), textAlign = TextAlign.End)
    }
}

@Composable
fun SfGoldButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true, icon: ImageVector? = null) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(SfDimens.ButtonRadius))
            .background(
                if (enabled) Brush.horizontalGradient(listOf(SfColors.GoldLight, SfColors.Gold))
                else Brush.horizontalGradient(listOf(SfColors.OtherTagBg, SfColors.OtherTagBg))
            )
            .clickable(enabled = enabled, onClick = onClick)
            .padding(vertical = 14.dp),
        contentAlignment = Alignment.Center,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            icon?.let {
                Icon(it, null, tint = if (enabled) Color.White else SfColors.TextSub, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
            }
            Text(text, color = if (enabled) Color.White else SfColors.TextSub, fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
        }
    }
}

@Composable
fun SfDisclaimer(text: String, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(SfColors.OtherTagBg)
            .padding(12.dp),
    ) {
        Text("ⓘ", color = SfColors.DeepBlue, fontWeight = FontWeight.Bold)
        Spacer(Modifier.width(8.dp))
        Text(text, style = MaterialTheme.typography.bodySmall, color = SfColors.TextSub, modifier = Modifier.weight(1f))
    }
}

@Composable
fun SfEmptyState(title: String, hint: String, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxWidth().padding(vertical = 36.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(Modifier.size(56.dp).clip(CircleShape).background(SfColors.OtherTagBg), contentAlignment = Alignment.Center) {
            Icon(Icons.Default.Search, null, tint = SfColors.OtherTag, modifier = Modifier.size(26.dp))
        }
        Spacer(Modifier.height(12.dp))
        Text(title, style = MaterialTheme.typography.titleSmall, color = SfColors.TextMain)
        Text(hint, style = MaterialTheme.typography.bodySmall, color = SfColors.TextSub)
    }
}

@Composable
fun SfLoading(modifier: Modifier = Modifier) {
    Box(modifier.fillMaxWidth().height(160.dp), contentAlignment = Alignment.Center) {
        androidx.compose.material3.CircularProgressIndicator(color = SfColors.Gold, modifier = Modifier.size(28.dp))
    }
}

/** 表格行通用容器（列表页共用，保证行高与分隔一致）。 */
@Composable
fun SfRowContainer(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable RowScopeAlias.() -> Unit,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        content = content,
    )
}

typealias RowScopeAlias = androidx.compose.foundation.layout.RowScope

@Composable
fun SfTableHeader(columns: List<Pair<String, Float>>, modifier: Modifier = Modifier) {
    Row(modifier = modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp)) {
        columns.forEach { (label, weight) ->
            Text(label, style = MaterialTheme.typography.labelSmall, color = SfColors.TextSub, modifier = Modifier.weight(weight))
        }
    }
}

@Composable
fun SfAspectBox(modifier: Modifier = Modifier, ratio: Float = 1f, content: @Composable () -> Unit) {
    Box(modifier = modifier.aspectRatio(ratio)) { content() }
}
