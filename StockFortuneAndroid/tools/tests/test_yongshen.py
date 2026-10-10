"""test_yongshen.py —— 喜用候选与格局解释内核单元测试。

覆盖：
  1. 身强（CONFIRMED）、身弱（CONFIRMED）、中和（CANDIDATE）三态与五行分配；
  2. 边界压线盘（得分贴近 +/- 2.0）；
  3. 调候环境观察独立双轴（不污染扶抑用喜五行）；
  4. 合规标签映射（零禁词，无「忌」字）；
  5. 异常柱位优雅降级为 UNAVAILABLE。
"""

from __future__ import annotations

import unittest
import sys
from pathlib import Path

TOOLS_DIR = Path(__file__).resolve().parents[1]
if str(TOOLS_DIR) not in sys.path:
    sys.path.insert(0, str(TOOLS_DIR))

import yongshen_core as yc
import bazi_core as bc


class TestYongshenCandidate(unittest.TestCase):

    def test_strong_chart(self):
        # 辛巳 丙申 壬戌 (贵州茅台)
        # 日主壬水, 月令申金(藏庚壬戊, 金生水+壬水同党), 日支戌土(戊辛丁)
        # 得分算式在 bazi_core 中可得:
        res = yc.compute_yongshen_candidate("辛巳", "丙申", "壬戌")
        self.assertEqual(res["status"], yc.STATUS_CONFIRMED)
        self.assertEqual(res["day_stem"], "壬")
        self.assertEqual(res["strength_level"], "身强")
        self.assertGreaterEqual(res["strength_score"], 2.0)
        # 壬水身强: 用神克我者=土(官杀), 喜神我生者=木(食伤)与我克者=火(财星)
        self.assertEqual(res["yong_shen"], ["土"])
        self.assertEqual(res["xi_shen"], ["木", "火"])
        self.assertEqual(res["ji_shen"], ["金", "水"])
        self.assertEqual(res["chou_shen"], ["金"])
        self.assertEqual(res["candidate_elements"], ["土", "木", "火"])
        self.assertIn("官杀「土」为用神", res["rationale"])
        self.assertNotIn("忌", res["rationale"])

    def test_weak_chart(self):
        # 戊戌 戊午 甲戌 (宁德时代)
        # 日主甲木, 生于午月(火旺木死), 年干月干戊土(财星耗身), 支皆火土燥土
        res = yc.compute_yongshen_candidate("戊戌", "戊午", "甲戌")
        self.assertEqual(res["status"], yc.STATUS_CONFIRMED)
        self.assertEqual(res["day_stem"], "甲")
        self.assertEqual(res["strength_level"], "身弱")
        self.assertLessEqual(res["strength_score"], -2.0)
        # 甲木身弱: 用神生我者=水(印星), 喜神同我者=木(比劫)
        self.assertEqual(res["yong_shen"], ["水"])
        self.assertEqual(res["xi_shen"], ["木"])
        self.assertEqual(res["ji_shen"], ["金", "土"])
        self.assertEqual(res["chou_shen"], ["火"])
        self.assertEqual(res["candidate_elements"], ["水", "木"])
        self.assertIn("印星「水」为用神", res["rationale"])
        self.assertNotIn("忌", res["rationale"])

    def test_balanced_chart(self):
        # 丁丑 壬子 甲午 (1997-12-18)
        # 甲木日主，子水月令生身，得分 0.75，处于中和区间 (-2.0 < score < 2.0)
        score = bc.strength_score("丁丑", "壬子", "甲午")
        self.assertEqual(score, 0.75)
        res = yc.compute_yongshen_candidate("丁丑", "壬子", "甲午")
        self.assertEqual(res["status"], yc.STATUS_CANDIDATE)
        self.assertEqual(res["strength_level"], "中和")
        # 中和格局不强判单一用神与喜神
        self.assertEqual(res["yong_shen"], [])
        self.assertEqual(res["xi_shen"], [])
        self.assertEqual(res["ji_shen"], [])
        self.assertEqual(res["chou_shen"], [])
        self.assertEqual(res["xian_shen"], ["木", "火", "土", "金", "水"])
        self.assertEqual(len(res["candidate_elements"]), 3)
        self.assertIn("不设单一扶抑主轴", res["rationale"])
        self.assertNotIn("忌", res["rationale"])

    def test_boundary_thresholds(self):
        # 测试在阈值 +/- 2.0 边界处的连续性与确定性
        # 1. 恰好 +2.0: 丁酉 乙巳 戊戌 (2017-05-11) -> 身强 (CONFIRMED)
        score_pos = bc.strength_score("丁酉", "乙巳", "戊戌")
        self.assertEqual(score_pos, 2.0)
        res_pos = yc.compute_yongshen_candidate("丁酉", "乙巳", "戊戌")
        self.assertEqual(res_pos["status"], yc.STATUS_CONFIRMED)
        self.assertEqual(res_pos["strength_level"], "身强")
        self.assertEqual(res_pos["yong_shen"], ["木"])  # 戊土用克我者官杀(木)

        # 2. 恰好 -2.0: 丁丑 癸丑 乙卯 (1998-01-08) -> 身弱 (CONFIRMED)
        score_neg = bc.strength_score("丁丑", "癸丑", "乙卯")
        self.assertEqual(score_neg, -2.0)
        res_neg = yc.compute_yongshen_candidate("丁丑", "癸丑", "乙卯")
        self.assertEqual(res_neg["status"], yc.STATUS_CONFIRMED)
        self.assertEqual(res_neg["strength_level"], "身弱")
        self.assertEqual(res_neg["yong_shen"], ["水"])  # 乙木用生我者印星(水)

        # 3. 贴近下界 -1.9: 丁丑 乙巳 庚午 (1997-05-28) -> 中和 (CANDIDATE)
        score_near = bc.strength_score("丁丑", "乙巳", "庚午")
        self.assertEqual(score_near, -1.9)
        res_near = yc.compute_yongshen_candidate("丁丑", "乙巳", "庚午")
        self.assertEqual(res_near["status"], yc.STATUS_CANDIDATE)
        self.assertEqual(res_near["strength_level"], "中和")
        self.assertEqual(res_near["yong_shen"], [])

        # 4. 贴近上界 +1.65: 丁丑 丙午 丙午 (1997-07-03) -> 中和 (CANDIDATE)
        score_mid = bc.strength_score("丁丑", "丙午", "丙午")
        self.assertEqual(score_mid, 1.65)
        res_mid = yc.compute_yongshen_candidate("丁丑", "丙午", "丙午")
        self.assertEqual(res_mid["status"], yc.STATUS_CANDIDATE)
        self.assertEqual(res_mid["strength_level"], "中和")
        self.assertEqual(res_mid["yong_shen"], [])

    def test_tiaohou_notes_independence(self):
        # 调候独立测试
        # 1. 亥子丑月 (冬月)
        note_zi = yc.compute_tiaohou_note("子")
        self.assertEqual(note_zi, "冬月生，天寒地冻，调候宜见火（暖局）")

        # 2. 巳午未月 (夏月)
        note_wu = yc.compute_tiaohou_note("午")
        self.assertEqual(note_wu, "夏月生，火燥水枯，调候宜见水（润局）")

        # 3. 辰戌月 (季土月)
        note_chen = yc.compute_tiaohou_note("辰")
        self.assertEqual(note_chen, "季月土重，调候宜木疏土或水润泽")

        # 4. 寅卯申酉月 (春秋月)
        note_yin = yc.compute_tiaohou_note("寅")
        self.assertEqual(note_yin, "春秋月生，寒暖适中，调候需求平和")

        # 调候不改变扶抑用神
        res_wu = yc.compute_yongshen_candidate("戊戌", "戊午", "甲戌")
        self.assertEqual(res_wu["tiaohou_note"], "夏月生，火燥水枯，调候宜见水（润局）")
        # 虽然夏月调候宜水，且甲木身弱用神恰好也是水，但扶抑用神数组独立，未被调候多重插入
        self.assertEqual(res_wu["yong_shen"], ["水"])

        # 贵州茅台（申月）
        res_moutai = yc.compute_yongshen_candidate("辛巳", "丙申", "壬戌")
        self.assertEqual(res_moutai["tiaohou_note"], "春秋月生，寒暖适中，调候需求平和")
        self.assertEqual(res_moutai["yong_shen"], ["土"])

    def test_label_mapper(self):
        self.assertEqual(yc.map_shen_label("用神"), "用神")
        self.assertEqual(yc.map_shen_label("喜神"), "喜神")
        self.assertEqual(yc.map_shen_label("忌神"), "制衡之神")
        self.assertEqual(yc.map_shen_label("仇神"), "耗身之神")
        self.assertEqual(yc.map_shen_label("闲神"), "调和之神")

    def test_unavailable_handling(self):
        res_bad = yc.compute_yongshen_candidate("", "戊午", "甲戌")
        self.assertEqual(res_bad["status"], yc.STATUS_UNAVAILABLE)
        self.assertEqual(res_bad["yong_shen"], [])


if __name__ == "__main__":
    unittest.main()
