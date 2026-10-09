#!/usr/bin/env python3
"""抽取「操作建议」核对清单 —— 只读分析工具，不参与打包、不被 APP 调用。

把两处的建议类文案并成一份可逐条勾选的核对表：

  A. 本系统现在给用户的每一句话（FortuneText 静态表 + 年度页实际渲染样例）
  B. 外部规则库 xlsx 里所有带建议性质的列（按能否落地分组）

    /usr/bin/python3 tools/extract_advice_review.py <规则库.xlsx> [输出.md]

禁词表直接从 ComplianceTextTest.kt 解析，不另立一份 —— 否则核对用的尺子和
上线把关的尺子会各自漂移，那正是这份清单要避免的事。
"""

from __future__ import annotations

import json
import re
import sys
import xml.etree.ElementTree as ET
import zipfile
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
M = "http://schemas.openxmlformats.org/spreadsheetml/2006/main"
NS = {"m": M}
TEST_FILE = ROOT / "app/src/test/java/com/stockfortune/app/ComplianceTextTest.kt"
FORTUNE = ROOT / "app/src/main/java/com/stockfortune/app/domain/calculator/FortuneText.kt"

CN = "甲乙丙丁戊己庚辛壬癸"

# 缺哪张表 → 触发条件里出现这些词就判缺。顺序即优先级。
MISSING_ENGINES = [
    ("缺·喜用引擎（用神/忌神未实现）", ("用神", "喜神", "忌神", "仇神", "闲神", "喜忌角色", "喜用时", "不匹配时")),
    ("缺·关系引擎（刑冲合会未实现）", ("六冲", "三合", "三刑", "五合", "六害", "六合", "相冲", "关系引擎", "命中事件")),
    ("缺·十二长生表", ("长生", "沐浴", "冠带", "临官", "帝旺", "胎养", "墓绝", "十二长生")),
]

# 显式来源清单：(表名, 建议列, 整表默认引擎判定)。
# 不靠正则猜哪张表是"建议"—— 07_干支关系词典的「推荐解释」是关系定义、08 的「跨年提示」是历法元数据，
# 都不是给人看的操作建议；靠列名匹配会把它们误收进来。
SOURCES = [
    ("22_十神场景行动", ("具体操作提示（短句）", "中性结构句", "补充解释（长句）"), None),
    ("23_十神喜忌组合", ("候选管理动作句", "喜忌修饰句"), "缺·喜用引擎（用神/忌神未实现）"),
    ("24_干支关系动作", ("经营情境操作提示", "证券研究提示", "中性解释句"), "缺·关系引擎（刑冲合会未实现）"),
    ("25_藏干长生与层级", ("可选情境操作提示", "允许的结构说法"), None),
    ("26_复杂叠加行动", ("经营操作提示（审核稿）", "组合解释句"), "缺·关系引擎（刑冲合会未实现）"),
    ("28_截图话术逐句核对", ("推荐可审语句（保留可执行含义）",), None),
    ("29_庚日主12月示例", ("经营情境操作提示示例", "示范结构句"), None),
    ("11_月度文案规则", ("建议可用句子（含变量）",), None),
    ("12_组合冲突规则", ("推荐表述",), "缺·关系引擎（刑冲合会未实现）"),
    ("13_2026十二个月样稿", ("示范性月度语句",), None),
    ("02_十神解释", ("建议月度文案", "中性专业说明"), None),
    ("09_喜用角色说明", ("标准文案",), "缺·喜用引擎（用神/忌神未实现）"),
]


def banned_lists() -> tuple[list[str], list[str]]:
    """从合规门禁源码里读禁词，保证核对用的尺子和线上一致。"""
    src = TEST_FILE.read_text(encoding="utf-8")
    def grab(name: str) -> list[str]:
        m = re.search(rf"{name} = listOf\((.*?)\n    \)", src, re.S)
        if not m:
            raise SystemExit(f" ComplianceTextTest 里找不到 {name}，清单不可信，先修脚本")
        return re.findall(r'"([^"]+)"', m.group(1))
    return grab("advisoryBanned") + grab("banned") + grab("bannedTraditional"), grab("advisoryBanned")


def sheet_map(path: Path) -> dict[str, str]:
    z = zipfile.ZipFile(path)
    rel = "http://schemas.openxmlformats.org/officeDocument/2006/relationships"
    rid = {e.get("Id"): e.get("Target") for e in ET.fromstring(z.read("xl/_rels/workbook.xml.rels"))}
    wb = ET.fromstring(z.read("xl/workbook.xml"))
    out = {}
    for s in wb.findall(".//m:sheet", NS):
        t = rid[s.get(f"{{{rel}}}id")].lstrip("/")
        out[s.get("name")] = t if t.startswith("xl/") else "xl/" + t
    return out


