"""relation_core.py —— 原局内部关系与时间互动引擎（22 类关系目录）。

与 StockFortuneAndroid/domain/calculator/RelationEngine.kt 保持 100% 对齐。
严格基于三柱六字（年/月/日），时柱不参与关系识别。
"""

from __future__ import annotations

from dataclasses import dataclass
from typing import Dict, List, Optional, Set, Tuple

# 22 类关系目录定义
RELATION_CATALOG = {
    "天干": ["天干五合", "天干相冲", "天干生", "天干受生", "天干克", "天干受克", "天干同五行"],
    "地支": ["六合", "六冲", "三合", "半合", "三会", "相刑", "三刑", "自刑", "相害", "六破", "同支"],
    "组合": ["伏吟", "反吟", "天合地合", "天克地冲"],
}

RELATION_TYPES: Tuple[str, ...] = tuple(
    rel for rels in RELATION_CATALOG.values() for rel in rels
)

RELATION_CATEGORIES: Dict[str, str] = {
    "天干五合": "干合",
    "天干相冲": "干冲",
    "天干生": "生克",
    "天干受生": "生克",
    "天干克": "生克",
    "天干受克": "生克",
    "天干同五行": "生克",
    "六合": "支合",
    "三合": "支合",
    "半合": "支合",
    "三会": "支合",
    "六冲": "支冲",
    "相刑": "支刑",
    "三刑": "支刑",
    "自刑": "支刑",
    "相害": "支害",
    "六破": "支破",
    "同支": "支同",
    "天合地合": "复合",
    "天克地冲": "复合",
    "伏吟": "特殊",
    "反吟": "特殊",
}

STEMS = ("甲", "乙", "丙", "丁", "戊", "己", "庚", "辛", "壬", "癸")
BRANCHES = ("子", "丑", "寅", "卯", "辰", "巳", "午", "未", "申", "酉", "戌", "亥")

STEM_WUXING = {
    "甲": "木", "乙": "木", "丙": "火", "丁": "火", "戊": "土",
    "己": "土", "庚": "金", "辛": "金", "壬": "水", "癸": "水",
}

BRANCH_WUXING = {
    "子": "水", "丑": "土", "寅": "木", "卯": "木", "辰": "土", "巳": "火",
    "午": "火", "未": "土", "申": "金", "酉": "金", "戌": "土", "亥": "水",
}

WUXING_GENERATES = {"木": "火", "火": "土", "土": "金", "金": "水", "水": "木"}
WUXING_OVERCOMES = {"木": "土", "土": "水", "水": "火", "火": "金", "金": "木"}

STEM_FIVE_HARMONY: Dict[Tuple[str, str], str] = {
    ("甲", "己"): "土", ("乙", "庚"): "金", ("丙", "辛"): "水",
    ("丁", "壬"): "木", ("戊", "癸"): "火",
}

STEM_HARMONY_KEYS: Set[Tuple[str, str]] = {
    tuple(sorted((a, b))) for a, b in STEM_FIVE_HARMONY.keys()
}

STEM_CLASH_PAIRS: Set[Tuple[str, str]] = {
    ("甲", "庚"), ("乙", "辛"), ("丙", "壬"), ("丁", "癸"),
}
STEM_CLASH_KEYS: Set[Tuple[str, str]] = {
    tuple(sorted((a, b))) for a, b in STEM_CLASH_PAIRS
}

BRANCH_SIX_HARMONY: Dict[Tuple[str, str], str] = {
    ("子", "丑"): "土", ("寅", "亥"): "木", ("卯", "戌"): "火",
    ("辰", "酉"): "金", ("巳", "申"): "水", ("午", "未"): "土",
}
BRANCH_HARMONY_OF: Dict[str, str] = {}
for (a, b), _ in BRANCH_SIX_HARMONY.items():
    BRANCH_HARMONY_OF[a] = b
    BRANCH_HARMONY_OF[b] = a

