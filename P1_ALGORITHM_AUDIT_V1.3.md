# P1 算法统一专项审计｜V1.2 → V1.3 离线命理融合

审计日期：2026-10-09 ｜ 本轮性质：**只读**（未改代码、未建表、未重写判词、未合并 PR）

---

## 0. 基线确认

| 项 | 实测值 | 证据 |
|---|---|---|
| 主仓库 HEAD | `4db60ac` Merge PR #4（chore/release-v1.2.0），提交时间 2026-10-09T12:34:48+08:00 | `git log -1` |
| 版本 | `versionName="1.2.0"`、`versionCode=3`；debug 加 `-debug` 后缀 | `StockFortuneAndroid/app/build.gradle.kts:16-17,48` |
| tag | `v1.1.0`、`v1.2.0` | `git tag -l` |
| 预置库 | `rule_version=bazi-rule-v1.2`、`data_version=2025-02-06`、`calendar_version=2026-09-29`、`generated_at=2026-10-09T12:20:18`、`source_sha256=57a26df3…` | `app/src/main/assets/databases/stock_fortune.db` → `app_meta` |
| 库规模 | 8.23 MiB；stock 5395 / stock_bazi 5395 / stock_hidden_ten_god 53347 / ganzhi_calendar 16467（1990-12-01→2035-12-31）/ trade_calendar 16467（交易日 11207） | 同上，逐表 `count(*)` |
| AssetManifest | `SCHEMA_VERSION=1`、`IDENTITY_HASH=a9f01a28a8ba17058a049e9d43ef006b`、`DATA_VERSION=2025-02-06+aaf86250c1f8` | `data/db/AssetManifest.kt:5-7` |
| 单测状态 | `./gradlew testDebugUnitTest --offline` **exit 0**；9 个测试类 **68 个用例全绿**（BaziCore 18 / RuntimeSmoke 12 / ClassicsCorpus 11 / Compliance 7 / AssetParity 4 / ClassicsRender 5 / ScreenRender 5 / TabContentRender 5 / DetailViewModel 1） | `app/build/test-results/testDebugUnitTest/*.xml` |
| 参考仓库 HEAD | `decb789` Merge PR #8（research-closure-remediation）；其前一条 `018d71a` fix: label bazi dayun assumptions | `stock-metaphysics-platform` `git log` |

**工作树现状（非本次改动，属既有未提交内容）**：`app/src/test/resources/parity/strength.csv` 仅 CRLF↔LF 行尾差异（`git diff --ignore-cr-at-eol` 为空，**内容零改动**）；三个未跟踪脚本 `tools/{build_advice_xlsx,build_fragment_xlsx,extract_advice_review}.py`。

---

## 1. V1.2 实际能力矩阵（附代码路径）

调用链：`stock_bazi / stock` → `TenGodCalculator` → `FortuneText` → `AnalysisRepository` → `YearAnalysis`/`MonthAnalysis`/`DayDetail` → Compose Tab。

| # | 能力 | 输入 | 输出 | 实际被使用的字段 | 测试证据 | 已知限制 |
|---|---|---|---|---|---|---|
| 1 | 四柱取数 | `stock_bazi`（`full_bazi`+8 干支列+`day_master_element`/`month_season_element`/`na_yin`/`day_master_strength`） | `StockBaziEntity` | 分析页实际只用 `dayStem` + `dayMasterStrength` 两个字段 | `AssetParityTest.kt:44-70`（回证 md5 前 12 位、`room_master_table`、`user_version`） | 全 App **无任何一处读 `hour_pillar` 参与判定**；时柱只进 `stock_hidden_ten_god` 作筛选维度（hour 16185 行 / 5395 股） |
| 2 | 十神 | 日干 + 任一干 | `TenGod` | `TenGodCalculator.tenGod` `domain/calculator/TenGodCalculator.kt:93-105` | `parity/ten_god.csv` 100 组全表 + `BaziCoreTest` | 只作用干；支一律先取本气 |
| 3 | 藏干 / 本气 / 气位 | 地支 | `HIDDEN_STEMS`（本/中/余） | `:107-114`；`AnalysisRepository.kt:84-94` 渲染四柱藏干 | `parity/stocks.csv` 60 股 | 判定域不含中气余气（见 §8 设计口径） |
| 4 | 纳音 | 干支 | `NA_YIN_PAIRS[idx/2]` | `:86-90` | `parity/stocks.csv` | 纯展示 |
| 5 | 财星判定（Rule v1.1） | 日干 + 单柱干支 | `WealthType` | `:130-140` 透干优先→本气→其他 | `parity/wealth.csv` 121 行含边界（壬日主·丙午→偏财） | 非交易日强制 `NONE`（`AnalysisRepository.kt:60,82,95`） |
| 6 | 日主强弱（Rule v1.2） | 年/月/日**三柱六字** | `Strength` 三态 | `:165-188`；权重 月支 3.0/1.5/0.75、他支 1.0/0.5/0.25、干 0.7、阈值 ±2.0 | `parity/strength.csv` 61 行（含 14 条压线盘，`gen_parity_fixtures.py:70-82` 专门塞的） | **认不出标签即抛错不兜底**（`AnalysisRepository.kt:143-145`）；无得令/得地分轴；日干不计自身 |
| 7 | 月令旺相 | 月支 | 「金旺·水相·土休·火囚·木死」 | `:191-200` | `BaziCoreTest` | 只作句子尾巴 |
| 8 | 流年/流月/流日 | `ganzhi_calendar` 按日 | `FlowPillar` ×3 | `AnalysisRepository.kt:77-103` | `parity/ganzhi_sample.csv` 630 日期含全部交节当日 | 三维皆「干十神 + 支本气十神」，**无干支与原局的互动** |
| 9 | 30 格文案 | `Pair<TenGod, Strength>` | 白话子句 | `FortuneText.kt:46-107`；取键 `:109-110` | `ComplianceTextTest.kt:142-154` 跑 `Strength×WealthType×TenGod` 全组合 | **缺格 `error()` 硬失败**，无兜底；两轴为「流月/流年天干十神 × 强弱」 |
| 10 | 判词拼接 | 位置句 + 30 格子句 + 月令句 | `monthSummary` | `FortuneText.kt:149-163`；调用点 `AnalysisRepository.kt:131,183` | 同上 | 年度页与月度页共用同一函数（`YearAnalysis.months[].summary`） |
| 11 | 年度概览/行业句/建议 | 流年财星、年支 | `wealthSummary`/`industryNote`/`advice` | `FortuneText.kt:113-147`；`AnalysisRepository.kt:188-199` | `ComplianceTextTest` 只覆盖 `monthSummary`/`yearWealthSummary`；`yearAdvice`、`yearIndustryNote`、`wealthBasis` **未被 assertNoAdvice 覆盖** | 五行生克五分句（`:130-141`）与 30 格无关 |
| 12 | 单日推导链展示 | 日干+流日 | `basis: List<String>` | `FortuneText.kt:176-205` | `BaziCoreTest.kt:180-199` 仅查枚举名泄漏 | 「藏而不透，按口径不计入判定」已如实向用户说明 |
| 13 | 交节口径 | 日粒度（交节当日 00:00 即换柱） | `ganzhi_calendar` 预置 | `tools/solar_terms.py:1-9,59-76`；月支按十二节区间 | `parity/ganzhi_sample.csv` | **与研究平台的时刻粒度有意不同**，已写入 App 内口径页 |
| 14 | 合规门禁 | 56 词 | 7 个测试 | `ComplianceTextTest.kt:26-35`（收益尺 14+14）、`:51-55`（建议尺 32）、`:182-214` 反射扫 `ui/**.kt` 中文字面量 | 68 用例全绿 | **只扫 `ui/`，不扫 `domain/`、`data/`、`assets/databases/*.db`** |
| 15 | 首日阴阳 | `stock.first_day_flag` | 仅界面渲染 | `ui/detail/Tabs.kt:123-127`（三值：阳/阴/数据缺失） | 无计算侧测试 | **V1.2 完全不参与任何算法**，纯展示字段 |

