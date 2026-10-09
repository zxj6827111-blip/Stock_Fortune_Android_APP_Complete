"""bazi_core.py —— 干支 / 藏干 / 十神 / 纳音 的确定性规则内核。

与 Android 侧 `domain/calculator` 保持同一套常量，两侧必须同步修改；
`verify_database.py` 会用本模块复算 Excel 四柱并交叉校验。
"""

from __future__ import annotations

STEMS = ("甲", "乙", "丙", "丁", "戊", "己", "庚", "辛", "壬", "癸")
BRANCHES = ("子", "丑", "寅", "卯", "辰", "巳", "午", "未", "申", "酉", "戌", "亥")

STEM_ELEMENT = {
    "甲": "木", "乙": "木", "丙": "火", "丁": "火", "戊": "土",
    "己": "土", "庚": "金", "辛": "金", "壬": "水", "癸": "水",
}
# 甲丙戊庚壬为阳，乙丁己辛癸为阴
STEM_YANG = {s: (i % 2 == 0) for i, s in enumerate(STEMS)}
BRANCH_ELEMENT = {
    "子": "水", "丑": "土", "寅": "木", "卯": "木", "辰": "土", "巳": "火",
    "午": "火", "未": "土", "申": "金", "酉": "金", "戌": "土", "亥": "水",
}
BRANCH_YANG = {b: (i % 2 == 0) for i, b in enumerate(BRANCHES)}

GENERATES = {"木": "火", "火": "土", "土": "金", "金": "水", "水": "木"}
OVERCOMES = {"木": "土", "土": "水", "水": "火", "火": "金", "金": "木"}
GENERATED_BY = {v: k for k, v in GENERATES.items()}
OVERCOME_BY = {v: k for k, v in OVERCOMES.items()}

# 地支藏干（本气 / 中气 / 余气）
HIDDEN_STEMS = {
    "子": ("癸",),
    "丑": ("己", "癸", "辛"),
    "寅": ("甲", "丙", "戊"),
    "卯": ("乙",),
    "辰": ("戊", "乙", "癸"),
    "巳": ("丙", "庚", "戊"),
    "午": ("丁", "己"),
    "未": ("己", "丁", "乙"),
    "申": ("庚", "壬", "戊"),
    "酉": ("辛",),
    "戌": ("戊", "辛", "丁"),
    "亥": ("壬", "甲"),
}
HIDDEN_RANKS = ("本气", "中气", "余气")

TEN_GODS = ("比肩", "劫财", "食神", "伤官", "偏财", "正财", "七杀", "正官", "偏印", "正印")

# 60 甲子纳音（每两柱共享一纳音）
_NA_YIN_PAIRS = (
    "海中金", "炉中火", "大林木", "路旁土", "剑锋金", "山头火",
    "涧下水", "城头土", "白蜡金", "杨柳木", "泉中水", "屋上土",
    "霹雳火", "松柏木", "长流水", "沙中金", "山下火", "平地木",
    "壁上土", "金箔金", "覆灯火", "天河水", "大驿土", "钗钏金",
    "桑柘木", "大溪水", "沙中土", "天上火", "石榴木", "大海水",
)


def sexagenary_index(ganzhi: str) -> int:
    """'甲子'→0 … '癸亥'→59；非法返回 -1。"""
    if len(ganzhi) != 2 or ganzhi[0] not in STEMS or ganzhi[1] not in BRANCHES:
        return -1
    s, b = STEMS.index(ganzhi[0]), BRANCHES.index(ganzhi[1])
    for i in range(60):
        if i % 10 == s and i % 12 == b:
            return i
    return -1


def ganzhi_of_index(i: int) -> str:
    i %= 60
    return STEMS[i % 10] + BRANCHES[i % 12]


