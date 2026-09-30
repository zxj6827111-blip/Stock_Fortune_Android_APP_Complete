package com.stockfortune.app.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.stockfortune.app.domain.model.TenGod
import com.stockfortune.app.domain.model.WealthType

/** 配色取自 docs/02_UI_DESIGN_SYSTEM.md 与效果图实测。 */
object SfColors {
    val DeepBlue = Color(0xFF123A57)
    val NavyDark = Color(0xFF0D2743)
    val Gold = Color(0xFFD6A84F)
    val GoldLight = Color(0xFFF2C56B)
    val PageBg = Color(0xFFF6F8FB)
    val CardBg = Color.White
    val TextMain = Color(0xFF1C2740)
    val TextSub = Color(0xFF6B7A90)
    val ZhengCai = Color(0xFFF45B5B)
    val ZhengCaiBg = Color(0xFFFDECEC)
    val PianCai = Color(0xFFF6B545)
    val PianCaiBg = Color(0xFFFFF6E6)
    val OtherTag = Color(0xFFC7CEDA)
    val OtherTagBg = Color(0xFFEFF2F7)
    val TradeGreen = Color(0xFF2FA36B)
    val Divider = Color(0xFFEDF0F5)
    val CardBorder = Color(0xFFE8EDF5)
    val CardBorderSubtle = Color(0x0F0D2743)

    /** 提示与徽标色 */
    val HintAmberBg = Color(0xFFFFF9EE)
    val HintAmberBorder = Color(0xFFFFE8BD)
    val HintAmberText = Color(0xFF9E6514)

    /** 排名冠亚季军徽标色 */
    val RankGold = Color(0xFFF5A623)
    val RankSilver = Color(0xFF9EABB8)
    val RankBronze = Color(0xFFCD7F32)

    /** 磁贴组件（如十神筛选）选中与未选中态 */
    val TileUnselectedBg = Color(0xFFF4F7FC)
    val TileSelectedBg = Color(0xFFEAF2FD)

    /** 渐变色 */
    val GoldGradient = listOf(Color(0xFFFFEEB8), Color(0xFFE8BD65), Color(0xFFD6A84F))
    val GoldButtonGradient = listOf(Color(0xFFF5D38A), Color(0xFFD6A84F))

    /** 首页四宫格底色 */
    val EntryBlue = Color(0xFF3B82F6)
    val EntryOrange = Color(0xFFF6B545)
    val EntryTeal = Color(0xFF22B8A6)
    val EntryGold = Color(0xFFD6A84F)
}

object SfDimens {
    val CardRadius: Dp = 22.dp
    val TagRadius: Dp = 999.dp
    val ButtonRadius: Dp = 16.dp
    val PagePadding: Dp = 16.dp
    val CardPadding: Dp = 16.dp
    val CardGap: Dp = 12.dp
    val HeroHeight: Dp = 190.dp
}

private val SfTypography = Typography(
    headlineMedium = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Bold, fontSize = 24.sp, lineHeight = 32.sp),
    titleLarge = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.SemiBold, fontSize = 19.sp, lineHeight = 26.sp),
    titleMedium = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.SemiBold, fontSize = 16.sp, lineHeight = 22.sp),
    titleSmall = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Medium, fontSize = 14.sp, lineHeight = 20.sp),
    bodyLarge = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Normal, fontSize = 15.sp, lineHeight = 22.sp),
    bodyMedium = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Normal, fontSize = 13.sp, lineHeight = 19.sp),
    bodySmall = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Normal, fontSize = 12.sp, lineHeight = 17.sp),
    labelLarge = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, lineHeight = 19.sp),
    labelMedium = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Medium, fontSize = 12.sp, lineHeight = 16.sp),
    labelSmall = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Medium, fontSize = 11.sp, lineHeight = 15.sp),
)

@Composable
fun StockFortuneTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = lightColorScheme(
            primary = SfColors.DeepBlue,
            onPrimary = Color.White,
            secondary = SfColors.Gold,
            onSecondary = SfColors.TextMain,
            background = SfColors.PageBg,
            onBackground = SfColors.TextMain,
            surface = SfColors.CardBg,
            onSurface = SfColors.TextMain,
            onSurfaceVariant = SfColors.TextSub,
            outline = SfColors.Divider,
        ),
        typography = SfTypography,
        shapes = MaterialTheme.shapes.copy(
            small = RoundedCornerShape(12.dp),
            medium = RoundedCornerShape(SfDimens.CardRadius),
            large = RoundedCornerShape(26.dp),
        ),
        content = content,
    )
}

fun wealthColor(type: WealthType): Color = when (type) {
    WealthType.ZHENG_CAI -> SfColors.ZhengCai
    WealthType.PIAN_CAI -> SfColors.PianCai
    WealthType.OTHER -> SfColors.OtherTag
    WealthType.NONE -> SfColors.OtherTag
}

fun wealthBg(type: WealthType): Color = when (type) {
    WealthType.ZHENG_CAI -> SfColors.ZhengCaiBg
    WealthType.PIAN_CAI -> SfColors.PianCaiBg
    else -> SfColors.OtherTagBg
}

/** 十神标签配色：财星用状态色，其余十神按五行分组用浅底深字。 */
fun tenGodColor(god: TenGod): Pair<Color, Color> = when (god) {
    TenGod.ZHENG_CAI -> SfColors.ZhengCai to SfColors.ZhengCaiBg
    TenGod.PIAN_CAI -> SfColors.PianCai to SfColors.PianCaiBg
    TenGod.ZHENG_GUAN, TenGod.QI_SHA -> Color(0xFF4C7DF0) to Color(0xFFEBF1FE)
    TenGod.SHI_SHEN, TenGod.SHANG_GUAN -> Color(0xFF22B8A6) to Color(0xFFE7F7F5)
    TenGod.ZHENG_YIN, TenGod.PIAN_YIN -> Color(0xFF8B5CF6) to Color(0xFFF1ECFD)
    TenGod.BI_JIAN, TenGod.JIE_CAI -> Color(0xFF6B7A90) to SfColors.OtherTagBg
}
