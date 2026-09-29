package com.stockfortune.app.data.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "stock", indices = [
    Index(value = ["code"], unique = true),
    Index("name"), Index("symbol"), Index("listing_date"),
])
data class StockEntity(
    @PrimaryKey val id: Long,
    val code: String,
    val symbol: String,
    val name: String,
    val exchange: String,
    val board: String,
    @ColumnInfo(name = "listing_date") val listingDate: String,
    @ColumnInfo(name = "first_open") val firstOpen: Double?,
    @ColumnInfo(name = "first_close") val firstClose: Double?,
    @ColumnInfo(name = "first_change") val firstChange: Double?,
    @ColumnInfo(name = "first_day_flag") val firstDayFlag: String,
    val industry: String,
    @ColumnInfo(name = "industry_full") val industryFull: String,
    @ColumnInfo(name = "stock_nature") val stockNature: String,
)

@Entity(tableName = "stock_bazi", indices = [
    Index("day_pillar"), Index("year_pillar"), Index("month_pillar"),
])
data class StockBaziEntity(
    @PrimaryKey @ColumnInfo(name = "stock_id") val stockId: Long,
    @ColumnInfo(name = "full_bazi") val fullBazi: String,
    @ColumnInfo(name = "year_pillar") val yearPillar: String,
    @ColumnInfo(name = "month_pillar") val monthPillar: String,
    @ColumnInfo(name = "day_pillar") val dayPillar: String,
    @ColumnInfo(name = "hour_pillar") val hourPillar: String,
    @ColumnInfo(name = "year_stem") val yearStem: String,
    @ColumnInfo(name = "year_branch") val yearBranch: String,
    @ColumnInfo(name = "month_stem") val monthStem: String,
    @ColumnInfo(name = "month_branch") val monthBranch: String,
    @ColumnInfo(name = "day_stem") val dayStem: String,
    @ColumnInfo(name = "day_branch") val dayBranch: String,
    @ColumnInfo(name = "hour_stem") val hourStem: String,
    @ColumnInfo(name = "hour_branch") val hourBranch: String,
    @ColumnInfo(name = "day_master_element") val dayMasterElement: String,
    @ColumnInfo(name = "month_season_element") val monthSeasonElement: String,
    @ColumnInfo(name = "na_yin") val naYin: String,
)

@Entity(tableName = "stock_hidden_ten_god", primaryKeys = ["stock_id", "pillar", "hidden_stem"], indices = [
    Index(value = ["ten_god", "stock_id"]),
])
data class StockHiddenTenGodEntity(
    @ColumnInfo(name = "stock_id") val stockId: Long,
    val pillar: String,
    val branch: String,
    @ColumnInfo(name = "hidden_stem") val hiddenStem: String,
    @ColumnInfo(name = "ten_god") val tenGod: String,
    val rank: String,
)

@Entity(tableName = "ganzhi_calendar")
data class GanzhiCalendarEntity(
    @PrimaryKey val date: String,
    @ColumnInfo(name = "year_ganzhi") val yearGanzhi: String,
    @ColumnInfo(name = "month_ganzhi") val monthGanzhi: String,
    @ColumnInfo(name = "day_ganzhi") val dayGanzhi: String,
    @ColumnInfo(name = "year_stem") val yearStem: String,
    @ColumnInfo(name = "year_branch") val yearBranch: String,
    @ColumnInfo(name = "month_stem") val monthStem: String,
    @ColumnInfo(name = "month_branch") val monthBranch: String,
    @ColumnInfo(name = "day_stem") val dayStem: String,
    @ColumnInfo(name = "day_branch") val dayBranch: String,
    @ColumnInfo(name = "month_branch_label") val monthBranchLabel: String,
    @ColumnInfo(name = "solar_term") val solarTerm: String?,
)

@Entity(tableName = "trade_calendar")
data class TradeCalendarEntity(
    @PrimaryKey val date: String,
    @ColumnInfo(name = "is_trade_day") val isTradeDay: Int,
    val weekday: Int,
    @ColumnInfo(name = "closed_reason") val closedReason: String?,
    val confidence: String,
)

@Entity(tableName = "scan_cache", primaryKeys = ["stock_id", "date"])
data class ScanCacheEntity(
    @ColumnInfo(name = "stock_id") val stockId: Long,
    val date: String,
    @ColumnInfo(name = "year_ten_god") val yearTenGod: String,
    @ColumnInfo(name = "month_ten_god") val monthTenGod: String,
    @ColumnInfo(name = "day_ten_god") val dayTenGod: String,
    @ColumnInfo(name = "wealth_type") val wealthType: String,
    @ColumnInfo(name = "is_trade_day") val isTradeDay: Int,
    @ColumnInfo(name = "computed_at") val computedAt: Long,
)

@Entity(tableName = "favorite")
data class FavoriteEntity(
    @PrimaryKey @ColumnInfo(name = "stock_id") val stockId: Long,
    @ColumnInfo(name = "added_at") val addedAt: Long,
)

@Entity(tableName = "app_meta")
data class AppMetaEntity(@PrimaryKey val key: String, val value: String)

/** 扫描用的扁平投影：股票 + 日主 */
data class StockWithDayMaster(
    @ColumnInfo(name = "id") val stockId: Long,
    val code: String,
    val symbol: String,
    val name: String,
    val industry: String,
    @ColumnInfo(name = "board") val board: String,
    @ColumnInfo(name = "day_stem") val dayStem: String,
)

/** 搜索结果投影 */
data class StockSearchHit(
    @ColumnInfo(name = "id") val stockId: Long,
    val code: String,
    val symbol: String,
    val name: String,
    @ColumnInfo(name = "listing_date") val listingDate: String,
    val board: String,
)
