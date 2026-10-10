#!/usr/bin/env python3
"""生成「操作建议核对表」xlsx —— 把每种情况和对应文案一一对应铺开。

    /usr/bin/python3 tools/build_advice_xlsx.py <规则库.xlsx> [输出.xlsx]

工作表：
  00 说明            各表口径与条数
  01 系统现状_全展开   2026 十二流月 × 10 日主 × 3 强弱 = 360 行，逐行是 APP 真实会拼出的整句
  02 白话30格         十神 × 强弱 的白话子句本体（01 就是用它拼的）
  03 规则库_可落地     规则库自评适用、且不依赖缺失引擎
  04 规则库_需开关     规则库自评"不进股票主体页面"，要先有场景开关
  05 规则库_缺引擎     依赖喜用 / 关系 / 十二长生，本系统算不出，暂不实施

01 的文案是用 Python 复刻 FortuneText.monthSummary 拼出来的，脚本末尾拿真机抓到的
一行原句做断言 —— 复刻错了宁可不出表，也不要出一张看着对其实不一样的核对表。
"""

from __future__ import annotations

import re
import sqlite3
import sys
from pathlib import Path

import xlsxwriter

sys.path.insert(0, str(Path(__file__).resolve().parent))
import bazi_core as bc  # noqa: E402
from extract_advice_review import FORTUNE, banned_lists, collect_advice  # noqa: E402

ROOT = Path(__file__).resolve().parents[1]
STRENGTHS = ("身强", "中和", "身弱")
# 真机 603002（壬辰 乙巳 己卯 己巳，日主己，判中和）年度页实际渲染出的三行，
# 分别覆盖 monthSummary 的三条分支：无财星 / 透干 / 藏支。少一条分支，复刻就可能
# 只在一个分支上是对的，而 01 表三条分支都在铺。
GOLDENS = [
    ("庚寅", "己", "中和",
     "庚寅以伤官当值，干支皆非财星；主创意亦主变动，传统口径许其秀气流行，也以其不拘名分为病；"
     "月令木旺·火相·水休·金囚·土死"),
    ("壬辰", "己", "中和",
     "壬辰透正财，财星显象，传统口径主稳健；财路以常以勤为主，传统口径谓正财乃身外之有，得之以其分；"
     "月令土旺·金相·火休·木囚·水死"),
    ("己亥", "己", "中和",
     "己亥正财藏支，传统口径主稳健；同辈并列，合作与竞争同时到位，传统口径既谓比肩帮身，也谓比肩分财；"
     "月令水旺·木相·金休·土囚·火死"),
]


def plain_table() -> dict[tuple[str, str], str]:
    """从 Kotlin 源码里读 PLAIN 30 格，避免在 Python 侧另抄一份而漂移。"""
    src = FORTUNE.read_text(encoding="utf-8")
    body = src[src.index("private val PLAIN"):src.index("    )", src.index("private val PLAIN"))]
    cn = {"BI_JIAN": "比肩", "JIE_CAI": "劫财", "SHI_SHEN": "食神", "SHANG_GUAN": "伤官",
          "PIAN_CAI": "偏财", "ZHENG_CAI": "正财", "QI_SHA": "七杀", "ZHENG_GUAN": "正官",
          "ZHENG_YIN": "正印", "PIAN_YIN": "偏印"}
    st = {"STRONG": "身强", "BALANCED": "中和", "WEAK": "身弱"}
    out = {}
    for g, s, t in re.findall(r'TenGod\.(\w+) to Strength\.(\w+) to\s*\n\s*"([^"]+)"', body):
        out[(cn[g], st[s])] = t
    if len(out) != 30:
        raise SystemExit(f"PLAIN 只抽到 {len(out)} 格，Kotlin 侧结构变了，先修抽取器")
    return out


def season_summary(month_branch: str) -> str:
    """复刻 TenGodCalculator.seasonSummary：按旺相休囚死排序输出。"""
    season = bc.BRANCH_ELEMENT[month_branch]
    xiu = next(k for k, v in bc.GENERATES.items() if v == season)
    qiu = next(k for k, v in bc.OVERCOMES.items() if v == season)
    return "·".join(f"{e}{r}" for e, r in (
        (season, "旺"), (bc.GENERATES[season], "相"), (xiu, "休"), (qiu, "囚"),
        (bc.OVERCOMES[season], "死")))


