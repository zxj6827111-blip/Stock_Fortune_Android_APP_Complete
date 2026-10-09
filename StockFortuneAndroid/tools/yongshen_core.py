"""yongshen_core.py —— 喜用候选与格局解释计算内核（V1.3 冻结口径）。

规范遵循：
  1. 与 Android 六字强弱（年月日三柱）同源推导，严禁使用时柱巳时常数项；
  2. 扶抑主轴与调候双轴完全解耦，调候提示作为环境观察，绝不直接改写扶抑五行；
  3. 支持三态枚举：confirmed（身强/身弱明确）、candidate（中和平衡不强判单一用神）、unavailable（不适用）；
  4. 底层数据保留命理学学术术语 ji_shen/chou_shen，对外渲染通过 LabelMapper 严禁出现禁词「忌」；
  5. 不使用虚假置信度。
"""

from __future__ import annotations

import bazi_core as bc

RULE_VERSION = "yongshen-candidate-v1.3"

WU_XING_ORDER = ("木", "火", "土", "金", "水")

GENERATED_BY = {
    "木": "水",
    "火": "木",
    "土": "火",
    "金": "土",
    "水": "金",
}

OVERCOME_BY = {
    "木": "金",
    "火": "水",
    "土": "木",
    "金": "火",
    "水": "土",
}

STATUS_CONFIRMED = "confirmed"
STATUS_CANDIDATE = "candidate"
STATUS_UNAVAILABLE = "unavailable"


def compute_tiaohou_note(month_branch: str) -> str:
    """调候环境观察（独立双轴，仅作环境观察，不改写扶抑五行集合）。"""
    if not month_branch or len(month_branch) != 1:
        return ""
    if month_branch in ("亥", "子", "丑"):
        return "冬月生，天寒地冻，调候宜见火（暖局）"
    if month_branch in ("巳", "午", "未"):
        return "夏月生，火燥水枯，调候宜见水（润局）"
    if month_branch in ("辰", "戌"):
        return "季月土重，调候宜木疏土或水润泽"
    if month_branch in ("寅", "卯", "申", "酉"):
        return "春秋月生，寒暖适中，调候需求平和"
    return ""


def compute_yongshen_candidate(
    year_pillar: str,
    month_pillar: str,
    day_pillar: str,
) -> dict:
    """基于年月日三柱六字绝对分计算喜用候选与调候提示。"""
    # 基础合法性检验
    if (
        not year_pillar
        or not month_pillar
        or not day_pillar
        or len(year_pillar) != 2
        or len(month_pillar) != 2
        or len(day_pillar) != 2
    ):
        return {
            "chart_key": f"{year_pillar}_{month_pillar}_{day_pillar}",
            "day_stem": day_pillar[0] if day_pillar else "",
            "month_branch": month_pillar[1] if month_pillar else "",
            "strength_score": 0.0,
            "strength_level": "未知",
            "status": STATUS_UNAVAILABLE,
            "yong_shen": [],
            "xi_shen": [],
            "ji_shen": [],
            "chou_shen": [],
            "xian_shen": [],
            "candidate_elements": [],
            "tiaohou_note": "",
            "rationale": "原局柱位数据不完整，暂不适用基础扶抑推导",
            "rule_version": RULE_VERSION,
        }

    day_stem = day_pillar[0]
    month_branch = month_pillar[1]
    chart_key = f"{year_pillar}_{month_pillar}_{day_pillar}"

    if day_stem not in bc.STEM_ELEMENT:
        return {
            "chart_key": chart_key,
            "day_stem": day_stem,
            "month_branch": month_branch,
            "strength_score": 0.0,
            "strength_level": "未知",
            "status": STATUS_UNAVAILABLE,
            "yong_shen": [],
            "xi_shen": [],
            "ji_shen": [],
            "chou_shen": [],
            "xian_shen": [],
            "candidate_elements": [],
            "tiaohou_note": "",
            "rationale": f"未知日干「{day_stem}」，暂不适用基础扶抑推导",
            "rule_version": RULE_VERSION,
        }

    dm_wx = bc.STEM_ELEMENT[day_stem]
    same_wx = dm_wx
    resource_wx = GENERATED_BY[dm_wx]
    output_wx = bc.GENERATES[dm_wx]
    wealth_wx = bc.OVERCOMES[dm_wx]
    officer_wx = OVERCOME_BY[dm_wx]

    score = bc.strength_score(year_pillar, month_pillar, day_pillar)
    level = bc.day_master_strength(year_pillar, month_pillar, day_pillar)
    tiaohou = compute_tiaohou_note(month_branch)

    if level == "身强":
        status = STATUS_CONFIRMED
        yong_shen = [officer_wx]
        xi_shen = [output_wx, wealth_wx]
        ji_shen = [resource_wx, same_wx]
        chou_shen = [resource_wx]
        xian_shen = []
        candidates = [officer_wx, output_wx, wealth_wx]
        rationale = (
            f"日主身强（得分 {score:+.2f}）→ 力量充沛，宜克泄耗："
            f"取官杀「{officer_wx}」为用神，食伤「{output_wx}」与财星「{wealth_wx}」为喜神"
        )
    elif level == "身弱":
        status = STATUS_CONFIRMED
        yong_shen = [resource_wx]
        xi_shen = [same_wx]
        ji_shen = [officer_wx, wealth_wx]
        chou_shen = [output_wx]
        xian_shen = []
        candidates = [resource_wx, same_wx]
        rationale = (
            f"日主身弱（得分 {score:+.2f}）→ 力量偏弱，宜生助扶持："
            f"取印星「{resource_wx}」为用神，比劫「{same_wx}」为喜神"
        )
    else:  # 中和
        status = STATUS_CANDIDATE
        yong_shen = []
        xi_shen = []
        ji_shen = []
        chou_shen = []
        xian_shen = list(WU_XING_ORDER)
        candidates = [output_wx, wealth_wx, officer_wx]
        rationale = (
            f"日主中和（得分 {score:+.2f}）→ 力量均衡无明显偏枯，"
            f"不设单一扶抑主轴，以岁运顺畅流通为主"
        )

    return {
        "chart_key": chart_key,
        "day_stem": day_stem,
        "month_branch": month_branch,
        "strength_score": score,
        "strength_level": level,
        "status": status,
        "yong_shen": yong_shen,
        "xi_shen": xi_shen,
        "ji_shen": ji_shen,
        "chou_shen": chou_shen,
        "xian_shen": xian_shen,
        "candidate_elements": candidates,
        "tiaohou_note": tiaohou,
        "rationale": rationale,
        "rule_version": RULE_VERSION,
    }


def map_shen_label(raw_shen: str) -> str:
    """合规术语映射（ADR-0007）：将用户可见文案中的传统敏感字转换为合规典雅词。"""
    mapping = {
        "用神": "用神",
        "喜神": "喜神",
        "忌神": "制衡之神",
        "仇神": "耗身之神",
        "闲神": "调和之神",
    }
    return mapping.get(raw_shen, raw_shen)