BRANCH_SIX_CLASH: Set[Tuple[str, str]] = {
    ("子", "午"), ("丑", "未"), ("寅", "申"), ("卯", "酉"), ("辰", "戌"), ("巳", "亥"),
}
BRANCH_CLASH_OF: Dict[str, str] = {}
for a, b in BRANCH_SIX_CLASH:
    BRANCH_CLASH_OF[a] = b
    BRANCH_CLASH_OF[b] = a

BRANCH_SIX_HARM: Set[Tuple[str, str]] = {
    ("子", "未"), ("丑", "午"), ("寅", "巳"), ("卯", "辰"), ("申", "亥"), ("酉", "戌"),
}
BRANCH_HARM_OF: Dict[str, str] = {}
for a, b in BRANCH_SIX_HARM:
    BRANCH_HARM_OF[a] = b
    BRANCH_HARM_OF[b] = a

BRANCH_SIX_BREAK: Set[Tuple[str, str]] = {
    ("子", "酉"), ("丑", "辰"), ("寅", "亥"), ("卯", "午"), ("巳", "申"), ("未", "戌"),
}
BRANCH_BREAK_OF: Dict[str, str] = {}
for a, b in BRANCH_SIX_BREAK:
    BRANCH_BREAK_OF[a] = b
    BRANCH_BREAK_OF[b] = a

BRANCH_TRIPLE_HARMONY: Dict[Tuple[str, str, str], str] = {
    ("申", "子", "辰"): "水",
    ("亥", "卯", "未"): "木",
    ("寅", "午", "戌"): "火",
    ("巳", "酉", "丑"): "金",
}

BRANCH_TRIPLE_MEETING: Dict[Tuple[str, str, str], str] = {
    ("寅", "卯", "辰"): "木",
    ("巳", "午", "未"): "火",
    ("申", "酉", "戌"): "金",
    ("亥", "子", "丑"): "水",
}

PUNISHMENT_GROUPS: Tuple[Tuple[str, ...], ...] = (
    ("寅", "巳", "申"),  # 无恩之刑
    ("丑", "戌", "未"),  # 恃势之刑
    ("子", "卯"),        # 无礼之刑
)

SELF_PUNISHMENTS = frozenset({"辰", "午", "酉", "亥"})

RULE_VERSION = "natal-relation-v1.3"


def pair_key(a: str, b: str) -> Tuple[str, str]:
    return (a, b) if a <= b else (b, a)


def punishment_hit(a: str, b: str) -> Optional[str]:
    """判断两支是否刑，返回刑名或组描述。"""
    if a == b and a in SELF_PUNISHMENTS:
        return f"{a}{a}自刑"
    for grp in PUNISHMENT_GROUPS:
        if a in grp and b in grp and a != b:
            return "".join(sorted(grp, key=lambda x: BRANCHES.index(x)))
    return None


@dataclass
class RelationEvent:
    relation_type: str
    category: str
    source_pillar: str
    target_pillar: str
    source_ganzhi: str
    target_ganzhi: str
    element: str = ""
    notes: str = ""
    rule_version: str = RULE_VERSION
    status: str = "confirmed"


