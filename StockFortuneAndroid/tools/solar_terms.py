"""solar_terms.py —— 节气"日粒度"口径。

数据源 `生辰八字.xlsx` 的 84 处年柱/月柱与时刻粒度实现的差异，全部落在交节当日
（例：2016-02-04 立春当日上市的股票，数据源记为丙申年，时刻粒度则因交节在 23:46 而仍属乙未年）。
本项目据此统一采用 **日粒度**：节气所在当日 00:00 起即算新柱，与数据源逐字一致。

兄弟项目 `stock-metaphysics-platform` 使用 lunar-python 的时刻粒度，因此立春/交节当日会与本
项目相差一柱——这是有意的口径选择，已在 PHASE0 §8 与交付报告中登记。
"""

from __future__ import annotations

import datetime as dt
import functools

from lunar_python import Solar

# 十二"节"（决定月柱切换），按寅月起始顺序
JIE_BY_MONTH_BRANCH = (
    ("寅", "立春"), ("卯", "惊蛰"), ("辰", "清明"), ("巳", "立夏"),
    ("午", "芒种"), ("未", "小暑"), ("申", "立秋"), ("酉", "白露"),
    ("戌", "寒露"), ("亥", "立冬"), ("子", "大雪"), ("丑", "小寒"),
)
LICHUN = "立春"


@functools.lru_cache(maxsize=None)
def term_dates(year: int) -> dict[str, dt.date]:
    """该公历年内所有节气的公历日期（按日）。"""
    out: dict[str, dt.date] = {}
    d = dt.date(year, 1, 1)
    while d.year == year:
        name = Solar.fromYmdHms(d.year, d.month, d.day, 12, 0, 0).getLunar().getJieQi()
        if name:
            out[name] = d
        d += dt.timedelta(days=1)
    return out


@functools.lru_cache(maxsize=None)
def lichun(year: int) -> dt.date:
    """`year` 年立春所在日；干支年 year（1984=甲子）自该日起生效。"""
    t = term_dates(year).get(LICHUN)
    if t is None:  # 理论上不会发生（立春必在 2 月）
        return dt.date(year, 2, 4)
    return t


@functools.lru_cache(maxsize=None)
def jie_day(month_branch: str, year: int) -> dt.date:
    """月支对应的"节"所在日；小寒属于当年，其余亦属当年。"""
    name = dict(JIE_BY_MONTH_BRANCH)[month_branch]
    t = term_dates(year).get(name)
    if t is None:
        t = term_dates(year + 1).get(name, dt.date(year + 1, 2, 4))
    return t


def ganzhi_year_of(d: dt.date) -> int:
    """干支历年份：d 落在 [立春(Y), 立春(Y+1)) 内则属 Y。"""
    y = d.year
    if d < lichun(y):
        return y - 1
    return y


# 一个公历年内十二节的先后顺序（小寒在 1 月，大雪在 12 月）
CHRONOLOGICAL_BRANCHES = ("丑", "寅", "卯", "辰", "巳", "午", "未", "申", "酉", "戌", "亥", "子")


def month_branch_of(d: dt.date) -> str:
    """按十二节区间确定月支：取"最后一个 <= d 的节"对应的月支。"""
    for branch in reversed(CHRONOLOGICAL_BRANCHES):
        if d >= jie_day(branch, d.year):
            return branch
    return "子"  # d 在该年小寒之前 → 上一年大雪起的子月
