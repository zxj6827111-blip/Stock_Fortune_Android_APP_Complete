"""stock_xlsx.py —— 用标准库直解 xlsx（本机无 openpyxl，且要求导入链可复现）。

只读 `总表`（sheet1）：`股票代码 股票名称 上市日期 上市首日开盘价 上市首日收盘价
首日涨跌幅 首日涨跌标识 完整八字 年柱 月柱 日柱 时柱`。
`上市日期` 为 Excel 1900 日期系统序列号，需换算。
"""

from __future__ import annotations

import datetime
import re
import xml.etree.ElementTree as ET
import zipfile
from dataclasses import dataclass
from pathlib import Path

NS = "{http://schemas.openxmlformats.org/spreadsheetml/2006/main}"
EXCEL_EPOCH = datetime.date(1899, 12, 30)
MAIN_SHEET = "xl/worksheets/sheet1.xml"
COLUMNS = (
    "code", "name", "serial", "first_open", "first_close",
    "first_change", "first_day_flag", "full_bazi", "year_pillar",
    "month_pillar", "day_pillar", "hour_pillar",
)


@dataclass(frozen=True)
class StockRow:
    code: str
    name: str
    listing_date: datetime.date
    first_open: float | None
    first_close: float | None
    first_change: float | None
    first_day_flag: str
    full_bazi: str
    year_pillar: str
    month_pillar: str
    day_pillar: str
    hour_pillar: str

    @property
    def symbol(self) -> str:
        return self.code.split(".")[0]

    @property
    def exchange(self) -> str:
        return self.code.split(".")[-1] if "." in self.code else ""


def _col_index(ref: str) -> int:
    n = 0
    for ch in re.match(r"([A-Z]+)", ref).group(1):
        n = n * 26 + (ord(ch) - 64)
    return n - 1


def _iter_sheet(zf: zipfile.ZipFile, member: str, shared: list[str]):
    with zf.open(member) as fh:
        for _, el in ET.iterparse(fh, ["end"]):
            if el.tag != NS + "row":
                continue
            cells: dict[int, str] = {}
            for c in el.findall(NS + "c"):
                ref = c.get("r")
                if not ref:
                    continue
                t = c.get("t")
                if t == "inlineStr":
                    is_el = c.find(NS + "is")
                    val = "".join(x.text or "" for x in is_el.iter(NS + "t")) if is_el is not None else ""
                else:
                    v = c.find(NS + "v")
                    if v is None or v.text is None:
                        val = ""
                    elif t == "s":
                        val = shared[int(v.text)]
                    else:
                        val = v.text
                cells[_col_index(ref)] = val
            yield el.get("r"), cells
            el.clear()


def _num(val: str) -> float | None:
    try:
        return float(val)
    except (TypeError, ValueError):
        return None


def read_industry(path: Path) -> dict[str, tuple[str, str]]:
    """读行业表（股票代码 | 股票名称 | 所属行业，三级用"-"分隔）。返回 code -> (名称, 完整行业)。"""
    zf = zipfile.ZipFile(path)
    shared = [
        "".join(t.text or "" for t in si.iter(NS + "t"))
        for si in ET.fromstring(zf.read("xl/sharedStrings.xml"))
    ]
    out: dict[str, tuple[str, str]] = {}
    for ref, cells in _iter_sheet(zf, MAIN_SHEET, shared):
        if ref == "1":
            continue
        code = cells.get(0, "").strip()
        name = cells.get(1, "").strip()
        industry = cells.get(2, "").strip()
        if not code or not industry:
            continue
        out[code] = (name, industry)
    return out


def read_workbook(path: Path) -> tuple[list[StockRow], dict[str, object]]:
    """返回 (股票行, 元信息)。元信息含 xlsx 摘要、sheet 列表、被跳过的行。"""
    data = path.read_bytes()
    import hashlib

    zf = zipfile.ZipFile(__import__("io").BytesIO(data))
    shared = [
        "".join(t.text or "" for t in si.iter(NS + "t"))
        for si in ET.fromstring(zf.read("xl/sharedStrings.xml"))
    ]
    workbook = ET.fromstring(zf.read("xl/workbook.xml"))
    sheets = [s.get("name") for s in workbook.iter(NS + "sheet")]

    rows: list[StockRow] = []
    skipped: list[tuple[str, str]] = []
    for ref, cells in _iter_sheet(zf, MAIN_SHEET, shared):
        if ref == "1":
            header = [cells.get(i, "") for i in range(12)]
            if header[0] != "股票代码":
                raise AssertionError(f"总表表头异常: {header}")
            continue
        vals = [cells.get(i, "").strip() for i in range(12)]
        if not any(vals):
            continue
        code, name, serial = vals[0], vals[1], vals[2]
        if not code or "." not in code:
            skipped.append((code or "(空)", "代码格式异常"))
            continue
        s = _num(serial)
        if s is None or not (20000 < s < 60000):
            skipped.append((code, f"上市日期无法解析: {serial!r}"))
            continue
        full, yp, mp, dp, hp = vals[7], vals[8], vals[9], vals[10], vals[11]
        if not (len(yp) == len(mp) == len(dp) == len(hp) == 2):
            skipped.append((code, "四柱字段长度异常"))
            continue
        rows.append(
            StockRow(
                code=code,
                name=name,
                listing_date=EXCEL_EPOCH + datetime.timedelta(days=int(s)),
                first_open=_num(vals[3]),
                first_close=_num(vals[4]),
                first_change=_num(vals[5]),
                first_day_flag=vals[6],
                full_bazi=full,
                year_pillar=yp,
                month_pillar=mp,
                day_pillar=dp,
                hour_pillar=hp,
            )
        )
    meta = {
        "source_file": path.name,
        "sha256": hashlib.sha256(data).hexdigest(),
        "sheets": sheets,
        "row_count": len(rows),
        "skipped": skipped,
        "snapshot_max_listing_date": max(r.listing_date for r in rows).isoformat(),
    }
    return rows, meta
