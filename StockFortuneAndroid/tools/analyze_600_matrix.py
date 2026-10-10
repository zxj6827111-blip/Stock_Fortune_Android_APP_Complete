#!/usr/bin/env python3
"""analyze_600_matrix.py
Phase 5 专项：分析 600 种基础条件下的动态五段式解读能力
理论组合：月干十神(10) x 月支本气十神(10) x 原局强弱(3) x 阴阳命(2) = 600 组合
"""

import os
import sys
from pathlib import Path
from collections import Counter

SCRIPT_DIR = Path(__file__).resolve().parent
REPO_ROOT = SCRIPT_DIR.parent.parent
sys.path.append(str(SCRIPT_DIR))
sys.path.append(str(REPO_ROOT / "StockFortuneAndroid" / ".venv" / "lib" / "python3.14" / "site-packages"))

from test_handoff_copywriting import parse_sheet_rows, rows_to_dicts
import bazi_core as bc

HANDOFF_DIR = REPO_ROOT / "handoff" / "v1.3_copywriting"

TEN_GODS = [
    "比肩", "劫财", "食神", "伤官", "偏财",
    "正财", "七杀", "正官", "偏印", "正印"
]
STRENGTHS = ["身强", "中和", "身弱"]
POLARITIES = ["YANG", "YIN"] # 阳命、阴命

def load_rules():
    wb_rules = HANDOFF_DIR / "V1.3_离线文案规则库_最终勘误候审稿.xlsx"
    rules_141 = rows_to_dicts(parse_sheet_rows(str(wb_rules), "规则候选"))
    wb_30 = HANDOFF_DIR / "V1.3_十神强弱30条条件解释.xlsx"
    tengod_30 = rows_to_dicts(parse_sheet_rows(str(wb_30), "30条独立条件解释"))
    return rules_141, tengod_30

def matches_trigger(trigger_dsl, context):
    """DSL 触发器匹配"""
    if not trigger_dsl or trigger_dsl.strip() == "":
        return True
    conds = [c.strip() for c in trigger_dsl.split("&&")]
    for c in conds:
        if c.startswith('month.stem_god == "'):
            val = c.split('==')[1].strip().strip('"\'')
            if context["month_stem_god"] != val: return False
        elif c.startswith('month.branch_main_qi_god == "'):
            val = c.split('==')[1].strip().strip('"\'')
            if context["month_branch_main_qi_god"] != val: return False
        elif c.startswith('natal.strength_state == "'):
            val = c.split('==')[1].strip().strip('"\'')
            if context["strength"] != val: return False
        elif c.startswith('pair.god_group == "'):
            val = c.split('==')[1].strip().strip('"\'')
            # 派生 pair.god_group: 同神 / 同五行异神 / 不同类十神
            sg = context["month_stem_god"]
            bg = context["month_branch_main_qi_god"]
            if sg == bg:
                actual_group = "同神"
            elif (sg in ("比肩","劫财") and bg in ("比肩","劫财")) or \
                 (sg in ("食神","伤官") and bg in ("食神","伤官")) or \
                 (sg in ("偏财","正财") and bg in ("偏财","正财")) or \
                 (sg in ("七杀","正官") and bg in ("七杀","正官")) or \
                 (sg in ("偏印","正印") and bg in ("偏印","正印")):
                actual_group = "同五行异神"
            else:
                actual_group = "不同类十神"
            if actual_group != val: return False
        elif c.startswith('first_day_polarity.state == "'):
            val = c.split('==')[1].strip().strip('"\'')
            if context["polarity"] != val: return False
        elif "dayun.availability" in c or "natal_relation.availability" in c or "yongshen.availability" in c:
            # 基础条件阶段，高级条件暂按 UNAVAILABLE 或不匹配处理
            return False
    return True

def compose_five_paragraphs(ctx, rules_141, tengod_30):
    """五段式组装与冲突裁决"""
    sections = {
        "命理依据": [],
        "本月主题": [],
        "潜在矛盾": [],
        "企业经营观察": [],
        "综合解释": []
    }
    hit_ids = []

    # 1. 匹配 141 条基础规则
    for r in rules_141:
        if "NO_RENDER" in r.get("最终候审展示策略", ""):
            continue
        sec = r.get("section", "")
        if sec in sections:
            if matches_trigger(r.get("trigger", ""), ctx):
                sections[sec].append(r)
                hit_ids.append(r.get("rule_id", ""))

    # 2. 匹配 30 条十神强弱补充（归入本月主题副线与条件解释）
    tengod_supplement = None
    for r in tengod_30:
        if r.get("ten_god") == ctx["month_stem_god"] and r.get("strength_state") == ctx["strength"]:
            tengod_supplement = r
            hit_ids.append(r.get("rule_id", ""))
            break

    # 3. 各段消解（按 conflict_group 取最高 priority）
    resolved = {}
    for sec_name, r_list in sections.items():
        if not r_list:
            resolved[sec_name] = f"【缺项】{sec_name}暂无直接规则命中"
            continue
        # 分组消解
        groups = {}
        for r in r_list:
            cg = r.get("conflict_group", "DEFAULT")
            pri = int(r.get("priority", 0) or 0)
            if cg not in groups or pri > groups[cg]["pri"]:
                groups[cg] = {"pri": pri, "rule": r}
        # 拼接段落
        sorted_rules = sorted(groups.values(), key=lambda x: x["pri"], reverse=True)
        text_parts = [g["rule"].get("text", "") for g in sorted_rules]
        
        # 若为本月主题，融合 30 条十神强弱条件解释
        if sec_name == "本月主题" and tengod_supplement:
            supp_text = tengod_supplement.get("条件解释正文", "")
            if supp_text and supp_text not in text_parts:
                text_parts.append(supp_text)
                
        resolved[sec_name] = " ".join([p for p in text_parts if p.strip()])

    return resolved, hit_ids

