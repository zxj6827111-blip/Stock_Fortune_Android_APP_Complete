"""verify_database.py —— 预置库的端到端校验（构建门禁，任一失败即 exit(1)）。

校验分三类：
  A 数据源复算：Excel 5395 行四柱必须与 lunar-python（09:30 口径）逐字一致；
  B 独立实现交叉：日柱用纯 60 甲子算式、时柱用五鼠遁、年柱用立春分界，与库中值交叉验证；
  C 结构与业务：日历连续性 / 上市日必为交易日 / 藏干十神一致性 / 库完整性 / 兄弟项目 golden 用例。
"""

from __future__ import annotations

import datetime as dt
import sqlite3
import sys
from pathlib import Path

import bazi_core as bc
import trade_calendar as tc
from stock_xlsx import read_workbook
from build_database import DEFAULT_XLSX, ASSETS_DB

GOLDEN = [  # 来自 stock-metaphysics-platform/tests/golden（lunar-python 1.4.8 已验证）
    (2001, 8, 27, 9, 30, "辛巳", "丙申", "壬戌", "乙巳", "贵州茅台上市时刻"),
    (1991, 4, 3, 9, 30, "辛未", "辛卯", "癸卯", "丁巳", "平安银行上市时刻"),
    (2018, 6, 11, 9, 30, "戊戌", "戊午", "甲戌", "己巳", "宁德时代上市时刻"),
    (1984, 2, 2, 12, 0, "癸亥", "乙丑", "丙寅", "甲午", "立春前年柱"),
    (2024, 2, 4, 10, 0, "癸卯", "乙丑", "戊戌", "丁巳", "2024 立春前"),
    (2024, 2, 4, 20, 0, "甲辰", "丙寅", "戊戌", "壬戌", "2024 立春后"),
]
DAY_ANCHOR = dt.date(1949, 10, 1)  # 甲子日

FAILS: list[str] = []
PASSES: list[str] = []


def check(name: str, ok: bool, detail: str = ""):
    (PASSES if ok else FAILS).append(f"{name}{' — ' + detail if detail else ''}")
    print(f"{'PASS' if ok else 'FAIL'}  {name}{'  ' + detail if detail else ''}")


