"""trade_calendar.py —— A 股休市日历生成（构建期使用，运行时随 APP 离线分发）。

口径：
* 周六、周日一律休市（A 股不因"调休上班"而在周末开市）。
* 法定节假日休市：由规则（农历节日 / 清明节气 / 固定日期）+ 已公布安排覆盖表 + 特殊休市日合成。
* 每个休市日带 `confidence`：`curated`（已公布安排/交易所公告）或 `rule`（按节假日规则推算，
  用于早年与未来年份，属预估）。

正确性以 `verify_database.py` 的门禁为准。注意两条口径边界：
* 实测反查（observed_open）优先于本模块的一切判定，因此"上市日必须都是交易日"这条
  门禁对本模块产出的表是恒真的；它只能发现漏判的休市日，不能发现错判的开市日。
* curated 表与实测冲突时（如全国哀悼日恰逢新股上市），会静默改判开市且不计入
  `corrected`，需要单独核对 `curated_conflicts`（见 build_database.py 的报告）。
"""

from __future__ import annotations

import datetime as dt
from dataclasses import dataclass

from lunar_python import Lunar, Solar

LUNAR_HOLIDAYS = {  # (农历月, 农历日, 天数, 说明)
    (1, 1): (7, "春节"),      # 除夕起算，见 spring_festival()
    (5, 5): (3, "端午节"),
    (8, 15): (3, "中秋节"),
}


@dataclass(frozen=True)
class Closure:
    start: dt.date
    end: dt.date
    reason: str
    confidence: str


def _solar_of_lunar(year: int, month: int, day: int) -> dt.date:
    s = Lunar.fromYmd(year, month, day).getSolar()
    return dt.date(s.getYear(), s.getMonth(), s.getDay())


def _holiday_era(year: int) -> bool:
    """该年是否处于"法定节假日连休调休"形态（2017 起清明/端午/中秋普遍连休 3 天以上）。

    这只管**区间长度**，不管结论来源；来源一律由 confidence 表达，二者不能混用同一个
    判断，否则规则补位会被标成 curated，等于伪造溯源。
    """
    return 2017 <= year <= 2026


def spring_festival(year: int, confidence: str = "rule") -> Closure:
    """春节休市区间。连休年份按"除夕~初六"；早年只锁法定"除夕~初二"3 天，
    调休多放的日期由实测反查（observed_open）纠正。
    """
    first_day = _solar_of_lunar(year, 1, 1)
    tail = 6 if _holiday_era(year) else 2
    start = first_day - dt.timedelta(days=1)          # 除夕
    end = first_day + dt.timedelta(days=tail)
    return Closure(start, end, "春节", confidence)


def qingming(year: int, confidence: str = "rule") -> Closure:
    """清明：取该年清明节气所在日；连休年份前后各 1 天，早年只锁当日。"""
    pad = 1 if _holiday_era(year) else 0
    for offset in range(0, 6):
        d = dt.date(year, 4, 3) + dt.timedelta(days=offset)
        if Solar.fromYmd(d.year, d.month, d.day).getLunar().getJieQi() == "清明":
            return Closure(d - dt.timedelta(days=pad), d + dt.timedelta(days=pad), "清明节", confidence)
    return Closure(dt.date(year, 4, 4), dt.date(year, 4, 6), "清明节", confidence)


def lunar_holiday(year: int, month: int, day: int, name: str, span: int,
                  confidence: str = "rule") -> Closure:
    """农历节日（端午/中秋）：连休年份按 3 天，早年只锁节日当天。"""
    mid = _solar_of_lunar(year, month, day)
    if not _holiday_era(year):
        return Closure(mid, mid, name, confidence)
    return Closure(mid - dt.timedelta(days=1), mid + dt.timedelta(days=span - 2), name, confidence)


