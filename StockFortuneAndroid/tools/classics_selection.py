"""classics_selection.py —— 《滴天髓輯要》首批引文的固定选段清单。

本文件是**人工审定**的抽取契约，不是自动挑选的结果：

* 每条只用「篇名 + 天干小节 + 扫描页码」定位，`excerpt` 必须是底本中**逐字连续**的片段；
  定位失败、命中多处或字数不符都会直接报错，禁止用关键词在语料里搜「利好」句子。
* `SCAN_RESTORE` 登记「底本回改」：精校版文字与 1936 刊本扫描页冲突且原书未立校记时，
  按用户裁定的口径改回哪一边。回改必须逐条写明证据与决策来源，审计报告原样收录。
* 异体字（溼/濕、强/強、叠/疊、眞/真）一律沿用精校版字形，只在审计报告记录，不算回改。
"""

from __future__ import annotations

import os
from pathlib import Path

# 底本与交叉核对源（外部书籍目录，只在重新抽取时需要；日常构建读已提交的 JSON）。
# clone 后拿不到这个目录也没关系：verify_classics.py 不带 --against-source 即可全绿，
# 两个消费方都会先做 exists() 检查并明确报错。
# 这里**不写死任何人的本机路径**（仓库是公开的）；重新抽取时显式给出：
#   SF_CLASSICS_BOOK_DIR=/path/to/滴天髓辑要 python3 extract_classics.py
BOOK_DIR = Path(os.environ.get(
    "SF_CLASSICS_BOOK_DIR", str(Path.home() / ".stockfortune-classics" / "滴天髓辑要")
))
SOURCE_MD = BOOK_DIR / "滴天髓辑要_全文_V2_精校版.md"
SOURCE_JSONL = BOOK_DIR / "滴天髓辑要_全文_V2_精校版.chunk.jsonl"
SOURCE_PDF = BOOK_DIR.parent.parent / "ditiansui_jiyiao_1936_nlc.pdf"

# APP 侧产物
ASSET_JSON = Path(__file__).resolve().parent.parent / "app/src/main/assets/classics/ditiansui_jiyao.json"
AUDIT_MD = Path(__file__).resolve().parent.parent / "docs/CLASSICS_AUDIT_ditiansui_v1.md"

CORPUS_VERSION = "dtjy-v1"
SCHEMA_VERSION = 1
PAGE_BASIS = "scan"  # entries[].page 是底本扫描页码，不是古籍印刷页码

BOOK = {
    "book_id": "ditiansui_jiyao",
    "title": "滴天髓輯要",
    "edition": "1936 年刊本（國家圖書館藏掃描件，ditiansui_jiyiao_1936_nlc.pdf，全 64 頁）",
    "author_note": "題署劉伯溫著，清相國海昌陳之遴（素庵氏）輯；題署係刊本原貌，非考訂結論",
    "provenance": (
        "文字取自《滴天髓輯要》全文 V2 精校版（RapidOCR 170dpi 初校 + 300dpi 二次 OCR，"
        "對校本為維基文庫《滴天髓》輯要 42 篇全本，旁校中國哲學書電子化計劃《滴天髓闡微》）；"
        "入库前逐段对照底本扫描页 8—14。"
    ),
}

CHAPTER = "天干論"

# 十干 → 稳定 ID 片段（与 APP 侧 ClassicQuoteRepository 的 dayStem 映射一致）
STEM_SLUG = {
    "甲": "jia", "乙": "yi", "丙": "bing", "丁": "ding", "戊": "wu",
    "己": "ji", "庚": "geng", "辛": "xin", "壬": "ren", "癸": "gui",
}

# ---------------------------------------------------------------- 底本回改

SCAN_RESTORE = [
    {
        "section": "癸水",
        "kind": "verse",
        "from": "合戊見火",
        "to": "合戊化火",
        "evidence": (
            "扫描页 14 第 3 列（5x 放大）确认为「化」（亻+匕），非「見」；"
            "精校版此处从维基文库作「見」，且与其自身原注「惟合戊化火，必通火根」冲突，未立校记。"
        ),
        "decision": "用户 2026-09-30 裁定：底本优先，用刊本「合戊化火」。",
    },
]

# ---------------------------------------------------------------- 选段清单

_VERSE = "verse"
_NOTE = "annotation_excerpt"

