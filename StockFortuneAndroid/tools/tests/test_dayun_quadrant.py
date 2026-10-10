"""test_dayun_quadrant.py —— 大运顺逆四象限与边界条件回归测试。

覆盖：
1. 顺逆四象限方向与干支序列自洽性（阳年阳命、阳年阴命、阴年阳命、阴年阴命）。
2. 平盘样本（315 只）不适用处理。
3. 阴阳缺失样本不适用处理。
4. 立春与交节边界样本识别。
5. 12 步大运周期连续性与区间校验。
"""

from __future__ import annotations

import datetime as dt
import unittest

import dayun_core as dc


class TestDayunQuadrant(unittest.TestCase):
    def test_four_quadrants_direction_and_stepping(self):
        """四象限方向推导及干支步进序列验证。"""
        # 1. 阳年 + 阳命 -> 顺排 (甲戌年 丙寅月)
        res1 = dc.calculate_stock_luck_cycle(
            listing_date=dt.date(1994, 2, 5),
            year_pillar="甲戌",
            month_pillar="丙寅",
            day_pillar="丁卯",
            hour_pillar="乙巳",
            first_change=0.05,
            first_day_flag="阳",
        )
        self.assertEqual(res1["direction"], "forward")
        self.assertEqual(res1["status"], "available")
        self.assertEqual(res1["first_day_polarity"], "yang")
        self.assertEqual(len(res1["periods"]), 12)
        # 丙寅顺排第一步为丁卯
        self.assertEqual(res1["periods"][0]["ganzhi"], "丁卯")
        self.assertEqual(res1["periods"][1]["ganzhi"], "戊辰")

        # 2. 阳年 + 阴命 -> 逆排 (甲戌年 丙寅月)
        res2 = dc.calculate_stock_luck_cycle(
            listing_date=dt.date(1994, 2, 5),
            year_pillar="甲戌",
            month_pillar="丙寅",
            day_pillar="丁卯",
            hour_pillar="乙巳",
            first_change=-0.03,
            first_day_flag="阴",
        )
        self.assertEqual(res2["direction"], "reverse")
        self.assertEqual(res2["status"], "available")
        self.assertEqual(res2["first_day_polarity"], "yin")
        self.assertEqual(len(res2["periods"]), 12)
        # 丙寅逆排第一步为乙丑
        self.assertEqual(res2["periods"][0]["ganzhi"], "乙丑")
        self.assertEqual(res2["periods"][1]["ganzhi"], "甲子")

        # 3. 阴年 + 阳命 -> 逆排 (乙亥年 戊寅月)
        res3 = dc.calculate_stock_luck_cycle(
            listing_date=dt.date(1995, 2, 5),
            year_pillar="乙亥",
            month_pillar="戊寅",
            day_pillar="己丑",
            hour_pillar="己巳",
            first_change=0.02,
            first_day_flag="阳",
        )
        self.assertEqual(res3["direction"], "reverse")
        self.assertEqual(res3["status"], "available")
        self.assertEqual(res3["first_day_polarity"], "yang")
        self.assertEqual(len(res3["periods"]), 12)
        # 戊寅逆排第一步为丁丑
        self.assertEqual(res3["periods"][0]["ganzhi"], "丁丑")
        self.assertEqual(res3["periods"][1]["ganzhi"], "丙子")

        # 4. 阴年 + 阴命 -> 顺排 (乙亥年 戊寅月)
        res4 = dc.calculate_stock_luck_cycle(
            listing_date=dt.date(1995, 2, 5),
            year_pillar="乙亥",
            month_pillar="戊寅",
            day_pillar="己丑",
            hour_pillar="己巳",
            first_change=-0.01,
            first_day_flag="阴",
        )
        self.assertEqual(res4["direction"], "forward")
        self.assertEqual(res4["status"], "available")
        self.assertEqual(res4["first_day_polarity"], "yin")
        self.assertEqual(len(res4["periods"]), 12)
        # 戊寅顺排第一步为己卯
        self.assertEqual(res4["periods"][0]["ganzhi"], "己卯")
        self.assertEqual(res4["periods"][1]["ganzhi"], "庚辰")

    def test_flat_samples_handling(self):
        """首日平盘样本 (first_change == 0.0) 必须标记为 flat 且大运 unavailable。"""
        res = dc.calculate_stock_luck_cycle(
            listing_date=dt.date(2000, 1, 1),
            year_pillar="己卯",
            month_pillar="丙子",
            day_pillar="戊午",
            hour_pillar="丁巳",
            first_change=0.0,
            first_day_flag="阳",  # 历史原表标阳，但实际平盘
        )
        self.assertEqual(res["direction"], "unavailable")
        self.assertEqual(res["status"], "unavailable_flat")
        self.assertEqual(res["status_reason"], "FIRST_DAY_FLAT_UNRESOLVED")
        self.assertEqual(res["first_day_polarity"], "flat")
        self.assertIsNone(res["start_date"])
        self.assertIsNone(res["start_age"])
        self.assertEqual(len(res["periods"]), 0)

    def test_missing_and_conflict_handling(self):
        """数据缺失与冲突样本处置。"""
        # 缺失
        res_missing = dc.calculate_stock_luck_cycle(
            listing_date=dt.date(1990, 12, 1),
            year_pillar="庚午",
            month_pillar="丁亥",
            day_pillar="癸酉",
            hour_pillar="丁巳",
            first_change=None,
            first_day_flag="数据缺失",
        )
        self.assertEqual(res_missing["direction"], "unavailable")
        self.assertEqual(res_missing["status"], "unavailable_missing")
        self.assertEqual(res_missing["status_reason"], "FIRST_DAY_FLAG_MISSING")
        self.assertEqual(len(res_missing["periods"]), 0)

        # 冲突 (涨跌幅为正却标阴)
        res_conflict = dc.calculate_stock_luck_cycle(
            listing_date=dt.date(2005, 5, 10),
            year_pillar="乙酉",
            month_pillar="辛巳",
            day_pillar="甲子",
            hour_pillar="己巳",
            first_change=0.05,
            first_day_flag="阴",
        )
        self.assertEqual(res_conflict["direction"], "unavailable")
        self.assertEqual(res_conflict["status"], "unavailable_conflict")
        self.assertEqual(res_conflict["status_reason"], "FIRST_DAY_FLAG_CONFLICT")
        self.assertEqual(len(res_conflict["periods"]), 0)

    def test_solar_term_boundary_tag(self):
        """立春当日上市样本必须打上 boundary 标签。"""
        # 1994-02-04 立春当日
        res = dc.calculate_stock_luck_cycle(
            listing_date=dt.date(1994, 2, 4),
            year_pillar="甲戌",
            month_pillar="丙寅",
            day_pillar="丙寅",
            hour_pillar="癸巳",
            first_change=0.1,
            first_day_flag="阳",
        )
        self.assertEqual(res["boundary_flag"], "solar_term_boundary")

        # 普通日期无标签
        res_normal = dc.calculate_stock_luck_cycle(
            listing_date=dt.date(1994, 2, 15),
            year_pillar="甲戌",
            month_pillar="丙寅",
            day_pillar="丁丑",
            hour_pillar="乙巳",
            first_change=0.1,
            first_day_flag="阳",
        )
        self.assertIsNone(res_normal["boundary_flag"])

    def test_period_continuity(self):
        """周期区间左闭右开连续性与索引顺序。"""
        res = dc.calculate_stock_luck_cycle(
            listing_date=dt.date(2001, 8, 27),
            year_pillar="辛巳",
            month_pillar="丙申",
            day_pillar="壬戌",
            hour_pillar="乙巳",
            first_change=0.08,
            first_day_flag="阳",
        )
        periods = res["periods"]
        self.assertEqual(len(periods), 12)
        for i in range(len(periods)):
            p = periods[i]
            self.assertEqual(p["cycle_index"], i + 1)
            self.assertEqual(p["end_year"] - p["start_year"], 9)
            self.assertEqual(p["end_age"] - p["start_age"], 9)
            if i > 0:
                # 周期起止时间连续
                self.assertEqual(p["start_date"], periods[i - 1]["end_date"])


if __name__ == "__main__":
    unittest.main()