**V1.2 明确不存在的东西**（全仓 grep `大运|dayun|起运|喜用|用神|忌神|六合|三合|相冲|相刑|相害` 在 `app/src/main` 零命中）：大运、起运、原局内部互动、流年流月与原局互动、喜用神/忌神、格局、调候、十二长生。`DELIVERY_REPORT.md:321` 自己也这么记的。

---

## 2. 两仓库算法差异清单

| 维度 | Android V1.2 | 研究平台（`decb789`） | 差异性质 | 证据 |
|---|---|---|---|---|
| 强弱输入柱数 | 三柱六字，**剔时柱** | **四柱全含**，无任何 exclude_hour 开关 | 口径冲突（有意） | `TenGodCalculator.kt:165-179` vs `bazi_engine.py:126-155`、`rules.py:112,118,220,341` |
| 强弱算法 | **绝对分**加权和，同党+1/异党−1 | **比例** `support/(support+drain)` | 不可互换 | `:152-178` vs `rules.py:242-259` |
| 日主自身 | 不参与计分（`i<2` 跳过日干） | 参与（日干按 0.8 计入 support 的比劫） | 结构差 | `:169-172` vs `rules.py:115` |
| 档位 | 3 档（身强/中和/身弱），阈值 ±2.0 | **5 档**（身强/偏强/中和/偏弱/身弱），切分 0.62/0.55/0.45/0.38 | 粒度差 | `:155,181-188` vs `rules.py:250-259` |
| 得令/得地 | 无（只有月支权重 3.0） | 显式 `de_ling`（仅「旺」）+`de_di`（`root_score>=1.0`） | 缺项 | `rules.py:211,220-228` |
| 月支藏干权重 | 3.0/1.5/0.75 | 1.0×`MONTH_BRANCH_MULTIPLIER 1.5`，藏干 0.6/0.3/0.1（二藏支 0.7/0.3） | 权重表完全不同 | `:152` vs `rules.py:46-58` + `core/constants.py:112-121` |
| 格局 | 无 | 8 正格 + 建禄/月刃/比肩/劫财，透干优先，失败返回 `availability="unavailable"` | 缺项 | `rules.py:341-410` |
| 从格/专旺 | 无 | **无**（全仓 grep 零命中）→ 极端盘会被硬判身强 | 两侧同缺 | grep 无命中 |
| 喜用/忌神 | 无 | 扶抑三分支 + 调候**实际改写 `xi_shen`**；无候选列表、confidence 硬编码、`availability` 在 `bazi_engine.py:301` **被写死 OK** | 缺项 | `rules.py:463-514` |
| 原局内部关系 | 无 | 两套并行口径：`rules.py:570-668` 四柱全对（含时柱）；`date_relation.py` v3 严格 3×3（**剔时柱**） | 缺项 + 平台内部不自洽 | `date_relation.py:54`、`schemas/relation.py:39` |
| 岁运 vs 原局 | 无 | 22 类关系目录、事件化、**分值恒为 None（测试锁死）** | 缺项 | `schemas/relation.py:66`、`tests/core/test_fortune_contract.py:237-262` |
| 大运 | 无 | 有：`resolve_first_day_yinyang_luck_cycle` + `build_luck_cycle_periods`（委托 lunar-python 1.4.8） | 缺项 | `fortune/luck_cycle.py:22`、`bazi_engine.py:480-524` |
| 交节粒度 | 日粒度 | 时刻粒度（`OBSERVATION_HOUR=12`，段内 +1s 定柱） | 有意不同，已登记 | `solar_terms.py:1-9` vs `ten_god_calendar.py:88-98,186-224` |
| 规则版本 | `bazi-rule-v1.2`（库内单值） | 多轴分版：`stock-luck-cycle-first-day-yinyang-v1`、`fortune-dayun-period-lunar-python-1.4.8-v1`、引擎 `smx-bazi-native-1.0.2`（1.0.1→1.0.2 改了方向推导） | 版本粒度不同 | `app_meta` vs `schemas/fortune.py:33-34`、`docs/calculation-differences-phase1.md:66-77` |
| 文案 | 30 格静态表，缺格即抛 | 因子 explanation + narrator 模板（默认非 LLM），`guard.py` 显式拒绝 | 架构同类，载体不同 | `FortuneText.kt:46-110` vs `narrator/narrator.py:37-38,167-177`、`guard.py:31-63` |

---

## 3. 关键冲突核验结果（A–E）

### A. Android 六字强弱 vs 平台四柱强弱 —— **已做全量对照**

对照方法：直读 Android 预置库 5395 只股票的四柱，分别用两侧原函数复算（平台侧 `sys.path` 注入 `src.engines.bazi.rules`，不复制常量，避免"评估用的规则和上线的规则各自漂移"）。脚本 `/tmp/audit_strength_parity.py`（只读，不落仓库）。

三态分布对照：

| 口径 | 身强 | 中和 | 身弱 |
|---|---|---|---|
| Android 六字（现行上线） | 888 / 16.5% | 1834 / 34.0% | 2673 / 49.5% |
| Android 四柱（含巳时） | 883 / 16.4% | 1708 / 31.7% | 2804 / 52.0% |
| 平台四柱（五档压三档） | 1458 / 27.0% | 1305 / 24.2% | 2632 / 48.8% |
| 平台三柱（剔时柱，仅为对照） | 1658 / 30.7% | 1350 / 25.0% | 2387 / 44.2% |