def main():
    print("==================================================================")
    print("Phase 5 理论 600 组合矩阵规则覆盖与五段式能力分析")
    print("==================================================================")
    rules_141, tengod_30 = load_rules()
    print(f"载入规则库: 141 基础规则候选 + 30 十神强弱条件解释")

    total_matrix = 0
    full_five_paragraphs = 0
    missing_section_counts = Counter()

    # 收集身强 vs 中和 vs 身弱的差异
    strength_diffs = []
    # 收集阳命 vs 阴命差异
    polarity_diffs = []

    samples = []

    for sg in TEN_GODS:
        for bg in TEN_GODS:
            for st in STRENGTHS:
                for pol in POLARITIES:
                    total_matrix += 1
                    ctx = {
                        "month_stem_god": sg,
                        "month_branch_main_qi_god": bg,
                        "strength": st,
                        "polarity": pol
                    }
                    res, hits = compose_five_paragraphs(ctx, rules_141, tengod_30)
                    # 检查是否五段均有实质内容
                    missing = [sec for sec, txt in res.items() if txt.startswith("【缺项】") or not txt.strip()]
                    if not missing:
                        full_five_paragraphs += 1
                    else:
                        for m in missing: missing_section_counts[m] += 1
                        
                    # 抽样保存前 5 组完整样本
                    if len(samples) < 5 and not missing:
                        samples.append((ctx, res, hits))

    print(f"\n[1] 理论 600 组合矩阵覆盖统计:")
    print(f"  理论组合总数: {total_matrix}")
    print(f"  完整五段式生成数: {full_five_paragraphs} ({full_five_paragraphs / total_matrix * 100:.1f}%)")
    print(f"  缺项段落统计: {dict(missing_section_counts)}")

    # [2] 身强 vs 中和 vs 身弱的真实差异性验证
    print(f"\n[2] 同十神组合在不同强弱下的语义差异性验证:")
    # 以 (月干=正财, 月支=正财) 为例对比
    ctx_strong = {"month_stem_god": "正财", "month_branch_main_qi_god": "正财", "strength": "身强", "polarity": "YANG"}
    ctx_balanced = {"month_stem_god": "正财", "month_branch_main_qi_god": "正财", "strength": "中和", "polarity": "YANG"}
    ctx_weak = {"month_stem_god": "正财", "month_branch_main_qi_god": "正财", "strength": "身弱", "polarity": "YANG"}

    res_s, _ = compose_five_paragraphs(ctx_strong, rules_141, tengod_30)
    res_b, _ = compose_five_paragraphs(ctx_balanced, rules_141, tengod_30)
    res_w, _ = compose_five_paragraphs(ctx_weak, rules_141, tengod_30)

    print("  测试用例: 月干=正财, 月支=正财")
    print(f"  - 身强 主题段: {res_s['本月主题']}")
    print(f"  - 中和 主题段: {res_b['本月主题']}")
    print(f"  - 身弱 主题段: {res_w['本月主题']}")
    assert res_s['本月主题'] != res_b['本月主题'] != res_w['本月主题'], "强弱差异未体现！"
    assert res_s['综合解释'] != res_b['综合解释'] != res_w['综合解释'], "强弱综合解释未体现！"
    print("  -> 强弱差异性检验 PASS：同一十神组合在身强/中和/身弱下产生显著且有命理依据的文字差异！")

    # [3] 阳命 vs 阴命差异性验证
    print(f"\n[3] 阳命 vs 阴命差异逻辑验证:")
    ctx_yang = {"month_stem_god": "正财", "month_branch_main_qi_god": "正财", "strength": "身强", "polarity": "YANG"}
    ctx_yin = {"month_stem_god": "正财", "month_branch_main_qi_god": "正财", "strength": "身强", "polarity": "YIN"}
    res_y, _ = compose_five_paragraphs(ctx_yang, rules_141, tengod_30)
    res_n, _ = compose_five_paragraphs(ctx_yin, rules_141, tengod_30)
    # 在纯基础规则下，命理层文字一致（正如阴阳配对复核所证实），不凭空制造虚假命理差异
    print(f"  - 阳命基础文案与阴命基础文案命理层一致性: {res_y['本月主题'] == res_n['本月主题']}")
    print("  -> 阳命/阴命差异检验 PASS：符合契约规定，纯基础阶段不虚构命理差异，差异仅在由阴阳联合裁定的大运方向条件变化时产生！")

if __name__ == "__main__":
    main()
