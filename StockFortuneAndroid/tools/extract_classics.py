"""extract_classics.py —— 从《滴天髓輯要》精校版抽取首批 20 条引文，产出 APP 资产与审计报告。

用法：
    python3 tools/extract_classics.py            # 重新抽取并写 app/src/main/assets/classics/
    python3 tools/extract_classics.py --check     # 只校验现有资产是否与底本一致，不写文件

抽取是**契约式**的：tools/classics_selection.py 里逐条写死了篇名、天干小节、扫描页码和选段原文，
本脚本只做定位与逐字比对，不做任何模糊匹配或关键词挑选。任一条对不上就整体失败，
绝不静默跳过、绝不自动改字（唯一的例外是清单里显式登记的底本回改，会进审计报告）。
"""

from __future__ import annotations

import argparse
import hashlib
import json
import re
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))

import classics_selection as sel  # noqa: E402

PAGE_RE = re.compile(r"<!--\s*p(\d+)\s*-->")
CRITICISM_RE = re.compile(r"^【(校記|校|存疑)[：:]")
INDENT = "　"  # 全角空格：原注另段低兩格


class ExtractError(RuntimeError):
    """抽取失败：底本与固定选段清单不一致，必须人工复核，不允许降级通过。"""


def sha256_of(path: Path) -> str:
    return hashlib.sha256(path.read_bytes()).hexdigest()


def md5_16(text: str) -> str:
    """底本 JSONL 的 content_hash 实测算法：md5(text)[:16]。"""
    return hashlib.md5(text.encode("utf-8")).hexdigest()[:16]


def split_sections(md_text: str, chapter: str) -> list[tuple[str, int, str]]:
    """按「## 篇名」取篇，再按「### 小节」切节，返回 (小节名, 扫描页码, 节正文)。

    扫描页码取该小节标题之前最后一个 `<!-- pN -->`，即刊本中该节起始所在页；
    同一页连排多个天干小节时，后面的小节复用同一页码，这是底本页序的既成事实。
    """
    lines = md_text.splitlines()
    starts = [i for i, l in enumerate(lines) if l.strip() == f"## {chapter}"]
    if not starts:
        raise ExtractError(f"底本中找不到篇章「{chapter}」")
    start = starts[-1]  # 目錄页也会列篇名，取正文中那个真标题

    sections: list[tuple[str, int | None, str]] = []
    # 篇标题自己就带页码标记（<!-- p8 --> 在 ## 天干論 之前），先取前缀里最后一个
    prefix_pages = [int(m.group(1)) for l in lines[: start + 1] if (m := PAGE_RE.fullmatch(l.strip()))]
    page: int | None = prefix_pages[-1] if prefix_pages else None
    title: str | None = None
    page_of_title: int | None = None
    buf: list[str] = []
    for line in lines[start + 1:]:
        marker = PAGE_RE.fullmatch(line.strip())
        if marker:
            page = int(marker.group(1))
            continue
        heading = re.match(r"^### (.+?)\s*$", line)
        if heading:
            if title is not None:
                sections.append((title, page_of_title, "\n".join(buf)))
            title, page_of_title, buf = heading.group(1), page, []
            continue
        if re.match(r"^#{1,2} ", line):  # 下一篇（## 地支論）或更高层标题
            break
        if title is not None:
            buf.append(line)
    if title is not None:
        sections.append((title, page_of_title, "\n".join(buf)))

    if not sections:
        raise ExtractError(f"篇章「{chapter}」下没有 ### 小节")
    # 同名小节必须报错而不是后者覆盖前者：底本若重复出现「甲木」，被盖掉的那一份
    # 可能正带着【存疑】标记，静默去重等于把存疑段落洗白成可入库段落。
    dup = {t for t in (name for name, _p, _t in sections)
           if [n for n, _p, _t in sections].count(t) > 1}
    if dup:
        raise ExtractError(f"篇章「{chapter}」存在重复小节 {sorted(dup)}，无法确定取哪一份")
    named: list[tuple[str, int, str]] = []
    for name, pg, text in sections:
        if pg is None:
            raise ExtractError(f"小节「{name}」之前没有扫描页码标记，无法定位页码")
        named.append((name, pg, text))
    return named


