package com.stockfortune.app.data.dao

import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.stockfortune.app.data.entity.AppMetaEntity
import com.stockfortune.app.data.entity.FavoriteEntity
import com.stockfortune.app.data.entity.GanzhiCalendarEntity
import com.stockfortune.app.data.entity.ScanCacheEntity
import com.stockfortune.app.data.entity.StockBaziEntity
import com.stockfortune.app.data.entity.StockEntity
import com.stockfortune.app.data.entity.StockSearchHit
import com.stockfortune.app.data.entity.StockWithDayMaster
import com.stockfortune.app.data.entity.TradeCalendarEntity

@Dao
interface StockDao {
    @Query(
        """SELECT s.id, s.code, s.symbol, s.name, s.listing_date, s.board FROM stock s
           WHERE s.code LIKE :pattern ESCAPE '\' OR s.symbol LIKE :pattern ESCAPE '\' OR s.name LIKE :pattern ESCAPE '\'
           ORDER BY CASE WHEN s.symbol = :exact THEN 0 WHEN s.symbol LIKE :exact || '%' ESCAPE '\' THEN 1
                         WHEN s.name LIKE :exact || '%' ESCAPE '\' THEN 2 ELSE 3 END, s.symbol
           LIMIT 60"""
    )
    suspend fun search(pattern: String, exact: String): List<StockSearchHit>

    @Query("SELECT * FROM stock WHERE code = :code LIMIT 1")
    suspend fun findByCode(code: String): StockEntity?

    @Query("SELECT * FROM stock WHERE symbol = :symbol ORDER BY symbol LIMIT 1")
    suspend fun findBySymbol(symbol: String): StockEntity?

    @Query("SELECT * FROM stock WHERE id = :id LIMIT 1")
    suspend fun findById(id: Long): StockEntity?

    @Query("SELECT COUNT(*) FROM stock")
    suspend fun count(): Int
}

@Dao
interface BaziDao {
    @Query("SELECT * FROM stock_bazi WHERE stock_id = :stockId LIMIT 1")
    suspend fun findByStockId(stockId: Long): StockBaziEntity?

    /**
     * 按日主集合取股票。流日财星只由"日主 × 流日干支"决定，10 个日主即可判定全市场，
     * 因此扫描/筛选只需取命中的那几类日主，避免物化 5395 行（实测从 3.3s 降到 0.3s 内）。
     */
    @Query(
        """SELECT s.id, s.code, s.symbol, s.name, s.industry, s.board, b.day_stem
           FROM stock s JOIN stock_bazi b ON b.stock_id = s.id
           WHERE b.day_stem IN (:stems)"""
    )
    suspend fun byDayStems(stems: List<String>): List<StockWithDayMaster>

    /** 各日主的股票数量（10 行），用于首页概览这类只要计数的场景。 */
    @Query("SELECT day_stem, COUNT(*) AS n FROM stock_bazi GROUP BY day_stem")
    suspend fun countByDayStem(): List<DayStemCount>
}

data class StockHiddenHit(
    @androidx.room.ColumnInfo(name = "stock_id") val stockId: Long,
    @androidx.room.ColumnInfo(name = "ten_god") val tenGod: String,
)

data class DayStemCount(
    @androidx.room.ColumnInfo(name = "day_stem") val dayStem: String,
    @androidx.room.ColumnInfo(name = "n") val n: Int,
)

@Dao
interface CalendarDao {
    @Query("SELECT * FROM ganzhi_calendar WHERE date = :date LIMIT 1")
    suspend fun ganzhi(date: String): GanzhiCalendarEntity?

    @Query("SELECT * FROM ganzhi_calendar WHERE date BETWEEN :start AND :end ORDER BY date")
    suspend fun ganzhiRange(start: String, end: String): List<GanzhiCalendarEntity>

    @Query("SELECT * FROM trade_calendar WHERE date = :date LIMIT 1")
    suspend fun tradeDay(date: String): TradeCalendarEntity?

    @Query("SELECT * FROM trade_calendar WHERE date BETWEEN :start AND :end ORDER BY date")
    suspend fun tradeRange(start: String, end: String): List<TradeCalendarEntity>

    @Query("SELECT MIN(date) FROM ganzhi_calendar")
    suspend fun minDate(): String?

    @Query("SELECT MAX(date) FROM ganzhi_calendar")
    suspend fun maxDate(): String?

    /** 最近交易日：先取 >= 目标日的第一个交易日（向前回退），用于"今日概览"默认日期 */
    @Query(
        """SELECT date FROM trade_calendar WHERE is_trade_day = 1 AND date <= :date
           ORDER BY date DESC LIMIT 1"""
    )
    suspend fun lastTradeDayOnOrBefore(date: String): String?