**分布层面结论**：
1. Android 六字 ↔ Android 四柱：**729 只（13.5%）三态翻转**；总分布几乎不变（16.4/31.7/52.0 → 16.5/34.0/49.5），但**按日主的偏置被常数项放大**：六字口径身强占比 9.7%（丁）↔22.6%（戊）＝2.3 倍；四柱口径 2.8%（甲）↔40.9%（戊）＝**14.6 倍**。巳时对每个盘是同一常数（木 −1.75 / 火 +0.25 / 土 +0.75 / 金 −0.25 / 水 −0.75），全库时支 **5395/5395 恒为巳**（已实测）。→ V1.2 剔时柱的理由成立。
2. Android 六字 ↔ 平台四柱（压三档）：**一致仅 3314 只（61.4%），不一致 2081 只（38.6%）**。不一致方向高度集中：`Android 中和→平台身强 626`、`Android 中和→平台身弱 576`、`Android 身弱→平台中和 501`、`Android 身强→平台中和 172`、`Android 身弱→平台身强 161`、`Android 身强→平台身弱 45`。→ **同一只股票在两个产品里会被说成相反的旺衰**，这不是精度差，是契约冲突。
3. **时柱敏感性在平台口径下远比 Android 严重**：平台四柱 vs 平台三柱，**3477 只（64.4%）跨档**（五档制下）。因为比例制把日主自身 0.8 算进 support，且月支/日支乘 1.5/1.2，一个 0.6 权重就能推动 0.01 级比例跨过切分点。
4. **阈值脆弱度**：平台 ratio 落在任一切分点 ±0.01 内的有 **1037 只（19.2%）**；Android 分数落在 ±2.0 阈值 0.5 缓冲带内的有 **938 只（17.4%）**。两侧都经不起"顺手调权重"。
5. 按日主分布（平台四柱）身强+偏强占比：甲 5.9% / 乙 8.3% / 丙 13.2% / 丁 40.0% / 戊 57.5% / 己 46.5% / 庚 48.0% / 辛 13.2% / 壬 15.9% / 癸 15.7% → **极差 9.7 倍**，比 Android 四柱口径温和但仍不均衡。

人工复核样本（边界 + 特殊）：

| 代码 | 名称 | 四柱 | Android | 平台 | 复核意见 |
|---|---|---|---|---|---|
| 000002.SZ | 万科A | 庚午 己丑 己亥 己巳 | 中和 | 身强（0.6353） | 三己土透干 + 巳火，平台把日主自身计入帮扶后必然偏高 |
| 000048.SZ | 京基智农 | 甲戌 甲戌 辛卯 癸巳 | **身强** | **偏弱（0.4059）** | 方向完全相反。Android 只看同党符号和；平台按比例分档，两者不可同时为真 |
| 000010.SZ | 美丽生态 | 乙亥 丙戌 辛卯 癸巳 | 中和 | 身弱（0.3000） | 跨两档 |
| 002943.SZ | 宇晶股份 | 戊戌 癸亥 乙丑 辛巳 | 身强（**恰好 +2.00**） | 中和（四柱口径 +0.95） | 压线盘；`>=` 与 `>` 的写法差异只有这种样本测得出来 |
| 600970.SH | 中材国际 | 乙酉 庚辰 丙寅 癸巳 | 身弱（恰好 −2.00） | 中和（−1.05） | 同上，另一端 |
| 301009/301011/688597 | 可靠股份/华立科技/煜邦电力 | 辛丑 甲午 丙申 癸巳 | 身弱（−2.00） | — | 三只同盘同判，可作回归夹具 |

极端盘检查：按平台口径 **support 或 drain ≈0 的股票数 = 0** → 平台未实现从格/专旺这件事，在本数据集上暂不产生错误判档（但结论不具普适性，扩样本需重测）。

### B. 首日阴阳兼容性别 vs 实际大运顺逆 —— **疑点成立，且是活的缺陷**

先纠正一处前提：**「首日阴阳」不是八字日干，是首日涨跌幅符号**（阳=收涨、阴=收跌）。Android 侧同名字段口径一致（`strings.xml:47` 就叫「首日涨跌」），实测 `first_change>0 → 阳`、`<0 → 阴`、完全对齐。

方向判定本身在 `luck_cycle.py:113-123` 是**正确**的传统口径：`direction = FORWARD if (年干阳 == proxy阳) else REVERSE`（同阴阳顺、异阴阳逆）。

**缺陷在编排层**：`stock_fortune.py:252-256` 传给排盘器的不是 `direction`，而是 `compatibility_variant_mode(luck)`，而它（`luck_cycle.py:172-181`）只把 `compatibility_gender` 映射成 FORWARD/REVERSE，而 `compatibility_gender`（`luck_cycle.py:114-116`）**只由首日阴阳决定、完全不看年干**。引擎再按 `variant_mode` **反解**出所需性别（`bazi_engine.py:537-544`：`male_compatibility = year_is_yang == wants_forward`），复合结果恒等式：**周期方向 ≡ variant_mode ≡ 首日阴阳**。

于是 `ADR-0017:18` 自己写的禁令「代码不得把兼容输入 VariantMode 直接当作实际 LuckCycleDirection」被违反了。`018d71a` 修的是**引擎侧**（variant_mode→性别的反解 + `da_yun_note` + `bazi.variant_mode` assumption），**遗留了编排侧**；且 `docs/calculation-differences-phase1.md:66-77` 把该条登记为 D7，状态仅「已实现；待本轮测试与实机验收」。

详见 §4 的四象限实测。

### C. 平盘标阳 / 阴阳缺失 / 来源冲突 / 收盘前不可用