def _stem_events(
    source_ganzhi: str,
    target_ganzhi: str,
    source_pillar: str,
    target_pillar: str,
) -> List[RelationEvent]:
    events: List[RelationEvent] = []
    s_stem, t_stem = source_ganzhi[0], target_ganzhi[0]
    s_wx, t_wx = STEM_WUXING.get(s_stem, ""), STEM_WUXING.get(t_stem, "")
    pkey = pair_key(s_stem, t_stem)

    if pkey in STEM_HARMONY_KEYS:
        elem = STEM_FIVE_HARMONY.get((s_stem, t_stem)) or STEM_FIVE_HARMONY.get((t_stem, s_stem), "")
        events.append(RelationEvent(
            relation_type="天干五合",
            category="干合",
            source_pillar=source_pillar,
            target_pillar=target_pillar,
            source_ganzhi=source_ganzhi,
            target_ganzhi=target_ganzhi,
            element=elem,
            notes=f"{s_stem}{t_stem}天干五合（合化{elem}）",
        ))

    if pkey in STEM_CLASH_KEYS:
        events.append(RelationEvent(
            relation_type="天干相冲",
            category="干冲",
            source_pillar=source_pillar,
            target_pillar=target_pillar,
            source_ganzhi=source_ganzhi,
            target_ganzhi=target_ganzhi,
            element="",
            notes=f"{s_stem}{t_stem}天干相冲",
        ))

    if s_wx and s_wx == t_wx:
        events.append(RelationEvent(
            relation_type="天干同五行",
            category="生克",
            source_pillar=source_pillar,
            target_pillar=target_pillar,
            source_ganzhi=source_ganzhi,
            target_ganzhi=target_ganzhi,
            element=s_wx,
            notes=f"天干同属{s_wx}",
        ))
    elif s_wx and t_wx and WUXING_GENERATES.get(s_wx) == t_wx:
        events.append(RelationEvent(
            relation_type="天干生",
            category="生克",
            source_pillar=source_pillar,
            target_pillar=target_pillar,
            source_ganzhi=source_ganzhi,
            target_ganzhi=target_ganzhi,
            element=t_wx,
            notes=f"{s_wx}生{t_wx}",
        ))
    elif s_wx and t_wx and WUXING_GENERATES.get(t_wx) == s_wx:
        events.append(RelationEvent(
            relation_type="天干受生",
            category="生克",
            source_pillar=source_pillar,
            target_pillar=target_pillar,
            source_ganzhi=source_ganzhi,
            target_ganzhi=target_ganzhi,
            element=s_wx,
            notes=f"{t_wx}生{s_wx}",
        ))

    if s_wx and t_wx and WUXING_OVERCOMES.get(s_wx) == t_wx:
        events.append(RelationEvent(
            relation_type="天干克",
            category="生克",
            source_pillar=source_pillar,
            target_pillar=target_pillar,
            source_ganzhi=source_ganzhi,
            target_ganzhi=target_ganzhi,
            element=t_wx,
            notes=f"{s_wx}克{t_wx}",
        ))
    elif s_wx and t_wx and WUXING_OVERCOMES.get(t_wx) == s_wx:
        events.append(RelationEvent(
            relation_type="天干受克",
            category="生克",
            source_pillar=source_pillar,
            target_pillar=target_pillar,
            source_ganzhi=source_ganzhi,
            target_ganzhi=target_ganzhi,
            element=s_wx,
            notes=f"{t_wx}克{s_wx}",
        ))

    return events


def _branch_events(
    source_ganzhi: str,
    target_ganzhi: str,
    source_pillar: str,
    target_pillar: str,
) -> List[RelationEvent]:
    events: List[RelationEvent] = []
    a, b = source_ganzhi[1], target_ganzhi[1]

    if a == b:
        events.append(RelationEvent(
            relation_type="同支",
            category="支同",
            source_pillar=source_pillar,
            target_pillar=target_pillar,
            source_ganzhi=source_ganzhi,
            target_ganzhi=target_ganzhi,
            element=BRANCH_WUXING.get(a, ""),
            notes=f"{a}{b}同支",
        ))
        if a in SELF_PUNISHMENTS:
            events.append(RelationEvent(
                relation_type="自刑",
                category="支刑",
                source_pillar=source_pillar,
                target_pillar=target_pillar,
                source_ganzhi=source_ganzhi,
                target_ganzhi=target_ganzhi,
                element="",
                notes=f"{a}{b}自刑",
            ))

    if BRANCH_HARMONY_OF.get(b) == a:
        elem = BRANCH_SIX_HARMONY.get((a, b)) or BRANCH_SIX_HARMONY.get((b, a), "")
        events.append(RelationEvent(
            relation_type="六合",
            category="支合",
            source_pillar=source_pillar,
            target_pillar=target_pillar,
            source_ganzhi=source_ganzhi,
            target_ganzhi=target_ganzhi,
            element=elem,
            notes=f"{a}{b}六合（合化{elem}）",
        ))

    if BRANCH_CLASH_OF.get(b) == a:
        events.append(RelationEvent(
            relation_type="六冲",
            category="支冲",
            source_pillar=source_pillar,
            target_pillar=target_pillar,
            source_ganzhi=source_ganzhi,
            target_ganzhi=target_ganzhi,
            element="",
            notes=f"{a}{b}六冲",
        ))

    if BRANCH_HARM_OF.get(b) == a:
        events.append(RelationEvent(
            relation_type="相害",
            category="支害",
            source_pillar=source_pillar,
            target_pillar=target_pillar,
            source_ganzhi=source_ganzhi,
            target_ganzhi=target_ganzhi,
            element="",
            notes=f"{a}{b}相害",
        ))

    if BRANCH_BREAK_OF.get(b) == a:
        events.append(RelationEvent(
            relation_type="六破",
            category="支破",
            source_pillar=source_pillar,
            target_pillar=target_pillar,
            source_ganzhi=source_ganzhi,
            target_ganzhi=target_ganzhi,
            element="",
            notes=f"{a}{b}六破",
        ))

    pun = punishment_hit(a, b)
    if pun and a != b:
        events.append(RelationEvent(
            relation_type="相刑",
            category="支刑",
            source_pillar=source_pillar,
            target_pillar=target_pillar,
            source_ganzhi=source_ganzhi,
            target_ganzhi=target_ganzhi,
            element="",
            notes=f"{a}{b}相刑（{pun}）",
        ))

    return events


