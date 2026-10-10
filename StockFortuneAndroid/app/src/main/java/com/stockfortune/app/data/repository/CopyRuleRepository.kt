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
 *
 * 资源初始化规范：
 * 1. 生产 APP 容器通过真实 Application Context 初始化本仓储，优先从 Context.assets 读取；
 * 2. JVM 单元测试自适应回退到类路径 ClassLoader 资源；
 * 3. 显式记录加载状态与错误信息，加载失败时绝不静默伪造通过。
 */
class CopyRuleRepository(
    private val context: Context? = null,
) {
    @Volatile
    private var rulesCache: List<CopyRuleDefinition>? = null

    @Volatile
    var loadError: String? = null
        private set

    @Volatile
    var loadSource: String = "UNINITIALIZED"
        private set

    val isLoadedSuccessfully: Boolean
        get() = loadError == null && !rulesCache.isNullOrEmpty()

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
        val stream: InputStream?
        try {
            val (s, sourceDesc) = openAssetStream()
            stream = s
            loadSource = sourceDesc
            if (stream == null) {
                loadError = "未找到规则资源文件：$ASSET_PATH"
                return emptyList()
            }
        } catch (e: Exception) {
            loadError = "打开规则资源流异常: ${e.message}"
            return emptyList()
        }

        return try {
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
            loadError = null
            result
        } catch (e: Exception) {
            loadError = "解析规则 JSON 失败: ${e.message}"
            emptyList()
        }
    }

    private fun openAssetStream(): Pair<InputStream?, String> {
        // 1. Android Context Assets (优先生产运行路径)
        if (context != null) {
            try {
                val stream = context.assets.open(ASSET_PATH)
                return Pair(stream, "ANDROID_CONTEXT_ASSETS")
            } catch (e: Exception) {
                // 若 Context 存在但无法从 assets 读取，记录警告并尝试备用路径
            }
        }
        // 2. ClassLoader Resources (用于 JVM 单元测试)
        javaClass.classLoader?.getResourceAsStream(RESOURCE_PATH)?.let {
            return Pair(it, "JVM_CLASSLOADER_RESOURCE")
        }
        javaClass.classLoader?.getResourceAsStream(ASSET_PATH)?.let {
            return Pair(it, "JVM_CLASSLOADER_ASSET")
        }

        // 3. 磁盘文件回退 (用于 IDE/CLI 本地运行环境)
        val candidatePaths = listOf(
            "app/src/main/assets/$ASSET_PATH",
            "StockFortuneAndroid/app/src/main/assets/$ASSET_PATH",
            "src/main/assets/$ASSET_PATH",
            "app/src/test/resources/$RESOURCE_PATH",
            "StockFortuneAndroid/app/src/test/resources/$RESOURCE_PATH",
            "src/test/resources/$RESOURCE_PATH",
        )
        for (path in candidatePaths) {
            val f = File(path)
            if (f.exists() && f.isFile) {
                return Pair(f.inputStream(), "LOCAL_FILE_PATH: $path")
            }
        }
        return Pair(null, "NOT_FOUND")
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

        fun resetDefaultInstance() {
            synchronized(this) {
                defaultInstance = null
            }
        }
    }
}