| 情形 | Android 实测 | 平台口径 | 结论 |
|---|---|---|---|
| 平盘 | **315 只 `first_change=0.0` 全被标「阳」**（占阳标 8.7%）；0 只标阴 | 校验直接短路：`phase4d_import_first_day_yinyang.py:92-94` 在 `pct==0` 时 `expected=None`，**不比对、原样继承工作簿标签**；`:194` 明写「未重新推算」；`db/models.py:93` 已写下风险「涨跌幅为 0 的蜡烛也可能在来源中标记阳/阴」；并被 `tests/core/test_first_day_yinyang_import.py:6-10` **固化为预期行为** | **平盘=阳 是继承来的，不是推出来的**。若 V1.3 拿它定大运方向，315 只股票的方向是数据源噪声 |
| 缺失 | 1 只「数据缺失」 | 三列 `nullable=True` 无默认、无 CHECK；`FortunePolarity` 只有 YANG/YIN，**没有 UNKNOWN/NEUTRAL**（`schemas/fortune.py:90-94`）；状态只有 `available/unavailable/conflict`（`:232`）；导入时 `flag not in {阳,阴}` **静默丢弃**（`:62`）；平台侧口径统计「阳 3630、阴 1758、缺失 716」 | 两侧**缺失计数不一致（Android 1 vs 平台 716）**，须先对齐同源；且无处安放"平盘/未知" |
| 来源冲突 | 无概念 | 单个 JSON 列 `stock_master.first_day_evidence_json`（migration `b9c7d81a6e34:24`），**只有一个登记来源** `source="user_authoritative_first_day_table"`（`:189`）→ 不是多源交叉，无优先级规则，**无人工裁定状态**（全仓 grep `adjudicat/待裁定/pending_review` 零命中） | 平台侧 15 条 conflict 只是"工作簿 vs 主档 vs 日历 vs 时段"的自洽核验 |
| 收盘前不可用 | 无概念（预置库全是历史事实，不受此约束） | `luck_cycle.py:103-111` fail-closed：`as_of < observed_at` → `UNAVAILABLE` + `Assumption(key="fortune.luck_cycle.unavailable")`；`observed_at = 观测日 15:00 Asia/Shanghai`（`exchange_sessions.py:66-67`、`market_sessions.py:19-21`）；实测 14:59 方向为 null、15:00 才得 REVERSE | **离线场景可绕**：App 只做历史/已收盘判定，不存在"当日未收盘"，但必须把 `visible_at`/`status` 如实入包，而不是把 unavailable 洗成 0 值 |
| 节气边界 | 日粒度（交节当日 00:00 换柱） | 时刻粒度 | 实测交集：**147 只上市日恰为十二节当日，其中 75 只交节时刻 ≥ 09:30**（此时两口径月柱会差一柱）；**9 只恰为立春当日，且 9/9 交节都在 09:30 之后**（17:46 / 17:03 / 22:58 / 09:30）→ 时刻粒度下年柱退回上一柱，**大运方向直接翻转**；1994-02-04 立春 09:30 **与开盘时刻同刻**，5 只（600822/600824/600825/600826/600827）需 tie-break 规则 |

### D. 时柱到底参与什么

| 逻辑 | Android | 平台 |
|---|---|---|
| 日主强弱 | **三柱**（剔时柱，有明确工程理由） | **四柱** |
| 喜用/忌神 | 无 | **四柱**（`schemas/relation.py:39 YONGSHEN_BASIS="full_four_pillars"`） |
| 格局 | 无 | **四柱** |
| 原局内部关系 | 无 | **两套**：`rules.py:570-668` 四柱全对；`date_relation.py:54 NATAL_POSITIONS=("year","month","day")` 严格 3×3 |
| 择日关系研究 | 无 | **三柱**（`date_relation_scan.py:192`「时柱退出择日关系研究范围」），且文档明写「3×3 只是研究范围，**不是**把股票八字改成三柱」 |
| 本命藏干十神（筛选） | **四柱全含**（`stock_hidden_ten_god` hour 16185 行 / 5395 股，`AnalysisRepository.kt:85-94` 详情页渲染四柱） | — |
| 大运 | 无 | 由月柱 + 年干 + 方向生成，时柱不进方向判定，但进 chart 结构 |

**关键不一致**：本项目的时支恒为巳（人造占位），平台却把它当真实支参与旺衰/喜忌/关系。若照搬平台四柱算法，等于把**一个常数注入 5395 个盘的喜用神推导**。Android 现有的「强弱剔时柱、藏干保留时柱」双标，本身就是需要写进契约的口径，不能默认平台接受。

### E. 喜用神扶抑算法的简化假设

- 纯扶抑三分支 + 中和分支（`rules.py:463-490`）：身强/偏强→用官杀、喜食伤财、忌比劫印、仇印；身弱/偏弱→用印、喜比劫、忌财官、仇食伤；**中和→用食伤、喜财、忌仇皆空**。
- 调候**会实际改写 `xi_shen`**（`rules.py:492-514`，亥子丑月补火、巳午未月补水；辰戌丑未月只出文字）。四季土月只给 note 不改五行 → 口径不闭合。
- **无候选列表、无置信度计算**：`confidence` 是从强弱档硬编码常量再减 0.08/0.12（`rules.py:513`）；`availability` 在 `bazi_engine.py:301` **被写死 `OK`**，喜用不存在 unavailable 路径 → "格局未定/置信度不足"在平台侧**无法表达**。
- **合冲不重算**：`compute_yongshen(strength)` 只吃 `StrengthResult`（`rules.py:451`），引擎顺序是「力量→旺衰→格局→喜用」**再**算关系（`bazi_engine.py:152-182`）→ 地支合冲改变力量时喜用**不更新**。V1.3 若要「刑冲合害 + 喜忌」同屏，必须自己定这条。
- 从格/专旺/病药完全未实现；「通关」只存在于 `rules.py:482` 注释。
- **平台侧阈值与分值无一处被测试断言**：`tests/engines/test_bazi_engine.py:82-83` 只断 `0<=ratio<=1` 与 `level ∈ 五枚举`；`tests/factors/test_factor_calculation.py:117` 同样。阈值唯一权威记载是文档 `docs/HANDOFF_PHASE1.md:674`。→ **不能把平台喜用当 golden 直接固化**，它自身未经断言。

---

## 4. 大运四象限验证结果（独立复现）

复现脚本 `/tmp/audit_dayun_quadrant.py`（只读；`/tmp/smp_ro_venv` 内 lunar-python 1.4.8 + pydantic）。**方向不读任何字段**，改由生成出的大运干支序列相对月柱序号的步进自证：顺排 = 月柱+1 起，逆排 = 月柱−1 起。

输入：阳年盘 1994-02-05 09:30（甲戌年 丙寅月）、阴年盘 1995-02-05 09:30（乙亥年 戊寅月）。

| 象限 | variant 入参 | `luck.direction`（字段） | **实排方向**（序列自证） | 大运序列前 3 | 首运起 | 判定 |
|---|---|---|---|---|---|---|
| 阳年 + 阳命 | FORWARD | FORWARD | FORWARD | 丁卯→戊辰→己巳 | 2003-09-05 | 一致 |
| 阳年 + 阴命 | REVERSE | REVERSE | REVERSE | 乙丑→甲子→癸亥 | 1994-06-05 | 一致 |
| **阴年 + 阳命** | FORWARD | **REVERSE** | **FORWARD** | 己卯→庚辰→辛巳 | 2004-10-05 | ★矛盾 |
| **阴年 + 阴命** | REVERSE | **FORWARD** | **REVERSE** | 丁丑→丙子→乙亥 | 1995-05-05 | ★矛盾 |