def _compound_events(
    source_ganzhi: str,
    target_ganzhi: str,
    source_pillar: str,
    target_pillar: str,
    existing_events: List[RelationEvent],
) -> List[RelationEvent]:
    events: List[RelationEvent] = []
    types = {ev.relation_type for ev in existing_events}

    if source_ganzhi == target_ganzhi:
        events.append(RelationEvent(
            relation_type="伏吟",
            category="特殊",
            source_pillar=source_pillar,
            target_pillar=target_pillar,
            source_ganzhi=source_ganzhi,
            target_ganzhi=target_ganzhi,
            element="",
            notes=f"{source_ganzhi}柱位相同伏吟",
        ))

    if "天干五合" in types and "六合" in types:
        events.append(RelationEvent(
            relation_type="天合地合",
            category="复合",
            source_pillar=source_pillar,
            target_pillar=target_pillar,
            source_ganzhi=source_ganzhi,
            target_ganzhi=target_ganzhi,
            element="",
            notes=f"{source_ganzhi}与{target_ganzhi}天合地合",
        ))

    if "天干克" in types and "六冲" in types:
        events.append(RelationEvent(
            relation_type="天克地冲",
            category="复合",
            source_pillar=source_pillar,
            target_pillar=target_pillar,
            source_ganzhi=source_ganzhi,
            target_ganzhi=target_ganzhi,
            element="",
            notes=f"{source_ganzhi}与{target_ganzhi}天克地冲",
        ))

    if ({"天干克", "天干相冲"} & types) and "六冲" in types:
        events.append(RelationEvent(
            relation_type="反吟",
            category="特殊",
            source_pillar=source_pillar,
            target_pillar=target_pillar,
            source_ganzhi=source_ganzhi,
            target_ganzhi=target_ganzhi,
            element="",
            notes=f"{source_ganzhi}与{target_ganzhi}反吟（干冲克且支相冲）",
        ))

    return events


def compute_pair_relations(
    source_ganzhi: str,
    target_ganzhi: str,
    source_pillar: str,
    target_pillar: str,
) -> List[RelationEvent]:
    """计算两柱之间的全部两两关系（天干、地支、组合）。"""
    events = _stem_events(source_ganzhi, target_ganzhi, source_pillar, target_pillar)
    events.extend(_branch_events(source_ganzhi, target_ganzhi, source_pillar, target_pillar))
    compounds = _compound_events(source_ganzhi, target_ganzhi, source_pillar, target_pillar, events)
    events.extend(compounds)
    return events


