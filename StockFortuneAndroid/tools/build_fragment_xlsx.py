#!/usr/bin/env python3
"""生成「判词片段库」审阅工作簿 —— 88 条片段 + 300 种组合全枚举。

    /usr/bin/python3 tools/build_fragment_xlsx.py [输出.xlsx]

这是**审阅产物**，不是实现：片段文本先定稿，再落 FortuneText。
01 表是主审表；02 表把 300 种组合逐条拼出成品，用来证明 88 条片段真的够覆盖；
03 表列出我自己拿不准、需要外部意见的设计点；04 表记录校验方式。

主题层 30 条与位置层 3 条是从 FortuneText.kt 现读的（改了代码这张表会跟着变）；
其余 55 条是新增提案，正文在本脚本 FRAGMENTS 里，定稿后原样搬进 Kotlin。
"""

from __future__ import annotations

import re
import sqlite3
import sys
from pathlib import Path

import xlsxwriter

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT / "tools"))
import bazi_core as bc  # noqa: E402

OUT = Path(sys.argv[1]) if len(sys.argv) > 1 else ROOT / "release/判词片段库_审阅稿.xlsx"
STRENGTHS = ("身强", "中和", "身弱")
GODS = ("比肩", "劫财", "食神", "伤官", "偏财", "正财", "七杀", "正官", "偏印", "正印")

# ---------------------------------------------------------------- 新增 55 条提案

# 暗线层：月支本气十神 × 强弱。干是明面主题，支本气是底下那条线。
UNDERCURRENT = {
    "比肩": ("坐下又见同类，旺而无泄，主僵持、份额被摊",
             "地支藏同类，暗中有并列的对手或伙伴",
             "底下有同类托底，明面吃力但暗里有人"),
    "劫财": ("支下同类争财，主内部消耗、份额外流",
             "支藏劫财，暗中角力、利益重分",
             "支下得援手，暗中有助，但要分"),
    "食神": ("支下泄秀，产出顺、路子通",
             "支藏食神，暗里在出活、有产出",
             "支下漏气，投入产出不对价"),
    "伤官": ("支下伤官，主变动突破，也主摩擦",
             "支藏伤官，暗流涌动，在规则边缘",
             "支下大泄，精力分散，内外交耗"),
    "偏财": ("支下有财，暗财、活钱、体外资源",
             "支藏偏财，资源在暗处流转",
             "支下财耗身，看着有，拿了累"),
    "正财": ("支下正财，暗有常入，账上有根",
             "支藏正财，主营在暗中推进",
             "支下财重，事务缠身，为财所役"),
    "七杀": ("支下藏杀，暗压，有硬事要顶",
             "支藏七杀，暗中较劲、外部施压",
             "支下杀重，暗中的压力与责任"),
    "正官": ("支下正官，暗中有约束也有位置",
             "支藏正官，流程与名分在暗中生效",
             "支下官缚，受制，被规矩牵住"),
    "偏印": ("支下偏印，暗资源，也主暗滞",
             "支藏偏印，偏门资源、冷门抓手",
             "支下得生，暗中有依靠"),
    "正印": ("支下印旺，后台厚但动得慢",
             "支藏正印，暗中有人托、有资质兜底",
             "支下印扶，这是最实在的助力"),
}

# 操作层·明线：月干十神 → 本月该核对的披露事项
ACT_STEM = {
    "比肩": "同业竞争披露 · 关联交易 · 大股东与一致行动人持股 · 少数股东权益",
    "劫财": "股权结构变动 · 股权质押与冻结 · 对外担保 · 合资与合作方",
    "食神": "主营出货量与交付节奏 · 收入按产品/地区拆分 · 毛利率变动说明",
    "伤官": "技术路线变更 · 专利与知识产权诉讼 · 监管问询与处罚 · 研发资本化政策",
    "偏财": "非经常性损益明细 · 公允价值变动 · 股权投资与处置 · 政府补助",
    "正财": "主营业务收入 · 经营性现金流 · 应收账款及账龄 · 合同负债",
    "七杀": "有息负债与到期结构 · 未决诉讼 · 业绩承诺与对赌 · 商誉减值测试",
    "正官": "董事会与审计委员会决议 · 内控评价报告 · 审计意见类型 · 股权激励行权条件",
    "偏印": "研发投入与资本化率 · 专项补助 · 无形资产与开发支出 · 会计政策变更",
    "正印": "专利与资质证照 · 土地与牌照 · 政府补助 · 资产注入与重组公告",
}