结论四条：
1. **阳年两格正确，阴年两格全反**。字段说逆、排出来是顺。
2. Golden 表已把这种不一致**写进断言**（`tests/golden/test_fortune_f2_rules.py:38-49` 的 `(阳,乙)→REVERSE+FORWARD`、`(阴,乙)→FORWARD+REVERSE`）→ 平台是"知情地保留了矛盾"，不是疏漏型崩溃。
3. **端到端没有任何测试**校验 `direction` 与 `cycle_periods` 一致（`tests/core/test_stock_fortune_engine.py:240-246` 用固定假周期，`:352` 只断 `direction is not None`）→ 修它不会立刻有测试变红，也意味着固化它会静默扩散。
4. 逐周期对象**不携带 direction**（`BaziLuckCyclePeriod` 无该字段，`bazi_engine.py:514-523`，尽管上游 `DaYunPeriod` 有）→ **Android 拿到周期后无法自检矛盾**。
5. 波及面（按 Android 现库口径统计）：**阴年且首日有涨跌标签的股票 2699 只 = 50.0%**。四象限分布：阳年+阳 1782（33.0%）/ 阳年+阴 913（16.9%）/ 阴年+阳 1848（34.3% ★排反）/ 阴年+阴 851（15.8% ★排反）/ 缺失 1。

**因此：研究平台的 `cycle_periods` / `current_cycle` / 大运干支，在阴年样本上不可作为离线固化的输入。** 离线侧要么按 `luck_cycle.py:117-123` 的 `year_is_yang == proxy_is_yang` 自行定方向再排，要么先把 `variant_mode` 改绑 `luck.direction`（REVERSE→REVERSE）。另外起运本身只精确到**时辰粒度**（`Yun.py:19-57` 的 `sect=1`，`hour_diff*10` 天，hour 恒 0），且用的是 lunar-python 内置节气表而**非本仓 CSV**、时区在 `bazi_engine.py:560-563` 被剥掉 tz —— 这三点决定了"跨仓起运对拍"必须先统一到同一份节气表。

---

## 5. 研究平台算法可复用性分级

| 等级 | 模块 | 说明 |
|---|---|---|
| **已完整实现 + 有测试断言** | 四柱/藏干内容/十神/纳音/十二长生（`tests/engines/test_bazi_engine.py:24-63`、`tests/golden/test_bazi_cross_validation.py:80-138` 与 lunar-python 对拍）；22 类 v3 关系目录命中（`tests/engines/test_date_relation_engine.py:164-189` 120×120 穷尽）；原局内部 `compute_relations`（`:145-182`）；无性别契约（`:214-289`）；起运/十年区间委托（lunar-python 1.4.8） | 可直接迁 Kotlin，对拍基线现成 |
| **已实现但无有效测试** | 五行分值与全部权重常量、五档阈值 0.62/0.55/0.45/0.38、`root_score>=1.0`、confidence 常量、喜用三分支、调候改写 `xi_shen`、`B_NATAL_016/017` | **迁移前必须自建夹具**，否则等于把未断言的规则当事实 |
| **仅候选/仅标签，不成文** | `_special_pattern_candidates` 五个组合格（`rules.py:415-429`，注释自承「不改变主格局」）；`relation_types/relation_type_counts/group`；`B_NATAL_009` 格局、`B_NATAL_010` 用神五行；`fingerprint.candidates`（无裁决消费方）；`unavailable_relations`/`GROUP_UNAVAILABLE` 恒空 | 结构可借，语义要自己补 |
| **真正形成文案** | 因子 `explanation`→`ReasonItem.text`→narrator 模板（`narrator.py:167-177,209-238`）；F4 流月 `_month_interpretations`（`stock_fortune_month_calendar.py:318-377`）；`build_day_stem_verdict.reason`；`_annotate_temporal` 短 note | 全是 **f-string 动态拼 + 单句**，不是条库；无「格局+用神」整段解读 |
| **明确不存在** | 从格/专旺/病药、拱/夹拱、藏干参与刑冲合会、相邻限制口径、化神成立口径、刑的传统子类名、`TEMPORAL_TO_TEMPORAL`（岁运互作用，只有枚举无构造点）、关系级分值/polarity、unverified/low-confidence 分支 | V1.3 若需要，只能自研 |
| **有实现但有缺陷** | 大运方向（§4）；平盘继承（§3-C）；喜用 `availability` 写死 OK；引擎版本断裂（1.0.1 证据 vs 1.0.2 逻辑） | **禁止原样固化** |

---

## 6. 离线融合设计：方案 A vs 方案 B

### 方案 A：Android 端 Kotlin 全量重算
把 `date_relation.py` + `rules.py` + `luck_cycle.py` + lunar-python 的起运/大运逻辑全部移植进 Kotlin。
- 优点：库不变、无新表；口径单一来源。
- 致命代价：**必须自带一份精确到分钟的节气表**（起运要"距上一/下一节的真实时刻"），1990–2035 约 46 年 × 24 节气 = 1104 条，且要与 lunar-python 1.4.8 逐条一致；起运换算含 `sect=1`、时辰粒度、时区剥离三处隐性行为，移植后**无法证明等价**；`build_luck_cycle_periods` 依赖第三方排运器（平台自己不重算是刻意的，见 `bazi_engine.py:488-490`）。
- 结论：可验证性最差，风险最高。

### 方案 B（推荐）：构建期预计算稳定原局 + 大运，打包 SQLite；运行时只算流年流月互动与判词匹配
依据：本项目**已经在做这件事** —— 四柱、藏干十神、纳音、强弱全部构建期算好入库，运行时只算十神/财星/文案（`build_all.sh:60-66` 的顺序硬约束、`build_database.py:163-179` 用 Room 自己的 createSql 重建库）。V1.3 只是把"稳定的东西"再多算几列。

**分层**：
- **稳定（随上市日一次定死，构建期算）**：大运方向、起运日期、12 个大运区间、原局内部互动、喜用/忌神（若采纳）、格局。
- **半稳定（随流年/流月定死，可预计算也可运行时）**：流年流月与原局的关系事件。
- **运行时**：流日、交易日过滤、财星判定、判词匹配（保持 V1.2 现状）。

**数据表变更建议**（Room `@Entity` + DAO 必须同步，否则 `verify_database.py:289-290` C20「无 Room 未知的多余表」直接红）：

