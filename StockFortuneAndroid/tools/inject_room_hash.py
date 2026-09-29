"""inject_room_hash.py —— 把 Room 的 identity_hash 写入预置库。

Room 打开数据库时会校验 `room_master_table.identity_hash`；预置库缺少该行会直接抛
"Room cannot verify the data integrity"。因此构建流程为：
  build_database.py → gradle assembleDebug（生成 app/schemas/**/1.json）→ 本脚本 → 再次打包。
"""

from __future__ import annotations

import glob
import json
import pathlib
import sqlite3
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
DB = ROOT / "app" / "src" / "main" / "assets" / "databases" / "stock_fortune.db"
SCHEMA_VERSION = 1


def main() -> int:
    files = sorted(glob.glob(str(ROOT / "app" / "schemas" / "**" / "*.json"), recursive=True))
    if not files:
        print("未找到 Room 导出的 schema JSON（需先执行一次 gradle compileDebugKotlin）")
        return 1
    latest = max(files, key=lambda p: Path(p).stat().st_mtime)
    data = json.loads(Path(latest).read_text(encoding="utf-8"))
    db = data["database"]
    identity = db["identityHash"]
    version = db["version"]

    con = sqlite3.connect(DB)
    con.executescript(
        "CREATE TABLE IF NOT EXISTS room_master_table "
        "(id INTEGER PRIMARY KEY, identity_hash TEXT);"
    )
    con.execute("INSERT OR REPLACE INTO room_master_table(id, identity_hash) VALUES(42, ?)", (identity,))
    con.commit()
    con.execute(f"PRAGMA user_version = {version}")
    con.commit()
    row = con.execute("SELECT identity_hash FROM room_master_table WHERE id = 42").fetchone()
    data_version = con.execute("SELECT value FROM app_meta WHERE key='data_version'").fetchone()
    uv = con.execute("PRAGMA user_version").fetchone()[0]
    tables = {r[0] for r in con.execute("SELECT name FROM sqlite_master WHERE type='table'")}
    con.close()

    # DATA_VERSION 带上整库内容哈希：schema 不变但数据变了（哪怕只是 app_meta 文案）
    # 也能让 AppDatabase 的升级守卫感知到，避免老用户库永不刷新。
    import hashlib
    content_hash = hashlib.md5(DB.read_bytes()).hexdigest()[:12]
    data_version_tag = f"{data_version[0] if data_version else ''}+{content_hash}"

    manifest = pathlib.Path(ROOT / "app/src/main/java/com/stockfortune/app/data/db/AssetManifest.kt")
    manifest.write_text(
        "package com.stockfortune.app.data.db\n\n"
        "// 由 tools/inject_room_hash.py 生成，勿手改：用于检测预置库与代码是否匹配。\n"
        "object AssetManifest {\n"
        f'    const val IDENTITY_HASH = "{identity}"\n'
        f'    const val SCHEMA_VERSION = {version}\n'
        f'    const val DATA_VERSION = "{data_version_tag}"\n'
        "}\n",
        encoding="utf-8",
    )
    print(f"AssetManifest.kt 已生成 (identity={identity})")

    expected = {e["tableName"] for e in db["entities"]}
    missing = expected - tables
    print(f"schema: {latest}")
    print(f"identity_hash = {identity}")
    print(f"db version = {version}, user_version = {uv}")
    print(f"缺失表: {sorted(missing) if missing else '无'}")
    ok = row and row[0] == identity and uv == version and not missing
    print("INJECT_OK" if ok else "INJECT_FAILED")
    return 0 if ok else 1


if __name__ == "__main__":
    sys.exit(main())
