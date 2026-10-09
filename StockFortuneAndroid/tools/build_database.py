"""build_database.py —— Excel → 预置 SQLite 数据库（构建期一次性执行，产物随 APK 分发）。

产物：
  app/src/main/assets/databases/stock_fortune.db   Room createFromAsset 直接打开
  database/init_stock_fortune.sql                  全量 DDL + 数据（可复现、可审阅）
  database/import_report.json                      行数 / 校验结论 / 数据快照版本

用法：python3 tools/build_database.py [--start 1990-12-01] [--end 2035-12-31]
"""

from __future__ import annotations

import argparse
import datetime as dt
import glob
import json
import sqlite3
from pathlib import Path

import bazi_core as bc
import dayun_core as dc
import relation_core as rc
import solar_terms as st
import trade_calendar as tc
import yongshen_core as yc
from stock_xlsx import read_industry, read_workbook

from lunar_python import Solar

ROOT = Path(__file__).resolve().parents[1]
DEFAULT_XLSX = ROOT.parent / "Stock_Fortune_Android_APP_AI_Start_Kit" / "input_data" / "生辰八字.xlsx"
if not DEFAULT_XLSX.exists() and len(ROOT.parents) > 2:
    alt = ROOT.parents[2] / "Stock_Fortune_Android_APP_AI_Start_Kit" / "input_data" / "生辰八字.xlsx"
    if alt.exists():
        DEFAULT_XLSX = alt
INDUSTRY_XLSX = DEFAULT_XLSX.parent / "行业分类.xlsx"
ASSETS_DB = ROOT / "app" / "src" / "main" / "assets" / "databases" / "stock_fortune.db"
SQL_OUT = ROOT / "database" / "init_stock_fortune.sql"
REPORT_OUT = ROOT / "database" / "import_report.json"

SCHEMA_VERSION = 4
CALENDAR_VERSION = "2026-09-29"