| 表 | 粒度 | 列（要点） | 行数 | 估算体积 |
|---|---|---|---|---|
| `stock_luck_cycle` | 每股 | `stock_id, direction, start_at, start_age, rule_version, provenance, conflict_flag` | 5395 | ≈ 0.35 MiB |
| `luck_cycle_period` | 每股 × 12 | `stock_id, cycle_index, ganzhi, start_at, end_at, start_year, end_year, start_age, end_age` | 64740 | ≈ 3.2 MiB（含索引 ≈ 5 MiB） |
| `natal_relation` | 每**唯一四柱** | `chart_key, relation_type, positions, source_pillars, basis` | 约 8000–12000 | ≈ 0.6 MiB |
| `stock_yongshen`（P3 可选） | 每唯一四柱 | `chart_key, method, yong_shen, xi_shen, ji_shen, chou_shen, level, confidence, availability, rule_version` | 2776 | ≈ 0.2 MiB |
| `flow_relation`（**建议不建表**） | — | 运行时按「唯一四柱 2776 × 60 流月 × 60 流年」现算，比物化便宜且避免 `as_of` 爆炸 | — | 0 |

去重收益实测：`distinct(year_pillar||month_pillar||day_pillar||hour_pillar) = 2776`，与 `distinct(listing_date) = 2776` 相等 → **原局类事实按 2776 张唯一盘存，不按 5395 只股存**（体积与"同盘不同判"的争议都减半）。

**总开销**：库从 8.23 MiB 增至约 **13–15 MiB**（+60%）；APK 现有约束 <25MB（`PHASE0 §10 Phase2`），仍有余量，但需要实测确认。

**版本管理**：
- 新增 `dayun_rule_version`、`relation_rule_version` 两列 + `app_meta.rule_version` 升级为 `bazi-rule-v1.3`（保持单值可 grep）。
- **每一行都自带 `rule_version` + `provenance` + `confidence`/`availability`**，不允许"整库一个版本"就完事 —— 平台已出现「1.0.1 产出的证据 vs 1.0.2 的方向逻辑」断裂（`STOCK-MONTHLY-CALENDAR-POLARITY-ACCEPTANCE-2026-09-28.md:38`），行级版本是唯一能追溯的形态。
- 构建期用 `parity/` 夹具做**跨仓对拍**（现有 parity 是 Android 自己的 `bazi_core.py` 镜像生成的，**不是**跟研究平台对拍，见 `gen_parity_fixtures.py:1-8` —— 这是必须补的一块）。
- 升级链路现成：改 `SCHEMA_VERSION` 或 identity → `AppDatabase.kt:80-99` 自动「备份 favorite → 删本地库 → Room 重抄 → 回写收藏」，**不需要写 Room migration**。这是选方案 B 最实际的理由。
- 严禁运行时调用 Python/网络：现有实现本就没有任何网络权限（`PHASE0 §10 Phase1`），保持。

**测试设计**（P1 只给清单，不写测试）：
1. 四象限方向夹具（含 §3-C 的 9 只立春盘 + 5 只 09:30 同刻盘），断言「字段方向 == 序列步进方向」——**这条测试在平台侧不存在**。
2. 起运对拍：Android 构建期 vs lunar-python 1.4.8，逐股 `start_at` 差 ≤ 1 日，超差即列出清单人工裁定。
3. 强弱/喜用对拍：两侧口径各存一份（`strength_axe`、`yongshen_basis`），断言界面只读被选定的那一根轴。
4. 关系目录对拍：22 类目录穷尽（借平台 `test_date_relation_engine.py:164-189` 的 120×120 思路）。
5. 合规：新文案表纳入 `ComplianceTextTest`（详见 §7）。
6. 库-码一致：C17–C20 + `AssetParityTest` 现有门禁继续跑。

---

## 7. 文案库衔接

**「800 条」的真实身份（已实测，本体不在仓库内）**：
- 脚本 `tools/{build_advice,build_fragment,extract_advice}_*.py` 的**输出**：`StockFortuneAndroid/release/操作建议核对表.xlsx`（65238B，10-08 20:00）与 `判词片段库_审阅稿.xlsx`（35441B，10-08 21:57）。`.gitignore:22-23` 明确不入库，`DELIVERY_REPORT.md:320` 记为"本体按既定口径排除在公开仓库外"。
- 直解 xlsx XML 实测行数：操作建议核对表 781 行（含表头）→ 数据 **765 条**，即"约 800"；判词片段库 427 行 → **89 片段 + 300 组合枚举 + 8 待决 + 7 校验 + 5 示例**。
- **外部规则库本体（`<规则库.xlsx>`）在两仓库内都不存在**（三份脚本一律从 `argv[1]` 收路径，无硬编码；唯一命中规则表名 `23_十神喜忌组合` 的地方是 `extract_advice_review.py` 自己）。→ 这 375 条**当前不可再生**，只能从已产出的核对表反向取用。

**分层（已在工作簿里成型的结构，直接沿用）**：

| 层 | 条数 | 状态 | 键 |
|---|---|---|---|
| 位置层（财星在哪：透干/藏支/无财） | 3 | 现状·已在代码 | 财星三分支 |
| 主题层（明线 = 月/年干十神 × 强弱） | 30 | 现状·已在代码 | `Pair<TenGod,Strength>` |
| **暗线层（月支本气十神 × 强弱）** | 30 | 新增提案 | 同键，换支轴 |
| 操作层·明线 / 暗线（十神→查什么） | 10 + 10 | 新增提案 | `TenGod` |
| 总纲层（强弱结论） | 3 | 新增提案 | `Strength` |
| 拼接规则（干支两线：同神 / 同党不同神 / 异党） | 3 | 新增提案 | 结构条件 |
| 大运层 | **0** | **缺** | 需新建 |
| 喜忌层 | 209 条在 `05_规则库_缺引擎` | **缺引擎** | 需 P3 |
| 刑冲合害/制化层 | 在 `24_干支关系动作`（22 条）+ `26_复杂叠加行动`（45 条）里 | 部分可落地 | 需 P4 |
| 冲突总结（一帮一耗怎么收口） | 拼接规则 3 条 + `12_组合冲突规则`（34 条） | 已有倾向 | 需 P4 |

**明确不做的**（工作簿自己的待决清单里也反对穷举，Q-01/Q-03）：暗线若纳入中气余气，组合数 300→约 1800；流日接暗线是 60×10×3 = 1800 格。**统一口径：只按"层 + 结构条件"存片段，运行时拼接，不做任何笛卡尔积物化。**

**最小必要增补清单（P2→P4 总量 82 条，非 800 条）**：