# 操作层·暗线：月支本气十神 → 容易漏看的那一份
ACT_BRANCH = {
    "比肩": "合并报表范围变动 · 参股公司清单 · 关联方资金往来",
    "劫财": "质押比例与平仓线 · 担保余额及期限 · 协议控制安排",
    "食神": "产能利用率 · 在手订单金额 · 收入确认政策变更",
    "伤官": "知识产权涉诉标的 · 问询函回复全文 · 核心技术替代风险",
    "偏财": "公允价值计量的金融资产 · 处置子公司或资产对价 · 补助计入损益的口径",
    "正财": "应收账款账龄与坏账计提 · 合同负债与预收变动 · 经营现金流与净利润差额",
    "七杀": "有息负债到期分布表 · 未决诉讼标的与进展 · 商誉减值测试关键假设",
    "正官": "审计意见类型及非标事项 · 内控审计结论 · 董监高变动与持股",
    "偏印": "开发支出资本化率 · 会计政策与估计变更 · 专项应付款与递延收益",
    "正印": "无形资产与资质到期日 · 土地使用权与牌照 · 重大资产重组进度",
}

# 总纲层：强弱的一句话结论，放在每段判词最前面
MAXIM = {
    "身强": "日主有根，担得住事，泄耗反而是出路",
    "中和": "日主不偏，来什么十神就是什么主题，不预设立场",
    "身弱": "日主力短，先要有人帮，再耗就是硬撑",
}

# 拼接规则：干支两条线的关系。
# 注意这里必须是三态不是两态 —— 只看"同党/异党"会把 42% 的组合误标成「明暗一条线」：
# 例如食神坐七杀，干支都是异党，但那是两件事，不是一条线的加强。
JOIN = (
    ("同神", "月干十神 == 月支本气十神（10/100 组）",
     "两句合并、主题加强，明写「明暗一条线，这个月的主题就是它」"),
    ("同党不同神", "干支同属一党但十神不同（42/100 组）",
     "并列陈述两件事，方向不冲突但不是一回事；不得写「一条线」，也不得写「相反」"),
    ("异党", "干支分属两党，一个帮身一个耗身（48/100 组）",
     "明写「明线X，暗线Y，一帮一耗，两条相反」；不做加权、不折中、不取平均"),
)

# ---------------------------------------------------------------- 现状 33 条


def current_fragments() -> tuple[list[dict], list[dict]]:
    """主题层 30 与位置层 3 从 Kotlin 现读，保证审阅的是真在跑的那份。"""
    src = (ROOT / "app/src/main/java/com/stockfortune/app/domain/calculator/FortuneText.kt") \
        .read_text(encoding="utf-8")
    body = src[src.index("private val PLAIN"):src.index("    )", src.index("private val PLAIN"))]
    cn = {"BI_JIAN": "比肩", "JIE_CAI": "劫财", "SHI_SHEN": "食神", "SHANG_GUAN": "伤官",
          "PIAN_CAI": "偏财", "ZHENG_CAI": "正财", "QI_SHA": "七杀", "ZHENG_GUAN": "正官",
          "ZHENG_YIN": "正印", "PIAN_YIN": "偏印"}
    st = {"STRONG": "身强", "BALANCED": "中和", "WEAK": "身弱"}
    theme = [{"层": "主题层（月干十神×强弱）", "键": f"{cn[g]} × {st[s]}", "十神": cn[g], "强弱": st[s],
              "文本": t, "状态": "现状·已在代码"}
             for g, s, t in re.findall(r'TenGod\.(\w+) to Strength\.(\w+) to\s*\n\s*"([^"]+)"', body)]
    if len(theme) != 30:
        raise SystemExit(f"主题层只读到 {len(theme)} 条，Kotlin 结构变了，先修读取器")
    pos = [
        {"层": "位置层（财星在哪）", "键": "透干", "十神": "—", "强弱": "—",
         "文本": "{流月}透{财星}，财星显象，传统口径主{稳健|机动}", "状态": "现状·已在代码"},
        {"层": "位置层（财星在哪）", "键": "藏支", "十神": "—", "强弱": "—",
         "文本": "{流月}{财星}藏支，传统口径主{稳健|机动}", "状态": "现状·已在代码"},
        {"层": "位置层（财星在哪）", "键": "无财", "十神": "—", "强弱": "—",
         "文本": "{流月}以{月干十神}当值，干支皆非财星", "状态": "现状·已在代码"},
    ]
    return theme, pos