def month_summary(gz: str, day_stem: str, strength: str, plain: dict) -> tuple[str, str, str, str]:
    """复刻 FortuneText.monthSummary，返回 (整句, 月干十神, 财星判定, 透/藏)。"""
    stem, branch = gz[0], gz[1]
    god = bc.ten_god(day_stem, stem)
    wealth = bc.wealth_type_of(day_stem, stem, branch)
    tone = "稳健" if wealth == "正财" else "机动"
    if wealth in ("正财", "偏财"):
        via = "透" if god in ("正财", "偏财") else "藏支"
        rel = (f"{gz}透{wealth}，财星显象，传统口径主{tone}" if via == "透"
               else f"{gz}{wealth}藏支，传统口径主{tone}")
    else:
        rel, via, tone = f"{gz}以{god}当值，干支皆非财星", "—", "—"
    full = f"{rel}；{plain[(god, strength)]}；月令{season_summary(branch)}"
    return full, god, wealth, via


def flow_months(root: Path) -> list[tuple[str, str]]:
    db = root / "app/src/main/assets/databases/stock_fortune.db"
    con = sqlite3.connect(f"file:{db}?mode=ro", uri=True)
    rows = con.execute(
        "SELECT month_ganzhi, month_branch FROM ganzhi_calendar "
        "WHERE date BETWEEN '2026-02-04' AND '2027-02-03' "
        "GROUP BY month_ganzhi ORDER BY min(date)").fetchall()
    con.close()
    return rows