# 已公布的春节 / 国庆 / 元旦实际安排（起止含端点）；键为公历年
CURATED_RANGES: dict[int, list[tuple[str, str, str, str]]] = {
    2017: [("2017-01-27", "2017-02-02", "春节", "curated"),
           ("2017-04-02", "2017-04-04", "清明节", "curated"),
           ("2017-04-29", "2017-05-01", "劳动节", "curated"),
           ("2017-05-28", "2017-05-30", "端午节", "curated"),
           ("2017-10-01", "2017-10-08", "国庆节·中秋节", "curated")],
    2018: [("2018-02-15", "2018-02-21", "春节", "curated"),
           ("2018-04-05", "2018-04-05", "清明节", "curated"),
           ("2018-04-29", "2018-05-01", "劳动节", "curated"),
           ("2018-06-16", "2018-06-18", "端午节", "curated"),
           ("2018-09-22", "2018-09-24", "中秋节", "curated"),
           ("2018-10-01", "2018-10-07", "国庆节", "curated")],
    2019: [("2019-02-04", "2019-02-10", "春节", "curated"),
           ("2019-04-05", "2019-04-05", "清明节", "curated"),
           ("2019-05-01", "2019-05-01", "劳动节", "curated"),
           ("2019-06-07", "2019-06-09", "端午节", "curated"),
           ("2019-09-13", "2019-09-15", "中秋节", "curated"),
           ("2019-10-01", "2019-10-07", "国庆节", "curated")],
    2020: [("2020-01-24", "2020-02-02", "春节（含疫情延长）", "curated"),
           ("2020-04-04", "2020-04-06", "清明节", "curated"),
           ("2020-05-01", "2020-05-05", "劳动节", "curated"),
           ("2020-06-25", "2020-06-27", "端午节", "curated"),
           ("2020-10-01", "2020-10-08", "国庆节·中秋节", "curated")],
    2021: [("2021-02-11", "2021-02-17", "春节", "curated"),
           ("2021-04-03", "2021-04-05", "清明节", "curated"),
           ("2021-05-01", "2021-05-05", "劳动节", "curated"),
           ("2021-06-12", "2021-06-14", "端午节", "curated"),
           ("2021-09-19", "2021-09-21", "中秋节", "curated"),
           ("2021-10-01", "2021-10-07", "国庆节", "curated")],
    2022: [("2022-01-31", "2022-02-06", "春节", "curated"),
           ("2022-04-03", "2022-04-05", "清明节", "curated"),
           ("2022-04-30", "2022-05-04", "劳动节", "curated"),
           ("2022-06-03", "2022-06-05", "端午节", "curated"),
           ("2022-09-10", "2022-09-12", "中秋节", "curated"),
           ("2022-10-01", "2022-10-07", "国庆节", "curated")],
    2023: [("2023-01-21", "2023-01-27", "春节", "curated"),
           ("2023-04-05", "2023-04-05", "清明节", "curated"),
           ("2023-04-29", "2023-05-03", "劳动节", "curated"),
           ("2023-06-22", "2023-06-24", "端午节", "curated"),
           ("2023-09-29", "2023-10-06", "国庆节·中秋节", "curated")],
    2024: [("2024-02-09", "2024-02-16", "春节", "curated"),
           ("2024-04-04", "2024-04-06", "清明节", "curated"),
           ("2024-05-01", "2024-05-05", "劳动节", "curated"),
           ("2024-06-10", "2024-06-10", "端午节", "curated"),
           ("2024-09-15", "2024-09-17", "中秋节", "curated"),
           ("2024-10-01", "2024-10-07", "国庆节", "curated"),
           ("2024-01-01", "2024-01-01", "元旦", "curated")],
    2025: [("2025-01-28", "2025-02-04", "春节", "curated"),
           ("2025-04-04", "2025-04-06", "清明节", "curated"),
           ("2025-05-01", "2025-05-05", "劳动节", "curated"),
           ("2025-05-31", "2025-06-02", "端午节", "curated"),
           ("2025-10-01", "2025-10-08", "国庆节·中秋节", "curated"),
           ("2025-01-01", "2025-01-01", "元旦", "curated")],
    2026: [("2026-01-01", "2026-01-02", "元旦", "curated"),
           ("2026-02-15", "2026-02-23", "春节", "curated"),
           ("2026-04-04", "2026-04-06", "清明节", "curated"),
           ("2026-05-01", "2026-05-05", "劳动节", "curated"),
           ("2026-06-19", "2026-06-21", "端午节", "curated"),
           ("2026-09-25", "2026-09-27", "中秋节", "curated"),
           ("2026-10-01", "2026-10-07", "国庆节", "curated")],
    2027: [("2027-01-01", "2027-01-03", "元旦", "rule"),
           ("2027-02-05", "2027-02-12", "春节", "rule"),
           ("2027-10-01", "2027-10-07", "国庆节", "rule")],
}

