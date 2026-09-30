package com.stockfortune.app.ui.components

import androidx.compose.foundation.Canvas
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
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
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

/** 白色大圆角卡片，带微阴影与极细描边，营造悬浮景深。 */
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
            .shadow(
                elevation = 2.dp,
                shape = shape,
                ambientColor = Color(0x0D0D2743),
                spotColor = Color(0x120D2743),
            )
            .clip(shape)
            .background(SfColors.CardBg)
            .border(width = 0.8.dp, color = SfColors.CardBorder, shape = shape)
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

/** 顶部主视觉：深蓝夜空山脉 + 金月 + 东方流金品牌信息。 */
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
        // 柔和暗夜与星宿感多层微渐变遮罩
        Box(
            Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        listOf(
                            Color(0x66061325),
                            Color(0x220D2743),
                            Color(0x770D2743),
                            Color(0xB3061325),
                        )
                    )
                ),
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
                    // Logo 外层增加古铜金微光描边容器
                    Box(
                        modifier = Modifier
                            .size(54.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(Color(0x330D2743))
                            .border(1.2.dp, Brush.verticalGradient(listOf(Color(0xFFFFDF88), Color(0x66D6A84F))), RoundedCornerShape(14.dp))
                            .padding(2.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        androidx.compose.foundation.Image(
                            painter = painterResource(id = R.drawable.hero_emblem),
                            contentDescription = null,
                            modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(12.dp)),
                            contentScale = ContentScale.Fit,
                        )
                    }
                    Spacer(Modifier.width(12.dp))
                }
                Column {
                    Text(
                        text = title,
                        style = TextStyle(
                            brush = Brush.verticalGradient(SfColors.GoldGradient),
                            fontSize = 26.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 0.5.sp,
                        ),
                    )
                    Text(
                        text = subtitle,
                        color = SfColors.GoldLight,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        letterSpacing = 0.3.sp,
                    )
                    if (slogan != null) {
                        Text(
                            text = slogan,
                            color = Color(0xCCFFFFFF),
                            fontSize = 11.sp,
                            letterSpacing = 1.sp,
                            modifier = Modifier.padding(top = 2.dp),
                        )
                    }
                }
            }
            Spacer(Modifier.weight(1f))
            bottom?.invoke()
        }
    }
}

/** 首页四宫格入口（醒目原色图标与微淡底色呼吸感）。 */
@Composable
fun SfEntryTile(
    label: String,
    sub: String,
    icon: ImageVector,
    tint: Color,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val bgTint = tint.copy(alpha = 0.05f)
    val borderTint = tint.copy(alpha = 0.16f)
    Row(
        modifier = modifier
            .shadow(1.5.dp, RoundedCornerShape(18.dp), ambientColor = Color(0x080D2743), spotColor = Color(0x0E0D2743))
            .clip(RoundedCornerShape(18.dp))
            .background(SfColors.CardBg)
            .background(bgTint)
            .border(0.8.dp, borderTint, RoundedCornerShape(18.dp))
            .clickable(onClick = onClick)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(42.dp)
                .clip(RoundedCornerShape(13.dp))
                .background(tint),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(22.dp))
        }
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.titleSmall, color = SfColors.TextMain)
            Text(sub, style = MaterialTheme.typography.labelSmall, color = SfColors.TextSub, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = SfColors.OtherTag, modifier = Modifier.size(18.dp))
    }
}