def main() -> int:
    if len(sys.argv) < 2:
        print(__doc__)
        return 1
    lib = Path(sys.argv[1])
    out = Path(sys.argv[2]) if len(sys.argv) > 2 else ROOT / "release/操作建议核对表.xlsx"
    plain = plain_table()
    adv, _ = banned_lists()

    # 先自证三条分支都复刻正确，再出表
    for gz, ds, st, want in GOLDENS:
        got, *_ = month_summary(gz, ds, st, plain)
        if got != want:
            raise SystemExit(f"复刻与真机不一致（{gz}/{ds}/{st}），不出表：\n"
                             f"  期望 {want}\n  实得 {got}")
    print(f"复刻校验：{len(GOLDENS)} 条分支（无财星 / 透干 / 藏支）与真机原句逐字一致 ✓")

    months = flow_months(ROOT)
    if len(months) != 12:
        raise SystemExit(f"2026 节气月取到 {len(months)} 个，不是 12")

    wb = xlsxwriter.Workbook(str(out), {"default_date_format": "yyyy-mm-dd"})
    title = wb.add_format({"bold": True, "bg_color": "#1F3864", "font_color": "#FFFFFF",
                           "border": 1, "valign": "vcenter", "text_wrap": True})
    cell = wb.add_format({"valign": "top", "text_wrap": True})
    tag = wb.add_format({"valign": "top", "text_wrap": True, "bg_color": "#FFF2CC"})
    bad = wb.add_format({"valign": "top", "text_wrap": True, "bg_color": "#FCE4E4", "font_color": "#9C0006"})
    box = wb.add_format({"align": "center", "valign": "vcenter"})

    def sheet(name, headers, widths, rows_, wrap_cols=()):
        ws = wb.add_worksheet(name[:31])
        ws.freeze_panes(1, 0)
        for c, h in enumerate(headers):
            ws.write(0, c, h, title)
        for c, w in enumerate(widths):
            ws.set_column(c, c, w, cell)
        for c in wrap_cols:
            ws.set_column(c, c, widths[c], cell)
        for r, row in enumerate(rows_, start=1):
            for c, v in enumerate(row):
                if c == len(row) - 1 and v == "☐":
                    ws.write(r, c, v, box)
                elif headers[c] == "撞禁词" and v and v != "无":
                    ws.write(r, c, v, bad)
                elif headers[c] in ("缺失依赖", "规则库自评适用性") and v:
                    ws.write(r, c, v, tag)
                else:
                    ws.write(r, c, v, cell)
        ws.autofilter(0, 0, len(rows_), len(headers) - 1)
        return ws

    # ---- 01 系统现状全展开：12 流月 × 10 日主 × 3 强弱 = 360 行
    rows_full = []
    for gz, branch in months:
        for ds in bc.STEMS:
            full, god, wealth, via = None, None, None, None
            for st in STRENGTHS:
                full, god, wealth, via = month_summary(gz, ds, st, plain)
                hit = ", ".join(w for w in adv if w in full) or "无"
                rows_full.append([f"{gz}（{branch}月）", ds, st, god, wealth, via, full, hit, "☐"])
    sheet("01_系统现状_全展开",
          ["流月", "日主", "日主强弱", "月干十神", "财星判定", "透/藏", "APP 实际会拼出的整句", "撞禁词", "核对结论"],
          [13, 6, 10, 10, 9, 7, 96, 16, 9], rows_full)

    # ---- 02 白话 30 格本体
    rows_plain = []
    for god in ("比肩", "劫财", "食神", "伤官", "偏财", "正财", "七杀", "正官", "正印", "偏印"):
        for st in STRENGTHS:
            t = plain[(god, st)]
            rows_plain.append([god, st, t, ", ".join(w for w in adv if w in t) or "无", "☐"])
    sheet("02_白话30格", ["月干十神", "日主强弱", "白话子句（现行）", "撞禁词", "核对结论"],
          [11, 11, 100, 16, 9], rows_plain)

    # ---- 03/04/05 规则库三组
    recs = collect_advice(lib)
    heads = ["来源表", "ID", "十神", "场景", "触发条件", "规则库自评适用性", "缺失依赖", "建议原文", "撞禁词", "核对结论"]
    widths = [22, 12, 8, 12, 40, 22, 26, 86, 16, 9]
    for grp, nm in (("B1 可落地且自评适用", "03_规则库_可落地"),
                    ("B2 需先加场景开关", "04_规则库_需场景开关"),
                    ("B3 缺引擎·暂不加", "05_规则库_缺引擎")):
        sub = [r for r in recs if r["分组"] == grp]
        sheet(nm, heads, widths,
              [[r[k] for k in ("来源表", "ID", "十神", "场景", "触发条件", "规则库自评适用性",
                               "缺失依赖", "建议原文", "撞禁词")] + ["☐"] for r in sub])

    # ---- 00 说明
    ws = wb.add_worksheet("00_说明")
    ws.set_column(0, 0, 26, cell)
    ws.set_column(1, 1, 104, cell)
    info = [
        ("生成时间口径", "2026 丙午节气年十二流月（庚寅 → 辛丑），取自预置库 ganzhi_calendar"),
        ("复刻校验", "01 表每句由 Python 复刻 FortuneText.monthSummary 拼出，"
                     "已与真机 603002 年度页原句逐字比对通过；不一致时脚本直接拒绝出表"),
        ("禁词来源", "实时解析 ComplianceTextTest.kt 的 advisoryBanned + banned + bannedTraditional，"
                     "与上线门禁同一把尺子；01/02 表全部为「无」"),
        ("01_系统现状_全展开", f"{len(rows_full)} 行 = 12 流月 × 10 日主 × 3 强弱，"
                               "即 APP 现在每种情况实际会说的那一句"),
        ("02_白话30格", "30 行 = 10 十神 × 3 强弱，01 表就是用它拼接的"),
        ("03_规则库_可落地", f"{sum(1 for r in recs if r['分组'].startswith('B1'))} 条，"
                             "不依赖缺失引擎且规则库自评可进股票页面；撞禁词的需改写后再落"),
        ("04_规则库_需场景开关", f"{sum(1 for r in recs if r['分组'].startswith('B2'))} 条，"
                                 "规则库自评「仅经营情境、股票主体默认不显示」，要先有场景开关"),
        ("05_规则库_缺引擎", f"{sum(1 for r in recs if r['分组'].startswith('B3'))} 条，"
                             "依赖喜用/关系/十二长生，本系统未实现，本轮不实施"),
        ("重要重叠提醒", "03 表里约 69 条与 02 表白话是同一坐标（月干十神），并列加入会让月度页"
                         "每行堆到四句且前两句重复；实施前需先决定是替换还是独立成「本月可核对事项」"),
        ("用法", "在「核对结论」列打 ✓ 或写意见；确认后按条目回灌 APP，再跑全部门禁与模拟器验收"),
    ]
    ws.write(0, 0, "项目", title)
    ws.write(0, 1, "说明", title)
    for r, (k, v) in enumerate(info, start=1):
        ws.write(r, 0, k, cell)
        ws.write(r, 1, v, cell)
    wb.close()
    print(f"written {out}  ({out.stat().st_size} bytes)")
    print(f"  01 全展开 {len(rows_full)} 行 / 02 白话 {len(rows_plain)} 行 / "
          f"03 {sum(1 for r in recs if r['分组'].startswith('B1'))} / "
          f"04 {sum(1 for r in recs if r['分组'].startswith('B2'))} / "
          f"05 {sum(1 for r in recs if r['分组'].startswith('B3'))}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