| 批次 | 内容 | 条数 | 触发格式 |
|---|---|---|---|
| P2 | 大运层片段：**大运十神(10) × 强弱(3) = 30 条**；再加方向句 2 条（顺/逆各一）、起运说明 1 条 | 33 | `dayun.stem_god == 'X' AND natal.strength == 'Y'` → 子句；`dayun.direction` → 方向句 |
| P2 | 冲突总结 | 3 | 复用拼接规则 3 条（同神/同党不同神/异党），扩到大运 vs 原局 |
| P3 | 喜忌层（**改名后的**喜/忌标签句） | 10 | `flow.stem_wuxing_role in {用,喜,忌,仇,闲} × {干,支}` → 半句，接在 30 格之后 |
| P3 | 格局 | 10 | `pattern.primary` → 一句 |
| P4 | 刑冲合害结构句 | 22（取 `24_干支关系动作`） | `relation.type in {六合,六冲,三合,半合,三会,相刑,三刑,自刑,相害,六破,天干五合,天干相冲,伏吟,反吟,天合地合,天克地冲}` |
| P4 | 制化/叠加 | 4（从 `26_复杂叠加行动` 45 条里挑真正会同时出现的） | `relation.type AND dayun.stem_god AND strength` 三元，**只列实际共现的组合，不穷举** |

**条件触发格式（建议与现有 `PLAIN` 同构，fail-fast 不兜底）**：
```
key   = (维度, 取值, Strength?)        // 例 (DAYUN_GOD, 七杀, STRONG)
text  = 片段（禁词已过滤，无指令句）
guard = 缺 key → error("…必须写全")   // 沿用 FortuneText.kt:109-110 的做法
```
`monthSummary` 之类拼句函数改为 `listOf(位置句, 明线句, 暗线句, 大运句?, 喜忌句?, 关系句?).joinToString("；")`，**空维度就整段不出现**，而不是塞兜底句。

**合规衔接（不删禁词、不收窄门禁）**：
- 实测撞词（尺子 = `ComplianceTextTest.kt:26-35,51-55` 全 60 词，仅按「建议原文」列复算，与工作簿自带 `撞禁词` 列**逐条完全吻合**）：
  - `03_可落地` 122 条 → 撞 23（18%）：优先 10、忌 7、收益 4、机会 4、大额 1
  - `04_需场景开关` 44 → 撞 16（36%）：优先 6、避免 5、机会 4、适合 1
  - `05_缺引擎` 209 → 撞 111（53%）：**忌 60**、优先 25、避免 8、收益 22、勿/适合/大额/规避 各 1
  - 合计 375 条 → **150 条（40%）撞词**；**89 条片段仅 1 条撞词**（操作层暗线含「收益」）。
- **最大的词不是脏词，是术语**：60 条含「忌」，全部来自喜神/**忌神**/仇神这套术语本身。而 `advisoryBanned` 里的单字「忌」是有意收的（它挡的是「忌合伙大额投资」这类指令句）。→ **不能为了放行而把「忌」从禁词表删掉**。可执行的两条路：① 界面措辞改用不带「忌」的等价标签（如「所违之神」「不宜之神」也都被别的词挡着，建议用自造中性名「耗身之神/违逆之神」并保留 `ji_shen` 作数据字段名）；② 数据层保留 `忌神` 枚举，**渲染层过一道 label 映射**，文案仍受全 60 词约束。推荐 ②，因为门禁代码一行都不用改。
- **Q-07 那条"把门禁范围收窄到只扫古籍引文"的倾向不予采纳**（它正好违反你「不能直接删禁词测试来让外部文案通过」的要求）。
- **必须补的门禁漏洞（实测）**：现有 7 个测试**扫不到新表/新 asset** —— UI 字面量扫描只走 `ui/`（`ComplianceTextTest.kt:182-214`），没有任何测试读 `assets/databases/*.db`，`AssetParityTest` 只比哈希。外部素材一旦以新表进来，**一条门禁都碰不到它**。唯一先例是古籍：靠 `ComplianceTextTest.kt:107` 点名 `ASSET_PATH` 才被纳入。→ 新增判词表必须同样**点名纳入**，并加一条"全表扫描"门禁（对 `advice_fragment` / `dayun_text` 等新表 `SELECT text` 全行跑 60 词）。
- 另需补：`yearAdvice`/`yearIndustryNote`/`wealthBasis` 三个函数目前**不在 `assertNoAdvice` 覆盖内**（`:142-154` 只跑 monthSummary/yearWealthSummary），V1.3 扩轴时一并补齐。
- 出处标注沿用现口径：自写片段一律「本项目概述」不挂书名（`strings.xml:50,127,128,136`；`FortuneText.kt:43-44`）；权重/阈值类工程取值同口径（`TenGodCalculator.kt:143-151`）。引文层与判词层继续**不混排**（`QuoteBlock` `Tabs.kt:198-234`）。

---

## 8. P2 / P3 / P4 实施顺序、工作量、测试门槛

**排期原则：先做"能自证正确"的，再做"依赖裁定"的；把两个已知缺陷（方向排反、平盘继承）挡在 P2 之外。**

### P2 — 大运接入（可独立交付，不含喜忌）
| 步骤 | 内容 | 工作量 |
|---|---|---|
| 2.1 | **前置裁定**：方向按 `year_is_yang == proxy_is_yang` 自定；平盘 315 只如何处置（见 §9 D1/D2） | 0.5 d（等你确认） |
| 2.2 | 构建期排运器：`tools/dayun_build.py`（节气表 + 起运换算 + 12 区间），**逐股与 lunar-python 1.4.8 对拍** | 2–3 d |
| 2.3 | 新表 `stock_luck_cycle` + `luck_cycle_period`，`chart_key` 侧另立去重表 | 1 d |
| 2.4 | Room 实体/DAO + `AnalysisRepository` 增 `currentDayun(stockId, date)`；`AppDatabase` SCHEMA_VERSION 递增 | 1 d |
| 2.5 | 年度页/详情页加「当前大运」区块（含起运岁数、区间、干支、口径免责） | 1.5 d |
| 2.6 | 大运层文案 33 条 + 冲突总结 3 条 + `PLAIN` 同构的 fail-fast 键 | 1 d |
| 2.7 | 门禁：四象限夹具、起运对拍、全表禁词扫描、`ComplianceTextTest` 扩到新表 | 1.5 d |
| — | **合计 8.5 人日**（不含裁定等待） | |

**P2 测试门槛（不过就不进 P3）**：
- G1 四象限：4/4 象限 `direction 字段 == 序列步进方向`，含 9 只立春盘；`error()` 不允许出现。
- G2 起运：5395 只 vs lunar-python，`|Δstart_at| ≤ 1 日` 100%，超出者逐条列清单人工署名。
- G3 覆盖：`start_at ≤ 2035-12-31` 内每只股票至少 1 个大运区间可命中；无覆盖时显示"未起运"而非空值。
- G4 分布：大运十神 10 档占比不得有 0 档（否则 33 条里有永远不触发的格子）。
- G5 合规：新表全行跑 60 词，offenders = 0；68 个既有用例继续全绿。
- G6 体积：APK < 25 MB、库 < 16 MB。
- G7 真机：按你的验收习惯点一遍（切年份到 1990 与 2035 边界、切到平盘股、切到缺失那 1 只、看免责条与口径页）。