SPECIAL_CLOSURES: list[tuple[str, str, str]] = [
    ("1999-12-20", "1999-12-20", "澳门回归"),
    ("2008-05-19", "2008-05-21", "汶川地震全国哀悼日"),
    ("2010-04-21", "2010-04-21", "玉树地震全国哀悼日"),
    ("2012-04-21", "2012-04-21", "玉树地震哀悼日"),
    ("2015-09-03", "2015-09-03", "抗战胜利70周年纪念活动放假"),
    ("2020-01-26", "2020-02-02", "春节假期延长（新冠疫情）"),
    ("2020-04-04", "2020-04-06", "新冠疫情哀悼日·清明"),
]

# 上市日反查得到的历史休市例外（周六开市：深市首批柜台交易）
ALWAYS_OPEN: dict[str, str] = {
    "1990-12-01": "深市首批股票集中交易（历史特例，周六开市）",
}

# 法定节假日族，用于判断"表已覆盖 / 需规则补位"
FAMILY_KEYS = ("元旦", "春节", "清明节", "劳动节", "端午节", "中秋节", "国庆节")

# 清明 / 端午 / 中秋 自 2008-01-01 起才是法定假日（国务院 2007-12 修订《全国年节及纪念日放假办法》）。
# 2008 年以前这些日子 A 股照常开市，有直接证据：1996-09-27（中秋）、1997-06-09、1999-06-18、
# 2004-06-22（端午）、2007-09-25（中秋）当天都有新股上市。旧实现无条件套用节假日规则，
# 出厂库因此在 1991-2007 之间多判了 33 个周中休市日。
STATUTORY_LUNAR_HOLIDAY_START = 2008


def families_of(reason: str) -> tuple[str, ...]:
    """一条 reason 可同时属于两族（如"国庆节·中秋节"），补位判断要按全部族名来。"""
    hit = tuple(k for k in FAMILY_KEYS if k in reason)
    return hit or (reason,)


def rule_closures(year: int) -> list[Closure]:
    """按节假日规则推算的区间，用于 curated 表未覆盖的年份与节日族。

    规则推算刻意保守（早年/未来年只锁法定当日或 3 天，不含调休多放的日子）：宁可少判
    几天休市，也不要凭猜测把真实交易日判成休市。
    """
    out: list[Closure] = [
        Closure(dt.date(year, 1, 1), dt.date(year, 1, 1), "元旦", "rule"),
        spring_festival(year),
    ]
    if year >= STATUTORY_LUNAR_HOLIDAY_START:
        out.append(qingming(year))
        out.append(lunar_holiday(year, 5, 5, "端午节", 3))
        out.append(lunar_holiday(year, 8, 15, "中秋节", 3))
    # 劳动节：1999-2007 为"五一黄金周"7 天；此后法定 1 天，连休由 curated 表给出
    if 1999 <= year <= 2007:
        out.append(Closure(dt.date(year, 5, 1), dt.date(year, 5, 7), "劳动节", "rule"))
    else:
        out.append(Closure(dt.date(year, 5, 1), dt.date(year, 5, 1), "劳动节", "rule"))
    # 国庆：1999 起 7 天，更早 3 天
    days = 7 if year >= 1999 else 3
    out.append(Closure(dt.date(year, 10, 1), dt.date(year, 10, 1 + days - 1), "国庆节", "rule"))
    return out


