# Phase 0 产品设计审查报告 · 股运通（Stock Fortune Android APP）

- 资料包：`Stock_Fortune_Android_APP_AI_Start_Kit`（10 篇文档 + 8 张效果图 + 3 张 Logo + `input_data/生辰八字.xlsx` + 3 份提示词）
- 审查日期：2026-09-29
- 审查方式：全部文档逐篇通读；8 张效果图逐张解析；Excel 数据源**全量 5395 行**程序化校验（stdlib 直解 xlsx）；与兄弟项目 `stock-metaphysics-platform` 的 golden 历法用例交叉验证
- 结论：**产品方向清晰、可开发**。数据源质量高于文档预期（四柱可 100% 复算）；但存在 **1 个硬阻塞（本机构建工具链缺失）** 与 **4 项需确认的设计口径**，见 §10。

---

## 1. 产品理解

**一句话**：把 A 股股票的"上市时刻八字"装进手机，离线研究每只股票在流年/流月/流日上的**十神关系**，重点看**正财/偏财**，不做任何涨跌预测。

- 名称：股运通 · 股票八字·十神择日；Slogan：顺势而为 · 知命行思
- 8 个功能页 + 我的/设置，全部数据本地，无网络权限（`AndroidManifest` 不声明 `INTERNET`）
- 明确不做：实时行情、收益回测、打分排名、AI 解释、账号/云同步
- 核心用户流程 4 条：单股查看 / 每日全市场扫描 / 十神筛选 / 八字择日
- 合规：全应用固定展示"仅为传统文化命理模型推演，不构成投资建议"（效果图 08 已给出文案，作为全局免责）

---

## 2. 数据源实测审查（关键，文档未覆盖）

`input_data/生辰八字.xlsx`：6 个 sheet（`总表`/`筛选1`/`筛选2`/`SH`/`SZ`/`BJ`）。`总表` 为三个分市场 sheet 的并集（2853 SZ + 2278 SH + 264 BJ = 5395 ✓），`筛选1` 是带查询条件的副本，`筛选2` 是透视表残留。**开发只需 `总表`。**

`总表` 列（12 列，5395 行，**0 空值**）：
`股票代码 | 股票名称 | 上市日期 | 上市首日开盘价 | 上市首日收盘价 | 首日涨跌幅 | 首日涨跌标识 | 完整八字 | 年柱 | 月柱 | 日柱 | 时柱`

实测校验结果：

| # | 校验项 | 结果 | 结论 |
|---|---|---|---|
| 1 | 代码唯一性 | 5395 个唯一代码，无重复 | 可作主键 |
| 2 | 上市日期 | Excel 序列号，范围 1990-12-01 ~ 2025-02-06（2776 个不同日期） | 需按 1900 日期系统换算 |
| 3 | **日柱** | 5395/5395 = 60 甲子(上市日期)，锚点 1949-10-01=甲子 | **完全可靠**，且与兄弟项目 golden（1900-01-01 甲戌、1984-02-02 丙寅、2001-08-27 壬戌）三例全对 |
| 4 | **时柱** | 时支 100% 为 `巳`（9:30 开盘巳时）；时干 100% 符合五鼠遁（由日干推出） | 时柱 = 巳时，非独立输入 |
| 5 | **年柱** | 491 行 ≠ 公元年直算，全部落在立春前；35 次年柱切换均对齐立春（如 1994-02-02 癸酉 → 1994-02-04 甲戌） | **以立春为分界**（非农历正月初一） |
| 6 | 月柱 | 2053 个（日柱,月柱）组合，需节气分界才能复算 | 依赖节气算法，Phase 2 用全量 5395 行做回归校验 |
| 7 | **阴阳** | = 首日涨跌标识：收盘>开盘→阳，<→阴，平盘 315 行→阳；一致率 5394/5394，另 1 行 `数据缺失` | **不是日主阴阳**，UI 卡片须标注"首日涨跌属性"；十神计算另用日干阴阳 |
| 8 | 完整八字 | 5395/5395 = 四柱拼接 | 冗余列，可校验 |
| 9 | 异常行 | `000004.SZ 国华网安 1990-12-01`：涨跌幅"数据缺失"、且该日为**周六**（深市首批柜台交易） | 交易日历需 1 条白名单例外 |
| 10 | 缺失字段 | **无 `行业` / `市场` / `股票属性` 列**，但效果图 02/06 要展示 | 见 §10-Q2 |

**重要发现（效果图数值为示意，不可作为算法基准）**：

