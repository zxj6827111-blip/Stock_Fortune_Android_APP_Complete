"""test_classics_verify.py —— 语料门禁的反例测试。

门禁的价值只在"坏数据会被拦下"。这里刻意构造一批应该失败的资产副本，
逐条断言 verify_classics 的退出码非 0；其中第 2 组就是 2026-09-30 独立验收
查出的漏洞：回改条目曾绕过全文比对，整段伪造也能过门禁。
"""

from __future__ import annotations

import copy
import io
import json
import sys
import tempfile
import unittest
from contextlib import redirect_stdout
from pathlib import Path

TOOLS = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(TOOLS))

import classics_selection as sel  # noqa: E402
import verify_classics as vc  # noqa: E402

BASE = json.loads(sel.ASSET_JSON.read_text(encoding="utf-8"))
GUI = "dtjy-v1-gui-verse"
GUI_TEXT = sel.expected_text("癸水", sel._VERSE, next(
    x for s, _lp, _c, k, x, _sp in sel.SELECTION if s == "癸水" and k == sel._VERSE))


def run_gate(mutate):
    """把改过的资产写到临时文件跑门禁，返回 (退出码, 输出)；不动仓库里的资产。"""
    corpus = copy.deepcopy(BASE)
    mutate(corpus)
    with tempfile.TemporaryDirectory() as td:
        p = Path(td) / "corpus.json"
        p.write_text(json.dumps(corpus, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
        buf = io.StringIO()
        with redirect_stdout(buf):
            code = vc.main(["--asset", str(p)])
        return code, buf.getvalue()


def entry(corpus, entry_id):
    return next(e for e in corpus["entries"] if e["entry_id"] == entry_id)


def text_of(corpus, entry_id):
    return entry(corpus, entry_id)["original_text"]


class GateAcceptsGood(unittest.TestCase):
    def test_当前资产必须整体通过(self):
        code, out = run_gate(lambda c: None)
        self.assertEqual(code, 0, out)

    def test_正确回改后的癸水歌诀必须通过(self):
        def m(c):
            entry(c, GUI)["original_text"] = GUI_TEXT
        code, out = run_gate(m)
        self.assertEqual(code, 0, out)
        self.assertIn("合戊化火", GUI_TEXT)


class GateRejectsRewrittenVerse(unittest.TestCase):
    """F2 复现：登记过底本回改，不等于这条文字可以随便改。"""

    def test_整段伪造但仍含回改词必须失败(self):
        def m(c):
            entry(c, GUI)["original_text"] = "癸水這是一段偽造內容，合戊化火，火根乃真。"
        code, out = run_gate(m)
        self.assertNotEqual(code, 0, "伪造文字仍过门禁：\n" + out)
        self.assertIn(GUI, out)

    def test_插入多余文字必须失败(self):
        def m(c):
            entry(c, GUI)["original_text"] = GUI_TEXT.replace("龍德", "龍德又德")
        self.assertNotEqual(run_gate(m)[0], 0)

    def test_删句必须失败(self):
        def m(c):
            entry(c, GUI)["original_text"] = GUI_TEXT.replace("不畏火土，", "")
        self.assertNotEqual(run_gate(m)[0], 0)

    def test_把回改字改回对校本读法也必须失败(self):
        def m(c):
            entry(c, GUI)["original_text"] = GUI_TEXT.replace("合戊化火", "合戊見火")
        self.assertNotEqual(run_gate(m)[0], 0)

    def test_非回改条目同样不许改字(self):
        target = "dtjy-v1-jia-verse"
        def m(c):
            e = entry(c, target)
            e["original_text"] = e["original_text"].replace("參天", "參天參天")
        self.assertNotEqual(run_gate(m)[0], 0)


class GateRejectsPageDrift(unittest.TestCase):
    """F1 复现：原注跨页时展示页码不能退回小节定位页。"""

    def test_己土原注页码退回11必须失败(self):
        def m(c):
            entry(c, "dtjy-v1-ji-annotation_excerpt")["page"] = 11
        code, out = run_gate(m)
        self.assertNotEqual(code, 0, "己土原注页码退回定位页仍过门禁：\n" + out)

    def test_辛金原注页码退回12必须失败(self):
        def m(c):
            entry(c, "dtjy-v1-xin-annotation_excerpt")["page"] = 12
        code, out = run_gate(m)
        self.assertNotEqual(code, 0, "辛金原注页码退回定位页仍过门禁：\n" + out)

    def test_两条跨页原注的展示页必须是12与13(self):
        self.assertEqual(entry(BASE, "dtjy-v1-ji-annotation_excerpt")["page"], 12)
        self.assertEqual(entry(BASE, "dtjy-v1-xin-annotation_excerpt")["page"], 13)


class GateRejectsSchemaAndResidue(unittest.TestCase):
    def test_不认识的schema版本必须失败(self):
        def m(c):
            c["_meta"]["schema_version"] = 999
        code, out = run_gate(m)
        self.assertNotEqual(code, 0, "schema_version=999 仍过门禁：\n" + out)

    def test_schema版本缺失必须失败(self):
        def m(c):
            del c["_meta"]["schema_version"]
        self.assertNotEqual(run_gate(m)[0], 0)

    def test_原文里塞回校记必须失败(self):
        def m(c):
            e = entry(c, "dtjy-v1-jia-verse")
            e["original_text"] = e["original_text"] + "【校：測試殘留】"
        self.assertNotEqual(run_gate(m)[0], 0)

    def test_语料版本号被改必须失败(self):
        def m(c):
            c["_meta"]["corpus_version"] = "dtjy-v9"
            for e in c["entries"]:
                e["entry_id"] = e["entry_id"].replace("dtjy-v1-", "dtjy-v9-")
        self.assertNotEqual(run_gate(m)[0], 0)


class GateRejectsProvenanceTampering(unittest.TestCase):
    """出处字段也要对账：改小节名、改篇名、改书名都能让引用指向错误的页。"""

    def _tamper(self, mutate, label):
        code, out = run_gate(mutate)
        self.assertNotEqual(code, 0, f"{label} 仍过门禁：\n{out}")

    def test_改小节名必须失败(self):
        def m(c):
            entry(c, "dtjy-v1-jia-annotation_excerpt")["section"] = "乙木"
        self._tamper(m, "section 被改")

    def test_改篇名必须失败(self):
        def m(c):
            for e in c["entries"]:
                e["chapter"] = "地支論"
        self._tamper(m, "chapter 被改")

    def test_改book_id指向不存在的书必须失败(self):
        def m(c):
            entry(c, "dtjy-v1-jia-verse")["book_id"] = "san_ming_tong_hui"
        self._tamper(m, "book_id 被改")

    def test_书目版本字段漂移必须失败(self):
        def m(c):
            c["books"][0]["edition"] = "民国石印本"
        self._tamper(m, "books[].edition 被改")

    def test_源文件名被改必须失败(self):
        def m(c):
            c["_meta"]["source_file"] = "另一本书.md"
        self._tamper(m, "_meta.source_file 被改")

    def test_改日主字段必须失败(self):
        def m(c):
            entry(c, "dtjy-v1-jia-verse")["day_stem"] = "乙"
        self._tamper(m, "day_stem 被改")

    def test_只改section就想换一套预期文本必须失败(self):
        # 回改查表若用资产侧的 section，改 section 就能给别的条目发豁免
        def m(c):
            e = entry(c, GUI)
            e["section"] = "壬水"
        self._tamper(m, "癸水歌诀的 section 被改")


class SelectionSelfConsistency(unittest.TestCase):
    """清单自身也要自洽，否则门禁对账的是两份同样错的数据。"""

    def test_十干各两条且id唯一(self):
        ids = [f"{sel.CORPUS_VERSION}-{sel.STEM_SLUG[s[0][0]]}-{k}"
               for s, _lp, _c, k, _x, _sp in sel.SELECTION]
        self.assertEqual(len(ids), 20)
        self.assertEqual(len(set(ids)), 20)

    def test_跨页条目必须都有核页依据(self):
        for s, lp, _c, k, _x, sp in sel.SELECTION:
            if sp != lp:
                self.assertIn((s, k), sel.SCAN_PAGE_EVIDENCE, f"{s}/{k} 缺核页依据")

    def test_展示页不得早于定位页(self):
        for s, lp, _c, k, _x, sp in sel.SELECTION:
            self.assertGreaterEqual(sp, lp, f"{s}/{k}")

    def test_回改锚点在选段里必须唯一(self):
        for s, _lp, _c, k, x, _sp in sel.SELECTION:
            r = sel.restore_for(s, k)
            if r:
                self.assertEqual(x.count(r["from"]), 1, f"{s}/{k} 回改锚点不唯一")


if __name__ == "__main__":
    unittest.main(verbosity=2)