def rows(z: zipfile.ZipFile, part: str, hdr: int = 4) -> list[dict[str, str]]:
    table = []
    for row in ET.fromstring(z.read(part)).findall(".//m:row", NS):
        vs = []
        for c in row.findall("m:c", NS):
            seg = [t.text or "" for t in c.iter(f"{{{M}}}t")]
            if not seg:
                v = c.find("m:v", NS)
                seg = [v.text] if v is not None and v.text else [""]
            vs.append("".join(seg))
        table.append(vs)
    if len(table) <= hdr:
        return []
    head = table[hdr - 1]
    return [dict(zip(head, r + [""] * (len(head) - len(r)))) for r in table[hdr:] if any(x.strip() for x in r)]


def classify(cond: str) -> str:
    for label, keys in MISSING_ENGINES:
        if any(k in cond for k in keys):
            return label
    return "可落地·不依赖缺失引擎"


def esc(s: str) -> str:
    return " ".join((s or "").split()).replace("|", "／")


def system_section() -> list[str]:
    src = FORTUNE.read_text(encoding="utf-8")
    out = ["## A · 本系统现在给用户说的每一句话", ""]
    out += [
        "**先说清一件事：系统当前没有任何操作建议。** 下面是全部对外文案，逐条列出来是为了和 B 部分对齐口径 —— "
        "如果要引入建议句，得先决定它替换谁、还是并列新增。",
        "",
        "### A1 · 白话层 30 格（流月/流年天干十神 × 日主强弱）",
        "",
        "| # | 十神 | 强弱 | 现行文案 | 撞禁词 | 核对结论 |",
        "|---|---|---|---|---|---|",
    ]
    adv, _ = banned_lists()
    body = src[src.index("private val PLAIN"):src.index("    )", src.index("private val PLAIN"))]
    cn = {"BI_JIAN": "比肩", "JIE_CAI": "劫财", "SHI_SHEN": "食神", "SHANG_GUAN": "伤官",
          "PIAN_CAI": "偏财", "ZHENG_CAI": "正财", "QI_SHA": "七杀", "ZHENG_GUAN": "正官",
          "ZHENG_YIN": "正印", "PIAN_YIN": "偏印"}
    st = {"STRONG": "身强", "BALANCED": "中和", "WEAK": "身弱"}
    n = 0
    for g, s, t in re.findall(r'TenGod\.(\w+) to Strength\.(\w+) to\s*\n\s*"([^"]+)"', body):
        n += 1
        hit = [w for w in adv if w in t]
        out.append(f"| {n} | {cn[g]} | {st[s]} | {esc(t)} | {', '.join(hit) or '无'} | ☐ |")
    if n != 30:
        out.append(f"| — | **格子数 {n} ≠ 30，抽取或表结构已变，先查这里** | | | | |")

    out += ["", "### A2 · 其余静态文案（术语层 / 建议位 / 提示位）", "",
            "| 出处 | 现行文案 | 撞禁词 | 核对结论 |", "|---|---|---|---|"]
    for label, pat in [
        ("yearAdvice 正财", r'WealthType\.ZHENG_CAI -> "(正财当值[^"]+)"'),
        ("yearAdvice 偏财", r'WealthType\.PIAN_CAI -> "(偏财当值[^"]+)"'),
        ("yearAdvice 其他", r'else -> "(无明显财星之年[^"]+)"'),
        ("monthTip 无财日", r'"(本月无明显财日[^"]+)"'),
        ("monthTip 有财日", r'"(本月正财日 [^"]+)"'),
        ("扫描卡注 正财", r'SCAN_ZHENG_NOTE = "([^"]+)"'),
        ("扫描卡注 偏财", r'SCAN_PIAN_NOTE = "([^"]+)"'),
    ]:
        m = re.search(pat, src)
        if not m:
            out.append(f"| {label} | **没抽到，模式已失效** | ? | ☐ |")
            continue
        t = m.group(1)
        hit = [w for w in adv if w in t]
        out.append(f"| {label} | {esc(t)} | {', '.join(hit) or '无'} | ☐ |")
    return out


POLICY_COLS = ("适合股票APP吗", "股票APP政策", "股票输出策略", "股票模式输出策略")


def policy_of(d: dict[str, str]) -> str:
    """规则库自己的适用性判定 —— 它比"缺不缺引擎"更能决定要不要做。"""
    for c in POLICY_COLS:
        v = (d.get(c) or "").strip()
        if v:
            return v
    return ""


