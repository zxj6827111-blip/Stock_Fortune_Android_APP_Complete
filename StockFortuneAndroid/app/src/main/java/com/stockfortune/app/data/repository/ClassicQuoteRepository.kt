package com.stockfortune.app.data.repository

import android.content.Context
import android.util.Log
import java.io.IOException
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONObject

/** 引文类型。顺序即展示顺序：歌诀在前，原注节选在后。 */
enum class ClassicQuoteKind {
    VERSE, ANNOTATION_EXCERPT;

    companion object {
        fun from(value: String?): ClassicQuoteKind? = when (value) {
            "verse" -> VERSE
            "annotation_excerpt" -> ANNOTATION_EXCERPT
            else -> null
        }
    }
}

/** 一条可回溯的古籍引文：原文 + 出处（书名、版本、篇名、小节、扫描页码）。 */
data class ClassicQuote(
    val entryId: String,
    val bookTitle: String,
    val edition: String,
    val chapter: String,
    val section: String,
    val kind: ClassicQuoteKind,
    val originalText: String,
    /** 底本扫描件的页序号，不是古籍印刷页码 */
    val scanPage: Int,
)

sealed interface ClassicQuoteResult {
    data class Found(val quotes: List<ClassicQuote>) : ClassicQuoteResult
    /** 语料可用，但该日主没有收录条目 */
    data object NoMatch : ClassicQuoteResult
    /** 资产缺失或解析失败：详情其余内容仍应照常展示 */
    data object LoadFailed : ClassicQuoteResult
}

/**
 * 只读加载随 APK 打进去的离线古籍引文（`assets/classics/`）。
 *
 * 引文按日主天干归类，与该股其余干支无关，因此这里只是一张 日主 → 条目 的静态表，
 * 不做任何推断。首次读取与解析在 IO 线程完成，进程内缓存；解析失败不写缓存，
 * 避免一次瞬时 IO 抖动把「暂不可用」钉住整个进程。
 */
class ClassicQuoteRepository(
    private val context: Context,
    /** 暴露出来只为让"资产缺失/损坏"这条降级路径可测，生产上不要传别的值 */
    private val assetPath: String = ASSET_PATH,
) {

    private val cache = AtomicReference<Map<String, List<ClassicQuote>>?>(null)
    private val loadMutex = Mutex()

    suspend fun forDayStem(dayStem: String): ClassicQuoteResult {
        val stem = dayStem.trim()
        if (stem.isEmpty()) return ClassicQuoteResult.NoMatch
        val byStem = cache.get() ?: load()?.also { cache.set(it) } ?: return ClassicQuoteResult.LoadFailed
        val quotes = byStem[stem] ?: return ClassicQuoteResult.NoMatch
        return if (quotes.isEmpty()) ClassicQuoteResult.NoMatch else ClassicQuoteResult.Found(quotes)
    }

    private suspend fun load(): Map<String, List<ClassicQuote>>? = loadMutex.withLock {
        cache.get()?.let { return@withLock it }
        try {
            withContext(Dispatchers.IO) {
                val text = context.assets.open(assetPath).bufferedReader().use { it.readText() }
                parse(text)
            }
        } catch (e: Exception) {
            Log.e(TAG, "引文资产加载失败（$assetPath）", e)
            null
        }
    }

    companion object {
        const val ASSET_PATH = "classics/ditiansui_jiyao.json"
        private const val SUPPORTED_SCHEMA_VERSION = 1
        private const val SUPPORTED_PAGE_BASIS = "scan"
        private const val TAG = "ClassicQuoteRepo"

        /**
         * 解析并归类。任何结构性问题都抛 [IOException] 由上层降级为 LoadFailed，
         * 不做部分容忍：一条出处错位的引文比没有引文更糟。
         */
        @Throws(IOException::class)
        fun parse(text: String): Map<String, List<ClassicQuote>> = try {
            parseEntries(text)
        } catch (e: org.json.JSONException) {
            throw IOException("引文资产结构不合法：${e.message}", e)
        }

        private fun parseEntries(text: String): Map<String, List<ClassicQuote>> {
            val root = JSONObject(text)
            // 版本闸门：只看字段名的话，一个语义已变的 v2 资产会被当作 v1 直接展示；
            // 而 entries[].page 是"底本扫描件页序号"，是用户翻核原文的唯一坐标，
            // 静默改义（比如换成印刷页码）比加载失败更糟 —— 引文出处错位比没有引文更坏。
            val meta = root.optJSONObject("_meta")
                ?: throw IOException("引文资产缺少 _meta，无法确认版本与页码口径")
            val schema = meta.optInt("schema_version", -1)
            if (schema != SUPPORTED_SCHEMA_VERSION) {
                throw IOException("引文资产 schema_version=$schema，APP 仅支持 $SUPPORTED_SCHEMA_VERSION")
            }
            val pageBasis = meta.optString("page_basis")
            if (pageBasis != SUPPORTED_PAGE_BASIS) {
                throw IOException("引文资产 page_basis=$pageBasis，与界面「扫描件页码」口径不符")
            }
            val books = root.getJSONArray("books")
            val byBook = HashMap<String, Pair<String, String>>()
            for (i in 0 until books.length()) {
                val b = books.getJSONObject(i)
                byBook[b.getString("book_id")] = b.getString("title") to b.getString("edition")
            }
            val out = HashMap<String, MutableList<ClassicQuote>>()
            val entries = root.getJSONArray("entries")
            for (i in 0 until entries.length()) {
                val e = entries.getJSONObject(i)
                val kind = ClassicQuoteKind.from(e.getString("kind"))
                    ?: throw IOException("未知引文类型：${e.optString("kind")} @${e.optString("entry_id")}")
                val book = byBook[e.getString("book_id")]
                    ?: throw IOException("引文 ${e.getString("entry_id")} 引用了不存在的书名 ${e.getString("book_id")}")
                val stem = e.getString("day_stem")
                val quote = ClassicQuote(
                    entryId = e.getString("entry_id"),
                    bookTitle = book.first,
                    edition = book.second,
                    chapter = e.getString("chapter"),
                    section = e.getString("section"),
                    kind = kind,
                    originalText = e.getString("original_text").trim().also {
                        if (it.isEmpty()) throw IOException("引文 ${e.getString("entry_id")} 原文为空")
                    },
                    scanPage = e.getInt("page"),
                )
                out.getOrPut(stem) { mutableListOf() }.add(quote)
            }
            return out.mapValues { (_, v) ->
                v.sortedBy { it.kind.ordinal }.also {
                    if (it.map { q -> q.entryId }.distinct().size != it.size) {
                        throw IOException("同一日主存在重复条目")
                    }
                }
            }
        }
    }
}