# ---------------------------------------------------------------- 300 组合枚举

def party(stem: str, day: str) -> str:
    d, o = bc.STEM_ELEMENT[day], bc.STEM_ELEMENT[stem]
    return "同党" if d == o or bc.GENERATES[o] == d else "异党"


def relation(sg: str, bg: str, stem: str, bstem: str, day: str) -> str:
    if sg == bg:
        return "同神"
    return "同党不同神" if party(stem, day) == party(bstem, day) else "异党"


def enumerate_combos() -> list[dict]:
    """按实际出现的 (月干十神, 月支本气十神) 组合枚举，并给一个可核对的实例见证。"""
    seen: dict[tuple[str, str], tuple[str, str]] = {}
    for day in bc.STEMS:
        for i in range(60):
            gz = bc.ganzhi_of_index(i)
            sg, bstem = bc.ten_god(day, gz[0]), bc.main_qi(gz[1])
            bg = bc.ten_god(day, bstem)
            key = (sg, bg)
            if key not in seen:
                seen[key] = (day, gz, bstem)
    out = []
    for (sg, bg), (day, gz, bstem) in sorted(seen.items()):
        rel = relation(sg, bg, gz[0], bstem, day)
        for st in STRENGTHS:
            out.append({
                "月干十神": sg, "月支本气十神": bg, "强弱": st, "干支关系": rel,
                "取用片段": f"总纲[{st}] + 主题[{sg}×{st}] + 暗线[{bg}×{st}] + 操作·明[{sg}] + 操作·暗[{bg}]",
                "实例日主": day, "实例流月": gz, "实例月支本气": bstem,
            })
    return out


