"""test_classics_extract.py —— 抽取器与语料门禁的行为测试。

用合成底本跑，不依赖外部书籍目录，clone 后也能执行。
"""

from __future__ import annotations

import hashlib
import json
import sys
import unittest
from pathlib import Path
from unittest import mock

TOOLS = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(TOOLS))

import classics_selection as sel  # noqa: E402
import extract_classics as ec  # noqa: E402

VERSE_JIA = "甲木參天，植立千古。"
NOTE_JIA = "甲為根幹之木，純陽之本，參天雄壯"
VERSE_YI = "乙木雖柔，可春可秋。"
NOTE_YI = "乙為枝葉之木，柔如花卉"
VERSE_BING = "丙火猛烈，甲來焚滅。"
NOTE_BING = "丙為焚烈之火，純陽之性"

FIXTURE_MD = f"""<!-- p7 -->
## 通天論

欲識三元萬物宗，先觀帝載與神功。

　　天有陰陽，故春木，夏火。

<!-- p8 -->
## 天干論

### 甲木

{VERSE_JIA}

【校記：刊本原作「胞胎」，通行諸本作「脫胎」，從刊本。】

　　{NOTE_JIA}，火者，木之子也。

　　辰為水庫，能制火滋木。

### 乙木

{VERSE_YI}

　　{NOTE_YI}，然坐丑未能制之，（丑未陰土，故乙能制。）如宰羊割牛。

<!-- p9 -->
### 丙火

{VERSE_BING}

　　{NOTE_BING}，故不畏秋而欺霜。

### 丁火

丁火柔中，可秋可冬。

【存疑：此節刊本殘缺一行，暫無從校補。】

　　丁為溫煖之火，豈不昭融乎。

## 地支論

陽支動且強，速達顯災祥。
"""


def chunk(cid: str, section: str, page: int | None, text: str) -> dict:
    return {"chunk_id": cid, "chapter": "天干論", "section": section, "page": page,
            "text": text, "content_hash": hashlib.md5(text.encode("utf-8")).hexdigest()[:16]}


def base_rows() -> list[dict]:
    return [
        chunk("DT2-0008", "甲木", 8, f"<!-- p8 -->\n# 天干論\n### 甲木\n{VERSE_JIA}\n\n　　{NOTE_JIA}，火者，木之子也。"),
        chunk("DT2-0008b", "乙木", 8, f"### 乙木\n{VERSE_YI}\n\n　　{NOTE_YI}，然坐丑未能制之。"),
        chunk("DT2-0009", "丙火", 9, f"<!-- p9 -->\n### 丙火\n{VERSE_BING}\n\n　　{NOTE_BING}，故不畏秋而欺霜。"),
        chunk("DT2-0010", "丁火", 9, f"### 丁火\n丁火柔中，可秋可冬。\n\n　　丁為溫煖之火，豈不昭融乎。"),
    ]


def selection(*items):
    return items or (
        ("甲木", 8, "DT2-0008", sel._VERSE, VERSE_JIA, 8),
        ("甲木", 8, "DT2-0008", sel._NOTE, NOTE_JIA, 8),
        ("乙木", 8, "DT2-0008b", sel._VERSE, VERSE_YI, 8),
        ("乙木", 8, "DT2-0008b", sel._NOTE, NOTE_YI, 8),
        ("丙火", 9, "DT2-0009", sel._VERSE, VERSE_BING, 9),
        ("丙火", 9, "DT2-0009", sel._NOTE, NOTE_BING, 9),
    )


def build(sel_items=None, restores=(), md=FIXTURE_MD, rows=None):
    """在合成底本上跑一次完整抽取，返回 (corpus, audit)。"""
    rows = base_rows() if rows is None else rows
    items = list(sel_items) if sel_items else list(selection())
    with mock.patch.object(sel, "SELECTION", items), \
            mock.patch.object(sel, "SCAN_RESTORE", list(restores)):
        return ec.build_corpus(md, rows, hashlib.sha256(md.encode("utf-8")).hexdigest())


