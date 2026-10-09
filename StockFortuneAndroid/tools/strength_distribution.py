#!/usr/bin/env python3
"""身强身弱三态分布 —— 离线评估脚本，只读，不参与打包、不被 APP 调用。

用途：在把「日主强弱」做成第二根判定轴之前，先按提案权重跑一遍全库，
看三态分布是否可用、阈值该定在哪、以及「时支恒为巳」带来的常数偏置有多大。

    /usr/bin/python3 tools/strength_distribution.py [--db PATH] [--no-hour] [--threshold 2.0]

口径与出处：得令 / 得地 / 得势的三分结构是子平通说；下面的具体权重是工程取值，
无古籍依据，任何由它产出的界面文案都只能标「本项目概述」，不得挂书名。
"""

from __future__ import annotations

import argparse
import sqlite3
import sys
from collections import Counter, defaultdict
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
import bazi_core as bc  # noqa: E402

# 权重与阈值一律取自 bazi_core（与 Android 侧 TenGodCalculator 手工镜像的那一份），
# 本脚本不再自带一套常量 —— 否则评估用的规则和最终上线的规则会各自漂移。
BRANCH_WEIGHTS = bc.STRENGTH_BRANCH_WEIGHTS
STEM_WEIGHT = bc.STRENGTH_STEM_WEIGHT
is_same_party = bc.is_same_party

DEFAULT_DB = Path(__file__).resolve().parent.parent / "app/src/main/assets/databases/stock_fortune.db"


def score_chart(day_stem: str, pillars: dict, use_hour: bool = True) -> float:
    """use_hour=False 即上线口径（三柱六字）；True 只为对照出巳时占位的偏置有多大。"""
    if not use_hour:
        return bc.strength_score(
            f"{pillars['year'][0]}{pillars['year'][1]}",
            f"{pillars['month'][0]}{pillars['month'][1]}",
            f"{pillars['day'][0]}{pillars['day'][1]}",
        )
    total = 0.0
    for pos in ("year", "month", "day", "hour"):
        stem, branch = pillars[pos]
        if pos != "day":
            total += STEM_WEIGHT * (+1 if is_same_party(day_stem, stem) else -1)
        w_main, w_mid, w_rest = BRANCH_WEIGHTS["month" if pos == "month" else "other"]
        for i, hidden in enumerate(bc.HIDDEN_STEMS[branch]):
            w = (w_main, w_mid, w_rest)[min(i, 2)]
            total += w * (+1 if is_same_party(day_stem, hidden) else -1)
    return round(total, 2)


def state(score: float, thr: float) -> str:
    return "身强" if score >= thr else "身弱" if score <= -thr else "中和"


def hour_constant(day_stem: str) -> float:
    """时支恒为巳（上市 9:30），故巳的三支对所有盘是同一常数项。"""
    w_main, w_mid, w_rest = BRANCH_WEIGHTS["other"]
    return round(
        sum(
            w * (+1 if is_same_party(day_stem, h) else -1)
            for w, h in zip((w_main, w_mid, w_rest), bc.HIDDEN_STEMS["巳"])
        ),
        2,
    )


def bar(n: int, total: int, width: int = 34) -> str:
    return "█" * round(width * n / total) if total else ""


def distribution(label: str, states: list[str], scores: list[float], thr: float) -> None:
    total = len(states)
    c = Counter(states)
    print(f"\n{label}  （n={total}，阈值 ±{thr}）")
    for k in ("身强", "中和", "身弱"):
        pct = 100 * c[k] / total if total else 0
        print(f"  {k}  {c[k]:6d}  {pct:5.1f}%  {bar(c[k], total)}")
    if total:
        ordered = sorted(scores)
        q = lambda p: ordered[min(total - 1, int(p * total))]
        print(f"  分位  p10={q(.10):+.2f} p25={q(.25):+.2f} 中位={q(.50):+.2f} "
              f"p75={q(.75):+.2f} p90={q(.90):+.2f}  极值=[{ordered[0]:+.2f}, {ordered[-1]:+.2f}]")


