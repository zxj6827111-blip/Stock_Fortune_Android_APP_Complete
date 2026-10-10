"""dayun_core.py —— 股运通 V1.3 大运与起运核心算法库。

遵循 V1.3_ALGORITHM_CONTRACT.md 与 ADR-0001, ADR-0002, ADR-0004, ADR-0005。
- 大运顺逆方向由年干阴阳与有效首日阴阳共同判定：
    direction = FORWARD if (year_is_yang == proxy_is_yang) else REVERSE
- 平盘（315 只）、缺失与冲突样本显式标记为 unavailable，不排大运干支。
- 逐周期干支步进序列与 direction 强校验，不自洽立即熔断。
- 适配 lunar-python 1.4.8。
"""

from __future__ import annotations

import datetime as dt
from typing import Any

from lunar_python import Solar

RULE_VERSION = "stock-luck-cycle-v1.3"

STEMS = ("甲", "乙", "丙", "丁", "戊", "己", "庚", "辛", "壬", "癸")
BRANCHES = ("子", "丑", "寅", "卯", "辰", "巳", "午", "未", "申", "酉", "戌", "亥")

STEM_YANG = {
    "甲": True, "乙": False,
    "丙": True, "丁": False,
    "戊": True, "己": False,
    "庚": True, "辛": False,
    "壬": True, "癸": False,
}

# 12 节气名（用于换月交节判定）
JIE_TERMS = {
    "立春", "惊蛰", "清明", "立夏", "芒种", "小暑",
    "立秋", "白露", "寒露", "立冬", "大雪", "小寒",
}


def resolve_first_day_polarity(
    first_change: float | None,
    first_day_flag: str,
) -> tuple[str, str]:
    """判定股票首日命别极性及状态码。

    Returns:
        (polarity, reason)
        polarity in {"yang", "yin", "flat", "missing", "conflict"}
    """
    flag = (first_day_flag or "").strip()
    if flag in {"数据缺失", "缺失", "UNKNOWN", "MISSING"} or first_change is None:
        return "missing", "FIRST_DAY_FLAG_MISSING"

    # 平盘 315 只样本判定
    if first_change == 0.0:
        return "flat", "FIRST_DAY_FLAT_UNRESOLVED"

    if first_change > 0.0:
        if flag in {"阳", "1", "YANG"}:
            return "yang", "OK"
        return "conflict", "FIRST_DAY_FLAG_CONFLICT"

    if first_change < 0.0:
        if flag in {"阴", "-1", "YIN"}:
            return "yin", "OK"
        return "conflict", "FIRST_DAY_FLAG_CONFLICT"

    return "missing", "FIRST_DAY_FLAG_MISSING"


def is_solar_term_boundary(listing_date: dt.date) -> bool:
    """检查上市日是否恰逢节气交节当日（立春或十二节）。"""
    solar = Solar.fromYmdHms(listing_date.year, listing_date.month, listing_date.day, 12, 0, 0)
    term = solar.getLunar().getJieQi()
    return bool(term and (term in JIE_TERMS or term == "立春"))


