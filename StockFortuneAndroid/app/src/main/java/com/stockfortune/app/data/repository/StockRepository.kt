package com.stockfortune.app.data.repository

import com.stockfortune.app.data.dao.BaziDao
import com.stockfortune.app.data.dao.FavoriteDao
import com.stockfortune.app.data.dao.FilterDao
import com.stockfortune.app.data.dao.MetaDao
import com.stockfortune.app.data.dao.StockDao
import com.stockfortune.app.data.entity.FavoriteEntity
import com.stockfortune.app.data.entity.StockSearchHit
import com.stockfortune.app.domain.model.BaziChart
import com.stockfortune.app.domain.model.PillarDisplay
import com.stockfortune.app.domain.model.StockInfo
import com.stockfortune.app.domain.model.TenGod

data class StockDetail(
    val stock: StockInfo,
    val bazi: BaziChart,
    val pillars: List<PillarDisplay>,
    val hiddenTenGods: List<TenGod>,
    val seasonSummary: String,
    val fateFeature: String,
    val isFavorite: Boolean,
    /** 按日主天干归类的古籍引文；与四柱其余干支无关，取不到就是无匹配 */
    val classics: ClassicQuoteResult,
)

class StockRepository(
    private val stockDao: StockDao,
    private val baziDao: BaziDao,
    private val filterDao: FilterDao,
    private val favoriteDao: FavoriteDao,
    private val metaDao: MetaDao,
    private val classicQuotes: ClassicQuoteRepository,
) {
    suspend fun search(query: String): List<StockSearchHit> {
        val q = query.trim()
        if (q.isEmpty()) return emptyList()
        val digits = q.filter { it.isDigit() }
        // LIKE 的通配符必须转义，否则输入 "%" 会命中全部股票
        val exact = (digits.ifEmpty { q })
            .replace("\\", "\\\\")
            .replace("%", "\\%")
            .replace("_", "\\_")
        return stockDao.search("%$exact%", exact)
    }

    suspend fun detail(codeOrSymbol: String): StockDetail? {
        val key = codeOrSymbol.trim().uppercase()
        val entity = stockDao.findByCode(key)
            ?: stockDao.findByCode("$key.SH")
            ?: stockDao.findByCode("$key.SZ")
            ?: stockDao.findByCode("$key.BJ")
            ?: stockDao.findBySymbol(key)
        val stock = entity ?: return null
        val bazi = baziDao.findByStockId(stock.id) ?: return null
        val hidden = filterDao.hiddenOfStock(stock.id)
        return StockDetail(
            stock = stock.toInfo(),
            bazi = bazi.toChart(),
            pillars = listOf(
                PillarDisplay("年柱", bazi.yearPillar, com.stockfortune.app.domain.calculator.TenGodCalculator.elementOf(bazi.yearStem), com.stockfortune.app.domain.calculator.TenGodCalculator.elementOfBranch(bazi.yearBranch)),
                PillarDisplay("月柱", bazi.monthPillar, com.stockfortune.app.domain.calculator.TenGodCalculator.elementOf(bazi.monthStem), com.stockfortune.app.domain.calculator.TenGodCalculator.elementOfBranch(bazi.monthBranch)),
                PillarDisplay("日柱", bazi.dayPillar, com.stockfortune.app.domain.calculator.TenGodCalculator.elementOf(bazi.dayStem), com.stockfortune.app.domain.calculator.TenGodCalculator.elementOfBranch(bazi.dayBranch)),
                PillarDisplay("时柱", bazi.hourPillar, com.stockfortune.app.domain.calculator.TenGodCalculator.elementOf(bazi.hourStem), com.stockfortune.app.domain.calculator.TenGodCalculator.elementOfBranch(bazi.hourBranch)),
            ),
            hiddenTenGods = TenGod.ORDER.filter { g -> hidden.any { it.tenGod == g.cn } },
            seasonSummary = com.stockfortune.app.domain.calculator.TenGodCalculator.seasonSummary(bazi.monthBranch),
            fateFeature = com.stockfortune.app.domain.calculator.FortuneText.fateFeature(bazi.dayStem),
            isFavorite = favoriteDao.isFavorite(stock.id),
            classics = classicQuotes.forDayStem(bazi.dayStem),
        )
    }

    suspend fun favoriteStocks(): List<StockInfo> = favoriteDao.stocksWithInfo().map { it.toInfo() }

    suspend fun toggleFavorite(stockId: Long): Boolean {
        return if (favoriteDao.isFavorite(stockId)) {
            favoriteDao.remove(stockId); false
        } else {
            favoriteDao.add(FavoriteEntity(stockId, System.currentTimeMillis())); true
        }
    }

    suspend fun meta(): Map<String, String> = metaDao.all().associate { it.key to it.value }

    suspend fun stockCount(): Int = stockDao.count()

}

private fun com.stockfortune.app.data.entity.StockEntity.toInfo() = StockInfo(
    id = id, code = code, symbol = symbol, name = name, exchange = exchange, board = board,
    listingDate = listingDate, industry = industry, industryFull = industryFull, stockNature = stockNature,
    firstDayFlag = firstDayFlag, firstOpen = firstOpen, firstClose = firstClose, firstChange = firstChange,
)

private fun com.stockfortune.app.data.entity.StockBaziEntity.toChart() = BaziChart(
    stockId = stockId, fullBazi = fullBazi, yearPillar = yearPillar, monthPillar = monthPillar,
    dayPillar = dayPillar, hourPillar = hourPillar, dayMaster = dayStem, naYin = naYin,
    strength = dayMasterStrength,
)
