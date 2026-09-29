package com.stockfortune.app

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.stockfortune.app.domain.calculator.FortuneText
import org.junit.Assert.assertFalse
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * 合规文案门禁：产品边界为"不做涨跌预测、不做收益与操作暗示"。
 * 旧门禁只扫 FortuneText 的函数返回值与 7 个敏感词，扫不到 strings.xml 与常量，
 * 曾让"稳健获利 / 短线机会"这类收益暗示漏进首页统计卡。这里把资源文案一并纳入。
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class ComplianceTextTest {

    private val banned = listOf(
        "买入", "卖出", "涨幅", "收益率", "必涨", "预测涨跌", "稳赚",
        "获利", "收益", "短线", "机会", "潜力", "把握时机", "适合把握",
    )

    private fun assertClean(label: String, text: String) {
        banned.forEach { b ->
            assertFalse("[$label] 含收益/操作暗示词「$b」: $text", text.contains(b))
        }
    }

    @Test
    fun `资源文案不含收益或操作暗示`() {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        val ids = listOf(
            R.string.app_subtitle, R.string.app_slogan,
            R.string.entry_stock_query_sub, R.string.entry_daily_scan_sub,
            R.string.entry_trade_calendar_sub, R.string.entry_date_select_sub,
            R.string.search_hint, R.string.today_overview,
            R.string.wealth_zheng, R.string.wealth_pian,
            R.string.scanner_title, R.string.scanner_subtitle,
            R.string.filter_title, R.string.filter_subtitle, R.string.filter_desc,
            R.string.filter_group_hidden_desc, R.string.filter_group_year_desc,
            R.string.filter_group_month_desc, R.string.filter_group_day_desc,
            R.string.legend_zheng, R.string.legend_pian, R.string.legend_none,
            R.string.legend_zheng_hint, R.string.legend_pian_hint, R.string.legend_none_hint,
            R.string.date_select_title, R.string.date_select_subtitle, R.string.date_select_hint,
            R.string.mine_offline, R.string.disclaimer, R.string.disclaimer_short,
        )
        ids.forEach { id -> assertClean(ctx.resources.getResourceEntryName(id), ctx.getString(id)) }
    }

    @Test
    fun `代码常量与生成文案不含收益或操作暗示`() {
        assertClean("SCAN_ZHENG_NOTE", FortuneText.SCAN_ZHENG_NOTE)
        assertClean("SCAN_PIAN_NOTE", FortuneText.SCAN_PIAN_NOTE)
        listOf(0 to 0, 3 to 5, 5 to 3, 21 to 0).forEach { (z, p) ->
            assertClean("monthTip($z,$p)", FortuneText.monthTip(z, p))
        }
    }
}