def compute_natal_internal_relations(
    year_ganzhi: str,
    month_ganzhi: str,
    day_ganzhi: str,
) -> List[RelationEvent]:
    """原局三柱内部关系（年/月/日 3×3）。

    包含：
    1. 两两柱位关系：(年, 月), (年, 日), (月, 日)
    2. 多支复合结构：三合（及半合）、三会、三刑
    """
    pillars = [("year", year_ganzhi), ("month", month_ganzhi), ("day", day_ganzhi)]
    all_events: List[RelationEvent] = []

    # 1. 两两关系
    for i in range(len(pillars)):
        for j in range(i + 1, len(pillars)):
            pos_a, gz_a = pillars[i]
            pos_b, gz_b = pillars[j]
            pair_events = compute_pair_relations(gz_a, gz_b, pos_a, pos_b)
            all_events.extend(pair_events)

    # 2. 多支复合结构
    branches_map = {pos: gz[1] for pos, gz in pillars}
    present_branches = list(branches_map.values())

    # 三合局与半合
    for combo, elem in BRANCH_TRIPLE_HARMONY.items():
        matched_positions = [pos for pos, b in branches_map.items() if b in combo]
        matched_branches = [branches_map[p] for p in matched_positions]
        unique_matched_branches = set(matched_branches)
        if len(unique_matched_branches) == 3:
            all_events.append(RelationEvent(
                relation_type="三合",
                category="支合",
                source_pillar=",".join(matched_positions),
                target_pillar=",".join(matched_positions),
                source_ganzhi=",".join(year_ganzhi if p == "year" else (month_ganzhi if p == "month" else day_ganzhi) for p in matched_positions),
                target_ganzhi=",".join(matched_branches),
                element=elem,
                notes=f"{''.join(combo)}原局三合{elem}局",
            ))
        elif len(unique_matched_branches) == 2:
            # 半合：两个地支匹配三合局中的两个
            pair_pos = [pos for pos in matched_positions if branches_map[pos] in unique_matched_branches]
            # 去重保留一对不同支
            seen_b = set()
            distinct_pos = []
            for p in pair_pos:
                b = branches_map[p]
                if b not in seen_b:
                    seen_b.add(b)
                    distinct_pos.append(p)
            if len(distinct_pos) == 2:
                p1, p2 = distinct_pos[0], distinct_pos[1]
                b1, b2 = branches_map[p1], branches_map[p2]
                all_events.append(RelationEvent(
                    relation_type="半合",
                    category="支合",
                    source_pillar=p1,
                    target_pillar=p2,
                    source_ganzhi=next(gz for p, gz in pillars if p == p1),
                    target_ganzhi=next(gz for p, gz in pillars if p == p2),
                    element=elem,
                    notes=f"{b1}{b2}半合{elem}局（缺一）",
                ))

    # 三会方
    for combo, elem in BRANCH_TRIPLE_MEETING.items():
        matched_positions = [pos for pos, b in branches_map.items() if b in combo]
        matched_branches = [branches_map[p] for p in matched_positions]
        if len(set(matched_branches)) == 3:
            all_events.append(RelationEvent(
                relation_type="三会",
                category="支合",
                source_pillar=",".join(matched_positions),
                target_pillar=",".join(matched_positions),
                source_ganzhi=",".join(year_ganzhi if p == "year" else (month_ganzhi if p == "month" else day_ganzhi) for p in matched_positions),
                target_ganzhi=",".join(matched_branches),
                element=elem,
                notes=f"{''.join(combo)}原局三会{elem}方",
            ))

    # 三刑（寅巳申 或 丑戌未）
    for combo in (("寅", "巳", "申"), ("丑", "戌", "未")):
        matched_positions = [pos for pos, b in branches_map.items() if b in combo]
        matched_branches = [branches_map[p] for p in matched_positions]
        if len(set(matched_branches)) == 3:
            all_events.append(RelationEvent(
                relation_type="三刑",
                category="支刑",
                source_pillar=",".join(matched_positions),
                target_pillar=",".join(matched_positions),
                source_ganzhi=",".join(year_ganzhi if p == "year" else (month_ganzhi if p == "month" else day_ganzhi) for p in matched_positions),
                target_ganzhi=",".join(matched_branches),
                element="",
                notes=f"{''.join(combo)}原局三刑",
            ))

    return all_events


