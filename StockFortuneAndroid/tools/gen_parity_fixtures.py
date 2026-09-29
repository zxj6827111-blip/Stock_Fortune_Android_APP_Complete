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
    rows, _ = read_workbook(DEFAULT_XLSX)
    rng = random.Random(20260929)

    # 1) 干支抽样：真实上市日 + 全部交节当日 + 随机日
    dates = {r.listing_date for r in rows}
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
    picks = rng.sample(rows, 60)
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
    print("fixtures written to", OUT, "ganzhi rows:", len(sample))


if __name__ == "__main__":
    main()
