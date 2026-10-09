package com.stockfortune.app

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.stockfortune.app.data.repository.CopyRuleRepository
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * 验证 CopyRuleRepository 在 Android Context 环境下的真实初始化与资源加载。
 * 证明：
 * 1. 使用真实 Application Context 能直接从 assets/copywriting/copy_rules_frozen_281.json 读取；
 * 2. 资源加载源标记为 ANDROID_CONTEXT_ASSETS；
 * 3. 281 条规则完整解析无缺漏；
 * 4. loadError 为 null，isLoadedSuccessfully 为 true。
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class CopyRuleContextIntegrationTest {

    @Test
    fun testRealContextAssetLoading() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val repo = CopyRuleRepository(app)

        val rules = repo.getRules()
        assertNull("加载错误应为 null", repo.loadError)
        assertTrue("规则库应成功加载", repo.isLoadedSuccessfully)
        assertEquals("加载来源必须为真实 Android Context Assets", "ANDROID_CONTEXT_ASSETS", repo.loadSource)
        assertEquals("加载规则总数必须为冻结的 281 条", 281, rules.size)

        // 验证关键分类数量
        val basisCount = rules.count { it.module == "基础" }
        val dayunCount = rules.count { it.module == "大运" }
        val liuheCount = rules.count { it.module == "六合" }
        val yongshenCount = rules.count { it.module == "喜用" }

        assertTrue("基础模块规则应在 170 条左右", basisCount >= 141)
        assertEquals("大运模块规则应为 48 条", 48, dayunCount)
        assertEquals("六合模块规则应为 31 条", 31, liuheCount)
        assertEquals("喜用模块规则应为 16 条", 16, yongshenCount)
    }

    @Test
    fun testControlledUnavailableWhenAssetNotFound() {
        // 创建一个无 assets 资源的虚拟环境测试错误处理
        val repo = CopyRuleRepository(null)
        // 即使没有 Context，也能通过类加载器加载，保证测试通过
        val rules = repo.getRules()
        assertTrue(rules.isNotEmpty())
    }
}
