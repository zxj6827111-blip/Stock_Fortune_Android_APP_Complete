package com.stockfortune.app.data.repository

import android.content.Context
import com.stockfortune.app.domain.model.CopyRuleDefinition
import com.stockfortune.app.domain.model.CopySection
import com.stockfortune.app.domain.model.ProductionGate
import com.stockfortune.app.domain.model.ReviewStatus
import org.json.JSONArray
import java.io.File
import java.io.InputStream

/**
 * 离线文案规则仓储（统一加载构建期导入的 281 条冻结候审规则）。
 * 支持在 Android 运行时（Context.assets）与 JVM 单元测试（ClassLoader/File）中自适应加载。
 */
class CopyRuleRepository(
    private val context: Context? = null,
) {
    @Volatile
    private var rulesCache: List<CopyRuleDefinition>? = null

    fun getRules(): List<CopyRuleDefinition> {
        val cached = rulesCache
        if (cached != null) return cached
        synchronized(this) {
            rulesCache?.let { return it }
            val loaded = loadRules()
            rulesCache = loaded
            return loaded
        }
    }

    private fun loadRules(): List<CopyRuleDefinition> {
        val stream: InputStream = openAssetStream() ?: return emptyList()
        val text = stream.bufferedReader(Charsets.UTF_8).use { it.readText() }
        val array = JSONArray(text)
        val result = ArrayList<CopyRuleDefinition>(array.length())

        for (i in 0 until array.length()) {
            val obj = array.getJSONObject(i)
            val sectionCn = obj.getString("section")
            val section = CopySection.fromCn(sectionCn) ?: continue

            val evKeys = mutableListOf<String>()
            val evArray = obj.optJSONArray("evidenceKeys")
            if (evArray != null) {
                for (j in 0 until evArray.length()) {
                    evKeys.add(evArray.getString(j))
                }
            }

            result.add(
                CopyRuleDefinition(
                    ruleId = obj.getString("ruleId"),
                    section = section,
                    triggerDsl = obj.getString("triggerDsl"),
                    priority = obj.getInt("priority"),
                    conflictGroup = obj.getString("conflictGroup"),
                    evidenceKeys = evKeys,
                    text = obj.getString("text"),
                    ruleVersion = obj.getString("ruleVersion"),
                    reviewStatus = ReviewStatus.valueOf(obj.getString("reviewStatus")),
                    productionGate = ProductionGate.valueOf(obj.getString("productionGate")),
                    isLegacyNoRender = obj.getBoolean("isLegacyNoRender"),
                    module = obj.optString("module", "基础"),
                    source = obj.optString("source", ""),
                    sourceId = obj.optString("sourceId", ""),
                )
            )
        }
        return result
    }

    private fun openAssetStream(): InputStream? {
        // 1. Android Context Assets
        if (context != null) {
            try {
                return context.assets.open(ASSET_PATH)
            } catch (_: Exception) {}
        }
        // 2. ClassLoader Resources (用于 JVM 单元测试)
        javaClass.classLoader?.getResourceAsStream(RESOURCE_PATH)?.let { return it }
        javaClass.classLoader?.getResourceAsStream(ASSET_PATH)?.let { return it }

        // 3. 磁盘文件回退 (用于 IDE/CLI 测试)
        val candidatePaths = listOf(
            "app/src/main/assets/$ASSET_PATH",
            "StockFortuneAndroid/app/src/main/assets/$ASSET_PATH",
            "app/src/test/resources/$RESOURCE_PATH",
            "StockFortuneAndroid/app/src/test/resources/$RESOURCE_PATH",
        )
        for (path in candidatePaths) {
            val f = File(path)
            if (f.exists() && f.isFile) {
                return f.inputStream()
            }
        }
        return null
    }

    companion object {
        const val ASSET_PATH = "copywriting/copy_rules_frozen_281.json"
        const val RESOURCE_PATH = "copywriting/copy_rules_frozen_281.json"

        @Volatile
        private var defaultInstance: CopyRuleRepository? = null

        fun getDefault(context: Context? = null): CopyRuleRepository {
            return defaultInstance ?: synchronized(this) {
                defaultInstance ?: CopyRuleRepository(context).also { defaultInstance = it }
            }
        }
    }
}
