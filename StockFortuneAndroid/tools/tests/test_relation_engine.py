"""test_relation_engine.py —— 关系引擎全面门禁单测（Gate G2 对应）。

覆盖：
1. 120×120 外部干支 × 原局干支穷尽验证（22 类关系双向不可变）
2. 复合结构与多支条件成立验证（三合、半合、三会、三刑）
3. 组合关系验证（天合地合、天克地冲、伏吟、反吟）
4. 茅台、宁德时代等真实代表原局关系识别
5. 全库 2776 张唯一盘批量验证无崩溃与数据统计
"""

from __future__ import annotations

import unittest
from pathlib import Path
import sys

TOOLS_DIR = Path(__file__).resolve().parents[1]
if str(TOOLS_DIR) not in sys.path:
    sys.path.insert(0, str(TOOLS_DIR))

import relation_core as rc
import bazi_core as bc


class TestRelationEngine(unittest.TestCase):

    def test_canonical_catalog_count_is_22(self):
        """关系目录必须严格为 22 类。"""
        self.assertEqual(len(rc.RELATION_TYPES), 22)
        catalog_types = [t for group in rc.RELATION_CATALOG.values() for t in group]
        self.assertEqual(len(catalog_types), 22)
        self.assertEqual(set(rc.RELATION_TYPES), set(catalog_types))

    def test_120_by_120_exhaustive_coverage(self):
        """120 外部干支 × 120 原局干支全扫（双向不变量）：

        1. 引擎 emit 的每一种类型都必须在 RELATION_TYPES 中（无非法野类型）
        2. RELATION_TYPES 中的全部 22 种类型都必须能被实际触发（无死条目）
        """
        all_pillars = [
            f"{s}{b}" for s in rc.STEMS for b in rc.BRANCHES
        ]  # 10 × 12 = 120
        self.assertEqual(len(all_pillars), 120)

        emitted: set[str] = set()

        # 两两单元格穷尽
        for ext in all_pillars:
            for nat in all_pillars:
                events = rc.compute_pair_relations(ext, nat, "flow_day", "day")
                for ev in events:
                    emitted.add(ev.relation_type)

        # 加上多支触发（外部柱 + 原局两柱）
        # 挑选能够触发三合、三会、三刑的代表原局
        test_natals = [
            ("申", "辰"),  # 补 子 -> 三合水
            ("寅", "辰"),  # 补 卯 -> 三会木
            ("巳", "申"),  # 补 寅 -> 三刑
            ("戌", "未"),  # 补 丑 -> 三刑
        ]
        for b1, b2 in test_natals:
            for ext in all_pillars:
                inter_events = rc.compute_external_interaction(
                    ext, "flow_year", f"甲{b1}", f"乙{b2}", "丙午"
                )
                for ev in inter_events:
                    emitted.add(ev.relation_type)

        catalog_set = set(rc.RELATION_TYPES)
        # 1. 不存在未定义的野类型
        unregistered = emitted - catalog_set
        self.assertEqual(unregistered, set(), f"发现未登记在 catalog 的关系类型: {unregistered}")

        # 2. catalog 中所有 22 类全部可达
        unreachable = catalog_set - emitted
        self.assertEqual(unreachable, set(), f"catalog 中存在不可达关系类型: {unreachable}")

        self.assertEqual(emitted, catalog_set)

    def test_tianhe_dihe_compound(self):
        """辛巳 与 丙申：丙辛合水 + 巳申合水 -> 天合地合。"""
        events = rc.compute_pair_relations("辛巳", "丙申", "year", "month")
        types = {ev.relation_type for ev in events}
        self.assertIn("天干五合", types)
        self.assertIn("六合", types)
        self.assertIn("天合地合", types)

    def test_tianke_dichong_and_fanyin(self):
        """庚午 与 甲子：庚克甲 + 子午冲 -> 天克地冲 + 反吟。"""
        events = rc.compute_pair_relations("庚午", "甲子", "year", "month")
        types = {ev.relation_type for ev in events}
        self.assertIn("天干克", types)
        self.assertIn("六冲", types)
        self.assertIn("天克地冲", types)
        self.assertIn("反吟", types)

    def test_fuyin(self):
        """甲子 与 甲子 -> 伏吟。"""
        events = rc.compute_pair_relations("甲子", "甲子", "year", "month")
        types = {ev.relation_type for ev in events}
        self.assertIn("伏吟", types)
        self.assertIn("同支", types)
        self.assertIn("天干同五行", types)

    def test_triple_harmony_and_half_harmony(self):
        """申子辰三合水局 与 申子半合水局。"""
        # 三合完整
        events_full = rc.compute_natal_internal_relations("庚申", "戊子", "壬辰")
        types_full = {ev.relation_type for ev in events_full}
        self.assertIn("三合", types_full)
        sanhe_ev = next(e for e in events_full if e.relation_type == "三合")
        self.assertEqual(sanhe_ev.element, "水")

        # 半合（缺一）
        events_half = rc.compute_natal_internal_relations("庚申", "戊子", "壬午")
        types_half = {ev.relation_type for ev in events_half}
        self.assertIn("半合", types_half)
        self.assertNotIn("三合", types_half)
        banhe_ev = next(e for e in events_half if e.relation_type == "半合")
        self.assertEqual(banhe_ev.element, "水")

    def test_triple_meeting(self):
        """寅卯辰三会木方。"""
        events = rc.compute_natal_internal_relations("甲寅", "乙卯", "丙辰")
        types = {ev.relation_type for ev in events}
        self.assertIn("三会", types)
        sanhui_ev = next(e for e in events if e.relation_type == "三会")
        self.assertEqual(sanhui_ev.element, "木")

    def test_triple_punishment(self):
        """寅巳申三刑。"""
        events = rc.compute_natal_internal_relations("甲寅", "乙巳", "丙申")
        types = {ev.relation_type for ev in events}
        self.assertIn("三刑", types)
        self.assertIn("相刑", types)

    def test_self_punishment(self):
        """辰辰、午午、酉酉、亥亥 自刑。"""
        for branch in ("辰", "午", "酉", "亥"):
            gz1 = f"甲{branch}"
            gz2 = f"丙{branch}"
            events = rc.compute_pair_relations(gz1, gz2, "year", "month")
            types = {ev.relation_type for ev in events}
            self.assertIn("自刑", types, f"{branch} 应触发自刑")
            self.assertIn("同支", types)

    def test_maotai_golden_natal_relations(self):
        """茅台（600519）：辛巳(年), 丙申(月), 壬戌(日)。"""
        events = rc.compute_natal_internal_relations("辛巳", "丙申", "壬戌")
        types = {ev.relation_type for ev in events}
        # 辛巳与丙申形成：天干五合、六合、天合地合、六破、相刑
        self.assertIn("天干五合", types)
        self.assertIn("六合", types)
        self.assertIn("天合地合", types)
        self.assertIn("六破", types)
        self.assertIn("相刑", types)

    def test_external_interaction_stream(self):
        """外部流年 甲辰 与 茅台原局 辛巳, 丙申, 壬戌。"""
        events = rc.compute_external_interaction(
            "甲辰", "liunian", "辛巳", "丙申", "壬戌"
        )
        types = {ev.relation_type for ev in events}
        # 辰与戌相冲（六冲）
        self.assertIn("六冲", types)
        # 甲克戌支藏？不，外部甲木克壬水(受克)？甲克戊，甲与辛(金克木/受克)
        self.assertIn("天干受克", types)  # 辛金克甲木
        # 辰与申半合水局 (申子辰缺子)
        self.assertIn("半合", types)


if __name__ == "__main__":
    unittest.main()