| 位置 | 效果图值 | 数据源/实算值 |
|---|---|---|
| 02 详情页 600519 日柱/时柱 | 戊辰 / 壬子 | **壬戌 / 乙巳**（数据源，且与 golden 用例一致） |
| 05 每日分析 2026-09-01 日柱 | 丙子 | **戊寅** |
| 04/05 页 2026 年 9 月 | 酉月 / 乙酉 | **申月 / 丙申**（白露前） |
| 03 年度页 丙午年 | 2026 年 | 立春后为丙午 ✓ 正确 |

→ 处理原则：**效果图固定布局、层级、配色、组件形态；数值一律以算法 + 数据源为准**。此原则写入 §9 验收标准。

---

## 3. 视觉资产审查

- `design_assets/logo/` 三个文件（`stock_fortune_logo.png` / `app_icon.png` / `splash_logo.png`）**MD5 完全相同**，实为同一张 1254×1254 图（八卦圆盘 + 太极 + 红 K 线 + 金色箭头 + "股运通/股票八字·十神择日"字样）。
  - 用途拆分需程序处理：`app_icon` 裁切圆形徽标区（去掉文字）生成 `mipmap-*` 五档密度；`splash` 用整图（含文字）；`hero` 用徽标缩放。
- **缺失素材**：效果图顶部"深蓝夜空 + 山脉 + 金色月亮 + 云雾"主视觉背景**未随包提供**。见 §10-Q3。
- 色板（`02_UI_DESIGN_SYSTEM.md`，以效果图实测为准）：`#123A57` 主深蓝 / `#0D2743` 加深 / `#D6A84F` 金 / `#F2C56B` 浅金 / `#F6F8FB` 页面底 / `#FFFFFF` 卡片 / `#1C2740` 主文本 / `#6B7A90` 次文本 / 正财 `#F45B5B` / 偏财 `#F6B545` / 其他 `#C7CEDA`。
- 卡片圆角 20~28dp，标签为胶囊浅底深字，Hero 区不承载复杂操作。

---

## 4. 八页效果图逐页解析（信息架构基准）