class SplitTests(unittest.TestCase):
    def test_小节继承其前的扫描页码(self):
        sections = dict((n, p) for n, p, _ in ec.split_sections(FIXTURE_MD, "天干論"))
        self.assertEqual(sections["甲木"], 8)
        self.assertEqual(sections["丙火"], 9)

    def test_同页多个天干复用同一页码(self):
        pages = {n: p for n, p, _ in ec.split_sections(FIXTURE_MD, "天干論")}
        self.assertEqual(pages["甲木"], pages["乙木"], "乙木之前无页码标记，应继承甲木所在页")
        self.assertEqual(pages["丙火"], pages["丁火"])

    def test_只取正文篇名不取目录(self):
        md = "## 天干論\n\n目錄占位。\n\n<!-- p8 -->\n## 天干論\n\n### 甲木\n\n" + VERSE_JIA + "\n"
        names = [n for n, _, _ in ec.split_sections(md, "天干論")]
        self.assertEqual(names, ["甲木"])

    def test_下一篇即止不吞地支論(self):
        _, _, body = next(t for t in ec.split_sections(FIXTURE_MD, "天干論") if t[0] == "丁火")
        self.assertNotIn("陽支動且強", body)

    def test_找不到篇章就报错(self):
        with self.assertRaises(ec.ExtractError):
            ec.split_sections(FIXTURE_MD, "不存在論")


class ParseTests(unittest.TestCase):
    def test_缩进是段类唯一判据(self):
        _, _, body = next(t for t in ec.split_sections(FIXTURE_MD, "天干論") if t[0] == "甲木")
        parsed = ec.parse_block(body)
        self.assertEqual(parsed["verse"], [VERSE_JIA])
        self.assertEqual(len(parsed["notes"]), 2)
        self.assertEqual(len(parsed["criticism"]), 1)
        self.assertEqual(parsed["doubt"], [])

    def test_校记不进展示文本(self):
        corpus, audit = build()
        joined = "\n".join(e["original_text"] for e in corpus["entries"])
        self.assertNotIn("【", joined)
        jia = next(a for a in audit if a["section"] == "甲木" and a["kind"] == sel._VERSE)
        self.assertIn("胞胎", jia["notes"][0]["text"], "校记应留在审计记录里")

    def test_存疑段落整节拒绝入库(self):
        with self.assertRaises(ec.ExtractError) as ctx:
            build((("丁火", 9, "DT2-0010", sel._VERSE, "丁火柔中，可秋可冬。", 9),))
        self.assertIn("存疑", str(ctx.exception))


class SelectionTests(unittest.TestCase):
    def test_选段不存在时报错而非跳过(self):
        with self.assertRaises(ec.ExtractError) as ctx:
            build((("甲木", 8, "DT2-0008", sel._NOTE, "甲為參天之木", 8),))
        self.assertIn("找不到连续片段", str(ctx.exception))

    def test_歌诀必须整段相等(self):
        with self.assertRaises(ec.ExtractError):
            build((("甲木", 8, "DT2-0008", sel._VERSE, "甲木參天", 8),))

    def test_选段命中多个原注段时报错(self):
        dup_md = FIXTURE_MD.replace(
            "　　辰為水庫，能制火滋木。",
            f"　　{NOTE_JIA}，重複一段以便測試。")
        with self.assertRaises(ec.ExtractError) as ctx:
            build(md=dup_md)
        self.assertIn("位置不唯一", str(ctx.exception))

    def test_同一段内重复两遍也要报错(self):
        # 命中段落数仍是 1，只有数出现次数才拦得住
        same_para_md = FIXTURE_MD.replace(NOTE_JIA, f"{NOTE_JIA}，{NOTE_JIA}")
        with self.assertRaises(ec.ExtractError) as ctx:
            build(md=same_para_md)
        self.assertIn("出现 2 次（跨 1 段）", str(ctx.exception))

    def test_页码不符即失败(self):
        with self.assertRaises(ec.ExtractError) as ctx:
            build((("甲木", 7, "DT2-0008", sel._VERSE, VERSE_JIA, 7),))
        self.assertIn("定位页不符", str(ctx.exception))

    def test_切片编号错配即失败(self):
        rows = base_rows() + [chunk("DT2-0099", "甲木", 8, "### 甲木\n这一片里不含任何选段。")]
        with self.assertRaises(ec.ExtractError) as ctx:
            build((("甲木", 8, "DT2-0099", sel._VERSE, VERSE_JIA, 8),), rows=rows)
        self.assertIn("选段不在切片", str(ctx.exception))

    def test_JSONL被改动即失败(self):
        rows = base_rows()
        rows[0]["text"] += "被人加了一句"
        with self.assertRaises(ec.ExtractError) as ctx:
            build(rows=rows)
        self.assertIn("content_hash", str(ctx.exception))

    def test_未知日主小节即失败(self):
        with self.assertRaises(ec.ExtractError):
            build((("子水", 8, "DT2-0008", sel._VERSE, VERSE_JIA, 8),))