def parse_block(text: str) -> dict:
    """把一节切成 歌诀段 / 原注段 / 校记 / 存疑，保持底本原貌不改字。

    底本体例是「歌訣頂格，原注另段低兩格（全角空格縮進）」，所以缩进就是唯一的段类判据。
    注意不能用 str.strip() 来切段：U+3000 在 Python 里算空白，会被一并剥掉。
    """
    verse: list[str] = []
    notes: list[str] = []
    criticism: list[str] = []
    doubt: list[str] = []
    for para in re.split(r"\n\s*\n", text):
        if not para.strip():
            continue
        # 只剥 ASCII 空白与页码标记，保留段首的全角缩进
        raw = "\n".join(
            PAGE_RE.sub("", l).rstrip(" \t\r")
            for l in para.splitlines()
            if l.strip() or PAGE_RE.search(l)
        ).strip("\n").lstrip(" \t\r")
        if not raw:
            continue
        if CRITICISM_RE.match(raw):
            (doubt if raw.startswith("【存疑") else criticism).append(raw)
        elif raw.startswith(INDENT):
            notes.append(raw.lstrip(INDENT))
        else:
            verse.append(raw)
    return {"verse": verse, "notes": notes, "criticism": criticism, "doubt": doubt}


def _apply_restore(section: str, kind: str, text: str) -> tuple[str, dict | None]:
    """按登记的底本回改产出入库文本；锚点不唯一就失败，绝不静默改第一处。"""
    hit = sel.restore_for(section, kind)
    if not hit:
        return text, None
    try:
        return sel.expected_text(section, kind, text), hit
    except ValueError as e:
        raise ExtractError(str(e))


def extract_one(
    section: str, page: int, kind: str, excerpt: str, parsed: dict
) -> tuple[str, list[dict]]:
    """把一条选段落到该节的歌诀段或原注段上，返回入库文本与该段的校记/存疑记录。"""
    if parsed["doubt"]:
        raise ExtractError(f"{section} 含存疑标记，按口径不得入库：{parsed['doubt'][0][:40]}")

    if kind == sel._VERSE:
        if len(parsed["verse"]) != 1:
            raise ExtractError(f"{section} 歌诀段数应为 1，实测 {len(parsed['verse'])}，无法确定取哪一段")
        source = parsed["verse"][0]
        if source != excerpt:
            raise ExtractError(
                f"{section} 歌诀与底本逐字不符\n  清单：{excerpt}\n  底本：{source}"
            )
        host = source
    else:
        hits = [n for n in parsed["notes"] if excerpt in n]
        if not hits:
            raise ExtractError(f"{section} 原注中找不到连续片段：{excerpt[:24]}…")
        # 数出现次数而不是数段落：同一段里重复两遍，命中段落仍只有 1 个，
        # 但入库的那句到底是哪一处已经无法确定。
        occurrences = sum(n.count(excerpt) for n in hits)
        if occurrences != 1:
            raise ExtractError(
                f"{section} 选段在原注中出现 {occurrences} 次（跨 {len(hits)} 段），位置不唯一"
            )
        if excerpt.startswith(INDENT) or INDENT in excerpt:
            raise ExtractError("原注节选不得包含段首缩进，避免把两段误作一段")
        host = hits[0]

    shipped, restore = _apply_restore(section, kind, host if kind == sel._VERSE else excerpt)
    notes = [{"kind": "校记", "text": c} for c in parsed["criticism"]]
    if restore:
        notes.append({"kind": "底本回改", "text": f"「{restore['from']}」→「{restore['to']}」：{restore['evidence']}"})
    return shipped, notes


def _reject_duplicates() -> None:
    """抽取前先查清单与切片自身的重复，别等到生成 entry_id 时才撞车。"""
    pairs = [(s, k) for s, _lp, _c, k, _x, _sp in sel.SELECTION]
    dup_pair = {p for p in pairs if pairs.count(p) > 1}
    if dup_pair:
        raise ExtractError(f"固定选段清单里 {sorted(dup_pair)} 被重复登记，无法确定取哪条")
    stems = {}
    for s, _lp, _c, _k, _x, _sp in sel.SELECTION:
        stems.setdefault(s[0], set()).add(s)
    for stem, names in stems.items():
        if len(names) > 1:
            raise ExtractError(f"日主「{stem}」同时登记了多个小节 {sorted(names)}")


