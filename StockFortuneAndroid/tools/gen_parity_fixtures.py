"""gen_parity_fixtures.py —— 生成 Python 参考实现与 Kotlin 实现的交叉校验夹具。

夹具写入 `app/src/test/resources/parity/`，由 `ParityTest` 读取：
  ganzhi_sample.csv : 抽样 600 个日期 → 年/月/日柱（含大量交节当日边界）
  stocks.csv        : 抽样 60 只股票 → 四柱 + 日主 + 纳音
  ten_god.csv       : 100 组 (日干, 对象干) → 十神
  wealth.csv        : 抽样 (日干, 流日干, 流日支) → 财星判定
"""

from __future__ import annotations

import csv
import datetime as dt
import random
from pathlib import Path

import bazi_core as bc
import solar_terms as st
from build_database import DAY_ANCHOR, DEFAULT_XLSX, ganzhi_row
from stock_xlsx import read_workbook

OUT = Path(__file__).resolve().parents[1] / "app" / "src" / "test" / "resources" / "parity"


def main() -> None:
    OUT.mkdir(parents=True, exist_ok=True)
    wb_rows, _ = read_workbook(DEFAULT_XLSX)
    rng = random.Random(20260929)

    # 1) 干支抽样：真实上市日 + 全部交节当日 + 随机日
    dates = {r.listing_date for r in wb_rows}
    boundary = {d for y in range(1991, 2036) for d in st.term_dates(y).values()}
    extra = {dt.date(1990, 12, 1) + dt.timedelta(days=rng.randrange(0, 16000)) for _ in range(120)}
    sample = sorted((dates | boundary | extra) - {None})
    sample = sample[:: max(1, len(sample) // 600)]
    with (OUT / "ganzhi_sample.csv").open("w", newline="", encoding="utf-8") as f:
        w = csv.writer(f)
        w.writerow(["date", "year_ganzhi", "month_ganzhi", "day_ganzhi"])
        for d in sample:
            g = ganzhi_row(d)
            w.writerow([d.isoformat(), g[1], g[2], g[3]])

    # 2) 股票四柱抽样
    picks = rng.sample(wb_rows, 60)
    with (OUT / "stocks.csv").open("w", newline="", encoding="utf-8") as f:
        w = csv.writer(f)
        w.writerow(["code", "listing_date", "year_pillar", "month_pillar", "day_pillar", "hour_pillar", "na_yin"])
        for r in picks:
            w.writerow([r.code, r.listing_date.isoformat(), r.year_pillar, r.month_pillar,
                        r.day_pillar, r.hour_pillar, bc.na_yin(r.day_pillar)])

    # 3) 十神全表
    with (OUT / "ten_god.csv").open("w", newline="", encoding="utf-8") as f:
        w = csv.writer(f)
        w.writerow(["day_stem", "other_stem", "ten_god"])
        for a in bc.STEMS:
            for b in bc.STEMS:
                w.writerow([a, b, bc.ten_god(a, b)])

    # 4) 财星判定抽样（含透干优先边界：壬日主 + 丙午日 → 偏财）
    with (OUT / "wealth.csv").open("w", newline="", encoding="utf-8") as f:
        w = csv.writer(f)
        w.writerow(["day_stem", "stem", "branch", "wealth"])
        for ds in bc.STEMS:
            for gz in rng.sample([bc.ganzhi_of_index(i) for i in range(60)], 12):
                w.writerow([ds, gz[0], gz[1], bc.wealth_type_of(ds, gz[0], gz[1])])
        # 透干优先的边界用例：壬日主遇丙午日，透丙为偏财（午藏丁才是正财）
        w.writerow(["壬", "丙", "午", bc.wealth_type_of("壬", "丙", "午")])

    # 5) 日主强弱（Rule v1.2，年月日六字口径，时柱不计）。
    #    除常规抽样外，专门把分数最贴近 ±2.0 阈值的命盘塞进夹具：双端一个是 `>=`、
    #    一个写成 `>` 这类差异，只有压线样本才测得出来，随机盘几乎全在安全区。
    scored = []
    for i in range(0, 12000, 11):
        r = ganzhi_row(dt.date(1990, 12, 1) + dt.timedelta(days=i))
        y_p, m_p, d_p = r[1], r[2], r[3]
        s = bc.strength_score(y_p, m_p, d_p)
        scored.append((abs(abs(s) - bc.STRENGTH_THRESHOLD), y_p, m_p, d_p, s))
    scored.sort(key=lambda t: t[0])
    boundary = scored[:14]
    spread = sorted(scored[14:], key=lambda t: t[1])
    rows = {t[1:4] + (t[4],) for t in boundary} | {t[1:4] + (t[4],) for t in spread[::len(spread) // 46]}
    with (OUT / "strength.csv").open("w", newline="", encoding="utf-8") as f:
        w = csv.writer(f)
        w.writerow(["year_pillar", "month_pillar", "day_pillar", "score", "strength"])
        for y_p, m_p, d_p, s in sorted(rows):
            w.writerow([y_p, m_p, d_p, f"{s:.2f}", bc.day_master_strength(y_p, m_p, d_p)])

    # 6) 大运与起运（V1.3 Gate G1 夹具）
    #    抽样包含：四象限代表股、平盘代表股、缺失代表股、立春及交节边界代表股
    import dayun_core as dc
    dayun_picks = []
    # 平盘 5 只
    flats = [r for r in wb_rows if r.first_change == 0.0][:5]
    dayun_picks.extend(flats)
    # 缺失 1 只
    missing = [r for r in wb_rows if r.first_day_flag in ("数据缺失", "缺失") or r.first_change is None]
    dayun_picks.extend(missing)
    # 四象限各 15 只
    for stem_yang in (True, False):
        for proxy_yang in (True, False):
            matched = [
                r for r in wb_rows
                if r.first_change not in (0.0, None) and r.first_day_flag not in ("数据缺失", "缺失")
                and dc.STEM_YANG[r.year_pillar[0]] == stem_yang
                and (r.first_change > 0) == proxy_yang
            ]
            dayun_picks.extend(matched[:15])
    # 立春当日 9 只
    lichun_samples = [r for r in wb_rows if dc.is_solar_term_boundary(r.listing_date)]
    dayun_picks.extend(lichun_samples[:9])

    with (OUT / "dayun.csv").open("w", newline="", encoding="utf-8") as f:
        w = csv.writer(f)
        w.writerow(["code", "listing_date", "year_pillar", "month_pillar", "first_change", "first_day_flag",
                    "direction", "status", "start_date", "start_age", "first_period_ganzhi", "boundary_flag"])
        seen_codes = set()
        for r in dayun_picks:
            if r.code in seen_codes:
                continue
            seen_codes.add(r.code)
            res = dc.calculate_stock_luck_cycle(
                r.listing_date, r.year_pillar, r.month_pillar, r.day_pillar, r.hour_pillar,
                r.first_change, r.first_day_flag,
            )
            first_gz = res["periods"][0]["ganzhi"] if res["periods"] else ""
            start_age_str = str(res["start_age"]) if res["start_age"] is not None else ""
            w.writerow([
                r.code, r.listing_date.isoformat(), r.year_pillar, r.month_pillar,
                r.first_change if r.first_change is not None else "",
                r.first_day_flag, res["direction"], res["status"],
                res["start_date"] or "", start_age_str,
                first_gz, res["boundary_flag"] or "",
            ])

    # 7) 原局内部关系（V1.3 Gate G2 夹具）
    #    选取代表性命局：茅台、宁德时代、平安银行、神华(0关系)、三合/三会/三刑/天合地合等多样性命盘
    import relation_core as rc
    STRUCTURAL_TYPES = frozenset({
        "天干五合", "天干相冲", "六合", "六冲", "三合", "半合", "三会",
        "相刑", "三刑", "自刑", "相害", "六破", "同支", "伏吟", "反吟", "天合地合", "天克地冲"
    })
    golden_codes = ["600519", "300750", "000001", "601088", "002171"]
    natal_picks = [r for r in wb_rows if r.code in golden_codes]
    # 再额外抽取 45 只各类型命盘
    picks_more = rng.sample(wb_rows, 45)
    natal_picks.extend(picks_more)

    seen_chart_keys = set()
    with (OUT / "natal_relation.csv").open("w", newline="", encoding="utf-8") as f:
        w = csv.writer(f)
        w.writerow(["code", "listing_date", "year_pillar", "month_pillar", "day_pillar",
                    "relation_count", "relation_types"])
        for r in natal_picks:
            ck = f"{r.year_pillar}_{r.month_pillar}_{r.day_pillar}"
            if ck in seen_chart_keys:
                continue
            seen_chart_keys.add(ck)
            events = rc.compute_natal_internal_relations(r.year_pillar, r.month_pillar, r.day_pillar)
            struct_events = [e for e in events if e.relation_type in STRUCTURAL_TYPES]
            types_str = ";".join(e.relation_type for e in struct_events)
            w.writerow([
                r.code, r.listing_date.isoformat(), r.year_pillar, r.month_pillar, r.day_pillar,
                len(struct_events), types_str
            ])

    # 8) 喜用候选与格局解释（V1.3 Gate G3 夹具）
    #    选取代表性命盘：茅台、宁德、平安、神华、压线极限盘、身强、身弱、中和各类型
    import yongshen_core as yc
    yongshen_picks = [r for r in wb_rows if r.code in golden_codes]
    boundary_charts = [
        ("丁酉", "乙巳", "戊戌"),  # 恰好 +2.0 (身强)
        ("丁丑", "癸丑", "乙卯"),  # 恰好 -2.0 (身弱)
        ("丁丑", "乙巳", "庚午"),  # -1.9 (中和)
        ("丁丑", "丙午", "丙午"),  # +1.65 (中和)
        ("丁丑", "壬子", "甲午"),  # +0.75 (中和)
    ]
    yongshen_picks.extend(rng.sample(wb_rows, 40))
    seen_yongshen_keys = set()
    with (OUT / "yongshen.csv").open("w", newline="", encoding="utf-8") as f:
        w = csv.writer(f)
        w.writerow([
            "year_pillar", "month_pillar", "day_pillar",
            "day_stem", "month_branch", "strength_score", "strength_level",
            "status", "yong_shen", "xi_shen", "ji_shen", "chou_shen",
            "candidate_elements", "tiaohou_note", "rationale"
        ])
        for y, m, d in boundary_charts:
            ck = f"{y}_{m}_{d}"
            seen_yongshen_keys.add(ck)
            res = yc.compute_yongshen_candidate(y, m, d)
            w.writerow([
                y, m, d,
                res["day_stem"], res["month_branch"], f"{res['strength_score']:.2f}", res["strength_level"],
                res["status"], ",".join(res["yong_shen"]), ",".join(res["xi_shen"]),
                ",".join(res["ji_shen"]), ",".join(res["chou_shen"]),
                ",".join(res["candidate_elements"]), res["tiaohou_note"], res["rationale"]
            ])
        for r in yongshen_picks:
            y, m, d = r.year_pillar, r.month_pillar, r.day_pillar
            ck = f"{y}_{m}_{d}"
            if ck in seen_yongshen_keys:
                continue
            seen_yongshen_keys.add(ck)
            res = yc.compute_yongshen_candidate(y, m, d)
            w.writerow([
                y, m, d,
                res["day_stem"], res["month_branch"], f"{res['strength_score']:.2f}", res["strength_level"],
                res["status"], ",".join(res["yong_shen"]), ",".join(res["xi_shen"]),
                ",".join(res["ji_shen"]), ",".join(res["chou_shen"]),
                ",".join(res["candidate_elements"]), res["tiaohou_note"], res["rationale"]
            ])

    print("fixtures written to", OUT, "ganzhi rows:", len(sample), "strength rows:", len(rows),
          "dayun rows:", len(seen_codes), "natal rows:", len(seen_chart_keys),
          "yongshen rows:", len(seen_yongshen_keys))


if __name__ == "__main__":
    main()
