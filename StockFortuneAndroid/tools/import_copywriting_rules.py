#!/usr/bin/env python3
"""import_copywriting_rules.py
从 Phase 7 冻结候审交付包统一导入 281 条全量文案规则：
- 141 条基础文案规则 (V1.3_离线文案规则库_最终勘误候审稿.xlsx)
- 30 条十神强弱条件解释 (V1.3_十神强弱30条条件解释.xlsx)
- 110 条高级文案规则 (股运通V1.3_高级文案总表_候审冻结版.xlsx)

生成目标：
1. StockFortuneAndroid/app/src/main/assets/copywriting/copy_rules_frozen_281.json
2. StockFortuneAndroid/app/src/test/resources/copywriting/copy_rules_frozen_281.json
3. handoff/v1.3_copywriting/v1.3_281_rules_catalog.json
"""

import os
import sys
import json
import hashlib
from pathlib import Path

SCRIPT_DIR = Path(__file__).resolve().parent
REPO_ROOT = SCRIPT_DIR.parent.parent
sys.path.append(str(SCRIPT_DIR))

from test_handoff_copywriting import parse_sheet_rows, rows_to_dicts

FINAL_UNPACK_DIR = REPO_ROOT / "handoff" / "v1.3_copywriting" / "final" / "acceptance_unpack"
P_141 = FINAL_UNPACK_DIR / "02_只读基线_不可改" / "V1.3_离线文案规则库_最终勘误候审稿.xlsx"
P_30 = FINAL_UNPACK_DIR / "02_只读基线_不可改" / "V1.3_十神强弱30条条件解释.xlsx"
P_110 = FINAL_UNPACK_DIR / "01_冻结候审交付" / "股运通V1.3_高级文案总表_候审冻结版.xlsx"

EXPECTED_HASHES = {
    P_141: "98b697cf6503f16d8ae28349f72235c33d97ce17c6f7767d83196f2cb912d5cf",
    P_30: "73c5a4541448d13caa596765b80e129d133acf03fc88a5720902225ac680edc4",
    P_110: "6faef5b13ecc31cec029cc47f08ac99b3cbf34edf2096bbf934b5c76eebbfeb1",
}

def sha256(path):
    h = hashlib.sha256()
    with open(path, "rb") as f:
        while chunk := f.read(65536):
            h.update(chunk)
    return h.hexdigest()

def verify_inputs():
    for p, exp in EXPECTED_HASHES.items():
        assert p.exists(), f"文件不存在: {p}"
        act = sha256(p)
        assert act == exp, f"SHA-256 不匹配: {p.name}, 期望 {exp}, 实际 {act}"
        print(f"PASS [SHA-256]: {p.name} ({act[:16]}...)")