def closures_for(year: int) -> list[Closure]:
    """某公历年的休市区间 = 已公布安排（curated）∪ 特殊休市日 ∪ 规则推算（rule）补位。

    三类来源**不是二选一**。旧实现写成 `if year in CURATED_RANGES … else 走规则`，于是
    表里少登记一个节日就等于该节日完全不产生闭市区间：2024 的表只有元旦/春节/国庆，
    出厂库因此把清明、劳动、端午、中秋共 8 个真实休市的周中标成交易日（2024 交易日数
    250，而邻年 2022/2023/2025/2026 都是 243）。现在规则推算只在表未覆盖该节日族时补位，
    且排在表之后，所以同族日期永远以公告为准。
    """
    out: list[Closure] = []
    covered: set[str] = set()
    for s, e, reason, conf in CURATED_RANGES.get(year, []):
        out.append(Closure(dt.date.fromisoformat(s), dt.date.fromisoformat(e), reason, conf))
        covered.update(families_of(reason))
    for s, e, reason in SPECIAL_CLOSURES:
        sd, ed = dt.date.fromisoformat(s), dt.date.fromisoformat(e)
        if sd.year == year or (sd.year < year < ed.year):
            out.append(Closure(sd, ed, reason, "curated"))
    # 表声称覆盖该年却漏了元旦时，按公告补一条 curated（保持旧行为）
    if year in CURATED_RANGES and "元旦" not in covered:
        out.append(Closure(dt.date(year, 1, 1), dt.date(year, 1, 1), "元旦", "curated"))
        covered.add("元旦")
    for c in rule_closures(year):
        if covered.isdisjoint(families_of(c.reason)):
            out.append(c)
    return out


def build_trade_calendar(
    start: dt.date,
    end: dt.date,
    observed_open: frozenset[str] = frozenset(),
) -> tuple[list[tuple[str, int, int, str | None, str]], list[tuple[str, str]]]:
    """返回 (日历行, 被实测反查修正的日期列表)。

    `observed_open` = 数据源中真实上市日集合：这些日期一定是交易日，任何规则/表内休市判定
    与之冲突都以实测为准（早于 2017 年的放假安排无法逐条核对，靠此机制自我纠正）。
    """
    rows: list[tuple[str, int, int, str | None, str]] = []
    corrected: list[tuple[str, str]] = []
    year_closures: dict[int, list[Closure]] = {}
    d = start
    while d <= end:
        if d.year not in year_closures:
            year_closures[d.year] = closures_for(d.year)
        iso = d.isoformat()
        if iso in ALWAYS_OPEN or iso in observed_open:
            reason = ALWAYS_OPEN.get(iso)
            rows.append((iso, 1, d.isoweekday(), reason or "实测开市（真实上市日反查）", "observed"))
            if iso not in ALWAYS_OPEN:
                for c in year_closures[d.year]:
                    if c.start <= d <= c.end and c.confidence != "curated":
                        corrected.append((iso, c.reason))
        else:
            reason = None
            conf = "rule"
            for c in year_closures[d.year]:
                if c.start <= d <= c.end:
                    reason, conf = c.reason, c.confidence
                    break
            if reason:
                rows.append((iso, 0, d.isoweekday(), reason, conf))
            elif d.weekday() >= 5:
                rows.append((iso, 0, d.isoweekday(), "周末", "curated"))
            else:
                rows.append((iso, 1, d.isoweekday(), None, conf))
        d += dt.timedelta(days=1)
    return rows, corrected
