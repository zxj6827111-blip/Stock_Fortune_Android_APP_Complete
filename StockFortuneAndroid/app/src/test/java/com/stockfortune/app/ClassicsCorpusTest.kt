package com.stockfortune.app

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.stockfortune.app.data.repository.ClassicQuoteKind
import com.stockfortune.app.data.repository.ClassicQuoteRepository
import com.stockfortune.app.data.repository.ClassicQuoteResult
import java.io.IOException
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * 古籍引文仓库与资产的行为测试：十干映射、固定顺序、无匹配、资产缺失与损坏降级，
 * 以及资产自身的结构完整性（Python 侧门禁只在构建机上跑，APK 里的这份必须自己守住）。
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class ClassicsCorpusTest {

    private val ctx: Context get() = ApplicationProvider.getApplicationContext()

    private fun repo() = ClassicQuoteRepository(ctx)

    private fun assetJson(): String =
        ctx.assets.open(ClassicQuoteRepository.ASSET_PATH).bufferedReader().use { it.readText() }

    // ---------------------------------------------------------------- 映射与顺序

    @Test
    fun `十个日主各取两条且顺序固定为歌诀先原注后`() = runBlocking {
        listOf("甲", "乙", "丙", "丁", "戊", "己", "庚", "辛", "壬", "癸").forEach { stem ->
            val result = repo().forDayStem(stem)
            assertTrue("$stem 应命中引文，实际 $result", result is ClassicQuoteResult.Found)
            val quotes = (result as ClassicQuoteResult.Found).quotes
            assertEquals("$stem 条目数", 2, quotes.size)
            assertEquals("$stem 第一条须为歌诀", ClassicQuoteKind.VERSE, quotes[0].kind)
            assertEquals("$stem 第二条须为原注节选", ClassicQuoteKind.ANNOTATION_EXCERPT, quotes[1].kind)
            assertTrue("$stem 条目日主错配", quotes.all { it.section.startsWith(stem) })
        }
    }

    @Test
    fun `出处字段齐备且页码落在扫描页 8 至 14`() = runBlocking {
        val r = repo()
        listOf("甲", "乙", "丙", "丁", "戊", "己", "庚", "辛", "壬", "癸").forEach { stem ->
            val quotes = (r.forDayStem(stem) as ClassicQuoteResult.Found).quotes
            quotes.forEach { q ->
                assertTrue("${q.entryId} 书名缺失", q.bookTitle.isNotBlank())
                assertTrue("${q.entryId} 版本缺失", q.edition.isNotBlank())
                assertEquals("${q.entryId} 篇名", "天干論", q.chapter)
                assertTrue("${q.entryId} 扫描页码越界：${q.scanPage}", q.scanPage in 8..14)
            }
        }
    }

    @Test
    fun `癸水歌诀按底本回改用刊本合戊化火`() = runBlocking {
        val verse = ((repo().forDayStem("癸")) as ClassicQuoteResult.Found).quotes.first()
        assertTrue("应为刊本读法「合戊化火」，实际：${verse.originalText}", verse.originalText.contains("合戊化火"))
        assertTrue("不得残留对校本读法「見火」", !verse.originalText.contains("見火"))
    }

    @Test
    fun `未知日主与空白输入都是无匹配而非报错`() = runBlocking {
        val r = repo()
        assertEquals(ClassicQuoteResult.NoMatch, r.forDayStem("子"))
        assertEquals(ClassicQuoteResult.NoMatch, r.forDayStem("丑"))
        assertEquals(ClassicQuoteResult.NoMatch, r.forDayStem(""))
        assertEquals(ClassicQuoteResult.NoMatch, r.forDayStem("   "))
    }

    @Test
    fun `引文保持繁体原貌且不含底本排版记号`() = runBlocking {
        val r = repo()
        listOf("甲", "乙", "丙", "丁", "戊", "己", "庚", "辛", "壬", "癸").forEach { stem ->
            (r.forDayStem(stem) as ClassicQuoteResult.Found).quotes.forEach { q ->
                listOf("【", "】", "<!--", "-->", "　", "**", "\\n").forEach { mark ->
                    assertTrue("${q.entryId} 残留底本记号 $mark", !q.originalText.contains(mark))
                }
            }
        }
        // 繁体原貌的抽查：这些字在精校版里就是繁体，被简化过就说明资产被手改
        val jia = ((r.forDayStem("甲")) as ClassicQuoteResult.Found).quotes[0].originalText
        assertTrue("甲木歌诀应保留繁体「熾」：$jia", jia.contains("熾"))
        val xin = ((r.forDayStem("辛")) as ClassicQuoteResult.Found).quotes[0].originalText
        assertTrue("辛金歌诀应保留繁体「疊」：$xin", xin.contains("疊"))
    }

    /**
     * 刊本一行一列，小节标题跟着歌诀走，原注却常被推到下一页页首。
     * 己土、辛金的原注节选实际分别从扫描页 12、13 起首，展示页码不许退回小节定位页。
     */
    @Test
    fun `跨页原注展示的是引文实际所在页而非小节定位页`() = runBlocking {
        val r = repo()
        val cases = mapOf(
            "己" to listOf(ClassicQuoteKind.VERSE to 11, ClassicQuoteKind.ANNOTATION_EXCERPT to 12),
            "辛" to listOf(ClassicQuoteKind.VERSE to 12, ClassicQuoteKind.ANNOTATION_EXCERPT to 13),
        )
        cases.forEach { (stem, pairs) ->
            val quotes = (r.forDayStem(stem) as ClassicQuoteResult.Found).quotes
            pairs.forEach { (kind, page) ->
                val q = quotes.first { it.kind == kind }
                assertEquals("$stem 的 ${kind.name} 展示扫描页码", page, q.scanPage)
            }
        }
        // 未跨页的日主，两条引文应同页
        listOf("甲", "乙", "丙", "丁", "戊", "庚", "壬", "癸").forEach { stem ->
            val quotes = (r.forDayStem(stem) as ClassicQuoteResult.Found).quotes
            assertEquals("$stem 两条引文不应跨页", quotes[0].scanPage, quotes[1].scanPage)
        }
    }

    // ---------------------------------------------------------------- 降级

    @Test
    fun `资产缺失时降级为加载失败而不是崩溃`() = runBlocking {
        val missing = ClassicQuoteRepository(ctx, "classics/no_such_corpus.json")
        assertEquals(ClassicQuoteResult.LoadFailed, missing.forDayStem("甲"))
    }

    @Test
    fun `损坏的语料文本被逐类拒绝而不是部分容忍`() {
        val valid = JSONObject(assetJson())
        val bookId = valid.getJSONArray("books").getJSONObject(0).getString("book_id")

        assertThrows(IOException::class.java) { ClassicQuoteRepository.parse("{ 这不是 JSON") }

        val badKind = JSONObject(assetJson())
        badKind.getJSONArray("entries").getJSONObject(0).put("kind", "prose")
        assertThrows(IOException::class.java) { ClassicQuoteRepository.parse(badKind.toString()) }

        val dangling = JSONObject(assetJson())
        dangling.getJSONArray("entries").getJSONObject(0).put("book_id", "not_a_book")
        assertThrows(IOException::class.java) { ClassicQuoteRepository.parse(dangling.toString()) }

        val blank = JSONObject(assetJson())
        blank.getJSONArray("entries").getJSONObject(0).put("original_text", "   ")
        assertThrows(IOException::class.java) { ClassicQuoteRepository.parse(blank.toString()) }

        val dup = JSONObject(assetJson())
        dup.getJSONArray("entries").getJSONObject(1).put("entry_id", dup.getJSONArray("entries").getJSONObject(0).getString("entry_id"))
        assertThrows(IOException::class.java) { ClassicQuoteRepository.parse(dup.toString()) }

        assertEquals(10, ClassicQuoteRepository.parse(valid.toString()).size)
    }

    @Test
    fun `加载失败不写缓存下次仍会重试`() = runBlocking {
        val r = ClassicQuoteRepository(ctx, "classics/no_such_corpus.json")
        repeat(3) { assertEquals(ClassicQuoteResult.LoadFailed, r.forDayStem("甲")) }
    }

    // ---------------------------------------------------------------- 资产自身

    @Test
    fun `资产结构与固定选段口径一致`() {
        val root = JSONObject(assetJson())
        val meta = root.getJSONObject("_meta")
        assertEquals(1, meta.getInt("schema_version"))
        assertEquals("dtjy-v1", meta.getString("corpus_version"))
        assertEquals("scan", meta.getString("page_basis"))
        assertTrue("缺校勘告警", meta.getString("textual_criticism_warning").length > 20)
        assertEquals(64, meta.getString("source_sha256").length)

        val entries = root.getJSONArray("entries")
        assertEquals(20, entries.length())
        val ids = HashSet<String>()
        val stems = HashSet<String>()
        for (i in 0 until entries.length()) {
            val e = entries.getJSONObject(i)
            assertTrue("entry_id 重复", ids.add(e.getString("entry_id")))
            stems.add(e.getString("day_stem"))
            assertEquals("neutral", e.getString("stance_hint"))
            assertEquals("ditiansui_jiyao", e.getString("book_id"))
            assertTrue(e.getString("original_text").isNotBlank())
        }
        assertEquals(10, stems.size)
        assertEquals(1, root.getJSONArray("books").length())
    }

    @Test
    fun `详情按数据库日干带出引文`() = runBlocking {
        val container = AppContainer(ctx)
        // 预置库里已核对的十干样本，逐干验证「日干 → 引文」这条接线
        val samples = mapOf(
            "000012" to "甲", "000011" to "乙", "000009" to "丙", "000027" to "丁", "000037" to "戊",
            "000002" to "己", "000004" to "庚", "000010" to "辛", "000016" to "壬", "000001" to "癸",
        )
        samples.forEach { (code, stem) ->
            val detail = container.stockRepository.detail(code)
            assertNotNull("查不到 $code", detail)
            assertEquals("$code 的日干应已核对为 $stem", stem, detail!!.bazi.dayMaster)
            val result = detail.classics
            assertTrue("$code 未带出引文：$result", result is ClassicQuoteResult.Found)
            val quotes = (result as ClassicQuoteResult.Found).quotes
            assertEquals("$code 引文条数", 2, quotes.size)
            assertTrue("$code 引文日主与日干不符", quotes.all { it.section.startsWith(stem) })
        }
    }
}