def main() -> int:
    if not ASSETS_DB.exists():
        print("预置库不存在，请先运行 build_database.py")
        return 1
    rows, meta = read_workbook(DEFAULT_XLSX)
    con = sqlite3.connect(ASSETS_DB)
    con.row_factory = sqlite3.Row

    # ---- A 数据源行数与主键
    check("A0 Excel 行数 5395", len(rows) == 5395, f"实际 {len(rows)}")
    db_stocks = {r["code"]: dict(r) for r in con.execute("SELECT * FROM stock")}
    check("A1 stock 表行数 == Excel 行数", len(db_stocks) == len(rows), f"{len(db_stocks)} vs {len(rows)}")

    # ---- B 独立算式复算日柱（不依赖 lunar-python）
    bad = [r.code for r in rows
           if bc.ganzhi_of_index((r.listing_date - DAY_ANCHOR).days % 60) != r.day_pillar]
    check("B1 日柱 == 60甲子算式(锚点1949-10-01甲子)", not bad, f"不符 {len(bad)} 条 {bad[:3]}")

    # ---- B 五鼠遁复算时柱
    bad = [r.code for r in rows
           if bc.hour_stem_of(r.day_pillar[0], bc.BRANCHES.index("巳")) != r.hour_pillar[0]
           or r.hour_pillar[1] != "巳"]
    check("B2 时柱 == 五鼠遁(日干, 巳时)", not bad, f"不符 {len(bad)} 条 {bad[:3]}")

    # ---- B 纳音复算
    bazi_db = {r["stock_id"]: dict(r) for r in con.execute("SELECT * FROM stock_bazi")}
    na_bad = [sid for sid, b in bazi_db.items() if bc.na_yin(b["day_pillar"]) != b["na_yin"]]
    check("B3 纳音与日柱一致", not na_bad, f"不符 {len(na_bad)} 条")

    # ---- A 数据源四柱 == 本项目日粒度实现（端到端复算）
    from lunar_python import Solar
    import solar_terms as st
    from build_database import ganzhi_row
    mism = {"year": [], "month": [], "day": [], "hour": []}
    bazi_db = {r["stock_id"]: dict(r) for r in con.execute("SELECT * FROM stock_bazi")}
    for r in rows:
        sid = db_stocks[r.code]["id"]
        want = bazi_db[sid]
        got = ganzhi_row(r.listing_date)
        for key, idx in (("year", 1), ("month", 2), ("day", 3)):
            if want[f"{key}_pillar"] != got[idx]:
                mism[key].append((r.code, want[f"{key}_pillar"], got[idx]))
        expect_hour = bc.hour_stem_of(got[3][0], bc.BRANCHES.index("巳")) + "巳"
        if want["hour_pillar"] != expect_hour:
            mism["hour"].append((r.code, want["hour_pillar"], expect_hour))
    total = sum(len(v) for v in mism.values())
    check("A2 数据源 5395 行四柱 == 日粒度实现（立春/交节当日切换）", total == 0,
          "; ".join(f"{k}:{len(v)}{v[:2]}" for k, v in mism.items() if v))

    # ---- A3 与 lunar-python 时刻粒度的差异必须全部落在交节当日
    diff_days = 0
    off_boundary = []
    for r in rows:
        e = Solar.fromYmdHms(r.listing_date.year, r.listing_date.month, r.listing_date.day,
                             9, 30, 0).getLunar().getEightChar()
        g = ganzhi_row(r.listing_date)
        if (e.getYear(), e.getMonth()) != (g[1], g[2]):
            diff_days += 1
            terms = st.term_dates(r.listing_date.year)
            if r.listing_date not in set(terms.values()):
                off_boundary.append((r.code, r.listing_date.isoformat()))
    check("A3 与时刻粒度差异仅存在于交节当日", not off_boundary,
          f"共 {diff_days} 处差异，其中非交节日 {len(off_boundary)} 处 {off_boundary[:3]}")

    # ---- C 兄弟项目 golden 用例（日柱/时柱口径完全一致；年柱月柱仅交节当日不同）
    bad = []
    for y, m, d, h, mi, yg, mg, dg, hg, note in GOLDEN:
        e = Solar.fromYmdHms(y, m, d, h, mi, 0).getLunar().getEightChar()
        if (e.getDay(), e.getTime()) != (dg, hg):
            bad.append((note, "日/时柱", e.getDay(), e.getTime()))
        g = ganzhi_row(dt.date(y, m, d))
        on_boundary = dt.date(y, m, d) in set(st.term_dates(y).values())
        if not on_boundary and (g[1], g[2]) != (yg, mg):
            bad.append((note, "年/月柱", g[1], g[2]))
    check("C1 兄弟项目 golden 用例（日/时柱 6/6，年/月柱非交节日全对）", not bad, str(bad[:3]))

    # ---- C 日历表完整性 / 连续性
    n_gz = con.execute("SELECT COUNT(*) FROM ganzhi_calendar").fetchone()[0]
    d0 = dt.date.fromisoformat(con.execute("SELECT MIN(date) FROM ganzhi_calendar").fetchone()[0])
    d1 = dt.date.fromisoformat(con.execute("SELECT MAX(date) FROM ganzhi_calendar").fetchone()[0])
    check("C2 干支日历无缺日", n_gz == (d1 - d0).days + 1, f"{n_gz} 行, {d0}~{d1}")
    prev, breaks = None, 0
    for date, day in con.execute("SELECT date, day_ganzhi FROM ganzhi_calendar ORDER BY date"):
        if prev is not None and bc.sexagenary_index(day) != (bc.sexagenary_index(prev) + 1) % 60:
            breaks += 1
        prev = day
    check("C3 日柱逐日推进 60 甲子（无跳变）", breaks == 0, f"跳变 {breaks} 处")

    # 年柱只在立春前后切换：每年至多一次，且只能落在 1-2 月
    flips = 0
    bad_month: list[str] = []
    per_year_flips: dict[str, int] = {}
    prev = None
    for date, yp in con.execute("SELECT date, year_ganzhi FROM ganzhi_calendar ORDER BY date"):
        if prev and yp != prev[1]:
            flips += 1
            per_year_flips[date[:4]] = per_year_flips.get(date[:4], 0) + 1
            if int(date[5:7]) not in (1, 2):
                bad_month.append(date)
        prev = (date, yp)
    over = {y: n for y, n in per_year_flips.items() if n > 1}
    # 旧断言是 flips <= 46（跨 45 年约等于不约束"每年至多一次"），且月份违规直接
    # append 到 FAILS、绕过 check() 既不打印也不计数 —— 一个自身有缺陷的校验器会给出虚假安全感。
    check("C4 年柱每年至多切换一次且仅在 1-2 月（立春分界）",
          not bad_month and not over and flips >= 44,
          f"{flips} 次切换 / {len(per_year_flips)} 年有切换，"
          f"非立春月切换 {bad_month[:3]}，一年多切 {list(over.items())[:3]}")

    # ---- C 交易日历：所有上市日必须是交易日
    trade = {r["date"]: r["is_trade_day"] for r in con.execute("SELECT date, is_trade_day FROM trade_calendar")}
    conflict = [r.code for r in rows if not trade.get(r.listing_date.isoformat(), 0)]
    detail = ""
    if conflict:
        pairs = [(c, db_stocks[c]["listing_date"],
                  con.execute("SELECT closed_reason FROM trade_calendar WHERE date=?",
                              (db_stocks[c]["listing_date"],)).fetchone()[0]) for c in conflict[:6]]
        detail = f"冲突 {len(conflict)} 条 {pairs}"
    check("C5 5395 个真实上市日均为交易日", not conflict, detail)
    weekend_open = [d for d, v in trade.items() if v == 1 and dt.date.fromisoformat(d).weekday() >= 5]
    check("C6 周末休市（仅历史特例可开市）",
          set(weekend_open) <= {"1990-12-01"}, f"周末开市 {weekend_open[:5]}")
    check("C7 今日(2026-09-29)为交易日", trade.get("2026-09-29") == 1,
          str(con.execute("SELECT * FROM trade_calendar WHERE date='2026-09-29'").fetchone()))
    n_trade = sum(trade.values())
    check("C8 交易日历覆盖 1990-12-01~2035-12-31", len(trade) == n_gz, f"{len(trade)} 天 / {n_trade} 个交易日")

    # ---- C25/C26/C27/C28 休市覆盖族与 curated 溯源
    # 背景一：closures_for 曾写成"表里有该年就完全不走规则"的互斥分支，2024 的表只登记了
    # 元旦/春节/国庆，于是清明/劳动/端午/中秋一个都不产生，出厂库把 8 个真实休市周中标成
    # 交易日（2024 因此 250 天，邻年 243）。
    # 背景二：清明/端午/中秋 自 2008 年才法定，旧实现无条件套用规则，1991-2007 多判 33 个
    # 周中休市日。下面四条把这两类都钉死。
    fam_conf: dict[str, dict[str, set[str]]] = {}
    year_trade: dict[str, int] = {}
    year_days: dict[str, int] = {}
    for date, t_day, reason, conf in con.execute(
        "SELECT date, is_trade_day, closed_reason, confidence FROM trade_calendar ORDER BY date"
    ):
        year = date[:4]
        year_days[year] = year_days.get(year, 0) + 1
        year_trade[year] = year_trade.get(year, 0) + (1 if t_day == 1 else 0)
        if t_day == 0 and reason and reason != "周末":
            for fam in tc.families_of(reason):
                fam_conf.setdefault(year, {}).setdefault(fam, set()).add(conf)
    full_years = sorted(y for y, n in year_days.items() if n >= 300)

    def required_fams(y: str) -> tuple[str, ...]:
        base = ("元旦", "春节", "劳动节", "国庆节")
        if int(y) >= tc.STATUTORY_LUNAR_HOLIDAY_START:
            return base + ("清明节", "端午节", "中秋节")
        return base

    missing_fam = {y: sorted(f for f in required_fams(y) if f not in fam_conf.get(y, {}))
                   for y in full_years}
    missing_fam = {y: v for y, v in missing_fam.items() if v}
    check("C25 每个完整年度的法定假日族都产生休市判定（按 2008 法定起点分年要求）",
          not missing_fam, f"缺族年份 {list(missing_fam.items())[:4]}")

    premature = {y: sorted(f for f in ("清明节", "端午节", "中秋节")
                           if f in fam_conf.get(y, {}))
                 for y in full_years if int(y) < tc.STATUTORY_LUNAR_HOLIDAY_START}
    premature = {y: v for y, v in premature.items() if v}
    check("C28 2008 年前不得把清明/端午/中秋判为休市（当年非法定假日）",
          not premature, f"多判休市 {sum(len(v) for v in premature.values())} 年次 {list(premature.items())[:4]}")

    # 表内条目全部标 curated 的年份 = 已公布安排已到位的年份
    published = sorted(y for y, es in tc.CURATED_RANGES.items()
                       if es and all(c == "curated" for _s, _e, _r, c in es))
    degraded = {str(y): sorted(f for f in required_fams(str(y))
                               if "curated" not in fam_conf.get(str(y), {}).get(f, set()))
                for y in published}
    degraded = {y: v for y, v in degraded.items() if v}
    check("C26 已公布年份的节假日族必须以 curated 判定（不得静默退化为规则推算）",
          not degraded, f"退化年份 {list(degraded.items())[:4]}；已公布年份 {published[0]}~{published[-1]}")

    off_band = {y: n for y, n in year_trade.items()
                if y in {str(p) for p in published} and not 241 <= n <= 247}
    check("C27 已公布年份交易日数落在 241~247（漏节日会冲到 250）",
          not off_band, f"异常年份 {sorted(off_band.items())[:6]}")

    # ---- C 藏干十神一致性
    db_hidden = {}
    for r in con.execute("SELECT stock_id, pillar, hidden_stem, ten_god FROM stock_hidden_ten_god"):
        db_hidden.setdefault((r["stock_id"], r["pillar"], r["hidden_stem"]), r["ten_god"])
    recomputed = {}
    for sid, b in bazi_db.items():
        for pillar in ("year", "month", "day", "hour"):
            branch = b[f"{pillar}_pillar"][1]
            for h in bc.hidden_stems(branch):
                recomputed[(sid, pillar, h)] = bc.ten_god(b["day_stem"], h)
    diff = [k for k in recomputed if db_hidden.get(k) != recomputed[k]]
    extra = [k for k in db_hidden if k not in recomputed]
    check("C9 stock_hidden_ten_god 与规则表重算一致",
          not diff and not extra, f"差异 {len(diff)} 多余 {len(extra)} 共 {len(recomputed)} 行")

    # ---- C 规则一致性（规则表不入库，改由 Kotlin BaziTables + ParityTest 双向校验）
    check("C10 十神全表 100 组取值合法",
          len({(a, b) for a in bc.STEMS for b in bc.STEMS}) == 100
          and all(bc.ten_god(a, b) in bc.TEN_GODS for a in bc.STEMS for b in bc.STEMS))
    check("C11 藏干表 28 条 / 纳音表 30 组",
          sum(len(v) for v in bc.HIDDEN_STEMS.values()) == 28 and len(bc._NA_YIN_PAIRS) == 30)
    check("C12 甲日见己=正财、甲日见戊=偏财（十神抽样）",
          bc.ten_god("甲", "己") == "正财" and bc.ten_god("甲", "戊") == "偏财"
          and bc.ten_god("甲", "庚") == "七杀" and bc.ten_god("甲", "癸") == "正印")

    # ---- C 行业字段（来自 行业分类.xlsx，三级"一级-二级-三级"）
    ind = list(con.execute("SELECT code, industry, industry_full FROM stock"))
    no_ind = [c for c, i, f in ind if not i or i == "未分类"]
    bad_full = [c for c, i, f in ind if len(f.split("-")) != 3]
    mismatch = [c for c, i, f in ind if f and f.split("-")[0] != i]
    check("C22 行业覆盖 5395/5395 且无未分类", len(ind) == 5395 and not no_ind,
          f"缺失 {len(no_ind)} {no_ind[:3]}")
    check("C23 细分行业均为三级且一级与 industry 一致", not bad_full and not mismatch,
          f"非三级 {len(bad_full)} 不一致 {len(mismatch)}")
    lv1 = {i for _, i, _ in ind}
    check("C24 一级行业数量合理（20-40）", 20 <= len(lv1) <= 40, f"共 {len(lv1)} 个一级行业")

    # ---- C 库完整性
    check("C13 PRAGMA integrity_check", con.execute("PRAGMA integrity_check").fetchone()[0] == "ok")
    fk = con.execute("PRAGMA foreign_key_check").fetchall()
    check("C14 外键完整", not fk, str(fk[:2]))
    check("C15 预置库体积 < 20MB", ASSETS_DB.stat().st_size < 20 * 1024 * 1024,
          f"{ASSETS_DB.stat().st_size / 1024 / 1024:.1f} MB")

    # ---- C17 预置库结构必须与 Room 导出的 schema 逐字一致（否则运行期可能打不开/列不符）
    import glob
    import json as _json
    import re as _re
    from build_database import ROOT as _ROOT
    schema_files = sorted(glob.glob(str(_ROOT / "app" / "schemas" / "**" / "*.json"), recursive=True))
    if schema_files:
        data = _json.loads(Path(max(schema_files, key=lambda p: Path(p).stat().st_mtime)).read_text(encoding="utf-8"))
        rdb = data["database"]

        def norm(sql: str) -> str:
            return _re.sub(r"\s+", " ", sql.replace("`", "").lower().replace("if not exists", "")).replace(" (", "(").strip().rstrip(";").strip()

        actual = {r[0]: norm(r[1]) for r in con.execute("SELECT name, sql FROM sqlite_master WHERE type='table'")}
        actual_idx = {r[0]: norm(r[1]) for r in con.execute("SELECT name, sql FROM sqlite_master WHERE type='index' AND name NOT LIKE 'sqlite_%'")}
        diffs = []
        for e in rdb["entities"]:
            want = norm(e["createSql"].replace("${TABLE_NAME}", e["tableName"]))
            if actual.get(e["tableName"]) != want:
                diffs.append(e["tableName"])
            for i in e.get("indices", []):
                iw = norm(i["createSql"].replace("${DATABASE_NAME}", "main").replace("${TABLE_NAME}", e["tableName"]))
                if actual_idx.get(i["name"]) != iw:
                    diffs.append(i["name"])
        check("C17 预置库 DDL 与 Room schema 逐字一致", not diffs, f"差异对象 {diffs[:6]}")
        extra_tables = set(actual) - {e["tableName"] for e in rdb["entities"]} - {"room_master_table", "android_metadata", "sqlite_sequence"}
        check("C20 无 Room 未知的多余表", not extra_tables, f"多余 {sorted(extra_tables)}")
        master = con.execute("SELECT identity_hash FROM room_master_table WHERE id=42").fetchone()
        check("C18 room_master_table 身份哈希与 Room 一致", master and master[0] == rdb["identityHash"],
              f"asset={master[0] if master else None} room={rdb['identityHash']}")
        uv = con.execute("PRAGMA user_version").fetchone()[0]
        check("C19 user_version 与 Room 版本一致", uv == rdb["version"], f"{uv} vs {rdb['version']}")
    else:
        check("C17 预置库 DDL 与 Room schema 逐字一致", False, "未找到 Room 导出的 schema JSON")

    # ---- C 业务抽样：2026-09-29 全市场扫描（与 APP 侧 Kotlin 结果应一致）
    gz = con.execute("SELECT * FROM ganzhi_calendar WHERE date='2026-09-29'").fetchone()
    cols = [c[0] for c in con.execute("SELECT * FROM ganzhi_calendar LIMIT 1").description]
    gz = dict(zip(cols, gz))
    wealth = {"正财": 0, "偏财": 0, "其他": 0}
    for sid, b in bazi_db.items():
        wealth[bc.wealth_type_of(b["day_stem"], gz["day_stem"], gz["day_branch"])] += 1
    check("C16 2026-09-29 全市场扫描可完成且正/偏财均非空",
          sum(wealth.values()) == 5395 and wealth["正财"] > 0 and wealth["偏财"] > 0,
          f"正财{wealth['正财']} 偏财{wealth['偏财']} 其他{wealth['其他']} 流年{gz['year_ganzhi']} 流月{gz['month_ganzhi']} 流日{gz['day_ganzhi']}")

    # ---- C21 所有 DAO @Query 必须能在预置库上成功预编译（抓列名/表名拼写错误）
    dao_src = Path(__file__).resolve().parents[1] / "app" / "src" / "main" / "java" / "com" / "stockfortune" / "app" / "data" / "dao" / "Daos.kt"
    import re as _re2
    src = dao_src.read_text(encoding="utf-8")
    sqls = _re2.findall(r'"""(.*?)"""', src, _re2.S) + _re2.findall(r'@Query\("(.*?)"\)', src)
    bad_sql = []
    for raw in sqls:
        q = " ".join(line.strip() for line in raw.strip().splitlines())
        q = _re2.sub(r"IN \(\s*:\w+\s*\)", "IN ('正财')", q)
        q = _re2.sub(r":\w+", "?", q)
        params = ["1990-01-01"] * q.count("?")
        try:
            con.execute(f"EXPLAIN {q}", params)
        except Exception as exc:  # noqa: BLE001
            bad_sql.append((q[:70], str(exc)[:80]))
    check("C21 DAO 全部 @Query 可在预置库预编译", not bad_sql, f"{len(sqls)} 条语句，失败 {bad_sql[:2]}")

    # ---- C30-C32 V1.3 Phase 1 大运与起运门禁 (Gate G1)
    luck_rows = con.execute("SELECT stock_id, direction, status, start_date, start_age, first_day_polarity FROM stock_luck_cycle").fetchall()
    check("C30 stock_luck_cycle 覆盖 5395/5395", len(luck_rows) == 5395, f"实际 {len(luck_rows)}")
    flat_count = sum(1 for r in luck_rows if r["status"] == "unavailable_flat" and r["direction"] == "unavailable")
    missing_count = sum(1 for r in luck_rows if r["status"] == "unavailable_missing" and r["direction"] == "unavailable")
    avail_count = sum(1 for r in luck_rows if r["status"] == "available" and r["direction"] in ("forward", "reverse"))
    check("C31 平盘(315)与缺失(1)标记为 unavailable 且有效股(5079)方向合规",
          flat_count == 315 and missing_count == 1 and avail_count == 5079,
          f"平盘 {flat_count}, 缺失 {missing_count}, 有效 {avail_count}")

    period_rows = con.execute("SELECT stock_id, cycle_index, ganzhi, stem, branch, start_year, end_year FROM luck_cycle_period").fetchall()
    check("C32 luck_cycle_period 60948 行且每只可用股票恰有 12 步周期",
          len(period_rows) == 5079 * 12,
          f"实际 {len(period_rows)} 行 vs 期望 {5079 * 12}")

    con.close()
    print(f"\n==== 校验结果: {len(PASSES)} 通过 / {len(FAILS)} 失败 ====")
    for f in FAILS:
        print("  ✗", f)
    return 1 if FAILS else 0


if __name__ == "__main__":
    sys.exit(main())