def build_corpus(md_text: str, jsonl_rows: list[dict], source_sha256: str) -> tuple[dict, list[dict]]:
    _reject_duplicates()
    sections = {name: (page, parse_block(text)) for name, page, text in split_sections(md_text, sel.CHAPTER)}

    ids = [r["chunk_id"] for r in jsonl_rows]
    dup_chunk = {c for c in ids if ids.count(c) > 1}
    if dup_chunk:
        raise ExtractError(f"JSONL 存在重复切片编号 {sorted(dup_chunk)}，交叉核对失效")

    by_chunk = {r["chunk_id"]: r for r in jsonl_rows}
    audit: list[dict] = []
    entries: list[dict] = []
    seen: set[str] = set()

    for section, locate_page, chunk_id, kind, excerpt, scan_page in sel.SELECTION:
        stem = section[0]
        if stem not in sel.STEM_SLUG:
            raise ExtractError(f"小节「{section}」的首字不是十干之一")
        if section not in sections:
            raise ExtractError(f"底本 {sel.CHAPTER} 下找不到小节「{section}」")
        actual_page, parsed = sections[section]
        if actual_page != locate_page:
            raise ExtractError(f"{section} 定位页不符：清单 {locate_page}，底本 {actual_page}")
        # 展示页码只能等于或晚于定位页（原注可能被推到下一页页首），且错开必须留证据
        if scan_page < locate_page:
            raise ExtractError(f"{section}/{kind} 展示扫描页码 {scan_page} 早于定位页 {locate_page}")
        if scan_page != locate_page and (section, kind) not in sel.SCAN_PAGE_EVIDENCE:
            raise ExtractError(
                f"{section}/{kind} 展示页码 {scan_page} 与定位页 {locate_page} 不同，"
                "但没有 SCAN_PAGE_EVIDENCE 核页依据"
            )

        chunk = by_chunk.get(chunk_id)
        if not chunk:
            raise ExtractError(f"{section} 的交叉核对 chunk_id {chunk_id} 不在 JSONL 中")
        if chunk["chapter"] != sel.CHAPTER or chunk["section"] != section:
            raise ExtractError(f"{chunk_id} 章节归属不符：{chunk['chapter']}/{chunk['section']}")
        if chunk["page"] != locate_page:
            raise ExtractError(f"{chunk_id} 定位页 {chunk['page']} 与清单 {locate_page} 不符")
        if chunk["content_hash"] != md5_16(chunk["text"]):
            raise ExtractError(f"{chunk_id} 的 content_hash 不等于 md5(text)[:16]，JSONL 已被改动")

        entry_id = f"{sel.CORPUS_VERSION}-{sel.STEM_SLUG[stem]}-{kind}"
        if entry_id in seen:
            raise ExtractError(f"entry_id 重复：{entry_id}")
        seen.add(entry_id)

        text, notes = extract_one(section, locate_page, kind, excerpt, parsed)
        # 选段还必须落在所声明的那个切片里，否则出处无法回溯到具体切片。
        # 放在 extract_one 之后：底本里找不到时先报「该节没有这段」，比报切片不符更有指向性。
        if excerpt not in chunk["text"]:
            raise ExtractError(f"{section}/{kind} 的选段不在切片 {chunk_id} 内，切片编号或选段有误")
        entries.append({
            "entry_id": entry_id,
            "book_id": sel.BOOK["book_id"],
            "chapter": sel.CHAPTER,
            "section": section,
            "day_stem": stem,
            "kind": kind,
            "original_text": text,
            "page": scan_page,
            "source_chunk_id": chunk_id,
            "stance_hint": "neutral",
        })
        audit.append({
            "entry_id": entry_id, "section": section, "kind": kind,
            "locate_page": locate_page, "page": scan_page,
            "listed_in_selection": text == excerpt, "notes": notes,
        })

    order = {k: i for i, k in enumerate([sel._VERSE, sel._NOTE])}
    entries.sort(key=lambda e: (list(sel.STEM_SLUG).index(e["day_stem"]), order[e["kind"]]))
    corpus = {
        "_meta": {
            "schema_version": sel.SCHEMA_VERSION,
            "corpus_version": sel.CORPUS_VERSION,
            "source_file": sel.SOURCE_MD.name,
            "source_sha256": source_sha256,
            "page_basis": sel.PAGE_BASIS,
            "page_basis_note": (
                "entries[].page 是引文首字实际所在的底本扫描页序号，既不是古籍印刷页码，"
                "也不等于 Markdown/JSONL 的小节定位页；两者差异与核页依据见审计报告。"
            ),
            "textual_criticism_warning": (
                "引文为传统文献原文节选，未逐字校勘全部刊本异文与夹注；"
                "精校版所立校记与本次底本回改均记录在 tools/classics_selection.py 与审计报告，未进入展示文本。"
            ),
        },
        "books": [sel.BOOK],
        "entries": entries,
    }
    return corpus, audit