def calculate_stock_luck_cycle(
    listing_date: dt.date,
    year_pillar: str,
    month_pillar: str,
    day_pillar: str,
    hour_pillar: str,
    first_change: float | None,
    first_day_flag: str,
    *,
    cycle_count: int = 12,
) -> dict[str, Any]:
    """计算单只股票的大运元数据与大运周期序列。

    严格遵循 ADR-0001 和 ADR-0002。
    """
    polarity, reason = resolve_first_day_polarity(first_change, first_day_flag)
    boundary_flag = "solar_term_boundary" if is_solar_term_boundary(listing_date) else None

    # 非有效样本直接返回 unavailable
    if polarity in {"flat", "missing", "conflict"}:
        return {
            "direction": "unavailable",
            "status": f"unavailable_{polarity}",
            "status_reason": reason,
            "start_date": None,
            "start_age": None,
            "first_day_polarity": polarity,
            "rule_version": RULE_VERSION,
            "boundary_flag": boundary_flag,
            "periods": [],
        }

    year_stem = year_pillar[0]
    if year_stem not in STEM_YANG:
        raise ValueError(f"未知年干：{year_stem}，无法判定年干阴阳")

    year_is_yang = STEM_YANG[year_stem]
    proxy_is_yang = (polarity == "yang")

    # 核心裁定（ADR-0001）：年干与首日命别同阴阳顺行，异阴阳逆行
    direction = "forward" if (year_is_yang == proxy_is_yang) else "reverse"

    # lunar-python 映射：
    # 阳命传 1 (男命)，阴命传 0 (女命)。
    # lunar-python 内部：阳年男顺女逆，阴年男逆女顺。
    # - 阳年 + 阳命(1) -> 顺行 (forward)
    # - 阳年 + 阴命(0) -> 逆行 (reverse)
    # - 阴年 + 阳命(1) -> 逆行 (reverse)
    # - 阴年 + 阴命(0) -> 顺行 (forward)
    # 与 direction 完全恒等！
    gender = 1 if proxy_is_yang else 0

    # 默认按股票上市开盘时刻 09:30:00 计算
    solar = Solar.fromYmdHms(listing_date.year, listing_date.month, listing_date.day, 9, 30, 0)
    lunar = solar.getLunar()

    # 边界对齐（ADR-0005）：
    # 本项目日历库采用日粒度标准（交节当日 00:00 换柱），当上市日恰逢节气交接且交节时刻晚于 09:30 时，
    # 09:30 在天文时刻上尚未过节，会导致 lunar-python 读出旧柱。
    # 为保证大运序列与 stock_bazi 中已入库的年月日柱 100% 同源自洽，在交节当日对齐至交节后时段。
    if (lunar.getYearInGanZhiExact(), lunar.getMonthInGanZhiExact()) != (year_pillar, month_pillar):
        solar = Solar.fromYmdHms(listing_date.year, listing_date.month, listing_date.day, 23, 59, 59)
        lunar = solar.getLunar()

    eight_char = lunar.getEightChar()
    yun = eight_char.getYun(gender, 1)

    start_solar = yun.getStartSolar()
    start_date = start_solar.toYmd()
    start_age = int(yun.getStartYear())

    # 提取周期
    dayuns = yun.getDaYun(cycle_count + 1)
    periods: list[dict[str, Any]] = []

    for dayun in dayuns:
        idx = int(dayun.getIndex())
        if idx < 1 or idx > cycle_count:
            continue

        ganzhi = str(dayun.getGanZhi())
        if len(ganzhi) != 2:
            raise ValueError(f"非法大运干支：{ganzhi}")

        stem, branch = ganzhi[0], ganzhi[1]
        start_period_solar = start_solar.nextYear((idx - 1) * 10)
        end_period_solar = start_solar.nextYear(idx * 10)

        periods.append({
            "cycle_index": idx,
            "ganzhi": ganzhi,
            "stem": stem,
            "branch": branch,
            "start_date": start_period_solar.toYmd(),
            "end_date": end_period_solar.toYmd(),
            "start_year": int(dayun.getStartYear()),
            "end_year": int(dayun.getEndYear()),
            "start_age": int(dayun.getStartAge()),
            "end_age": int(dayun.getEndAge()),
            "rule_version": RULE_VERSION,
        })

    # 自洽性硬校验：校验干支序列相对于月柱的步进方向
    _verify_cycle_stepping(month_pillar, direction, periods)

    return {
        "direction": direction,
        "status": "available",
        "status_reason": "OK",
        "start_date": start_date,
        "start_age": start_age,
        "first_day_polarity": polarity,
        "rule_version": RULE_VERSION,
        "boundary_flag": boundary_flag,
        "periods": periods,
    }


def _verify_cycle_stepping(
    month_pillar: str,
    direction: str,
    periods: list[dict[str, Any]],
) -> None:
    """严格校验大运干支序列的步进方向，不自洽则直接抛错。"""
    if not periods:
        return

    expected_stem_step = 1 if direction == "forward" else 9
    expected_branch_step = 1 if direction == "forward" else 11

    # 首运与月柱的关系
    month_stem_idx = STEMS.index(month_pillar[0])
    month_branch_idx = BRANCHES.index(month_pillar[1])
    first_stem_idx = STEMS.index(periods[0]["stem"])
    first_branch_idx = BRANCHES.index(periods[0]["branch"])

    if (first_stem_idx - month_stem_idx) % 10 != expected_stem_step:
        raise ValueError(
            f"首运天干步进不符：月柱 {month_pillar} -> 首运 {periods[0]['ganzhi']}，"
            f"direction={direction}, step={(first_stem_idx - month_stem_idx) % 10}"
        )
    if (first_branch_idx - month_branch_idx) % 12 != expected_branch_step:
        raise ValueError(
            f"首运地支步进不符：月柱 {month_pillar} -> 首运 {periods[0]['ganzhi']}，"
            f"direction={direction}, step={(first_branch_idx - month_branch_idx) % 12}"
        )

    # 后续运与前一运的关系
    for i in range(1, len(periods)):
        prev = periods[i - 1]
        curr = periods[i]
        p_stem_idx = STEMS.index(prev["stem"])
        c_stem_idx = STEMS.index(curr["stem"])
        p_branch_idx = BRANCHES.index(prev["branch"])
        c_branch_idx = BRANCHES.index(curr["branch"])

        if (c_stem_idx - p_stem_idx) % 10 != expected_stem_step:
            raise ValueError(
                f"第 {curr['cycle_index']} 步大运天干步进不符：{prev['ganzhi']} -> {curr['ganzhi']}"
            )
        if (c_branch_idx - p_branch_idx) % 12 != expected_branch_step:
            raise ValueError(
                f"第 {curr['cycle_index']} 步大运地支步进不符：{prev['ganzhi']} -> {curr['ganzhi']}"
            )