def compute_external_interaction(
    source_ganzhi: str,
    source_pillar: str,
    natal_year: str,
    natal_month: str,
    natal_day: str,
) -> List[RelationEvent]:
    """计算单个外部柱（如流年/流月/大运）与原局三柱的关系事件。

    涵盖：
    1. 外部柱与年/月/日各单元格的两两关系
    2. 外部柱参与构成的三合、半合、三会、三刑
    """
    natal_pillars = [("year", natal_year), ("month", natal_month), ("day", natal_day)]
    events: List[RelationEvent] = []

    # 1. 与各原局柱两两关系
    for target_pillar, target_ganzhi in natal_pillars:
        cell_events = compute_pair_relations(source_ganzhi, target_ganzhi, source_pillar, target_pillar)
        events.extend(cell_events)

    # 2. 多支复合关系
    s_branch = source_ganzhi[1]
    branches_map = {pos: gz[1] for pos, gz in natal_pillars}

    # 三合局与半合（外部柱参与）
    for combo, elem in BRANCH_TRIPLE_HARMONY.items():
        if s_branch not in combo:
            continue
        others = [b for b in combo if b != s_branch]
        matched = [pos for pos, b in branches_map.items() if b in others]
        unique_matched_branches = {branches_map[p] for p in matched}
        if len(unique_matched_branches) >= 2:
            # 外部柱 + 原局 2 支 = 三合局
            for target_p in matched[:2]:
                events.append(RelationEvent(
                    relation_type="三合",
                    category="支合",
                    source_pillar=source_pillar,
                    target_pillar=target_p,
                    source_ganzhi=source_ganzhi,
                    target_ganzhi=next(gz for p, gz in natal_pillars if p == target_p),
                    element=elem,
                    notes=f"{''.join(combo)}三合{elem}局",
                ))
        elif len(unique_matched_branches) == 1:
            # 外部柱 + 原局 1 支 = 半合局
            target_p = matched[0]
            events.append(RelationEvent(
                relation_type="半合",
                category="支合",
                source_pillar=source_pillar,
                target_pillar=target_p,
                source_ganzhi=source_ganzhi,
                target_ganzhi=next(gz for p, gz in natal_pillars if p == target_p),
                element=elem,
                notes=f"{s_branch}{branches_map[target_p]}半合{elem}局（缺一）",
            ))

    # 三会方（外部柱参与）
    for combo, elem in BRANCH_TRIPLE_MEETING.items():
        if s_branch not in combo:
            continue
        others = [b for b in combo if b != s_branch]
        matched = [pos for pos, b in branches_map.items() if b in others]
        if len(set(branches_map[p] for p in matched)) >= 2:
            for target_p in matched[:2]:
                events.append(RelationEvent(
                    relation_type="三会",
                    category="支合",
                    source_pillar=source_pillar,
                    target_pillar=target_p,
                    source_ganzhi=source_ganzhi,
                    target_ganzhi=next(gz for p, gz in natal_pillars if p == target_p),
                    element=elem,
                    notes=f"{''.join(combo)}三会{elem}方",
                ))

    # 三刑（外部柱参与）
    for combo in (("寅", "巳", "申"), ("丑", "戌", "未")):
        if s_branch not in combo:
            continue
        others = [b for b in combo if b != s_branch]
        matched = [pos for pos, b in branches_map.items() if b in others]
        if len(set(branches_map[p] for p in matched)) >= 2:
            for target_p in matched[:2]:
                events.append(RelationEvent(
                    relation_type="三刑",
                    category="支刑",
                    source_pillar=source_pillar,
                    target_pillar=target_p,
                    source_ganzhi=source_ganzhi,
                    target_ganzhi=next(gz for p, gz in natal_pillars if p == target_p),
                    element="",
                    notes=f"{''.join(combo)}三刑",
                ))

    return events