def build_rules():
    r_141 = rows_to_dicts(parse_sheet_rows(str(P_141), "规则候选"))
    r_30 = rows_to_dicts(parse_sheet_rows(str(P_30), "30条独立条件解释"))
    r_110 = rows_to_dicts(parse_sheet_rows(str(P_110), "规则总表"))

    assert len(r_141) == 141, f"基础规则条数应为 141，实为 {len(r_141)}"
    assert len(r_30) == 30, f"十神强弱解释条数应为 30，实为 {len(r_30)}"
    assert len(r_110) == 110, f"高级规则条数应为 110，实为 {len(r_110)}"

    entries = []

    # 1. 141 条基础规则
    for r in r_141:
        is_legacy = "NO_RENDER" in r.get("最终候审展示策略", "")
        entries.append({
            "ruleId": r["rule_id"].strip(),
            "module": "基础",
            "section": r["section"].strip(),
            "triggerDsl": r.get("trigger", "").strip(),
            "priority": int(r.get("priority", 0)),
            "conflictGroup": r.get("conflict_group", "").strip(),
            "evidenceKeys": [k.strip() for k in r.get("evidence_keys", "").split("|") if k.strip()],
            "text": r.get("text", "").strip(),
            "ruleVersion": r.get("rule_version", "offline-copy-v1.3-draft.2").strip(),
            "reviewStatus": "PENDING_REVIEW",
            "productionGate": "AUDIT_ONLY" if is_legacy else "CANDIDATE_ONLY",
            "isLegacyNoRender": is_legacy,
            "source": r.get("source", "").strip(),
            "sourceId": r.get("source_rule_ids", "").strip()[:100],
        })

    # 2. 30 条十神强弱条件解释
    for r in r_30:
        sec = "本月主题" if "本月主题" in r.get("section", "") else r.get("section", "").strip()
        entries.append({
            "ruleId": r["rule_id"].strip(),
            "module": "十神强弱",
            "section": sec,
            "triggerDsl": r.get("trigger", "").strip(),
            "priority": int(r.get("priority", 80)),
            "conflictGroup": r.get("conflict_group", "MONTH_GOD_STRENGTH_REVIEW").strip(),
            "evidenceKeys": [k.strip() for k in r.get("evidence_keys", "").split("|") if k.strip()],
            "text": r.get("条件解释正文", "").strip(),
            "ruleVersion": r.get("rule_version", "offline-copy-v1.3-draft.3-review").strip(),
            "reviewStatus": "PENDING_REVIEW",
            "productionGate": "CANDIDATE_ONLY",
            "isLegacyNoRender": False,
            "source": r.get("source", "").strip(),
            "sourceId": r.get("rule_id", "").strip(),
        })

    # 3. 110 条高级文案
    for r in r_110:
        entries.append({
            "ruleId": r["rule_id"].strip(),
            "module": r.get("module", "").strip(),
            "section": r.get("section", "").strip(),
            "triggerDsl": r.get("trigger条件JSON", "").strip(),
            "priority": int(r.get("优先级", 100)),
            "conflictGroup": r.get("冲突组", "").strip(),
            "evidenceKeys": [k.strip() for k in r.get("evidence_keys", "").split("|") if k.strip()],
            "text": r.get("候审文案text", "").strip(),
            "ruleVersion": r.get("rule_version", "v1.3-advanced").strip(),
            "reviewStatus": "PENDING_REVIEW",
            "productionGate": "CANDIDATE_ONLY",
            "isLegacyNoRender": False,
            "source": r.get("source", "").strip(),
            "sourceId": r.get("关联原需求ID", "").strip(),
        })

    assert len(entries) == 281, f"总规则数应为 281，实为 {len(entries)}"
    ids = set()
    for e in entries:
        assert e["ruleId"] not in ids, f"重复规则 ID: {e['ruleId']}"
        ids.add(e["ruleId"])
        assert e["text"], f"规则正文为空: {e['ruleId']}"

    print(f"成功构建 281 条候选文案规则 (唯一 ID 数: {len(ids)})")
    return entries

def main():
    print("==================================================================")
    print("导入 Phase 7 冻结候审交付包 281 条全量文案规则")
    print("==================================================================")
    verify_inputs()
    rules = build_rules()

    # 导出目标
    out_assets = REPO_ROOT / "StockFortuneAndroid" / "app" / "src" / "main" / "assets" / "copywriting" / "copy_rules_frozen_281.json"
    out_test = REPO_ROOT / "StockFortuneAndroid" / "app" / "src" / "test" / "resources" / "copywriting" / "copy_rules_frozen_281.json"
    out_catalog = REPO_ROOT / "handoff" / "v1.3_copywriting" / "v1.3_281_rules_catalog.json"

    out_assets.parent.mkdir(parents=True, exist_ok=True)
    out_test.parent.mkdir(parents=True, exist_ok=True)
    out_catalog.parent.mkdir(parents=True, exist_ok=True)

    json_str = json.dumps(rules, ensure_ascii=False, indent=2)

    with open(out_assets, "w", encoding="utf-8") as f:
        f.write(json_str)
    print(f"已写入 Asset 目标: {out_assets} ({len(json_str)} 字节)")

    with open(out_test, "w", encoding="utf-8") as f:
        f.write(json_str)
    print(f"已写入 Test Resource: {out_test}")

    with open(out_catalog, "w", encoding="utf-8") as f:
        f.write(json_str)
    print(f"已写入交付溯源清单: {out_catalog}")

    print("==================================================================")
    print("281 条规则导入完成：全部保持 PENDING_REVIEW 且 CANDIDATE_ONLY")
    print("==================================================================")

if __name__ == "__main__":
    main()