# (天干小节, 定位页, JSONL chunk_id, 类型, 选段, 展示扫描页码)
#
# 两个页码不是一回事，必须分开：
#   定位页 = 精校 Markdown / JSONL 里该小节标题所在的扫描页，用来对账切片与回溯定位；
#   展示扫描页码 = 这段引文**首字实际落在哪一页扫描图上**，是给用户看的出处。
# 刊本一行一列，小节标题跟着歌诀走，但原注常被推到下一页页首，于是两者会错开。
# verse 的选段是该节完整歌诀段；annotation_excerpt 是原注中的连续片段，不补字不补标点。
SELECTION = [
    ("甲木", 8, "DT2-0008", _VERSE,
     "甲木參天，胞胎要火，春不容金，秋不容土，火熾乘龍，水蕩騎虎，地潤天和，植立千古。", 8),
    ("甲木", 8, "DT2-0008", _NOTE,
     "甲為根幹之木，純陽之本，參天雄壯", 8),
    ("乙木", 9, "DT2-0009", _VERSE,
     "乙木雖柔，刲羊解牛，懷丁抱丙，跨雞乘猴，虛濕之地，騎馬亦憂，藤蘿繫甲，可春可秋。", 9),
    ("乙木", 9, "DT2-0009", _NOTE,
     "乙為枝葉之木，柔如花卉", 9),
    ("丙火", 10, "DT2-0010", _VERSE,
     "丙火猛烈，欺霜侮雪，能煆庚金，逄辛反怯，土眾成慈，水猖顯節，虎馬犬鄉，甲來焚滅。", 10),
    ("丙火", 10, "DT2-0010", _NOTE,
     "丙為焚烈之火，純陽之性", 10),
    ("丁火", 10, "DT2-0011", _VERSE,
     "丁火柔中，內性昭融，抱乙而孝，合壬而忠，旺而不烈，衰而不窮，如有嫡母，可秋可冬。", 10),
    ("丁火", 10, "DT2-0011", _NOTE,
     "丁為溫煖之火，其性雖烈而屬陰，則柔而得其中矣，外柔順而內文明，豈不昭融乎", 10),
    ("戊土", 11, "DT2-0012", _VERSE,
     "戊土固重，既中且正，靜翕動闢，萬物司命，水旺物生，火燥喜潤，若在坤艮，怕沖宜靜。", 11),
    ("戊土", 11, "DT2-0012", _NOTE,
     "戊為山岡之土，非城牆之謂，較己土特高厚剛燥，乃己土之發源地也", 11),
    ("己土", 11, "DT2-0013", _VERSE,
     "己土卑濕，中正蓄藏，不愁木盛，不畏水旺，火少火晦，金多金明，若要物昌，宜助宜幫。", 11),
    ("己土", 11, "DT2-0013", _NOTE,
     "己為田園之土，其性卑濕，乃戊土枝葉之地，亦主中正，蓄藏萬物", 12),
    ("庚金", 12, "DT2-0014", _VERSE,
     "庚金帶煞，剛強為最，得水而清，得火而銳，土潤則生，土乾則脆，能勝甲兄，輸於乙妹。", 12),
    ("庚金", 12, "DT2-0014", _NOTE,
     "庚乃陽金，是太白之精，帶煞而剛健", 12),
    ("辛金", 12, "DT2-0015", _VERSE,
     "辛金軟弱，溫潤而清，畏土之疊，樂水之盈，能扶社稷，能救生靈，熱則喜母，寒則喜丁。", 12),
    ("辛金", 12, "DT2-0015", _NOTE,
     "辛乃陰金，非珠玉之謂，特溫柔清潤耳", 13),
    ("壬水", 13, "DT2-0016", _VERSE,
     "壬水汪洋，能洩金氣，剛中之德，周流不滯，通根透癸，沖天奔地，化則有情，從則相濟。", 13),
    ("壬水", 13, "DT2-0016", _NOTE,
     "壬乃癸水之源，有分有合，運行不息，為百川，亦為雨露，不可岐而二之", 13),
    ("癸水", 14, "DT2-0017", _VERSE,
     "癸水至弱，達於天津，龍德而運，功化斯神，不畏火土，不論庚辛，合戊見火，火根乃真。", 14),
    ("癸水", 14, "DT2-0017", _NOTE,
     "癸乃純陰而至弱，然上達天津", 14),
]

