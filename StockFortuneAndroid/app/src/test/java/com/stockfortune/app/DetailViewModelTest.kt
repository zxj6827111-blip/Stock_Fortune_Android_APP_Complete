package com.stockfortune.app

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.stockfortune.app.ui.vm.StockDetailViewModel
import java.time.LocalDate
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/** ViewModel 层：详情容器一次装载后 4 个 Tab 的数据都应就绪。 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class DetailViewModelTest {

    @Test
    fun `load 后年 月 日三份数据均就绪`() = runBlocking(Dispatchers.Default) {
        val ctx = ApplicationProvider.getApplicationContext<android.app.Application>()
        AppRuntime.container = AppContainer(ctx)
        val vm = StockDetailViewModel(AppRuntime.container)
        val today = LocalDate.now()
        vm.load("600519", today.year, today.year to today.monthValue, today.year to today.monthValue)

        val latch = CountDownLatch(1)
        var waited = 0
        while (waited < 8000) {
            val s = vm.state.value
            if (s.detail != null && s.year != null && s.month != null && s.daily != null) { latch.countDown(); break }
            delay(50); waited += 50
        }
        val s = vm.state.value
        assertNotNull("detail 未装载", s.detail)
        assertNotNull("年度数据未装载: loading=${s.loading} yearValue=${s.yearValue}", s.year)
        assertNotNull("月度数据未装载", s.month)
        assertNotNull("每日数据未装载", s.daily)
        assertEquals(12, s.year!!.months.size)
        assertEquals(21, s.month!!.tradeDayCount)
    }
}