    @Query("SELECT MIN(SUBSTR(date,1,4)) FROM ganzhi_calendar")
    suspend fun minYear(): String?

    @Query("SELECT MAX(SUBSTR(date,1,4)) FROM ganzhi_calendar")
    suspend fun maxYear(): String?
}

@Dao
interface ScanCacheDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(rows: List<ScanCacheEntity>)

    /** 缓存命中时连股票信息一次取回，避免重复扫描同日时重算全市场。 */
    @Query(
        """SELECT c.stock_id AS stockId, s.code AS code, s.symbol AS symbol, s.name AS name,
                  s.industry AS industry, c.day_ten_god AS dayTenGod, c.wealth_type AS wealthType
           FROM scan_cache c JOIN stock s ON s.id = c.stock_id
           WHERE c.date = :date
           ORDER BY CASE WHEN c.wealth_type = '正财' THEN 0 ELSE 1 END, s.symbol"""
    )
    suspend fun rowsWithStock(date: String): List<ScanCacheJoin>

    @Query("DELETE FROM scan_cache")
    suspend fun clear()

    @Query("SELECT COUNT(*) FROM scan_cache")
    suspend fun count(): Int

    /** 只保留最近 keep 个扫描日，避免缓存无界增长。 */
    @Query(
        """DELETE FROM scan_cache WHERE date NOT IN
           (SELECT date FROM scan_cache GROUP BY date ORDER BY date DESC LIMIT :keep)"""
    )
    suspend fun evictOld(keep: Int)
}

data class ScanCacheJoin(
    @ColumnInfo(name = "stockId") val stockId: Long,
    @ColumnInfo(name = "code") val code: String,
    @ColumnInfo(name = "symbol") val symbol: String,
    @ColumnInfo(name = "name") val name: String,
    @ColumnInfo(name = "industry") val industry: String,
    @ColumnInfo(name = "dayTenGod") val dayTenGod: String,
    @ColumnInfo(name = "wealthType") val wealthType: String,
)

@Dao
interface FilterDao {
    @Query("SELECT stock_id, ten_god FROM stock_hidden_ten_god WHERE ten_god IN (:gods)")
    suspend fun hiddenGodPairs(gods: List<String>): List<StockHiddenHit>

    @Query("SELECT * FROM stock_hidden_ten_god WHERE stock_id = :stockId ORDER BY pillar, rank, hidden_stem")
    suspend fun hiddenOfStock(stockId: Long): List<com.stockfortune.app.data.entity.StockHiddenTenGodEntity>
}

@Dao
interface FavoriteDao {
    /** 收藏列表连股票信息一次取回，避免逐条 findById 的 N+1。 */
    @Query("SELECT s.* FROM favorite f JOIN stock s ON s.id = f.stock_id ORDER BY f.added_at DESC")
    suspend fun stocksWithInfo(): List<StockEntity>

    @Query("SELECT EXISTS(SELECT 1 FROM favorite WHERE stock_id = :stockId)")
    suspend fun isFavorite(stockId: Long): Boolean

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun add(row: FavoriteEntity)

    @Query("DELETE FROM favorite WHERE stock_id = :stockId")
    suspend fun remove(stockId: Long)
}

@Dao
interface MetaDao {
    @Query("SELECT * FROM app_meta")
    suspend fun all(): List<AppMetaEntity>

    @Query("SELECT value FROM app_meta WHERE key = :key LIMIT 1")
    suspend fun value(key: String): String?
}

@Dao
interface LuckCycleDao {
    @Query("SELECT * FROM stock_luck_cycle WHERE stock_id = :stockId LIMIT 1")
    suspend fun findByStockId(stockId: Long): com.stockfortune.app.data.entity.StockLuckCycleEntity?

    @Query("SELECT * FROM luck_cycle_period WHERE stock_id = :stockId ORDER BY cycle_index")
    suspend fun periodsByStockId(stockId: Long): List<com.stockfortune.app.data.entity.LuckCyclePeriodEntity>

    @Query(
        """SELECT * FROM luck_cycle_period
           WHERE stock_id = :stockId AND :year BETWEEN start_year AND end_year
           LIMIT 1"""
    )
    suspend fun currentPeriodForYear(stockId: Long, year: Int): com.stockfortune.app.data.entity.LuckCyclePeriodEntity?

    @Query("SELECT COUNT(*) FROM stock_luck_cycle")
    suspend fun countCycles(): Int

    @Query("SELECT COUNT(*) FROM luck_cycle_period")
    suspend fun countPeriods(): Int
}