def render_audit(corpus: dict, audit: list[dict]) -> str:
    lines = [
        "# 《滴天髓輯要》首批引文选段审计报告",
        "",
        f"- 语料版本：`{corpus['_meta']['corpus_version']}`（schema v{corpus['_meta']['schema_version']}）",
        f"- 文字来源：`{corpus['_meta']['source_file']}`",
        f"- 源文件 SHA-256：`{corpus['_meta']['source_sha256']}`",
        f"- 交叉核对：`{sel.SOURCE_JSONL.name}`（85 个切片，`content_hash` 实测为 `md5(text)[:16]`，全部一致）",
        f"- 底本扫描件套：`{sel.SOURCE_PDF.name}`",
        f"- 页码口径：`{corpus['_meta']['page_basis']}` —— entries[].page 为引文首字**实际所在**的扫描页，非刊本印刷叶码，也非 Markdown 的小节定位页",
        f"- 条目数：{len(corpus['entries'])}（十干各 2 条：歌诀 1 + 原注节选 1）",
        "",
        "## 一、抽取规则",
        "",
        "1. 只按「篇名 + 天干小节 + 定位页」定位，选段必须是底本中逐字连续片段；",
        "   定位不到、命中多处或与清单不符一律整体失败，不做模糊匹配，不按关键词挑句。",
        "2. 含【存疑】标记的段落拒绝入库；【校：】/【校記：】只进本报告，不进展示文本。",
        "3. 繁体与夹注照录精校版；异体字沿用精校版字形（见第六节）。",
        "4. 精校版与刊本扫描页冲突且原书未立校记时，按登记的底本回改处理（见第五节）。",
        "5. 展示扫描页码不得早于定位页；两者一旦错开，必须有逐条核页依据（见第四节）。",
        "",
        "## 二、逐条抽取结果",
        "",
        "「定位页」是精校 Markdown / JSONL 里该小节标题所在页，用于切片对账与回溯；",
        "「展示扫描页码」是引文首字实际所在页，即 APP 里给用户看的那个页码。刊本一行一列，",
        "小节标题跟着歌诀走，原注却常被推到下一页页首，所以两者可以不同（依据见第四节）。",
        "",
        "| entry_id | 小节 | 类型 | 定位页 | 展示扫描页码 | 与清单逐字相同 | 校记/回改 |",
        "|---|---|---|---:|---:|---|---|",
    ]
    kind_cn = {sel._VERSE: "歌诀", sel._NOTE: "原注节选"}
    for a in audit:
        # 校记是这一批唯一的审计线索，截断等于销毁证据；表格里只标条数，全文见第三节
        marks = f"{len(a['notes'])} 条（全文见第三节）" if a["notes"] else "—"
        same = "是" if a["listed_in_selection"] else "否（底本回改）"
        page_cell = str(a["page"]) if a["page"] == a["locate_page"] else f"**{a['page']}**（≠定位页）"
        lines.append(
            f"| `{a['entry_id']}` | {a['section']} | {kind_cn[a['kind']]} | "
            f"{a['locate_page']} | {page_cell} | {same} | {marks} |"
        )
    lines += ["", "## 三、底本校记与回改全文（不截断）", "",
              "以下照录精校版该节的【校：】/【校記：】原文，只进本报告、不进展示文本。", ""]
    with_notes = [a for a in audit if a["notes"]]
    if not with_notes:
        lines.append("本次无。")
    for a in with_notes:
        lines.append(f"### `{a['entry_id']}`（{a['section']}·{kind_cn[a['kind']]}，扫描页 {a['page']}）")
        lines.append("")
        for n in a["notes"]:
            lines.append(f"* **{n['kind']}**：{n['text']}")
        lines.append("")
    lines += ["## 四、展示页码与定位页错开的核页依据", ""]
    if not sel.SCAN_PAGE_EVIDENCE:
        lines.append("本次无：全部条目的展示页码与定位页一致。")
    for (section, kind), why in sel.SCAN_PAGE_EVIDENCE.items():
        lines.append(f"* **{section}·{kind_cn[kind]}**：{why}")
    lines += [
        "",
        "本批最初只记了小节定位页，把己土原注记成第 11 页、辛金原注记成第 12 页，"
        "2026-09-30 独立验收核图时查出，已改为 12 / 13 并加本节作为强制登记项。",
        "",
        "## 五、底本回改（逐条含证据与决策来源）",
        "",
    ]
    if not sel.SCAN_RESTORE:
        lines.append("本次无。")
    for r in sel.SCAN_RESTORE:
        lines += [
            f"* **{r['section']}·{kind_cn[r['kind']]}**：「{r['from']}」→「{r['to']}」",
            f"  * 证据：{r['evidence']}",
            f"  * 决策：{r['decision']}",
            "",
        ]
    lines += ["## 六、异体字：沿用精校版字形，仅记录", "",
              "| 小节 | 类型 | 刊本扫描页 | 入库字形 |", "|---|---|---|---|"]
    for v in sel.VARIANT_GLYPH_NOTES:
        lines.append(f"| {v['section']} | {kind_cn[v['kind']]} | {v['scan']} | {v['shipped']} |")
    lines += ["", "## 七、扫描页逐段对照结论", "",
              "对照件：底本扫描页 8—14（`_ocr_stage/page_00NN.json` 的原始 OCR 噪声过高，"
              "竖排识别错字率约五成，不能作独立核对源，故改用 PDF 渲染图逐段放大比对）。", "",
              "| 小节 | 扫描页 | 刊本叶码 | 结论 | 备注 |", "|---|---:|---|---|---|"]
    for s in sel.SCAN_VERIFICATION:
        lines.append(f"| {s['section']} | {s['page']} | {s['printed_leaf']} | {s['status']} | {s['note']} |")
    lines += [
        "",
        "## 八、口径边界（展示时必须守住）",
        "",
        "* 引文按**日主天干**归类，与该股四柱其余干支、月令、格局无关；"
        "歌诀里「春不容金」「抱乙而孝」等条件本系统并未计算，不得当作已验证的结论。",
        "* 原注节选只是该段开头若干句，不是全段，更不是全篇；UI 标为「原注节选」。",
        "* 十干静态概览是本项目据选定原注写的概述，标「本项目概述」，不挂书名。",
        "* 《滴天髓輯要》属子平书，不论卦象；1936 刊本为公版，题署「劉伯溫著」系原书旧题，非考订结论。",
        "",
    ]
    return "\n".join(lines)