/** 正财/偏财专属福袋图形资产（解决原先仅有 10dp 小圆点的单薄感）。 */
@Composable
fun FortuneBagBadge(isZhengCai: Boolean, modifier: Modifier = Modifier) {
    val bgBrush = if (isZhengCai) {
        Brush.verticalGradient(listOf(Color(0xFFFF6666), Color(0xFFED3838)))
    } else {
        Brush.verticalGradient(listOf(Color(0xFFFFBA42), Color(0xFFF39818)))
    }
    val symbolColor = if (isZhengCai) Color(0xFFDC2828) else Color(0xFFD67F05)

    Box(
        modifier = modifier
            .size(46.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(bgBrush)
            .border(1.dp, Color(0x33FFFFFF), RoundedCornerShape(14.dp)),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(modifier = Modifier.size(26.dp)) {
            val w = size.width
            val h = size.height
            val pouch = Path().apply {
                // 顶部聚气褶皱花边
                moveTo(w * 0.30f, h * 0.18f)
                cubicTo(w * 0.20f, h * 0.04f, w * 0.38f, 0f, w * 0.50f, h * 0.08f)
                cubicTo(w * 0.62f, 0f, w * 0.80f, h * 0.04f, w * 0.70f, h * 0.18f)
                // 颈部金绳束口
                lineTo(w * 0.62f, h * 0.30f)
                // 饱满右侧福身
                cubicTo(w * 0.94f, h * 0.44f, w * 0.94f, h * 0.88f, w * 0.68f, h * 0.97f)
                // 袋底弧度
                cubicTo(w * 0.58f, h * 1.01f, w * 0.42f, h * 1.01f, w * 0.32f, h * 0.97f)
                // 饱满左侧福身
                cubicTo(w * 0.06f, h * 0.88f, w * 0.06f, h * 0.44f, w * 0.38f, h * 0.30f)
                close()
            }
            drawPath(pouch, color = Color.White)

            // 金黄色束带结
            val ribbon = Path().apply {
                moveTo(w * 0.34f, h * 0.28f)
                lineTo(w * 0.66f, h * 0.28f)
                lineTo(w * 0.64f, h * 0.34f)
                lineTo(w * 0.36f, h * 0.34f)
                close()
            }
            drawPath(ribbon, color = Color(0xFFFFD56B))
        }

        Text(
            text = "¥",
            color = symbolColor,
            fontSize = 11.sp,
            fontWeight = FontWeight.Black,
            modifier = Modifier.padding(top = 4.dp),
        )
    }
}

/** 统计卡：集成福袋资产、大数排版法则与进阶微阴影。 */
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
    val isZheng = label.contains("正财")
    Row(
        modifier = modifier
            .shadow(1.5.dp, RoundedCornerShape(18.dp), ambientColor = Color(0x080D2743), spotColor = Color(0x100D2743))
            .clip(RoundedCornerShape(18.dp))
            .background(background)
            .border(0.8.dp, accent.copy(alpha = 0.22f), RoundedCornerShape(18.dp))
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        FortuneBagBadge(isZhengCai = isZheng)
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(label, style = MaterialTheme.typography.titleSmall, color = SfColors.TextMain, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.weight(1f))
                Icon(
                    Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = null,
                    tint = accent.copy(alpha = 0.7f),
                    modifier = Modifier.size(16.dp),
                )
            }
            Spacer(Modifier.height(4.dp))
            Row(verticalAlignment = Alignment.Bottom) {
                Text(value, color = accent, fontSize = 24.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.width(3.dp))
                Text(unit, color = accent, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(bottom = 2.dp))
            }
            if (hint.isNotBlank()) {
                Spacer(Modifier.height(2.dp))
                Text(
                    hint,
                    style = MaterialTheme.typography.labelSmall,
                    color = SfColors.TextSub,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

/** 榜单排名勋章（前 3 名显示金/银/铜皇冠，4 名及以后显示数字）。 */
@Composable
fun RankBadge(rank: Int, modifier: Modifier = Modifier) {
    if (rank in 1..3) {
        val crownColor = when (rank) {
            1 -> SfColors.RankGold
            2 -> SfColors.RankSilver
            else -> SfColors.RankBronze
        }
        Canvas(modifier = modifier.size(18.dp)) {
            val w = size.width
            val h = size.height
            val path = Path().apply {
                moveTo(w * 0.15f, h * 0.85f)
                lineTo(w * 0.85f, h * 0.85f)
                lineTo(w * 0.82f, h * 0.35f)
                lineTo(w * 0.64f, h * 0.58f)
                lineTo(w * 0.50f, h * 0.22f)
                lineTo(w * 0.36f, h * 0.58f)
                lineTo(w * 0.18f, h * 0.35f)
                close()
            }
            drawPath(path, color = crownColor)
            drawCircle(crownColor, radius = w * 0.06f, center = Offset(w * 0.18f, h * 0.30f))
            drawCircle(crownColor, radius = w * 0.07f, center = Offset(w * 0.50f, h * 0.18f))
            drawCircle(crownColor, radius = w * 0.06f, center = Offset(w * 0.82f, h * 0.30f))
        }
    } else {
        Text(
            text = "$rank",
            style = MaterialTheme.typography.bodyMedium,
            color = SfColors.TextSub,
            textAlign = TextAlign.Center,
            modifier = modifier,
        )
    }
}

/** 温暖琥珀色提示卡（对齐效果图中的各类温馨提示与要点卡）。 */
@Composable
fun SfAmberCallout(
    text: String,
    modifier: Modifier = Modifier,
    title: String? = null,
    icon: ImageVector = Icons.Filled.Lightbulb,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(SfColors.HintAmberBg)
            .border(0.8.dp, SfColors.HintAmberBorder, RoundedCornerShape(16.dp))
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Box(
            modifier = Modifier
                .size(28.dp)
                .clip(CircleShape)
                .background(Color(0x1AF6B545)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, contentDescription = null, tint = SfColors.HintAmberText, modifier = Modifier.size(16.dp))
        }
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            if (title != null) {
                Text(title, style = MaterialTheme.typography.titleSmall, color = SfColors.HintAmberText, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(2.dp))
            }
            Text(text, style = MaterialTheme.typography.bodySmall, color = SfColors.HintAmberText, lineHeight = 18.sp)
        }
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