def main() -> int:
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("--db", type=Path, default=DEFAULT_DB)
    ap.add_argument("--threshold", type=float, default=2.0)
    ap.add_argument("--no-hour", action="store_true", help="只算年月日六字，剔除恒为巳的时柱")
    args = ap.parse_args()

    if not args.db.exists():
        print(f"找不到预置库：{args.db}", file=sys.stderr)
        return 1

    con = sqlite3.connect(f"file:{args.db}?mode=ro", uri=True)
    rows = con.execute(
        "SELECT day_stem, year_stem, year_branch, month_stem, month_branch,"
        " day_branch, hour_stem, hour_branch FROM stock_bazi"
    ).fetchall()
    con.close()
    if not rows:
        print("stock_bazi 为空", file=sys.stderr)
        return 1

    use_hour = not args.no_hour
    print(f"库：{args.db.name}    股票数：{len(rows)}    含时柱：{use_hour}")

    # 时柱一致性自检：本项目的约定是时支恒为巳
    hour_branches = Counter(r[7] for r in rows)
    print(f"时支分布：{dict(hour_branches)}")

    distinct = {r[1:] for r in rows}
    print(f"去重后不同命盘数：{len(distinct)}（同一日主多股共享命盘，按股票计数会被放大）")

    print("\n巳时（丙本/庚中/戊余）对各日主的固定贡献：")
    for el, stems in (("木", "甲乙"), ("火", "丙丁"), ("土", "戊己"), ("金", "庚辛"), ("水", "壬癸")):
        vals = {s: hour_constant(s) for s in stems}
        print(f"  {el} {'/'.join(stems)}: {vals[stems[0]]:+.2f}")

    per_stem = defaultdict(list)
    scores, states = [], []
    for r in rows:
        day_stem = r[0]
        pillars = {
            "year": (r[1], r[2]), "month": (r[3], r[4]),
            "day": (day_stem, r[5]), "hour": (r[6], r[7]),
        }
        s = score_chart(day_stem, pillars, use_hour)
        st = state(s, args.threshold)
        scores.append(s)
        states.append(st)
        per_stem[day_stem].append(st)

    distribution("全库三态", states, scores, args.threshold)

    print(f"\n按日主拆分（身强 / 中和 / 身弱，占比）：")
    for s in bc.STEMS:
        c = Counter(per_stem[s])
        n = len(per_stem[s])
        print(f"  {s}  n={n:5d}  "
              f"强 {100 * c['身强'] / n:5.1f}%  中 {100 * c['中和'] / n:5.1f}%  "
              f"弱 {100 * c['身弱'] / n:5.1f}%   巳时贡献 {hour_constant(s):+.2f}")

    # 与现有财星口径的交叉：不硬编码 28/20，直接用 bazi_core 在 60 甲子上复算，
    # 免得脚本的对照表和 Rule v1.1 的实现各说各话。
    wealth_days = {}
    for s in bc.STEMS:
        wealth_days[s] = sum(
            1 for gz in (bc.ganzhi_of_index(i) for i in range(60))
            if bc.wealth_type_of(s, gz[0], gz[1]) != "其他"
        )
    print("\n日主 × 60 甲子内财日数 × 身强占比（看两根轴的偏置方向是否重合）：")
    for s in bc.STEMS:
        c = Counter(per_stem[s])
        n = len(per_stem[s])
        flag = "  ← 同向" if (c["身强"] / n > 0.5 and wealth_days[s] == max(wealth_days.values())) else ""
        print(f"  {s}  财日 {wealth_days[s]:2d}   身强 {100 * c['身强'] / n:5.1f}%{flag}")

    print("\n判定：身强占比落在 20%–45% 且中和不为零，阈值可用；"
          "若某一侧超过 70% 或中和吃掉一半，需回炉调权重或改按日主分组校准。")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
