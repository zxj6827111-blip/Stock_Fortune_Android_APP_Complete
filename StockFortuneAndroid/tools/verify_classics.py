"""verify_classics.py —— 古籍引文资产的构建门禁（任一失败即 exit(1)）。

日常构建只校验**已提交的 JSON**，不需要外部书籍目录；
带 --against-source 时才回到底本逐字复算（重新抽取或定期复核时用）。
"""

from __future__ import annotations

import argparse
import json
import re
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))

import classics_selection as sel

FAILS: list[str] = []
PASSES: list[str] = []

STEMS = list(sel.STEM_SLUG)
KINDS = [sel._VERSE, sel._NOTE]
REQUIRED = ["entry_id", "book_id", "chapter", "section", "day_stem", "kind",
            "original_text", "page", "source_chunk_id", "stance_hint"]
# 底本的排版与校勘记号一律不得漏进展示文本
RESIDUE = [r"【", r"<!--", r"-->", r"　", r"^\s*#", r"\*\*", r"\\n"]


def check(name: str, ok: bool, detail: str = ""):
    (PASSES if ok else FAILS).append(f"{name}{' — ' + detail if detail else ''}")
    print(f"{'PASS' if ok else 'FAIL'}  {name}{'  ' + detail if detail else ''}")


def main(argv=None) -> int:
    ap = argparse.ArgumentParser()
    ap.add_argument("--against-source", action="store_true", help="回到底本逐字复算（需外部书籍目录）")
    ap.add_argument("--asset", default=None, help="改校验指定 JSON（反例测试用），不动仓库里的资产")
    args = ap.parse_args(argv)
    FAILS.clear()
    PASSES.clear()

    asset = Path(args.asset) if args.asset else sel.ASSET_JSON
    if not asset.exists():
        print(f"FAIL  引文资产不存在：{asset}", file=sys.stderr)
        return 1

    corpus = json.loads(asset.read_text(encoding="utf-8"))
    meta, books, entries = corpus.get("_meta", {}), corpus.get("books", []), corpus.get("entries", [])

    # ---- 结构
    check("C01 _meta 必填项齐", all(k in meta for k in
          ["schema_version", "corpus_version", "source_file", "source_sha256",
           "page_basis", "textual_criticism_warning"]))
    # 只查键存在等于没查：schema_version 改成 999 也能过，而 APP 侧只认 v1 的字段布局
    check("C01b schema_version 只接受当前版本", meta.get("schema_version") == sel.SCHEMA_VERSION,
          f"资产 {meta.get('schema_version')!r} / 期望 {sel.SCHEMA_VERSION!r}")
    check("C02 page_basis 标明扫描页码", meta.get("page_basis") == sel.PAGE_BASIS, str(meta.get("page_basis")))
    check("C03 语料版本与清单一致", meta.get("corpus_version") == sel.CORPUS_VERSION, str(meta.get("corpus_version")))
    check("C04 书籍表恰好 1 条且 book_id 唯一", len(books) == 1 and len({b["book_id"] for b in books}) == 1)
    check("C05 书目字段齐", all(
        all(k in b and str(b[k]).strip() for k in ["book_id", "title", "edition", "author_note", "provenance"])
        for b in books))

    # ---- 数量与覆盖
    check("C06 恰好 20 条", len(entries) == 20, f"实际 {len(entries)}")
    check("C07 十干全覆盖", {e["day_stem"] for e in entries} == set(STEMS),
          f"缺 {sorted(set(STEMS) - {e['day_stem'] for e in entries})}")
    per_stem = {s: [e for e in entries if e["day_stem"] == s] for s in STEMS}
    check("C08 每干条目数恰为 2", all(len(v) == 2 for v in per_stem.values()),
          str({s: len(v) for s, v in per_stem.items() if len(v) != 2}))
    check("C09 每干 kind 不重复", all(len({e["kind"] for e in v}) == 2 for v in per_stem.values()))

    # ---- 单条字段
    check("C10 entry_id 唯一", len({e["entry_id"] for e in entries}) == len(entries))
    check("C11 必填字段齐且非空", all(
        all(k in e and (e[k] != "" if isinstance(e[k], str) else True) for k in REQUIRED) for e in entries))
    book_ids = {b["book_id"] for b in books}
    check("C12 book_id 引用有效", all(e["book_id"] in book_ids for e in entries))
    check("C13 kind 只取 verse/annotation_excerpt", all(e["kind"] in KINDS for e in entries))
    check("C14 day_stem 只取十干", all(e["day_stem"] in STEMS for e in entries))
    check("C15 页码为 8—14 的整数", all(
        isinstance(e["page"], int) and 8 <= e["page"] <= 14 for e in entries))
    check("C16 stance_hint 全为 neutral", all(e["stance_hint"] == "neutral" for e in entries))
    check("C17 原文非空且含句读", all(len(e["original_text"]) >= 8 and "，" in e["original_text"] for e in entries))
    check("C18 章节归属为天干論", all(e["chapter"] == sel.CHAPTER for e in entries))
    check("C19 section 以日主开头", all(e["section"].startswith(e["day_stem"]) for e in entries))
    check("C20 source_chunk_id 形如 DT2-00NN", all(re.fullmatch(r"DT2-\d{4}", e["source_chunk_id"]) for e in entries))

    residue = [(e["entry_id"], pat) for e in entries for pat in RESIDUE if re.search(pat, e["original_text"])]
    check("C21 无校记/存疑/页码/缩进等残留标记", not residue, str(residue[:4]))
    bad_id = [e["entry_id"] for e in entries
              if e["entry_id"] != f"{sel.CORPUS_VERSION}-{sel.STEM_SLUG[e['day_stem']]}-{e['kind']}"]
    check("C22 entry_id 命名稳定可推导", not bad_id, str(bad_id[:3]))

    # ---- 与抽取清单对账（不依赖底本，防清单与资产漂移）
    # 出处字段逐个核：只比文本和页码的话，把 section 改成别的小节、chapter 改成别的篇名
    # 都能过，而界面上「篇名 / 小节」正是给用户看的出处。
    listed = {(sel.CORPUS_VERSION, sel.STEM_SLUG[s[0]], k): (s, lp, c, x, sp)
              for s, lp, c, k, x, sp in sel.SELECTION}
    drift = []
    for e in entries:
        key = (sel.CORPUS_VERSION, sel.STEM_SLUG[e["day_stem"]], e["kind"])
        if key not in listed:
            drift.append(f"{e['entry_id']} 不在清单")
            continue
        listed_section, locate, chunk_id, excerpt, scan_page = listed[key]
        for field, want in (
            ("section", listed_section),
            ("chapter", sel.CHAPTER),
            ("book_id", sel.BOOK["book_id"]),
            ("day_stem", listed_section[0]),
            ("entry_id", f"{sel.CORPUS_VERSION}-{sel.STEM_SLUG[listed_section[0]]}-{e['kind']}"),
        ):
            if e.get(field) != want:
                drift.append(f"{e['entry_id']} 出处字段 {field}={e.get(field)!r}，清单为 {want!r}")
        if e["page"] != scan_page:
            drift.append(f"{e['entry_id']} 展示页码 {e['page']} 与清单 {scan_page} 不符")
        if e["source_chunk_id"] != chunk_id:
            drift.append(f"{e['entry_id']} 切片编号与清单不符")
        # 全文比对，不给任何豁免：回改条目也必须等于「清单选段 + 登记的那一次替换」。
        # 早先这里是「登记过回改就跳过比对」，实测把癸水歌诀整段换成伪造文字仍能过门禁。
        # 回改查表用清单侧的小节名，不用资产里的 section，否则改 section 就能换一套预期文本。
        try:
            want_text = sel.expected_text(listed_section, e["kind"], excerpt)
        except ValueError as err:
            drift.append(f"{e['entry_id']} 回改登记失配：{err}")
            continue
        if e["original_text"] != want_text:
            drift.append(
                f"{e['entry_id']} 文本与清单不符\n    预期：{want_text}\n    实际：{e['original_text']}"
            )
    check("C23 资产与固定选段清单逐条全文对账（含回改条目）", not drift, "; ".join(drift[:3])[:400])
    check("C24 底本回改条目数与清单登记一致",
          sum(1 for e in entries if sel.restore_for(e["section"], e["kind"])) == sum(
              1 for s, _, _, k, _, _ in sel.SELECTION if sel.restore_for(s, k)))

    # ---- 书目与源文件元数据对账
    book_bad = []
    for k, want in sel.BOOK.items():
        got = books[0].get(k) if books else None
        if got != want:
            book_bad.append(f"books[0].{k} 与清单不符")
    for k in ("schema_version", "corpus_version", "source_file", "page_basis"):
        want = {"schema_version": sel.SCHEMA_VERSION, "corpus_version": sel.CORPUS_VERSION,
                "source_file": sel.SOURCE_MD.name, "page_basis": sel.PAGE_BASIS}[k]
        if meta.get(k) != want:
            book_bad.append(f"_meta.{k}={meta.get(k)!r}，清单为 {want!r}")
    check("C24b 书目条目与清单逐字段一致", not book_bad, "; ".join(book_bad[:4]))

    # ---- 页码口径：展示页只能等于或晚于小节定位页，错开必须留核页依据
    page_bad = []
    for s, locate, _c, k, _x, scan_page in sel.SELECTION:
        if scan_page < locate:
            page_bad.append(f"{s}/{k} 展示页 {scan_page} 早于定位页 {locate}")
        if scan_page != locate and (s, k) not in sel.SCAN_PAGE_EVIDENCE:
            page_bad.append(f"{s}/{k} 页码错开但无 SCAN_PAGE_EVIDENCE 依据")
    for e in entries:
        if e["page"] < 8 or e["page"] > 14:
            page_bad.append(f"{e['entry_id']} 展示页 {e['page']} 越出 8—14")
    check("C25 页码口径自洽（定位页/展示页/核页依据）", not page_bad, "; ".join(page_bad[:3]))
    check("C26 页码错开条目数与核页依据登记一致",
          sum(1 for s, lp, _c, k, _x, sp in sel.SELECTION if sp != lp) == len(sel.SCAN_PAGE_EVIDENCE))

    if args.against_source:
        ok = all(p.exists() for p in (sel.SOURCE_MD, sel.SOURCE_JSONL))
        check("C28 底本可读", ok, f"{sel.SOURCE_MD}")
        if ok:
            import extract_classics as ec
            md = sel.SOURCE_MD.read_text(encoding="utf-8")
            rows = [json.loads(l) for l in sel.SOURCE_JSONL.read_text(encoding="utf-8").splitlines() if l.strip()]
            live = ec.sha256_of(sel.SOURCE_MD)
            check("C29 源文件 SHA-256 未变", live == meta["source_sha256"],
                  f"当前 {live[:12]}… vs 资产记录 {str(meta['source_sha256'])[:12]}…（不一致需重跑 extract_classics.py 并复核审计报告）")
            fresh, _ = ec.build_corpus(md, rows, live)
            check("C30 重新抽取结果与资产一致",
                  json.dumps(fresh, ensure_ascii=False, sort_keys=True)
                  == json.dumps(corpus, ensure_ascii=False, sort_keys=True))
    else:
        print("SKIP  C28-C30 底本复算（加 --against-source 开启）")

    print(f"\n{len(PASSES)} passed, {len(FAILS)} failed")
    if FAILS:
        for f in FAILS:
            print("  FAIL:", f)
        return 1
    print("CLASSICS_OK")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