def collect_advice(path: Path) -> list[dict[str, str]]:
    """把规则库里所有建议行抽成结构化记录，markdown 与 xlsx 两条输出共用。"""
    z = zipfile.ZipFile(path)
    sm = sheet_map(path)
    adv, _ = banned_lists()
    out = []
    for name, cols, default_engine in SOURCES:
        if name not in sm:
            continue
        for d in rows(z, sm[name]):
            text = " ／ ".join(esc(d.get(c, "")) for c in cols if d.get(c, "").strip())
            if not text:
                continue
            cond = " ".join(esc(d.get(k, "")) for k in d
                            if any(t in k for t in ("条件", "触发", "场景", "所属组", "类别")))
            policy = policy_of(d)
            engine = default_engine or classify(cond + " " + text)
            if not engine.startswith("可落地"):
                group = "B3 缺引擎·暂不加"
            elif policy and any(t in policy for t in ("不显示", "禁止", "隐藏", "拒绝")):
                group = "B2 需先加场景开关"
            else:
                group = "B1 可落地且自评适用"
            out.append({
                "来源表": name,
                "ID": next((d[k] for k in ("动作ID", "组合ID", "规则ID", "事件ID", "修饰ID",
                                          "样稿ID", "句子ID", "ID") if d.get(k)), ""),
                "十神": esc(d.get("月干十神", "") or d.get("十神", "") or d.get("类别", "")),
                "场景": esc(d.get("输出场景", "") or d.get("组合主题", "") or d.get("场景", "")),
                "触发条件": esc(cond),
                "规则库自评适用性": esc(policy),
                "缺失依赖": "" if engine.startswith("可落地") else engine.split("——")[-1].strip(),
                "建议原文": text,
                "撞禁词": ", ".join(w for w in adv if w in text) or "无",
                "分组": group,
            })
    return out


def xlsx_section(path: Path) -> list[str]:
    out = ["", "---", "", "## B · 规则库里的操作建议（按能否落地分组）", "",
           "> 「撞禁词」= 命中 `ComplianceTextTest` 现行禁词表，直接抄会让门禁变红。",
           "> 分组先看规则库自己的适用性判定，再看是否依赖本系统尚未实现的引擎。", ""]
    header = ("| 来源表｜ID | 十神/场景 | 触发条件（摘要） | 规则库自评适用性 | 建议原文 | 撞禁词 | 核对结论 |",
              "|---|---|---|---|---|---|---|")
    groups: dict[str, list[str]] = {}
    for r in collect_advice(path):
        ident = f"{r['来源表']}｜{r['ID']}" if r["ID"] else r["来源表"]
        scene = (r["十神"] + " " + r["场景"]).strip()[:12]
        groups.setdefault(f"{r['分组']} —— {r['缺失依赖']}".rstrip(" —"), []).append(
            f"| {ident} | {scene} | {r['触发条件'][:46]} | {r['规则库自评适用性'][:22]} "
            f"| {r['建议原文'][:180]} | {r['撞禁词']} | ☐ |")
    for key in sorted(groups, key=lambda k: (not k.startswith("B1"), k)):
        out += [f"### {key} —— {len(groups[key])} 条", "", *header, *groups[key], ""]
    return out


def main() -> int:
    if len(sys.argv) < 2:
        print(__doc__)
        return 1
    xlsx = Path(sys.argv[1])
    if not xlsx.exists():
        print(f"找不到规则库：{xlsx}", file=sys.stderr)
        return 1
    out = Path(sys.argv[2]) if len(sys.argv) > 2 else ROOT / "docs/ADVICE_REVIEW_checklist.md"
    lines = [
        "# 操作建议核对清单（外部规则库 × 本系统现状）",
        "",
        f"- 规则库：`{xlsx.name}`",
        f"- 系统现状取自：`FortuneText.kt`（禁词表实时解析自 `ComplianceTextTest.kt`，与上线门禁同一把尺子）",
        "- 用法：在「核对结论」列打 ☐→✅ 或写修改意见；确认无误后再动 APP。",
        "",
        *system_section(),
        *xlsx_section(xlsx),
        "",
        "## C · 汇总",
        "",
        "填完这行再决定实施范围：**本次要落进 APP 的条目编号：＿＿＿＿＿＿**",
        "",
    ]
    out.parent.mkdir(parents=True, exist_ok=True)
    out.write_text("\n".join(lines), encoding="utf-8")
    print(f"written {out}  ({out.stat().st_size} bytes)")
    counts, cur = {}, None
    for l in lines:
        if l.startswith("### "):
            cur = l[4:].split(" ——")[0]
            counts.setdefault(cur, 0)
        elif cur and l.startswith("| ") and l.rstrip().endswith("| ☐ |"):
            counts[cur] += 1
    for k, v in counts.items():
        print(f"  {k}: {v} 条")
    print(f"  合计待核对: {sum(counts.values())} 条")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