# 展示页码与定位页错开时，必须逐条写明核页依据；没有依据就不允许错开。
SCAN_PAGE_EVIDENCE = {
    ("己土", _NOTE): (
        "扫描页 11 末列是己土歌訣「巳土卑溼…宜幫。」，原注另起于扫描页 12 最右列页首"
        "「已爲田園之土。其性卑溼。乃戊土枝葉之地。亦主中正。蓄藏萬物。」"
        "（刊本叶码七），故节选首字实际在第 12 页。"
    ),
    ("辛金", _NOTE): (
        "扫描页 12 末列是辛金歌訣「辛金軟弱…寒則喜丁。」，原注另起于扫描页 13 最右列页首"
        "「辛乃陰金。非珠玉之謂。特溫柔清潤耳。戊土多則埋故畏之。」（刊本叶码八），"
        "故节选首字实际在第 13 页。"
    ),
}


def restore_for(section: str, kind: str):
    return next((r for r in SCAN_RESTORE if r["section"] == section and r["kind"] == kind), None)


def expected_text(section: str, kind: str, excerpt: str) -> str:
    """清单选段 + 已登记的底本回改 = 该条**唯一**的预期入库文本。

    抽取器与门禁都走这个函数，避免两边各算一套。回改只能执行登记的那一次替换，
    绝不放宽成「这条允许不一样」——否则整段被改写也照样过门禁。
    """
    r = restore_for(section, kind)
    if r is None:
        return excerpt
    hits = excerpt.count(r["from"])
    if hits != 1:
        raise ValueError(
            f"{section}/{kind} 的回改锚点「{r['from']}」在清单选段中出现 {hits} 次（应为 1 次）"
        )
    return excerpt.replace(r["from"], r["to"])


# 逐段对照扫描页的结论（审计报告收录；status=confirmed 表示已在放大图上逐字核过）
SCAN_VERIFICATION = [
    {"section": "甲木", "page": 8, "printed_leaf": "三", "status": "confirmed",
     "note": "歌訣與原注首句逐字吻合；「胞胎要火」從刊本（通行諸本作「脫胎」，精校版已立校記）。"},
    {"section": "乙木", "page": 9, "printed_leaf": "四", "status": "confirmed",
     "note": "歌訣與原注首句逐字吻合；原注夾注（丑未陰土，故乙能制。）刊本同在。"},
    {"section": "丙火", "page": 10, "printed_leaf": "五", "status": "confirmed",
     "note": "「能煆庚金，逄辛反怯」之「逄」經 14x 放大确认非「逢」，精校版與維基文庫一致，照錄。"},
    {"section": "丁火", "page": 10, "printed_leaf": "五", "status": "confirmed",
     "note": "「抱乙而孝」刊本作「孝」，佐證精校版對維基文庫「考」之校改；「溫煖」之「煖」刊本同。"},
    {"section": "戊土", "page": 11, "printed_leaf": "六", "status": "confirmed",
     "note": "「萬物司命」「火燥喜潤」刊本同，佐證精校版兩條校記；原注首句逐字吻合。"},
    {"section": "己土", "page": 11, "printed_leaf": "六—七", "status": "confirmed",
     "note": "歌訣在掃描頁 11 末列、原注起於掃描頁 12 首列，兩頁皆已核。刊本歌訣首字作「巳」、"
           "原注首字作「已」，係刊本己/已/巳形近不別，精校版作「己」與十干體例相符，照錄不改。"},
    {"section": "庚金", "page": 12, "printed_leaf": "七", "status": "confirmed",
     "note": "「能勝甲兄」刊本作「甲」，可證維基文庫作「申」為訛字。"},
    {"section": "辛金", "page": 12, "printed_leaf": "七—八", "status": "confirmed",
     "note": "原注首句吻合。"},
    {"section": "壬水", "page": 13, "printed_leaf": "八", "status": "confirmed",
     "note": "原注首句吻合，「不可岐而二之」之「岐」刊本同（非「歧」）。"},
    {"section": "癸水", "page": 14, "printed_leaf": "九", "status": "restored",
     "note": "歌訣第七句刊本作「合戊化火」，精校版作「合戊見火」，已按 SCAN_RESTORE 回改。"},
]

# 精校版與刊本之間的異體字差異：沿用精校版字形，僅記錄（用戶 2026-09-30 裁定）
VARIANT_GLYPH_NOTES = [
    {"section": "己土", "kind": _NOTE, "scan": "卑溼", "shipped": "卑濕"},
    {"section": "庚金", "kind": _VERSE, "scan": "剛强", "shipped": "剛強"},
    {"section": "辛金", "kind": _VERSE, "scan": "之叠", "shipped": "之疊"},
    {"section": "癸水", "kind": _VERSE, "scan": "火根乃眞", "shipped": "火根乃真"},
]
