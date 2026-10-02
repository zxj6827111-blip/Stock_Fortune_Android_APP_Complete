"""test_trade_calendar.py —— 休市日历生成的行为测试（不依赖外部数据源）。

钉住两类曾进入出厂库的回归：
1. curated 表登记不全时，"表优先否则规则"的互斥分支会让该节日**完全不产生**休市
   （2024 只登记元旦/春节/国庆 → 清明/劳动/端午/中秋 8 个真实休市周中被标成交易日）。
2. 清明/端午/中秋 自 2008 年才是法定假日，早于一律套用会让 1991-2007 多判 33 个周中休市日。
"""

from __future__ import annotations

import datetime as dt
import sys
import unittest
from pathlib import Path
from unittest import mock

TOOLS = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(TOOLS))

import trade_calendar as tc  # noqa: E402

START, END = dt.date(1990, 12, 1), dt.date(2035, 12, 31)


def build(year: int, observed: frozenset[str] = frozenset()) -> dict[str, tuple[int, str | None, str]]:
    """某年逐日结果：iso -> (is_trade_day, closed_reason, confidence)。"""
    rows, _ = tc.build_trade_calendar(dt.date(year, 1, 1), dt.date(year, 12, 31), observed)
    return {r[0]: (r[1], r[3], r[4]) for r in rows}


def families_of_year(year: int) -> set[str]:
    fams: set[str] = set()
    for c in tc.closures_for(year):
        fams.update(tc.families_of(c.reason))
    return fams


LUNAR3 = ("清明节", "端午节", "中秋节")
ALWAYS4 = ("元旦", "春节", "劳动节", "国庆节")


class TestPublishedArrangements(unittest.TestCase):
    """2024 按沪深北交易所公告、2026 按上交所公告核对周中休市。"""

    # 公告：2024 清明 4/4-4/6、劳动 5/1-5/5、端午 6/10、中秋 9/15-9/17、春节 2/9-2/17、国庆 10/1-10/7
    CLOSED_2024 = [
        "2024-01-01", "2024-02-12", "2024-02-13", "2024-02-14", "2024-02-15", "2024-02-16",
        "2024-04-04", "2024-04-05",
        "2024-05-01", "2024-05-02", "2024-05-03",
        "2024-06-10",
        "2024-09-16", "2024-09-17",
        "2024-10-01", "2024-10-02", "2024-10-03", "2024-10-04", "2024-10-07",
    ]

    def test_2024_公告休市日全部判为闭市(self):
        day = build(2024)
        wrong = [d for d in self.CLOSED_2024 if day[d][0] != 0]
        self.assertEqual([], wrong, "这些日期按公告应当休市")

    def test_2024_交易日数为_242(self):
        self.assertEqual(242, sum(1 for v in build(2024).values() if v[0] == 1))

    def test_2024_节日族来自公告而非规则推算(self):
        day = build(2024)
        for d, fam in [("2024-04-04", "清明节"), ("2024-05-01", "劳动节"),
                       ("2024-06-10", "端午节"), ("2024-09-16", "中秋节")]:
            self.assertEqual((0, fam, "curated"), day[d], f"{d} 应以 curated 公告判定为{fam}")

    def test_2026_春节休市至公告所述周一(self):
        # 上交所公告：2/15(日)~2/23(一) 休市，2/24 起照常开市
        day = build(2026)
        self.assertEqual(0, day["2026-02-23"][0], "2026-02-23 春节延长假应休市")
        self.assertEqual(1, day["2026-02-24"][0])


class TestRuleBackfillInvariant(unittest.TestCase):
    """表登记不全时规则必须补位 —— 旧实现做不到这一点。"""

    INCOMPLETE_2024 = [
        ("2024-01-01", "2024-01-01", "元旦", "curated"),
        ("2024-02-09", "2024-02-16", "春节", "curated"),
        ("2024-10-01", "2024-10-07", "国庆节", "curated"),
    ]

    def test_表不全时节日族仍必须全部产生(self):
        with mock.patch.dict(tc.CURATED_RANGES, {2024: self.INCOMPLETE_2024}):
            fams = families_of_year(2024)
        for f in ALWAYS4 + LUNAR3:
            self.assertIn(f, fams, f"表未登记{f}时规则推算必须补位，旧互斥分支会漏掉它")

    def test_补位必须标注为规则推算(self):
        with mock.patch.dict(tc.CURATED_RANGES, {2024: self.INCOMPLETE_2024}):
            day = build(2024)
        self.assertEqual((0, "清明节", "rule"), day["2024-04-04"], "补位不得伪装成已公布安排")

    def test_同族日期以公告为准(self):
        # 表内 2024 春节写到 2/16，规则推算给的区间不同也不能改写公告结论
        day = build(2024)
        self.assertEqual("curated", day["2024-02-12"][2])

    def test_已覆盖完整节日族的年份不产生多余规则区间(self):
        curated_only = {c.reason for c in tc.closures_for(2022) if c.confidence == "curated"}
        rule_extra = [c for c in tc.closures_for(2022) if c.confidence == "rule"]
        self.assertEqual([], rule_extra, f"2022 表已覆盖全部节日族，不应有规则补位: {rule_extra}")


class TestStatutoryStartBoundary(unittest.TestCase):
    """清明/端午/中秋 自 2008 起才是法定假日。"""

    def test_2008_年前不得有三族休市(self):
        for y in range(1991, 2008):
            fams = families_of_year(y)
            self.assertEqual([], [f for f in LUNAR3 if f in fams], f"{y} 年这三族尚未法定")

    def test_2008_起必须有三族休市(self):
        for y in range(2008, 2027):
            fams = families_of_year(y)
            for f in LUNAR3:
                self.assertIn(f, fams, f"{y} 年应产生{f}休市判定")

    def test_四族常年必备(self):
        for y in range(1991, 2036):
            fams = families_of_year(y)
            for f in ALWAYS4:
                self.assertIn(f, fams, f"{y} 年缺 {f}")

    def test_实际上市日可证当日开市(self):
        # 数据源可反查的直接证据：这些"节日"当天有新股上市，说明股市照常开市
        observed = frozenset({
            "1996-09-27",  # 中秋
            "1997-06-09", "1999-06-18", "2004-06-22",  # 端午
            "2007-09-25",  # 中秋
        })
        rows, _ = tc.build_trade_calendar(START, END, observed)
        day = {r[0]: r[1] for r in rows}
        for d in sorted(observed):
            self.assertEqual(1, day[d], f"{d} 有新股上市，必须判为交易日")


class TestShape(unittest.TestCase):
    def test_周末一律休市且无调休开市(self):
        rows, _ = tc.build_trade_calendar(START, END)
        weekend_open = [r[0] for r in rows if r[1] == 1 and r[2] >= 6]
        self.assertEqual({"1990-12-01"}, set(weekend_open), "唯一历史特例是深市首批周六开市")

    def test_families_of_处理合并节日名(self):
        self.assertEqual({"国庆节", "中秋节"}, set(tc.families_of("国庆节·中秋节")))
        self.assertEqual({"汶川地震全国哀悼日"}, set(tc.families_of("汶川地震全国哀悼日")))

    def test_全区间日历行数等于日期跨度(self):
        rows, _ = tc.build_trade_calendar(START, END)
        self.assertEqual((END - START).days + 1, len(rows))


if __name__ == "__main__":
    unittest.main()