def na_yin(ganzhi: str) -> str:
    idx = sexagenary_index(ganzhi)
    if idx < 0:
        raise ValueError(f"非法干支: {ganzhi}")
    return _NA_YIN_PAIRS[idx // 2]


def ten_god(day_stem: str, other_stem: str) -> str:
    """other_stem 相对日主 day_stem 的十神（子平通行规则）。"""
    if day_stem not in STEM_ELEMENT or other_stem not in STEM_ELEMENT:
        raise ValueError(f"非法天干: {day_stem} / {other_stem}")
    dw, ow = STEM_ELEMENT[day_stem], STEM_ELEMENT[other_stem]
    same = STEM_YANG[day_stem] == STEM_YANG[other_stem]
    if dw == ow:
        return "比肩" if same else "劫财"
    if GENERATES[dw] == ow:
        return "食神" if same else "伤官"
    if OVERCOMES[dw] == ow:
        return "偏财" if same else "正财"
    if OVERCOME_BY[dw] == ow:
        return "七杀" if same else "正官"
    if GENERATED_BY[dw] == ow:
        return "偏印" if same else "正印"
    raise AssertionError("unreachable")


def hidden_stems(branch: str) -> tuple[str, ...]:
    return HIDDEN_STEMS[branch]


def pillar_stem(pillar: str) -> str:
    return pillar[0]


def pillar_branch(pillar: str) -> str:
    return pillar[1]


def hour_stem_of(day_stem: str, branch_index: int) -> str:
    """五鼠遁：甲己起甲子时、乙庚丙作初、丙辛从戊起、丁壬庚子居、戊癸壬子头。"""
    start = {"甲": 0, "己": 0, "乙": 2, "庚": 2, "丙": 4, "辛": 4, "丁": 6, "壬": 6, "戊": 8, "癸": 8}
    return STEMS[(start[day_stem] + branch_index) % 10]


def month_stem_of(year_stem: str, branch_index: int) -> str:
    """五虎遁：甲己之年丙作首（寅月丙寅）…；branch_index 以 子=0 计。"""
    start = {"甲": 2, "己": 2, "乙": 4, "庚": 4, "丙": 6, "辛": 6, "丁": 8, "壬": 8, "戊": 0, "癸": 0}
    # 寅=2 对应起始天干索引
    return STEMS[(start[year_stem] + (branch_index - 2) % 12) % 10]


def element_strength(day_element: str, season_element: str) -> str:
    """四时五行令：当令旺、令生者相、生令者休、克令者囚、令克者死。"""
    if day_element == season_element:
        return "旺"
    if GENERATES[season_element] == day_element:
        return "相"
    if GENERATED_BY[season_element] == day_element:
        return "休"
    if OVERCOMES[day_element] == season_element:
        return "囚"
    return "死"


def main_qi(branch: str) -> str:
    """地支本气。"""
    return HIDDEN_STEMS[branch][0]


# ---------------------------------------------------------------- 日主强弱（Rule v1.2）
#
# 得令 / 得地 / 得势的三分结构是子平通说；下面的具体权重与阈值是工程取值，
# 没有任何古籍依据，由它产出的界面文案一律标「本项目概述」，不得挂书名。
#
# 刻意只用年、月、日六字，剔除时柱：本项目的时柱是「上市日 9:30 → 巳时」的历法约定，
# 5395 只股票时支恒为巳，巳藏丙/庚/戊给每个盘的贡献是同一个常数（木 −1.75 到 土 +0.75）。
# 实测含时柱时身强占比在日主间极差 14 倍（甲 2.8% ↔ 戊 40.9%），剔除后降到 2.3 倍，
# 而总体三态分布几乎不变（16.4/31.7/52.0 → 16.5/34.0/49.5）。
# 复算脚本见 tools/strength_distribution.py。
STRENGTH_BRANCH_WEIGHTS = {"month": (3.0, 1.5, 0.75), "other": (1.0, 0.5, 0.25)}
STRENGTH_STEM_WEIGHT = 0.7
STRENGTH_THRESHOLD = 2.0
STRENGTHS = ("身强", "中和", "身弱")


def is_same_party(day_stem: str, other_stem: str) -> bool:
    """同党 = 同我（比劫）或生我（印）；其余（食伤 / 财 / 官杀）为异党。"""
    d, o = STEM_ELEMENT[day_stem], STEM_ELEMENT[other_stem]
    return d == o or GENERATES[o] == d


def strength_score(year_pillar: str, month_pillar: str, day_pillar: str) -> float:
    """三柱加权求和，同党取正、异党取负。日主即日干，不参与自身计分，时柱不计。"""
    day_stem = day_pillar[0]
    total = 0.0
    for i, pillar in enumerate((year_pillar, month_pillar, day_pillar)):
        stem, branch = pillar[0], pillar[1]
        if i < 2:  # 年干、月干透干帮身或耗身；日干不重复计入
            total += STRENGTH_STEM_WEIGHT * (1 if is_same_party(day_stem, stem) else -1)
        w_main, w_mid, w_rest = STRENGTH_BRANCH_WEIGHTS["month" if i == 1 else "other"]
        for j, hidden in enumerate(HIDDEN_STEMS[branch]):
            w = (w_main, w_mid, w_rest)[min(j, 2)]
            total += w * (1 if is_same_party(day_stem, hidden) else -1)
    return round(total, 2)


def day_master_strength(year_pillar: str, month_pillar: str, day_pillar: str) -> str:
    """三态而非两态：边界盘硬判强弱会给假精确，「中和」承接本项目的口径声明风格。"""
    s = strength_score(year_pillar, month_pillar, day_pillar)
    if s >= STRENGTH_THRESHOLD:
        return "身强"
    if s <= -STRENGTH_THRESHOLD:
        return "身弱"
    return "中和"


def wealth_type_of(day_stem: str, stem: str, branch: str) -> str:
    """单柱财星判定（Rule v1.1，透干优先）：

    1. 该柱天干十神为财 → 即为该柱财星（透干，显性）；
    2. 否则看地支本气十神是否为财（藏支，隐性）；
    3. 都不是 → 其他。

    之所以不合并多柱、也不纳入全部藏干：
      * 实测效果图 04 的密度（2026-09 约 8/22 交易日为财日）与"流日单柱 + 天干/本气"口径吻合；
      * 若把正财优先级凌驾于透干之上，会出现"丙午日全市场偏财为 0"的退化结果
        （壬日主透丙为偏财、午藏丁为正财），故改为透干优先。
    藏干十神仍作为独立筛选维度（见 stock_hidden_ten_god 表）。
    """
    if stem:
        tg = ten_god(day_stem, stem)
        if tg in ("正财", "偏财"):
            return tg
    if branch:
        tg = ten_god(day_stem, main_qi(branch))
        if tg in ("正财", "偏财"):
            return tg
    return "其他"
