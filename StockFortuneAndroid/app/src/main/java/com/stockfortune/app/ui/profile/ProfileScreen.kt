package com.stockfortune.app.ui.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import com.stockfortune.app.R
import com.stockfortune.app.domain.model.StockInfo
import com.stockfortune.app.ui.common.sfContainer
import com.stockfortune.app.ui.common.sfViewModel
import com.stockfortune.app.ui.components.SfCard
import com.stockfortune.app.ui.components.SfDisclaimer
import com.stockfortune.app.ui.components.SfGoldButton
import com.stockfortune.app.ui.components.SfHero
import com.stockfortune.app.ui.components.SfInfoRow
import com.stockfortune.app.ui.components.SfSectionTitle
import com.stockfortune.app.ui.navigation.Routes
import com.stockfortune.app.ui.theme.SfColors
import com.stockfortune.app.ui.theme.SfDimens
import com.stockfortune.app.ui.vm.ProfileViewModel

/** 我的：离线数据版本、缓存管理、功能入口、收藏列表。 */
@Composable
fun ProfileScreen(nav: NavHostController) {
    val vm: ProfileViewModel = sfViewModel { ProfileViewModel.Factory(sfContainer()) }
    val state by vm.state.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) { vm.refresh() }

    Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        SfHero(
            title = stringResource(R.string.nav_mine),
            subtitle = stringResource(R.string.app_slogan),
            height = 120.dp,
            showLogo = false,
        )

        Spacer(Modifier.height(SfDimens.CardGap))

        // 数据与离线
        SfCard(modifier = Modifier.padding(horizontal = SfDimens.PagePadding)) {
            SfSectionTitle(title = stringResource(R.string.mine_data_group))
            Spacer(Modifier.height(6.dp))
            SfInfoRow(label = stringResource(R.string.mine_stock_count), value = "${state.stockCount} ${stringResource(R.string.unit_only)}")
            SfInfoRow(
                label = stringResource(R.string.mine_data_version),
                value = state.meta["data_version"]?.let { it + stringResource(R.string.snapshot_suffix) } ?: "—",
            )
            SfInfoRow(
                label = stringResource(R.string.mine_calendar_version),
                value = state.meta["calendar_version"] ?: "—",
            )
            SfInfoRow(
                label = stringResource(R.string.mine_rule),
                value = state.meta["rule_version"] ?: "—",
            )
            SfInfoRow(label = stringResource(R.string.mine_cache), value = stringResource(R.string.count_rows, state.cacheCount))
            SfInfoRow(label = stringResource(R.string.mine_run_mode), value = stringResource(R.string.mine_offline))
            Spacer(Modifier.height(10.dp))
            SfGoldButton(
                text = stringResource(R.string.mine_clear_cache),
                onClick = { vm.clearCache() },
                icon = Icons.Filled.Delete,
            )
        }

        Spacer(Modifier.height(SfDimens.CardGap))

        // 功能入口
        SfCard(
            modifier = Modifier.padding(horizontal = SfDimens.PagePadding),
            padding = PaddingValues(vertical = 6.dp),
        ) {
            EntryRow(stringResource(R.string.mine_algorithm)) { nav.navigate(Routes.ALGORITHM) }
            EntryRow(stringResource(R.string.entry_daily_scan)) { nav.navigate(Routes.scanner()) }
            EntryRow(stringResource(R.string.filter_title)) { nav.navigate(Routes.FILTER) }
            EntryRow(stringResource(R.string.entry_date_select)) { nav.navigate(Routes.dateSelect()) }
        }

        Spacer(Modifier.height(SfDimens.CardGap))

        // 我的收藏
        SfCard(
            modifier = Modifier.padding(horizontal = SfDimens.PagePadding),
            padding = PaddingValues(vertical = 6.dp),
        ) {
            SfSectionTitle(
                title = stringResource(R.string.favorites),
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                trailing = {
                    Text(
                        stringResource(R.string.count_stocks, state.favorites.size),
                        style = MaterialTheme.typography.labelMedium,
                        color = SfColors.TextSub,
                    )
                },
            )
            if (state.favorites.isEmpty()) {
                Text(
                    stringResource(R.string.favorites_empty),
                    style = MaterialTheme.typography.bodySmall,
                    color = SfColors.TextSub,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 14.dp),
                )
            } else {
                state.favorites.forEach { FavoriteRow(it) { nav.navigate(Routes.stock(it.code)) } }
            }
        }

        Spacer(Modifier.height(SfDimens.CardGap))
        SfDisclaimer(
            stringResource(R.string.disclaimer),
            modifier = Modifier.padding(horizontal = SfDimens.PagePadding),
        )
        Spacer(Modifier.height(SfDimens.CardGap))
    }
}

@Composable
private fun EntryRow(label: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(6.dp)
                .clip(CircleShape)
                .background(SfColors.Gold),
        )
        Spacer(Modifier.width(10.dp))
        Text(label, style = MaterialTheme.typography.bodyLarge, color = SfColors.TextMain)
        Spacer(Modifier.weight(1f))
        Icon(
            Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = SfColors.OtherTag,
            modifier = Modifier.size(18.dp),
        )
    }
}

@Composable
private fun FavoriteRow(stock: StockInfo, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(stock.name, style = MaterialTheme.typography.titleSmall, color = SfColors.TextMain)
                Spacer(Modifier.width(8.dp))
                Text(stock.symbol, style = MaterialTheme.typography.labelSmall, color = SfColors.TextSub)
            }
            Text(stringResource(R.string.listing_date_prefix, stock.listingDate), style = MaterialTheme.typography.labelSmall, color = SfColors.TextSub)
        }
        Icon(
            Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = SfColors.OtherTag,
            modifier = Modifier.size(18.dp),
        )
    }
}