DDL = """
PRAGMA journal_mode = DELETE;
CREATE TABLE stock(
  id INTEGER PRIMARY KEY NOT NULL,
  code TEXT NOT NULL UNIQUE,
  symbol TEXT NOT NULL,
  name TEXT NOT NULL,
  exchange TEXT NOT NULL,
  board TEXT NOT NULL,
  listing_date TEXT NOT NULL,
  first_open REAL,
  first_close REAL,
  first_change REAL,
  first_day_flag TEXT NOT NULL,
  industry TEXT NOT NULL DEFAULT '未分类',
  industry_full TEXT NOT NULL DEFAULT '',
  stock_nature TEXT NOT NULL DEFAULT 'A股'
);
CREATE INDEX idx_stock_name ON stock(name);
CREATE INDEX idx_stock_symbol ON stock(symbol);
CREATE INDEX idx_stock_listing ON stock(listing_date);

CREATE TABLE stock_bazi(
  stock_id INTEGER PRIMARY KEY REFERENCES stock(id) ON DELETE CASCADE,
  full_bazi TEXT NOT NULL,
  year_pillar TEXT NOT NULL, month_pillar TEXT NOT NULL,
  day_pillar TEXT NOT NULL, hour_pillar TEXT NOT NULL,
  year_stem TEXT NOT NULL, year_branch TEXT NOT NULL,
  month_stem TEXT NOT NULL, month_branch TEXT NOT NULL,
  day_stem TEXT NOT NULL, day_branch TEXT NOT NULL,
  hour_stem TEXT NOT NULL, hour_branch TEXT NOT NULL,
  day_master_element TEXT NOT NULL,
  month_season_element TEXT NOT NULL,
  na_yin TEXT NOT NULL,
  day_master_strength TEXT NOT NULL
);
CREATE INDEX idx_bazi_day_pillar ON stock_bazi(day_pillar);
CREATE INDEX idx_bazi_year_pillar ON stock_bazi(year_pillar);
CREATE INDEX idx_bazi_month_pillar ON stock_bazi(month_pillar);

CREATE TABLE stock_hidden_ten_god(
  stock_id INTEGER NOT NULL REFERENCES stock(id) ON DELETE CASCADE,
  pillar TEXT NOT NULL,
  branch TEXT NOT NULL,
  hidden_stem TEXT NOT NULL,
  ten_god TEXT NOT NULL,
  rank TEXT NOT NULL,
  PRIMARY KEY(stock_id, pillar, hidden_stem)
);
CREATE INDEX idx_shtg_god ON stock_hidden_ten_god(ten_god, stock_id);

CREATE TABLE ganzhi_calendar(
  date TEXT PRIMARY KEY,
  year_ganzhi TEXT NOT NULL, month_ganzhi TEXT NOT NULL, day_ganzhi TEXT NOT NULL,
  year_stem TEXT NOT NULL, year_branch TEXT NOT NULL,
  month_stem TEXT NOT NULL, month_branch TEXT NOT NULL,
  day_stem TEXT NOT NULL, day_branch TEXT NOT NULL,
  month_branch_label TEXT NOT NULL,
  solar_term TEXT
);

CREATE TABLE trade_calendar(
  date TEXT PRIMARY KEY,
  is_trade_day INTEGER NOT NULL,
  weekday INTEGER NOT NULL,
  closed_reason TEXT,
  confidence TEXT NOT NULL
);

CREATE TABLE scan_cache(
  stock_id INTEGER NOT NULL, date TEXT NOT NULL,
  year_ten_god TEXT NOT NULL, month_ten_god TEXT NOT NULL, day_ten_god TEXT NOT NULL,
  wealth_type TEXT NOT NULL, is_trade_day INTEGER NOT NULL, computed_at INTEGER NOT NULL,
  PRIMARY KEY(stock_id, date)
);
CREATE TABLE favorite(
  stock_id INTEGER PRIMARY KEY REFERENCES stock(id) ON DELETE CASCADE,
  added_at INTEGER NOT NULL
);
CREATE TABLE app_meta(key TEXT PRIMARY KEY, value TEXT NOT NULL);

CREATE TABLE stock_luck_cycle(
  stock_id INTEGER PRIMARY KEY REFERENCES stock(id) ON DELETE CASCADE,
  stock_code TEXT NOT NULL,
  direction TEXT NOT NULL,
  status TEXT NOT NULL,
  status_reason TEXT NOT NULL,
  start_date TEXT,
  start_age INTEGER,
  first_day_polarity TEXT NOT NULL,
  rule_version TEXT NOT NULL,
  boundary_flag TEXT
);

CREATE TABLE luck_cycle_period(
  id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
  stock_id INTEGER NOT NULL REFERENCES stock(id) ON DELETE CASCADE,
  cycle_index INTEGER NOT NULL,
  ganzhi TEXT NOT NULL,
  stem TEXT NOT NULL,
  branch TEXT NOT NULL,
  start_date TEXT NOT NULL,
  end_date TEXT NOT NULL,
  start_year INTEGER NOT NULL,
  end_year INTEGER NOT NULL,
  start_age INTEGER NOT NULL,
  end_age INTEGER NOT NULL,
  rule_version TEXT NOT NULL
);
CREATE INDEX index_luck_cycle_period_stock_id ON luck_cycle_period(stock_id);
CREATE INDEX index_luck_cycle_period_stock_id_start_year_end_year ON luck_cycle_period(stock_id, start_year, end_year);

CREATE TABLE natal_relation(
  id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
  chart_key TEXT NOT NULL,
  listing_date TEXT NOT NULL,
  relation_type TEXT NOT NULL,
  category TEXT NOT NULL,
  positions TEXT NOT NULL,
  source_pillar TEXT NOT NULL,
  target_pillar TEXT NOT NULL,
  source_ganzhi TEXT NOT NULL,
  target_ganzhi TEXT NOT NULL,
  element TEXT,
  notes TEXT NOT NULL,
  rule_version TEXT NOT NULL,
  status TEXT NOT NULL
);
CREATE INDEX index_natal_relation_chart_key ON natal_relation(chart_key);
CREATE INDEX index_natal_relation_listing_date ON natal_relation(listing_date);
CREATE INDEX index_natal_relation_relation_type ON natal_relation(relation_type);

CREATE TABLE stock_yongshen(
  chart_key TEXT PRIMARY KEY NOT NULL,
  day_stem TEXT NOT NULL,
  month_branch TEXT NOT NULL,
  strength_score REAL NOT NULL,
  strength_level TEXT NOT NULL,
  status TEXT NOT NULL,
  yong_shen TEXT NOT NULL,
  xi_shen TEXT NOT NULL,
  ji_shen TEXT NOT NULL,
  chou_shen TEXT NOT NULL,
  xian_shen TEXT NOT NULL,
  candidate_elements TEXT NOT NULL,
  tiaohou_note TEXT NOT NULL,
  rationale TEXT NOT NULL,
  rule_version TEXT NOT NULL
);
CREATE INDEX index_stock_yongshen_chart_key ON stock_yongshen(chart_key);
"""