| 页 | 结构（自上而下） | 固定要素 | 数据来源 |
|---|---|---|---|
| 01 首页 | Hero(Logo+股运通+副标题+slogan) → 搜索框(带主色按钮) → 四宫格入口(股票查询/每日扫描/交易日历/八字择日，各自图标底色) → 今日概览卡(日期+周几、正财 N 只、偏财 N 只) → 底部导航 | 概览卡可点进对应列表 | `stock`+运行时计算 |
| 02 股票详情 | Hero(名称+代码+一句话定位+收藏星) → 4 Tab(基本信息/年度运势/月度运势/每日分析) → 基本信息卡(代码/名称/上市日期/行业/市场/属性) → 简介提示条 → 八字信息卡(四柱 + 每柱五行) → 阴阳属性卡(阴阳类型/五行旺相/纳音五行/命理特征) | Tab 容器承载 3/4/5 页 | `stock`,`stock_bazi`,静态文案表 |
| 03 年度运势 | 年份切换(‹ 2026年 ›) → 年度概览卡(流年干支/五行/整体财运/行业影响/总体建议) → 各月十神出现情况(图例 正财/偏财/无；12 行"月份+月支"标签) | 月行点击进月度页 | 运行时计算 |
| 04 月度运势 | 月份切换(‹ 2026年9月(申月) ›) → 月度信息卡(月支图形/月干支/月干·月支五行 tag/五行属性/与日主关系/月运简述) → 本月交易日十神分布(图例 正财N天/偏财N天/其他N天) → **7 列日历网格**(每日数字 + 底部色点，财日高亮) → 本月提示 | 日历为强制要求 | 运行时计算 + `ganzhi_calendar` |
| 05 每日分析 | 月份切换 → 提示条"仅显示交易日，共 N 个交易日" → 列表(日期+ISO 副行 / 星期 / 干支+五行 / 十神标签 / ›) | 十神标签含 正官/食神/伤官/偏印 等，非仅财星 | 运行时计算 |
| 06 每日扫描 | 标题+副标题+日期 chip(可切换) → 两张统计卡(正财 N 只/稳健获利·价值回归；偏财 N 只/短线机会·题材波动) → Tab(全部/正财/偏财 带计数) → 表格(# / 代码 / 名称 / 行业 / 十神类型)，前 3 名皇冠标记 | 行点击进详情 | 全市场 5395 只单日计算 |
| 07 十神筛选 | 4 个可折叠分区(藏干十神/流年十神/流月十神/流日十神)，每区 10 个复选框(比肩…正印) + 说明副标题 → 底部金色渐变 CTA"开始筛选" → 结果复用 06 表格 | 记住上次勾选(DataStore) | `stock_bazi_hidden` + 运行时 |
| 08 八字择日 | Hero(标题+副标题+八卦图) → 表单卡(股票代码/开始日期/结束日期，日期选择器) → CTA"开始分析" → 提示条 → 择日结果(共找到 N 个吉日) 表格(日期+ISO/星期/财运类型/是否交易日) → 温馨提示免责 | 支持"仅交易日"过滤 | 区间遍历 |

**底部导航统一**（效果图 4 页文案不一致：选股/记录/月运/筛选/行情…）：固定 5 项 = **首页 · 选股 · 扫描 · 日历 · 我的**（首页图为基准；"选股"落十神筛选页，"扫描"落每日扫描页，"日历"落全局交易日历页，"我的"落设置/关于）。

---

## 5. 数据库设计（Room / SQLite）

预置只读库 `app/src/main/assets/databases/stock_fortune.db`（由 Python 脚本构建，约 3~5MB），Room `createFromAsset` 打开；用户数据表（收藏/筛选预设/缓存）在同一库中为空表，运行时可写。

```sql
-- 1 股票主表（Excel 总表直采 + 代码前缀推导）
CREATE TABLE stock(
  id INTEGER PRIMARY KEY AUTOINCREMENT,
  code TEXT NOT NULL UNIQUE,            -- '600519.SH'
  symbol TEXT NOT NULL,                 -- '600519'
  name TEXT NOT NULL,
  exchange TEXT NOT NULL,               -- SH / SZ / BJ
  board TEXT NOT NULL,                  -- 沪市主板/科创板/深市主板/创业板/北交所
  listing_date TEXT NOT NULL,           -- 'YYYY-MM-DD'
  first_open REAL, first_close REAL, first_change REAL,   -- NULL=数据缺失
  first_day_flag TEXT NOT NULL,         -- 阳/阴/数据缺失（=文档中的 yin_yang）
  industry TEXT NOT NULL DEFAULT '未分类',
  stock_nature TEXT NOT NULL DEFAULT 'A股',
  tagline TEXT                          -- 详情页一句话定位（可空）
);
CREATE INDEX idx_stock_name ON stock(name);
CREATE INDEX idx_stock_symbol ON stock(symbol);

-- 2 股票八字（四柱拆干支，便于索引与筛选）
CREATE TABLE stock_bazi(
  stock_id INTEGER PRIMARY KEY REFERENCES stock(id) ON DELETE CASCADE,
  full_bazi TEXT NOT NULL,
  year_pillar TEXT NOT NULL, month_pillar TEXT NOT NULL,
  day_pillar TEXT NOT NULL,  hour_pillar TEXT NOT NULL,
  year_stem TEXT, year_branch TEXT, month_stem TEXT, month_branch TEXT,
  day_stem TEXT NOT NULL,    day_branch TEXT NOT NULL,      -- 日主 = day_stem
  hour_stem TEXT, hour_branch TEXT,
  day_master_element TEXT NOT NULL,     -- 日主五行
  na_yin TEXT NOT NULL                  -- 纳音五行（60 甲子表）
);
CREATE INDEX idx_bazi_day_pillar ON stock_bazi(day_pillar);
CREATE INDEX idx_bazi_year_pillar ON stock_bazi(year_pillar);
CREATE INDEX idx_bazi_month_pillar ON stock_bazi(month_pillar);

-- 3 藏干十神物化（八字自身四柱地支藏干 vs 日主，供"藏干十神"筛选走 SQL）
CREATE TABLE stock_hidden_ten_god(
  stock_id INTEGER NOT NULL REFERENCES stock(id) ON DELETE CASCADE,
  pillar TEXT NOT NULL,                 -- year/month/day/hour
  branch TEXT NOT NULL, hidden_stem TEXT NOT NULL,
  ten_god TEXT NOT NULL,                -- 十神枚举
  PRIMARY KEY(stock_id, pillar, hidden_stem)
);
CREATE INDEX idx_shtg_god ON stock_hidden_ten_god(ten_god, stock_id);

-- 4 干支日历（1990-12-01 ~ 2035-12-31，约 16.5k 行；节气精确到日）
CREATE TABLE ganzhi_calendar(
  date TEXT PRIMARY KEY,
  year_ganzhi TEXT NOT NULL, month_ganzhi TEXT NOT NULL, day_ganzhi TEXT NOT NULL,
  year_stem TEXT, year_branch TEXT, month_stem TEXT, month_branch TEXT,
  day_stem TEXT, day_branch TEXT,
  month_branch_label TEXT NOT NULL,     -- '申月'
  solar_term TEXT                       -- 当日交节名称（可空）
);
CREATE INDEX idx_gz_month ON ganzhi_calendar(date);  -- 主键即覆盖月度区间扫描

-- 5 交易日历（1990-12-01 ~ 2035-12-31）
CREATE TABLE trade_calendar(
  date TEXT PRIMARY KEY,
  is_trade_day INTEGER NOT NULL,        -- 1/0
  weekday INTEGER NOT NULL,             -- 1..7
  closed_reason TEXT                    -- 周末/春节/国庆/…
);

-- 6/7 规则表（可被"关于/算法说明"页读出，也用于单元测试）
CREATE TABLE ten_god_rule(day_stem TEXT NOT NULL, target_stem TEXT NOT NULL,
  ten_god TEXT NOT NULL, PRIMARY KEY(day_stem,target_stem));            -- 100 行
CREATE TABLE hidden_stem_rule(branch TEXT NOT NULL, hidden_stem TEXT NOT NULL,
  ord INTEGER NOT NULL, PRIMARY KEY(branch,hidden_stem));                -- 本气/中气/余气

-- 8 运行时缓存（扫描/筛选结果，可清空；非预置数据）
CREATE TABLE scan_cache(
  stock_id INTEGER NOT NULL, date TEXT NOT NULL,
  day_ten_god TEXT NOT NULL, month_ten_god TEXT NOT NULL, year_ten_god TEXT NOT NULL,
  wealth_type TEXT NOT NULL,           -- 正财/偏财/其他/无
  is_trade_day INTEGER NOT NULL, computed_at INTEGER NOT NULL,
  PRIMARY KEY(stock_id,date));

-- 9/10 用户数据
CREATE TABLE favorite(stock_id INTEGER PRIMARY KEY REFERENCES stock(id) ON DELETE CASCADE, added_at INTEGER NOT NULL);
CREATE TABLE filter_preset(id INTEGER PRIMARY KEY AUTOINCREMENT, name TEXT NOT NULL,
  hidden_gods TEXT, year_gods TEXT, month_gods TEXT, day_gods TEXT, created_at INTEGER NOT NULL);
CREATE TABLE app_meta(key TEXT PRIMARY KEY, value TEXT NOT NULL);  -- data_version / calendar_version / schema_version
```

Room 实体与上表一一对应，`@Database(version = 1, exportSchema = true)`；DAO：`StockDao`（模糊查询/分页/按代码）、`BaziDao`、`CalendarDao`、`ScannerDao`（JOIN + 缓存写入）、`FilterDao`（藏干集合 ∩ 条件）、`FavoriteDao`、`MetaDao`。

**不做**：把 5395×N 天的十神结果全量预计算入库（估算 500 个交易日 ≈ 270 万行、100MB+，收益为负）。改为运行时计算 + 单日期缓存：单日全市场 5395 次纯查表运算，实测目标 < 300ms（JMH/Android 上以 JUnit 计时用例验证）。

---

## 6. Android 工程结构

```
StockFortuneAndroid/
├─ settings.gradle.kts  build.gradle.kts  gradle.properties  gradle/wrapper/
├─ gradlew  local.properties(不入库)
├─ tools/                         # 构建期脚本（不进 APK）
│  ├─ import_excel.py             # xlsx(总表) → 表数据（stdlib zipfile+xml+sqlite3）
│  ├─ solar_terms.py              # 寿星天文历：12 节气精确到日
│  ├─ gen_calendar.py             # ganzhi_calendar + trade_calendar 生成
│  ├─ build_assets_db.py          # 组装 stock_fortune.db + init_stock_fortune.sql + 校验报告
│  └─ verify_import.py            # 5395 行四柱复算 / 上市日必为交易日 / 与 golden 对齐
├─ database/                      # 产物：init_stock_fortune.sql、import_report.json、schema/
├─ release/                       # 产物：APK、签名与安装说明
└─ app/
   ├─ build.gradle.kts  proguard-rules.pro  src/main/AndroidManifest.xml
   ├─ src/main/assets/databases/stock_fortune.db
   ├─ src/main/res/{drawable,mipmap-*,values,font}/
   └─ src/main/java/com/stockfortune/app/
      ├─ StockFortuneApp.kt  MainActivity.kt  AppContainer.kt      # 手写 DI（不引 Hilt）
      ├─ data/
      │  ├─ db/{AppDatabase.kt, DatabaseProvider.kt, Converters.kt}
      │  ├─ entity/{StockEntity, StockBaziEntity, StockHiddenTenGodEntity,
      │  │          GanzhiCalendarEntity, TradeCalendarEntity, ScanCacheEntity,
      │  │          FavoriteEntity, FilterPresetEntity, AppMetaEntity}.kt
      │  ├─ dao/{StockDao, BaziDao, CalendarDao, ScannerDao, FilterDao, FavoriteDao, MetaDao}.kt
      │  └─ repository/{StockRepository, CalendarRepository, AnalysisRepository, SettingsRepository}.kt
      ├─ domain/
      │  ├─ model/{Bazi, Pillar, Stem, Branch, TenGod, WealthType, DayAnalysis,
      │  │         MonthAnalysis, YearAnalysis, ScanResult, DateSelectionResult}.kt
      │  ├─ calculator/{GanzhiCalculator, TenGodCalculator, HiddenStemCalculator,
      │  │              WealthResolver, NaYinTable, ElementStrength, FortuneTextTable}.kt
      │  └─ usecase/{SearchStock, GetStockDetail, GetYearAnalysis, GetMonthAnalysis,
      │              GetDailyAnalysis, ScanMarketByDate, FilterByTenGods, SelectAuspiciousDates,
      │              ToggleFavorite}.kt
      ├─ ui/
      │  ├─ theme/{Color, Type, Shape, Dimens, Theme}.kt
      │  ├─ components/{SfHero, SfCard, SfTag, SfTenGodTag, SfSegmentedTabs, SfStatCard,
      │  │              SfSearchBar, SfEntryTile, SfMonthCalendar, SfDataTable, SfEmptyState,
      │  │              SfDisclaimer, SfBottomBar, SfSectionHeader}.kt
      │  ├─ navigation/{Routes.kt, SfNavHost.kt, BottomBar}.kt
      │  ├─ home/  ├─ filter/  ├─ scanner/  ├─ calendar/  ├─ profile/
      │  ├─ stock/{StockDetailScreen, BasicInfoTab, YearTab, MonthTab, DailyTab}.kt
      │  ├─ dateselect/DateSelectScreen.kt
      │  └─ common/{UiState, Formatters, DateExt}.kt
      └─ (无网络层、无推送、无账号)
   src/test/java/.../   # JVM 单元测试：历法/十神/藏干/财富判定/仓库/ViewModel
   src/androidTest/     # 可选：Room DAO instrumentation（无设备时跳过）
```

技术选型：Kotlin 2.0.x + AGP 8.7.x + Gradle 8.9+；`compileSdk 35 / targetSdk 35 / minSdk 26`；**Jetpack Compose + Material3**（卡片圆角、渐变、日历网格还原效率最高）；Room 2.6.x + KSP；Coroutines/Flow；DataStore Preferences（勾选记忆、设置项）；**不引入** Hilt / Retrofit / 任何网络库 / 崩溃上报。`debug` 与 `release` 双变体，release 用本地生成的 keystore（自签名，仅本机安装）。

---

## 7. 页面路由设计

```kotlin
sealed route（navigation-compose，字符串模板 + 参数）
ROOT_SCAFFOLD(bottomBar 5 项，仅顶层显示)
├─ "home"                                  首页(Tab1)
├─ "filter"                                十神筛选(Tab2)  ← 07
├─ "scanner"                               每日扫描(Tab3)  ← 06，默认最近交易日
├─ "calendar"                              交易日历(Tab4)  全局月历 + 选中日干支
├─ "profile"                               我的(Tab5)      数据版本/算法说明/免责/清缓存
├─ "search?q={query}"                      搜索结果列表（首页搜索提交）
├─ "stock/{code}?tab={tab}"                股票详情容器     ← 02
│     tab ∈ basic|year|month|daily（默认 basic，Tab 切换不改路由栈）
│     ├─ 年度参数：  "stock/{code}/year?year={year}"          ← 03
│     ├─ 月度参数：  "stock/{code}/month?year={y}&month={m}"  ← 04
│     └─ 每日参数：  "stock/{code}/daily?year={y}&month={m}"  ← 05
├─ "dayDetail?code={code}&date={date}"     单日详情（Phase 5 可选，行点击）
├─ "dateSelect?code={code}"                八字择日         ← 08（首页入口 + 详情页跳转带代码）
└─ "about" | "algorithmDoc" | "disclaimer" 我的子页
```

跳转关系：`home.search → search → stock/{code}`；`home.四宫格 → filter/scanner/calendar/dateSelect`；`home.今日概览 → scanner?tab=wealth`；`scanner/filter 表格行 → stock/{code}?tab=basic`；`stock.year 月行 → stock.month`；`stock.month 日历日 → stock.daily(定位该日)`；`dateSelect 结果行 → dayDetail`。全部 `popUpTo` 保持返回栈自然，系统返回键优先回退 Tab 内层级。

---

## 8. 算法与规则（Rule v1.1，已按实测密度定稿）

```kotlin
// 输入：stock_bazi（四柱）+ ganzhi_calendar（流年/流月/流日）+ trade_calendar
日主 DM = 股票日柱天干                                  // 与"首日涨跌标识"无关
十神(干) = tenGod(DM, X)：同我 比肩/劫财；我生 食神/伤官；
        我克 偏财/正财；克我 七杀/正官；生我 偏印/正印   // 同性→前者，异性→后者
藏干十神 = { tenGod(DM, h) | h ∈ 四柱地支全部藏干 }        // 独立筛选维度，入库 stock_hidden_ten_god

// 单柱财星判定（v1.1 关键：透干优先）
wealth(柱) = 天干十神 ∈ {正财,偏财}            → 取之（显性）
             否则 地支本气十神 ∈ {正财,偏财}  → 取之（隐性，UI 标 "·藏"）
             否则                              → 其他
流年/流月/流日财星 = wealth(该柱干支)；非交易日 → 无

年度页月标签 = 该干支月（寅月…丑月共 12 个）的流月财星，"其他"显示为"无"
月度页日历   = 每个自然日按流日财星上色；非交易日置灰
每日分析     = 仅交易日；标签 = 流日天干十神（十神全集，非仅财星）
扫描(某日)   = 全市场按流日财星分正财/偏财；排序 正财 > 偏财 > 代码
十神筛选     = 藏干集合 ∩ A ≠ （组内 OR）且 流年 ∈ B 且 流月 ∈ C 且 流日 ∈ D（组间 AND；空集=不限）
择日         = 区间内流日财星 ∈ {正财,偏财} 的日期，标注是否交易日，可"仅交易日"过滤
```

**为什么是"透干优先"而不是"正财优先"**：初版把"命中集合含正财即判正财"，实测 2026-09-29（丙午日）全市场偏财数为 **0** —— 壬日主透丙为偏财、午藏丁为正财，正财优先级把偏财吞掉了。改为透干优先后该日为 正财 465 / 偏财 511，分布合理。

**为什么判定域不含全部藏干**：若把流年+流月+流日三柱全部藏干并入，正财命中率虚高到 39%（2103/5395），扫描列表失去区分度。改用"流日单柱 + 天干/本气"后，600519 在 2026-09 得 正财3 / 偏财5 / 其他13，与效果图 04 标注的 正财3天 / 偏财5天 / 其他14天 吻合，口径得到验证。

历法口径（已由数据源锁定）：日柱 = 60 甲子连续推进（锚点 1949-10-01 甲子）；年柱 = **立春**分界；月柱 = **十二节**分界 + 五虎遁；时柱 = 9:30 → 巳时 + 五鼠遁；纳音 = 60 甲子纳音表；五行旺相按四时五行令（当令旺、令生者相、生令者休、克令者囚、令克者死）。

**交节口径 = 日粒度**：数据源 84 处（9 年柱 + 75 月柱）与"时刻粒度"实现的差异**全部落在交节当日**（如 1994-02-04、2016-02-04 立春当日上市，数据源记新年柱）。本项目据此统一采用"交节当日即换柱"，与数据源逐字一致（5395/5395 复算通过）。兄弟项目 `stock-metaphysics-platform` 使用 lunar-python 的时刻粒度，因此**立春/交节当日两项目会相差一柱**，这是有意的口径选择，已写入 APP 内"算法口径说明"页。

"命理特征 / 月运简述 / 年度建议"等文案来自**静态文案表**（`FortuneText`，日主五行 × 财星关系映射），非 AI 生成，符合"不做 AI 解释"边界；并有测试断言文案中不出现"买入/卖出/涨幅/收益率/必涨/稳赚"等措辞。

回归基准：`tools/verify_database.py` 共 28 项门禁（含 5395 行四柱复算、上市日必为交易日、33 条 DAO 语句全部可预编译、预置库 DDL 与 Room schema 逐字一致）；Kotlin `BaziCoreTest` 复用兄弟项目 golden + Python 生成的 parity 夹具（630 个日期、60 只股票、100 组十神、财星抽样）。

## 9. Excel 导入与初始化方案

1. `tools/import_excel.py`：stdlib 直解 `xl/worksheets/sheet1.xml` + `sharedStrings.xml`（已验证可行，无需 openpyxl）→ 记录 xlsx SHA-256 与行数 → 输出规范化行。
2. 字段映射：`股票代码→code/symbol/exchange/board`、`股票名称→name`、`上市日期(序列号)→listing_date`、`开盘/收盘/涨跌幅→first_*`、`首日涨跌标识→first_day_flag`、`四柱→stock_bazi 拆分`；`industry/stock_nature` 依 Q2 结论填充。
3. `tools/gen_calendar.py`：节气算法生成 `ganzhi_calendar`（1990-12-01~2035-12-31）；`trade_calendar` = 周一至周五 − 法定节假日表（curated 1991-2026 + 2027 预估）+ 1990-12-01 白名单例外；用 5395 个上市日反向验证（若某上市日被标为非交易日 → 构建失败）。
4. `tools/build_assets_db.py`：生成 `app/src/main/assets/databases/stock_fortune.db` + `database/init_stock_fortune.sql`（可读全量 DDL+规则表 INSERT）+ `database/import_report.json`（行数、校验结论、数据快照日期、工具版本）。
5. 幂等：脚本可重跑，产物入库（APK 直接带库），APP 内不做任何下载；`app_meta` 记录 `data_version=2025-02-06`，"我的"页展示。
6. 数据更新说明：Phase 6 文档给出"替换 xlsx → 跑三个脚本 → 重新打包"的操作路径。

---

## 10. 开发任务拆解与验收

| Phase | 任务 | 产出 | 验收（自动化 + 人工） |
|---|---|---|---|
| **0 审查** | 本报告 | `PHASE0_DESIGN_REVIEW.md` | 用户确认 Q1~Q4 ✅ 门禁 |
| **1 骨架** | 工具链就绪、工程初始化、主题/字体/圆角、通用组件（Hero/Card/Tag/BottomBar/Segmented/StatCard）、底部导航、首页静态版 | 可运行空壳 + `ui/theme` + 组件库 | `./gradlew assembleDebug` 通过；组件预览截图对比 01 首页结构 6 项清单；无网络权限 |
| **2 数据层** | 导入脚本 + 日历生成 + 预置库 + Room 实体/DAO/Repository + 股票搜索 | `stock_fortune.db`、`verify_import.py` 报告、搜索页 | 5395/5395 四柱复算；100% 上市日为交易日；DAO 单测；搜索"600519/茅台/moutain 拼音?"命中正确；APK 体积 < 25MB |
| **3 详情与运势** | 详情页 4 Tab、八字卡、阴阳卡（纳音/旺相/文案表）、年度页、月度日历页、每日列表页 | 02~05 四页 | `GanzhiCalculatorTest` 8 golden + 200 抽样；600519 显示 辛巳/丙申/**壬戌**/乙巳；月度日历点数与每日列表天数一致；视觉清单逐页勾选 |
| **4 扫描与筛选** | 每日扫描（统计卡/Tab/表格/日期切换）、十神筛选（四段式 + 记忆）、八字择日、交易日历页 | 06~08 + 日历页 | 单日全市场扫描 < 300ms（JVM 计时用例）；筛选 SQL 与运行时结果一致；择日区间 ≤ 366 天；空结果/非交易日态覆盖 |
| **5 收尾** | 我的/设置（数据版本、算法说明、免责、清缓存、收藏列表）、性能、ProGuard、debug+release APK、装机 | `release/*.apk`、签名说明 | 离线飞行模式全功能回归；release 自签名可安装；5000+ 股票滚动无掉帧；`08_UI_VISUAL_ACCEPTANCE.md` 全清单勾选 |
| **6 交付** | 安装说明、使用说明、数据更新说明、交付报告 | `release/README.md`、`DELIVERY_REPORT.md` | 报告含：实测数据、已知偏差、风险、后续建议 |

每 Phase 结束输出阶段小结（做了什么 / 测试结果 / 修了什么问题 / 下一步），符合资料包"每完成阶段输出验收报告"要求。

---

## 11. 风险清单

| # | 风险 | 影响 | 处置 |
|---|---|---|---|
| R1 | **本机无 JDK / Android SDK / Gradle / node**（已实测：`java` 不可用、无 `~/.gradle`、无 `ANDROID_HOME`；网络可达 `dl.google.com`/Maven Central/`services.gradle.org`） | **硬阻塞**：无法产出 APK | 需授权一次性安装到用户目录（约 2.5~3GB，无 sudo）：Temurin JDK 17 + cmdline-tools + platform-tools + platforms;android-35 + build-tools + Gradle Wrapper |
| R2 | 节气算法误差导致月柱边界差 1 天 | 中 | 用数据源 5395 行 + golden 用例双重量化；边界日单独断言；不达标则改用 Maven Central 的 `cn.6tail:lunar` 仅做构建期生成 |
| R3 | 未来年份（2027+）法定节假日未公布 | 低 | 日历标注 `calendar_version`，"我的"页说明为预估；2026 年内数据精确 |
| R4 | 效果图数值为示意（§2 末表） | 中（易被误判为 bug） | 已固定原则：布局照图、数值照算法；交付报告逐条说明差异 |
| R5 | 数据快照止于 2025-02-06（今日 2026-09-29），缺 2025-02 之后新股 | 低 | 首页/我的标注"数据版本 2025-02-06，共 5395 只"；提供更新流程 |
| R6 | 行业/股票属性字段缺失（效果图 02/06 需要） | 中 | 见 Q2 |
| R7 | 无实机/模拟器（无 adb、无 Android 设备） | 中 | 见 Q4 |
| R8 | 三个 Logo 文件实为同一张图，Hero 背景素材缺失 | 中 | 见 Q3 |
| R9 | 命理类文案合规 | 低 | 全局免责条 + 不出现"预测/收益/建议买入"字样；文案表仅描述五行关系 |

---

## 12. 确认结果（2026-09-29 用户已定，据此开发完毕）

- **Q1 算法规则**：按 §8 定稿执行，并在实现中迭代为 **Rule v1.1（透干优先 + 单柱判定域）**，原因见 §8 两段说明。
- **Q2 行业 / 股票属性**：`board`（沪市主板/科创板/深市主板/创业板/北交所）由代码前缀推导；`industry` 统一"未分类"，并预留 `code,industry` CSV 合并钩子。
- **Q3 主视觉背景**：由 AI 按 UI 规范生成，存放于 `design_assets/generated/hero_night_mountain.png`，已入 `res/drawable-nodpi/hero_night.png`。
- **Q4 工具链与测试路径**：授权安装到用户目录（JDK 17 / cmdline-tools / platform-tools / android-35 / build-tools 35 / Gradle 8.11.1）；**只出 APK，由用户自行装机验证**（不启模拟器）。
- **Q5 扩展**：不安装任何 Skill/MCP。Qoder 官方市场检索到 4 个 Android 相关候选（minimax-android-dev、alphaonedev-android-jetpack、android-adb、diegosouzapw-android-unit-test）；社区源 skills.sh 因本机无 `node/npx` 记为源级不可用。

## 13. 实现阶段的定稿变更（相对 §5/§6 原设计）

| 变更 | 原因 |
|---|---|
| 删除 `ten_god_rule` / `hidden_stem_rule` / `na_yin_rule` 三张规则表 | 十神/藏干/纳音常量在 Kotlin `BaziTables` 已是唯一事实来源，入库属重复；由 parity 夹具双向校验 |
| 删除 `filter_preset` 表与 `PresetDao` | 效果图只要求"记住上一次勾选"，DataStore 已覆盖 |
| 删除 `stock.tagline` 列 | 无数据来源，详情页改用上市日期作副标题 |
| 预置库建表语句改为**直接取 Room 导出的 createSql** | 消除"预置库列定义与实体不符"这类只在运行期暴露的风险；新增 C17-C20 四道门禁 |
| 扫描/筛选改为"10 个日主先判定 → `day_stem IN(:stems)` 下推" | 单日全市场从 3.3 s 降到 800 ms 以内 |
| ViewModel 状态更新改为原子 `_state.update { }` | 修掉年/月/日三路并发写互相覆盖导致月度 Tab 空白的真 bug |
| 交易日历新增 `confidence` 列与"实测反查"机制 | 早年放假安排无法逐条核对，用 5395 个真实上市日反向纠正 |