class RestoreTests(unittest.TestCase):
    def test_底本回改需先能逐字命中(self):
        restores = [{"section": "甲木", "kind": sel._VERSE, "from": "甲木參天", "to": "甲木参天",
                     "evidence": "测试", "decision": "测试"}]
        corpus, audit = build(restores=restores)
        entry = next(e for e in corpus["entries"] if e["kind"] == sel._VERSE and e["day_stem"] == "甲")
        self.assertEqual(entry["original_text"], "甲木参天，植立千古。")
        rec = next(a for a in audit if a["entry_id"] == entry["entry_id"])
        self.assertFalse(rec["listed_in_selection"])
        self.assertTrue(any(n["kind"] == "底本回改" for n in rec["notes"]))

    def test_回改锚点在底本不存在即失败(self):
        restores = [{"section": "甲木", "kind": sel._VERSE, "from": "合戊見火", "to": "合戊化火",
                     "evidence": "测试", "decision": "测试"}]
        with self.assertRaises(ec.ExtractError) as ctx:
            build(restores=restores)
        self.assertIn("回改锚点", str(ctx.exception))

    def test_回改锚点命中多处即失败(self):
        # NOTE_JIA 里「之」出现两次，锚点不唯一必须报错，不能只改第一处
        restores = [{"section": "甲木", "kind": sel._NOTE, "from": "之", "to": "X",
                     "evidence": "测试", "decision": "测试"}]
        with self.assertRaises(ec.ExtractError):
            build(restores=restores)


class PageSplitTests(unittest.TestCase):
    """定位页与展示扫描页是两回事：原注常被刊本推到下一页页首。"""

    def test_展示页码写进资产且可晚于定位页(self):
        with mock.patch.object(sel, "SCAN_PAGE_EVIDENCE", {("甲木", sel._NOTE): "测试依据"}):
            corpus, audit = build((
                ("甲木", 8, "DT2-0008", sel._VERSE, VERSE_JIA, 8),
                ("甲木", 8, "DT2-0008", sel._NOTE, NOTE_JIA, 9),
            ))
        verse, note = corpus["entries"]
        self.assertEqual(verse["page"], 8)
        self.assertEqual(note["page"], 9, "原注展示页应取它实际所在的那一页")
        self.assertEqual(note["source_chunk_id"], "DT2-0008", "切片回溯关系要保留在定位侧")
        self.assertEqual(next(a for a in audit if a["kind"] == sel._NOTE)["locate_page"], 8)

    def test_展示页早于定位页即失败(self):
        with self.assertRaises(ec.ExtractError) as ctx:
            build((("甲木", 8, "DT2-0008", sel._VERSE, VERSE_JIA, 7),))
        self.assertIn("早于定位页", str(ctx.exception))

    def test_页码错开但没有核页依据即失败(self):
        with mock.patch.object(sel, "SCAN_PAGE_EVIDENCE", {}):
            with self.assertRaises(ec.ExtractError) as ctx:
                build((("甲木", 8, "DT2-0008", sel._NOTE, NOTE_JIA, 9),))
        self.assertIn("SCAN_PAGE_EVIDENCE", str(ctx.exception))

    def test_定位页与底本小节页不符即失败(self):
        with self.assertRaises(ec.ExtractError) as ctx:
            build((("甲木", 6, "DT2-0008", sel._VERSE, VERSE_JIA, 6),))
        self.assertIn("定位页不符", str(ctx.exception))