BOARD_BY_PREFIX = {
    "60": "沪市主板", "68": "科创板",
    "000": "深市主板", "001": "深市主板", "002": "深市主板", "003": "深市主板",
    "300": "创业板", "301": "创业板", "302": "创业板",
    "43": "北交所", "83": "北交所", "87": "北交所", "92": "北交所",
}


def board_of(symbol: str, exchange: str) -> str:
    for n in (3, 2):
        b = BOARD_BY_PREFIX.get(symbol[:n])
        if b:
            return b
    return "其他"


def nature_of(board: str) -> str:
    return f"A股 {board}"



DAY_ANCHOR = dt.date(1949, 10, 1)  # 甲子日


def ganzhi_row(d: dt.date) -> tuple:
    """日粒度口径（与数据源逐字一致，见 solar_terms.py 模块说明）。

    年柱 = 立春当日切换；月柱 = 十二节当日切换；日柱 = 60 甲子连续；时柱 = 巳时 + 五鼠遁。
    """
    gy = st.ganzhi_year_of(d)
    y_pillar = bc.ganzhi_of_index((gy - 1984) % 60)
    mb = st.month_branch_of(d)
    m_pillar = bc.month_stem_of(y_pillar[0], bc.BRANCHES.index(mb)) + mb
    day = bc.ganzhi_of_index((d - DAY_ANCHOR).days % 60)
    h_stem = bc.hour_stem_of(day[0], bc.BRANCHES.index("巳"))
    term = Solar.fromYmdHms(d.year, d.month, d.day, 12, 0, 0).getLunar().getJieQi()
    return (
        d.isoformat(), y_pillar, m_pillar, day,
        y_pillar[0], y_pillar[1], m_pillar[0], m_pillar[1], day[0], day[1],
        f"{mb}月",
        term if term else None,
    )


def room_ddl_statements() -> dict | None:
    """若已存在 Room 导出的 schema JSON，则直接采用 Room 自己的建表语句。

    这样预置库的 sqlite_master 与 APP 内 Room 期望的结构逐字一致，
    避免"预置库列定义与实体不符"这类只在运行期暴露的问题。
    """
    files = sorted(glob.glob(str(ROOT / "app" / "schemas" / "**" / "*.json"), recursive=True))
    if not files:
        return None
    data = json.loads(Path(max(files, key=lambda p: Path(p).stat().st_mtime)).read_text(encoding="utf-8"))
    db = data["database"]
    stmts: list[str] = []
    for e in db["entities"]:
        stmts.append(e["createSql"].replace("${TABLE_NAME}", e["tableName"]))
        for i in e.get("indices", []):
            stmts.append(i["createSql"].replace("${DATABASE_NAME}", "main").replace("${TABLE_NAME}", e["tableName"]))
    return {"stmts": stmts, "identity": db["identityHash"], "version": db["version"]}