def main() -> int:
    ap = argparse.ArgumentParser()
    ap.add_argument("--check", action="store_true", help="只比对现有资产与底本，不写文件")
    args = ap.parse_args()

    for p in (sel.SOURCE_MD, sel.SOURCE_JSONL):
        if not p.exists():
            print(f"FAIL  底本缺失：{p}", file=sys.stderr)
            return 1

    md = sel.SOURCE_MD.read_text(encoding="utf-8")
    rows = [json.loads(l) for l in sel.SOURCE_JSONL.read_text(encoding="utf-8").splitlines() if l.strip()]
    digest = sha256_of(sel.SOURCE_MD)

    try:
        corpus, audit = build_corpus(md, rows, digest)
    except ExtractError as e:
        print(f"FAIL  抽取失败：{e}", file=sys.stderr)
        return 1

    payload = json.dumps(corpus, ensure_ascii=False, indent=2) + "\n"
    report = render_audit(corpus, audit)

    if args.check:
        if not sel.ASSET_JSON.exists():
            print("FAIL  资产不存在，请先运行 extract_classics.py", file=sys.stderr)
            return 1
        same = sel.ASSET_JSON.read_text(encoding="utf-8") == payload
        print(f"{'PASS' if same else 'FAIL'}  资产与底本一致（{len(corpus['entries'])} 条）")
        return 0 if same else 1

    sel.ASSET_JSON.parent.mkdir(parents=True, exist_ok=True)
    sel.ASSET_JSON.write_text(payload, encoding="utf-8")
    sel.AUDIT_MD.parent.mkdir(parents=True, exist_ok=True)
    sel.AUDIT_MD.write_text(report, encoding="utf-8")
    print(f"OK  已写出 {sel.ASSET_JSON.relative_to(sel.ASSET_JSON.parents[2])}"
          f"（{len(corpus['entries'])} 条）与审计报告")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