class ExpectedTextTests(unittest.TestCase):
    """清单 + 登记回改 = 唯一预期文本；抽取器与门禁共用同一函数。"""

    def test_无回改时预期文本就是选段(self):
        self.assertEqual(sel.expected_text("甲木", sel._VERSE, VERSE_JIA), VERSE_JIA)

    def test_有回改时只替换登记的那一处(self):
        restores = [{"section": "甲木", "kind": sel._VERSE, "from": "參天", "to": "参天",
                     "evidence": "测试", "decision": "测试"}]
        with mock.patch.object(sel, "SCAN_RESTORE", restores):
            self.assertEqual(sel.expected_text("甲木", sel._VERSE, VERSE_JIA), "甲木参天，植立千古。")

    def test_锚点不唯一时直接抛错(self):
        restores = [{"section": "甲木", "kind": sel._NOTE, "from": "之", "to": "X",
                     "evidence": "测试", "decision": "测试"}]
        with mock.patch.object(sel, "SCAN_RESTORE", restores):
            with self.assertRaises(ValueError):
                sel.expected_text("甲木", sel._NOTE, NOTE_JIA)


class DeterminismTests(unittest.TestCase):
    def test_重复执行结果一致(self):
        a, _ = build()
        b, _ = build()
        dump = lambda c: json.dumps(c, ensure_ascii=False, sort_keys=True)  # noqa: E731
        self.assertEqual(dump(a), dump(b))

    def test_条目顺序固定为歌诀先原注后(self):
        corpus, _ = build()
        kinds = [(e["day_stem"], e["kind"]) for e in corpus["entries"]]
        self.assertEqual(kinds[0], ("甲", sel._VERSE))
        self.assertEqual(kinds[1], ("甲", sel._NOTE))
        self.assertEqual(kinds[2], ("乙", sel._VERSE))

    def test_源文件改变会被哈希捕获(self):
        digest_a = hashlib.sha256(FIXTURE_MD.encode()).hexdigest()
        digest_b = hashlib.sha256((FIXTURE_MD + "多一行").encode()).hexdigest()
        self.assertNotEqual(digest_a, digest_b)
        corpus, _ = build()
        self.assertNotEqual(corpus["_meta"]["source_sha256"], digest_b)


class DuplicateLocationTests(unittest.TestCase):
    """重复定位必须炸，不能静默取最后一条——被盖掉的那份可能正带着【存疑】。"""

    def test_底本出现同名小节即失败(self):
        # 重复小节必须落在 天干論 篇内（## 地支論 之前），否则会被篇章边界截走
        dup_md = FIXTURE_MD.replace("## 地支論", (
            "### 甲木\n\n" + VERSE_JIA + "\n\n　　" + NOTE_JIA + "，重复的一份。\n\n"
            "## 地支論"))
        with self.assertRaises(ec.ExtractError) as ctx:
            ec.split_sections(dup_md, "天干論")
        self.assertIn("重复小节", str(ctx.exception))

    def test_重复小节不得掩盖存疑标记(self):
        # 第一份 丁火 带【存疑】，第二份干净；若按「后者覆盖前者」就会把存疑段落洗白入库
        dup_md = FIXTURE_MD.replace("## 地支論", (
            "### 丁火\n\n丁火柔中，可秋可冬。\n\n　　丁為溫煖之火，豈不昭融乎。\n\n"
            "## 地支論"))
        with self.assertRaises(ec.ExtractError):
            build(md=dup_md)

    def test_清单重复登记同一条即失败(self):
        with self.assertRaises(ec.ExtractError) as ctx:
            build((
                ("甲木", 8, "DT2-0008", sel._VERSE, VERSE_JIA, 8),
                ("甲木", 8, "DT2-0008", sel._VERSE, VERSE_JIA, 8),
            ))
        self.assertIn("重复登记", str(ctx.exception))

    def test_同一日主登记多个小节即失败(self):
        with self.assertRaises(ec.ExtractError) as ctx:
            build((
                ("甲木", 8, "DT2-0008", sel._VERSE, VERSE_JIA, 8),
                ("乙木", 8, "DT2-0008b", sel._VERSE, VERSE_YI, 8),
                ("乙木2", 8, "DT2-0008b", sel._NOTE, NOTE_YI, 8),
            ))
        self.assertIn("多个小节", str(ctx.exception))

    def test_JSONL重复切片编号即失败(self):
        rows = base_rows() + [base_rows()[0]]
        with self.assertRaises(ec.ExtractError) as ctx:
            build(rows=rows)
        self.assertIn("重复切片编号", str(ctx.exception))


if __name__ == "__main__":
    unittest.main(verbosity=2)