def build(start: dt.date, end: dt.date, xlsx: Path) -> dict:
    rows, meta = read_workbook(xlsx)
    industries = read_industry(INDUSTRY_XLSX) if INDUSTRY_XLSX.exists() else {}
    if not industries:
        print("!! 未找到 行业分类.xlsx，industry 将全部落为 未分类")
    room = room_ddl_statements()
    db_path = ASSETS_DB
    db_path.parent.mkdir(parents=True, exist_ok=True)
    if db_path.exists():
        db_path.unlink()
    sql_lines = [f"-- 股运通 预置数据库（由 tools/build_database.py 生成，勿手改）",
                 f"-- 数据源: {meta['source_file']} sha256={meta['sha256']}",
                 f"-- DDL 来源: {'Room 导出 schema' if room else '内置 DDL（首次构建，之后请以 Room schema 重建）'}", ""]

    con = sqlite3.connect(db_path)
    if room:
        con.executescript(";\n".join(room["stmts"]) + ";")
        sql_lines += [s + ";" for s in room["stmts"]]
    else:
        con.executescript(DDL.strip())
        sql_lines.append(DDL.strip())
    cur = con.cursor()

    def emit(table: str, cols: list[str], batch: list[tuple]):
        if not batch:
            return
        placeholders = ",".join("?" * len(cols))
        cur.executemany(f"INSERT INTO {table}({','.join(cols)}) VALUES ({placeholders})", batch)
        for row in batch:
            vals = ",".join(
                "NULL" if v is None else (f"'{str(v).replace(chr(39), chr(39) * 2)}'" if isinstance(v, str) else str(v))
                for v in row
            )
            sql_lines.append(f"INSERT INTO {table}({','.join(cols)}) VALUES ({vals});")

    # 规则表（十神/藏干/纳音）不入库：Kotlin 侧 `BaziTables` 是唯一事实来源，
    # 并由 ParityTest 与本脚本双向校验，避免 APK 内出现 Room 未知的多余表。

    # 股票 + 八字 + 藏干十神
    stock_cols = ["id", "code", "symbol", "name", "exchange", "board", "listing_date",
                  "first_open", "first_close", "first_change", "first_day_flag",
                  "industry", "industry_full", "stock_nature"]
    bazi_cols = ["stock_id", "full_bazi", "year_pillar", "month_pillar", "day_pillar", "hour_pillar",
                 "year_stem", "year_branch", "month_stem", "month_branch", "day_stem", "day_branch",
                 "hour_stem", "hour_branch", "day_master_element", "month_season_element", "na_yin",
                 "day_master_strength"]
    hidden_cols = ["stock_id", "pillar", "branch", "hidden_stem", "ten_god", "rank"]

    stock_batch, bazi_batch, hidden_batch = [], [], []
    for sid, r in enumerate(rows, start=1):
        board = board_of(r.symbol, r.exchange)
        ind_full = industries.get(r.code, ("", ""))[1]
        ind_l1 = ind_full.split("-")[0] if ind_full else "未分类"
        stock_batch.append((sid, r.code, r.symbol, r.name, r.exchange, board, r.listing_date.isoformat(),
                            r.first_open, r.first_close, r.first_change, r.first_day_flag,
                            ind_l1, ind_full, nature_of(board)))
        season = bc.BRANCH_ELEMENT[r.month_pillar[1]]
        bazi_batch.append((sid, r.full_bazi, r.year_pillar, r.month_pillar, r.day_pillar, r.hour_pillar,
                           r.year_pillar[0], r.year_pillar[1], r.month_pillar[0], r.month_pillar[1],
                           r.day_pillar[0], r.day_pillar[1], r.hour_pillar[0], r.hour_pillar[1],
                           bc.STEM_ELEMENT[r.day_pillar[0]], season, bc.na_yin(r.day_pillar),
                           bc.day_master_strength(r.year_pillar, r.month_pillar, r.day_pillar)))
        for pillar, val in (("year", r.year_pillar), ("month", r.month_pillar),
                            ("day", r.day_pillar), ("hour", r.hour_pillar)):
            branch = val[1]
            for i, h in enumerate(bc.hidden_stems(branch)):
                hidden_batch.append((sid, pillar, branch, h, bc.ten_god(r.day_pillar[0], h), bc.HIDDEN_RANKS[i]))
        if len(stock_batch) >= 1000:
            emit("stock", stock_cols, stock_batch)
            emit("stock_bazi", bazi_cols, bazi_batch)
            emit("stock_hidden_ten_god", hidden_cols, hidden_batch)
            stock_batch, bazi_batch, hidden_batch = [], [], []
    emit("stock", stock_cols, stock_batch)
    emit("stock_bazi", bazi_cols, bazi_batch)
    emit("stock_hidden_ten_god", hidden_cols, hidden_batch)

    # 大运元数据与周期数据（Phase 1 P2-B）
    luck_cols = ["stock_id", "stock_code", "direction", "status", "status_reason",
                 "start_date", "start_age", "first_day_polarity", "rule_version", "boundary_flag"]
    period_cols = ["stock_id", "cycle_index", "ganzhi", "stem", "branch",
                   "start_date", "end_date", "start_year", "end_year", "start_age", "end_age", "rule_version"]
    luck_batch, period_batch = [], []
    for sid, r in enumerate(rows, start=1):
        res = dc.calculate_stock_luck_cycle(
            r.listing_date, r.year_pillar, r.month_pillar, r.day_pillar, r.hour_pillar,
            r.first_change, r.first_day_flag,
        )
        luck_batch.append((
            sid, r.code, res["direction"], res["status"], res["status_reason"],
            res["start_date"], res["start_age"], res["first_day_polarity"],
            res["rule_version"], res["boundary_flag"],
        ))
        for p in res["periods"]:
            period_batch.append((
                sid, p["cycle_index"], p["ganzhi"], p["stem"], p["branch"],
                p["start_date"], p["end_date"], p["start_year"], p["end_year"],
                p["start_age"], p["end_age"], p["rule_version"],
            ))
        if len(luck_batch) >= 1000:
            emit("stock_luck_cycle", luck_cols, luck_batch)
            emit("luck_cycle_period", period_cols, period_batch)
            luck_batch, period_batch = [], []
    emit("stock_luck_cycle", luck_cols, luck_batch)
    emit("luck_cycle_period", period_cols, period_batch)

    # 原局内部关系（按 2776 张唯一盘构建并映射）
    STRUCTURAL_TYPES = frozenset({
        "天干五合", "天干相冲", "六合", "六冲", "三合", "半合", "三会",
        "相刑", "三刑", "自刑", "相害", "六破", "同支", "伏吟", "反吟", "天合地合", "天克地冲"
    })
    unique_charts = {}
    for r in rows:
        ld_str = r.listing_date.isoformat()
        if ld_str not in unique_charts:
            unique_charts[ld_str] = (r.year_pillar, r.month_pillar, r.day_pillar)

    natal_cols = [
        "chart_key", "listing_date", "relation_type", "category", "positions",
        "source_pillar", "target_pillar", "source_ganzhi", "target_ganzhi",
        "element", "notes", "rule_version", "status"
    ]
    natal_batch = []
    for ld_str, (y, m, d) in unique_charts.items():
        chart_key = f"{y}_{m}_{d}"
        events = rc.compute_natal_internal_relations(y, m, d)
        for ev in events:
            if ev.relation_type in STRUCTURAL_TYPES:
                pos = f"{ev.source_pillar},{ev.target_pillar}" if ev.source_pillar != ev.target_pillar else ev.source_pillar
                natal_batch.append((
                    chart_key, ld_str, ev.relation_type, ev.category, pos,
                    ev.source_pillar, ev.target_pillar, ev.source_ganzhi, ev.target_ganzhi,
                    ev.element if ev.element else None,
                    ev.notes, ev.rule_version, ev.status
                ))
    emit("natal_relation", natal_cols, natal_batch)

    # 喜用候选与格局解释（按 2776 张唯一盘构建并映射，Phase 3 P3-B）
    yongshen_cols = [
        "chart_key", "day_stem", "month_branch", "strength_score", "strength_level",
        "status", "yong_shen", "xi_shen", "ji_shen", "chou_shen", "xian_shen",
        "candidate_elements", "tiaohou_note", "rationale", "rule_version"
    ]
    yongshen_batch = []
    for ld_str, (y, m, d) in unique_charts.items():
        chart_key = f"{y}_{m}_{d}"
        res = yc.compute_yongshen_candidate(y, m, d)
        yongshen_batch.append((
            chart_key,
            res["day_stem"],
            res["month_branch"],
            res["strength_score"],
            res["strength_level"],
            res["status"],
            ",".join(res["yong_shen"]),
            ",".join(res["xi_shen"]),
            ",".join(res["ji_shen"]),
            ",".join(res["chou_shen"]),
            ",".join(res["xian_shen"]),
            ",".join(res["candidate_elements"]),
            res["tiaohou_note"],
            res["rationale"],
            res["rule_version"],
        ))
    emit("stock_yongshen", yongshen_cols, yongshen_batch)

    # 日历
    gz_cols = ["date", "year_ganzhi", "month_ganzhi", "day_ganzhi", "year_stem", "year_branch",
               "month_stem", "month_branch", "day_stem", "day_branch", "month_branch_label", "solar_term"]
    gz_batch = []
    d = start
    while d <= end:
        gz_batch.append(ganzhi_row(d))
        if len(gz_batch) >= 2000:
            emit("ganzhi_calendar", gz_cols, gz_batch)
            gz_batch = []
        d += dt.timedelta(days=1)
    emit("ganzhi_calendar", gz_cols, gz_batch)

    observed = frozenset(r.listing_date.isoformat() for r in rows)
    trade_rows, corrected = tc.build_trade_calendar(start, end, observed)
    emit("trade_calendar", ["date", "is_trade_day", "weekday", "closed_reason", "confidence"], trade_rows)

    n_stock = cur.execute("SELECT COUNT(*) FROM stock").fetchone()[0]
    n_gz = cur.execute("SELECT COUNT(*) FROM ganzhi_calendar").fetchone()[0]
    n_td = cur.execute("SELECT COUNT(*) FROM trade_calendar WHERE is_trade_day=1").fetchone()[0]
    n_luck = cur.execute("SELECT COUNT(*) FROM stock_luck_cycle").fetchone()[0]
    n_period = cur.execute("SELECT COUNT(*) FROM luck_cycle_period").fetchone()[0]
    n_natal = cur.execute("SELECT COUNT(*) FROM natal_relation").fetchone()[0]
    n_yongshen = cur.execute("SELECT COUNT(*) FROM stock_yongshen").fetchone()[0]
    meta_rows = [
        ("schema_version", str(SCHEMA_VERSION)),
        ("data_version", meta["snapshot_max_listing_date"]),
        ("calendar_version", CALENDAR_VERSION),
        ("calendar_start", start.isoformat()),
        ("calendar_end", end.isoformat()),
        ("stock_count", str(n_stock)),
        ("trade_day_count", str(n_td)),
        ("luck_cycle_count", str(n_luck)),
        ("luck_period_count", str(n_period)),
        ("natal_relation_count", str(n_natal)),
        ("yongshen_count", str(n_yongshen)),
        ("source_sha256", meta["sha256"]),
        ("rule_version", "bazi-rule-v1.2"),
        ("dayun_rule_version", "stock-luck-cycle-v1.3"),
        ("relation_rule_version", "natal-relation-v1.3"),
        ("yongshen_rule_version", "yongshen-candidate-v1.3"),
        ("generated_at", dt.datetime.now().isoformat(timespec="seconds")),
    ]
    emit("app_meta", ["key", "value"], meta_rows)
    con.commit()
    cur.execute("VACUUM")
    con.close()

    sql_lines.append(f"PRAGMA user_version = {SCHEMA_VERSION};")
    SQL_OUT.parent.mkdir(parents=True, exist_ok=True)
    SQL_OUT.write_text("\n".join(sql_lines) + "\n", encoding="utf-8")

    report = {
        "source": meta,
        "counts": {
            "stock": n_stock,
            "ganzhi_calendar": n_gz,
            "trade_calendar": len(trade_rows),
            "trade_days": n_td,
        },
        "calendar": {"start": start.isoformat(), "end": end.isoformat(), "version": CALENDAR_VERSION,
                     "observed_corrections": len(corrected),
                     "correction_samples": [f"{d}:{r}" for d, r in corrected[:20]]},
    }
    con2 = sqlite3.connect(db_path)
    for t in ("stock_bazi", "stock_hidden_ten_god", "stock_luck_cycle", "luck_cycle_period", "natal_relation", "stock_yongshen", "app_meta"):
        report["counts"][t] = con2.execute(f"SELECT COUNT(*) FROM {t}").fetchone()[0]
    report["db_bytes"] = db_path.stat().st_size
    report["sql_lines"] = len(sql_lines)
    con2.close()
    REPORT_OUT.write_text(json.dumps(report, ensure_ascii=False, indent=2), encoding="utf-8")
    return report


if __name__ == "__main__":
    ap = argparse.ArgumentParser()
    ap.add_argument("--start", default="1990-12-01")
    ap.add_argument("--end", default="2035-12-31")
    ap.add_argument("--xlsx", default=str(DEFAULT_XLSX))
    a = ap.parse_args()
    rep = build(dt.date.fromisoformat(a.start), dt.date.fromisoformat(a.end), Path(a.xlsx))
    print(json.dumps(rep, ensure_ascii=False, indent=2)[:1200])