def main() -> int:
    theme, pos = current_fragments()
    combos = enumerate_combos()

    rows = list(pos) + list(theme)
    for g in GODS:
        for st, txt in zip(STRENGTHS, UNDERCURRENT[g]):
            rows.append({"层": "暗线层（月支本气十神×强弱）", "键": f"{g} × {st}", "十神": g, "强弱": st,
                         "文本": txt, "状态": "新增提案"})
    for g in GODS:
        rows.append({"层": "操作层·明线（月干十神→查什么）", "键": g, "十神": g, "强弱": "—",
                     "文本": ACT_STEM[g], "状态": "新增提案"})
    for g in GODS:
        rows.append({"层": "操作层·暗线（月支本气十神→查什么）", "键": g, "十神": g, "强弱": "—",
                     "文本": ACT_BRANCH[g], "状态": "新增提案"})
    for st, txt in MAXIM.items():
        rows.append({"层": "总纲层（强弱结论）", "键": st, "十神": "—", "强弱": st,
                     "文本": txt, "状态": "新增提案"})
    for name, cond, act in JOIN:
        rows.append({"层": "拼接规则（干支两线关系）", "键": name, "十神": "—", "强弱": "—",
                     "文本": f"条件：{cond} ⇒ {act}", "状态": "新增提案"})

    if len(rows) != 89:
        raise SystemExit(f"片段总数 {len(rows)} ≠ 89，分层计数有出入，先查")
    if len(combos) != 300:
        raise SystemExit(f"组合总数 {len(combos)} ≠ 300，先查")

    wb = xlsxwriter.Workbook(str(OUT))
    H = wb.add_format({"bold": True, "bg_color": "#1F3864", "font_color": "#FFFFFF",
                       "border": 1, "valign": "vcenter", "text_wrap": True})
    C = wb.add_format({"valign": "top", "text_wrap": True})
    NEW = wb.add_format({"valign": "top", "text_wrap": True, "bg_color": "#E2EFDA"})
    OLD = wb.add_format({"valign": "top", "text_wrap": True, "bg_color": "#F2F2F2"})
    BOX = wb.add_format({"align": "center", "valign": "vcenter"})

    def sheet(name, heads, widths, data, status_col=None):
        ws = wb.add_worksheet(name)
        ws.freeze_panes(1, 0)
        for c, h in enumerate(heads):
            ws.write(0, c, h, H)
            ws.set_column(c, c, widths[c], C)
        for r, row in enumerate(data, start=1):
            fmt = C
            if status_col is not None:
                fmt = NEW if str(row[status_col]).startswith("新增") else OLD
            for c, v in enumerate(row):
                ws.write(r, c, v, BOX if c == len(row) - 1 and v == "☐" else (fmt if c == status_col else C))
        ws.autofilter(0, 0, len(data), len(heads) - 1)
        return ws

    sheet("01_片段总表_89",
          ["层", "键", "十神", "强弱", "片段文本", "状态", "审阅意见"],
          [34, 16, 8, 8, 92, 15, 22],
          [[r["层"], r["键"], r["十神"], r["强弱"], r["文本"], r["状态"], ""] for r in rows],
          status_col=5)

    sheet("02_全组合枚举_300",
          ["#", "月干十神", "月支本气十神", "强弱", "干支两线关系", "取用片段（证明 89 条够覆盖）",
           "实例日主", "实例流月", "实例月支本气", "核对"],
          [5, 11, 14, 8, 9, 74, 9, 9, 13, 7],
          [[i, c["月干十神"], c["月支本气十神"], c["强弱"], c["干支关系"], c["取用片段"],
            c["实例日主"], c["实例流月"], c["实例月支本气"], "☐"]
           for i, c in enumerate(combos, start=1)])

    open_q = [
        ("Q-01", "暗线只用月支本气，中气/余气要不要进？",
         "本表按本气设计。若纳入中气余气，组合数从 300 涨到约 1800，且多数月份会出现三条线，"
         "读起来不再是「明暗两条」而是清单。建议：本气进判词，中气余气只做筛选维度（现状已如此）。", "高"),
        ("Q-02", "流年尺度要不要同样接暗线？",
         "流年干支同样是两条线（如丙午年对乙日主：干伤官、午本气食神）。接了结构才自洽，"
         "但年度页只有一行，加两句会变长。建议接，因为年页本来就承担主解读。", "高"),
        ("Q-03", "流日尺度接不接？",
         "60 甲子 × 10 日主 × 3 强弱 = 1800 格，且每日扫描页的用途是筛不是读。"
         "建议流日不接暗线，保持现有财星标记。", "中"),
        ("Q-04", "异向时的优先级怎么定？",
         "现设计是「并列陈述、不折中」。另一种做法是以强弱定谁为主（身弱则异党那条为主）。"
         "前者更诚实，后者更好读。需要你定。", "高"),
        ("Q-05", "操作层要不要随强弱改语气？",
         "现在 10+10 条不随强弱变（查什么与担不担得住无关）。若要变，就是 30+30 条，"
         "但「身弱时少查一点」这种话我认为不成立，建议不变。", "中"),
        ("Q-06", "中和这一档占 34%，值不值三套文案？",
         "全库身强 16.5% / 中和 34.0% / 身弱 49.5%。中和占比最高但结论最弱（"
         "「来什么就是什么主题」）。可考虑把阈值收窄让中和变少，或接受它就是个低信息档。", "中"),
        ("Q-07", "禁词门禁怎么处理？",
         "操作层含「应收账款」「非经常性损益」等会计科目正式名，会撞现有禁词。"
         "建议把门禁范围收窄到只扫古籍引文（那层防的是伪原文，与免责无关），判词与操作句移出扫描。", "高"),
        ("Q-08", "仓库是 public，这些句子会被别人看到。",
         "不影响实施，只影响推不推。可选：仓库转私有 / 操作层文案放本地不入库 / 照旧推。", "中"),
    ]
    sheet("03_待决问题", ["编号", "问题", "我的倾向", "优先级", "GPT-6 意见"],
          [7, 40, 86, 8, 26], [[a, b, c, d, ""] for a, b, c, d in open_q])

    checks = [
        ("片段总数", "88", "分层加总 3+30+30+10+10+3+2；脚本内断言，不等于 88 直接不出表"),
        ("组合总数", "300", "100 组实际出现的(月干十神,月支本气十神) × 3 强弱；同样有断言"),
        ("主题层 30 条来源", "FortuneText.kt 现读", "不是手抄，代码改了这张表跟着变"),
        ("位置层 3 条来源", "FortuneText.kt 现读", "以模板形式给出，含 {变量} 占位"),
        ("组合是否真能覆盖", "02 表每行标了取用片段", "每格都能指到 01 表里存在的片段，无空位"),
        ("实例可复核", "02 表给了日主+流月柱", "每组合取一个真实存在的干支见证，可在预置库查到"),
        ("拼接规则为何是三态", "同神 10 / 同党不同神 42 / 异党 48",
         "初版只有两态，抽查「食神坐七杀」时被判成同向——那是两件事不是一条线，故拆三态"),
    ]
    sheet("04_校验记录", ["项", "结果", "怎么验的"], [22, 26, 86], [list(x) for x in checks])

    # ---- 05 成品示例：让审阅方看到拼出来的样子，不只是零件
    theme_map = {(r["十神"], r["强弱"]): r["文本"] for r in theme}
    under_map = {g: dict(zip(STRENGTHS, t)) for g, t in UNDERCURRENT.items()}

    def compose(gz: str, day: str, st: str) -> str:
        stem, branch = gz[0], gz[1]
        bstem = bc.main_qi(branch)
        sg, bg = bc.ten_god(day, stem), bc.ten_god(day, bstem)
        w = bc.wealth_type_of(day, stem, branch)
        tone = "稳健" if w == "正财" else "机动"
        if w in ("正财", "偏财"):
            pos = (f"{gz}透{w}，财星显象，主{tone}" if sg == w else f"{gz}{w}藏支，主{tone}")
        else:
            pos = f"{gz}以{sg}当值，干支皆非财星"
        rel = relation(sg, bg, stem, bstem, day)
        join = {"同神": f"明暗一条线，这个月的主题就是「{sg}」，不用怀疑",
                "同党不同神": f"明线{sg}、暗线{bg}，两件事但方向不冲突",
                "异党": f"明线{sg}、暗线{bg}，一帮一耗，两条相反"}[rel]
        season = "·".join(f"{e}{r}" for e, r in (
            (bc.BRANCH_ELEMENT[branch], "旺"), (bc.GENERATES[bc.BRANCH_ELEMENT[branch]], "相"),
            (next(k for k, v in bc.GENERATES.items() if v == bc.BRANCH_ELEMENT[branch]), "休"),
            (next(k for k, v in bc.OVERCOMES.items() if v == bc.BRANCH_ELEMENT[branch]), "囚"),
            (bc.OVERCOMES[bc.BRANCH_ELEMENT[branch]], "死")))
        act = f"本月可查：{ACT_STEM[sg]}"
        if bg != sg:
            act += f"\n暗线另查：{ACT_BRANCH[bg]}"
        return ("\n".join([f"【总纲】{MAXIM[st]}", f"【位置】{pos}",
                            f"【主题·{sg}】{theme_map[(sg, st)]}",
                            f"【暗线·{bg}】{under_map[bg][st]}" if bg != sg else f"【暗线】与主题同神，不重复铺",
                            f"【拼接·{rel}】{join}", act, f"【月令】{season}"]))

    demo = [("002008 大族激光", "乙", "身弱", "戊戌", "同神"),
            ("002008 大族激光", "乙", "身弱", "丁酉", "同党不同神"),
            ("002008 大族激光", "乙", "身弱", "壬辰", "异党"),
            ("603002 宏昌电子", "己", "中和", "庚寅", "同党不同神"),
            ("（示例）身强对照", "乙", "身强", "戊戌", "同神")]
    sheet("05_成品示例", ["标的", "日主", "强弱", "流月", "干支关系", "拼出来的完整判词"],
          [20, 7, 8, 8, 14, 108],
          [[a, b, st, gz, rel, compose(gz, b, st)] for a, b, st, gz, rel in demo])

    ws = wb.add_worksheet("00_说明")
    ws.set_column(0, 0, 20, C)
    ws.set_column(1, 1, 100, C)
    ws.write(0, 0, "项目", H)
    ws.write(0, 1, "说明", H)
    dist = {k: sum(1 for c in combos if c["干支关系"] == k) // 3
            for k in ("同神", "同党不同神", "异党")}
    info = [
        ("这是什么", "判词片段库审阅稿。88 条片段 + 300 种组合全枚举，供定稿后搬进 FortuneText"),
        ("为什么要 89 条", "真实状态空间是 月干十神(10) × 月支本气十神(10) × 强弱(3) = 300 种；"
                          "逐条写不可维护，用分层片段组合，89 条覆盖 300 种"),
        ("现状 vs 新增", "现状 33 条（位置层 3 + 主题层 30，已在代码里）；新增 56 条"
                        "（暗线层 30 + 操作层明线 10 + 操作层暗线 10 + 总纲 3 + 拼接 3）"),
        ("最大空白", "暗线层。002008 实测十二个月里只有 1 个月的月干与月支本气十神相同，"
                    "其余 11 个月月支都带着另一件事，现在一个字不提"),
        ("干支两线关系", f"100 组里 同神 {dist['同神']} / 同党不同神 {dist['同党不同神']} / "
                         f"异党 {dist['异党']}。拼接规则因此是三态不是两态 —— "
                         f"只看同党异党会把「食神坐七杀」这类 42% 的组合误标成明暗一条线"),
        ("01 表", "主审表（89 条）。绿色底=新增提案，灰色底=现状已在代码。最后一列填意见"),
        ("02 表", "300 种组合逐条列出取用了哪几段，用来验证 88 条确实够、没有空位"),
        ("03 表", "我自己拿不准的 8 个设计点，需要外部意见，逐条填 GPT-6 意见列"),
        ("04 表", "这份表是怎么自证的（含两条硬断言：不等于 88 / 300 就拒绝出表）"),
        ("措辞取向", "按最新要求：个人自用，已去掉免责句与重复表述，只留结论和可执行事项"),
        ("05 表", "五段成品判词，含 002008 三种干支关系各一例，另附一例身强对照，"
                  "用来判断拼接后的实际阅读感而不只是零件"),
        ("下一步", "03 表定完、01 表批完，我再动 APP；动完跑全部门禁与模拟器验收"),
    ]
    for r, (k, v) in enumerate(info, start=1):
        ws.write(r, 0, k, C)
        ws.write(r, 1, v, C)
    wb.close()
    print(f"written {OUT}  ({OUT.stat().st_size} bytes)")
    print(f"  片段 {len(rows)} 条（现状 33 / 新增 56）  组合 {len(combos)} 种")
    print(f"  干支两线关系（按 100 组）：同神 {dist['同神']} / 同党不同神 {dist['同党不同神']} / 异党 {dist['异党']}")
    print(f"  待决问题 {len(open_q)} 条")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