### P3 — 喜用神 / 格局（**强依赖 P2 之后的裁定，风险最高**）
| 步骤 | 内容 | 工作量 |
|---|---|---|
| 3.1 | **先定口径**：沿用 Android 六字，还是改用平台四柱比例制（§3-A：两者对 38.6% 的股票说法相反，不能并存） | 0.5 d |
| 3.2 | 把平台 `compute_wuxing_scores/compute_strength/compute_pattern/compute_yongshen` 迁 Kotlin，**并为权重/阈值/切分点补自建夹具**（平台侧一个断言都没有） | 3 d |
| 3.3 | 喜用标签渲染改名（规避「忌」，数据层保留 `ji_shen`）+ `availability/confidence` 真实化（现被写死 OK） | 1.5 d |
| 3.4 | 新表 `stock_yongshen`（按 2776 唯一盘）+ 年度页喜忌句 | 1.5 d |
| 3.5 | 文案 20 条（喜忌 10 + 格局 10）+ 门禁扩展 | 1 d |
| — | **合计 8 人日** | |

**P3 门槛**：G8 阈值脆弱度报告（±0.01 内 1037 只必须给出处置口径，否则任何权重微调都会静默改判 19% 用户看到的旺衰）；G9 「格局未定」必须有可见的 unavailable 态，不得渲染成空串或默认「比肩」；G10 从格/专旺不实现这条**必须写进口径页**（当前实测 0 只极端盘，但要说明这是数据集巧合）。

### P4 — 原局互动 + 流年流月关系（体量大但可分片）
| 步骤 | 内容 | 工作量 |
|---|---|---|
| 4.1 | 关系目录迁 Kotlin（22 类，**先只取有测试的那一套**：v3 矩阵）；定死"几柱"（三柱 3×3 还是四柱全对，§3-D） | 2.5 d |
| 4.2 | `natal_relation` 表构建期预计算（2776 盘） | 1 d |
| 4.3 | 运行时流年/流月 × 原局事件匹配（不建表） | 2 d |
| 4.4 | 关系分值/polarity **必须自研**（平台契约就是 None，且被测试锁死） | 2 d |
| 4.5 | 文案 26 条（关系 22 + 制化 4）+ 门禁 | 1.5 d |
| 4.6 | 与 P2 大运 × 原局的关系（`TEMPORAL_TO_TEMPORAL` 平台没有，需自定） | 1.5 d |
| — | **合计 10.5 人日** | |

**P4 门槛**：G11 关系命中目录穷尽（借 120×120 思路）；G12 分值必须可解释（每个分值有 basis 句子）；G13 与 P2/P3 三轴同屏时不得出现"同盘不同判"。

**总计 27 人日**（P2 8.5 / P3 8 / P4 10.5），不含真机验收与 PR 往返。

---

## 9. 需要你先裁定的决策点（P2 开工前）

| # | 决策 | 选项 | 我的倾向 |
|---|---|---|---|
| D1 | 大运方向 | ① 沿用平台 `variant_mode=首日阴阳`（= 阴年 50% 排反）② 按 `year_is_yang == proxy_is_yang` 自定 ③ 不做大运 | **②**。①是把已知缺陷固化进包 |
| D2 | 平盘 315 只（`first_change=0` 却标阳） | ① 照旧继承 ② 单列「平」档、不给方向 ③ 剔除大运 | **②**。①的"阳"没有来源支撑，③太可惜 |
| D3 | 阴阳缺失 | Android 1 只 vs 平台口径 716 只，先对齐同源再定 | 先对齐；缺失保持 unavailable，不兜底 |
| D4 | 立春/交节当日盘 | 9 只（含 5 只 09:30 同刻）按日粒度还是时刻粒度；tie-break 怎么写 | 沿用日粒度（与数据源逐字一致），并在口径页点名这 9 只 |
| D5 | 强弱轴 | 六字绝对分 vs 四柱比例制（38.6% 不一致） | **保六字**（时柱恒为巳的论证成立，且 14 倍偏置证据在库内可复算）；喜用若要用四柱，必须先解决与强弱轴同源 |
| D6 | 时柱参与范围 | 强弱剔、藏干留、喜忌/关系要不要留 | 需与 D5 一起定；不一致就是 §3-D 的现状 |
| D7 | 「忌神」措辞 | ① 删禁词里的「忌」 ② 渲染层改名、数据层保留 | **②**。①违反你的红线 |
| D8 | 800 条本体在 public 仓外（待决 Q-08） | 转私有 / 只本地不入库 / 照旧 | 照旧（片段走 assets 但**不推外部规则库原文**） |
| D9 | 平台喜用能否当 golden 直接对拍 | 平台阈值/分值**零断言**、confidence 硬编码、`availability` 写死 | 不能当 golden，只能当"参考实现"，Android 自建夹具 |

---

## 10. 完成度声明（本轮没做到什么）

- **做到了**：调用链逐环节核对（含每个字段是否真被用）；两仓算法差异 18 项清单；A–E 五项冲突全部有实测数字（不是纸面推演）；四象限**独立复现**且方向自证；平盘 315 / 阴年 2699 / 立春 9 / 交节 75 等边界集已定位到具体股票与代码行；800 条素材的真实身份、条数、撞词分布**逐条复算并与工作簿自带列对表一致**；方案 A/B 含体积估算与升级链路结论。
- **没做（有意或受限）**：① 未审 `lucky_cycle` 起运与 lunar-python 的**逐股**对拍差异清单（P2 2.2 才做，需先定 D1/D2）；② 未跑 Android instrumentation/真机（本轮无 UI 改动）；③ 未读研究平台的紫微/黄历侧（与 V1.3 无关）；④ `<规则库.xlsx>` 本体不在仓库，375 条句子**只能从核对表反取，不可再生** —— 若你要改这些句子，需要你把工作簿补传回来；⑤ 因子层 `B_NATAL_016/017`、`_strength_to_normalized` 这类"分值→tanh→10 分"的换算未逐条核（V1.3 不做因子，不做涨跌）；⑥ 未评估 ProGuard/R8 对新增反射门禁的影响。
- 本轮**零代码改动**：未动 `app/src/**`、未动 `tools/**`、未建表、未改判词、未开 PR。两份只读复现脚本写在 `/tmp`（`audit_strength_parity.py`、`audit_dayun_quadrant.py`、`audit_copy_gate.py`），仓库工作树保持你原有的三个未跟踪脚本不变。
