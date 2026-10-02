# 《滴天髓輯要》引文接入 v1 —— 交付报告

分支：`codex/classics-quotes-v1`（自 `feature/ui-visual-polish` 建立）
日期：2026-09-30
产物：`app/src/main/assets/classics/ditiansui_jiyao.json`（20 条）、debug APK、
      `docs/CLASSICS_AUDIT_ditiansui_v1.md`（选段审计报告）、`release/verification/classics_v1/`（验收截图，不入库）

---

## 一、本次实际做完的事

| 范围 | 落点 |
|---|---|
| 语料抽取（20 条 = 十干 × 歌诀 1 + 原注节选 1） | `tools/classics_selection.py`（人工审定的固定选段清单）+ `tools/extract_classics.py` |
| 语料门禁 | `tools/verify_classics.py`（24 项；带 `--against-source` 时 27 项） |
| 抽取行为测试 | `tools/tests/test_classics_extract.py`（21 项，合成底本，clone 后可跑） |
| 只读引文仓库 | `data/repository/ClassicQuoteRepository.kt`，IO 线程首读 + 进程内缓存 + 三态返回 |
| 接线 | `AppContainer` 注入 → `StockRepository.detail()` 按数据库日干取引文 → 随 `StockDetail` 传递 |
| 展示 | 基本信息页概览下方新增「典籍依据」卡（`ui/detail/Tabs.kt`） |
| 文案校正 | `FortuneText.FATE_FEATURE` 十条重写；算法口径页新增「三·六、典籍引文口径」 |
| 门禁扩展 | `ComplianceTextTest` 扩扫 20 条引文 + 十条概览 + 新增界面文案，禁词补繁体对应形式 |
| 构建脚本 | `tools/build_all.sh` 新增第 [5/8] 步语料门禁（不访问外部书籍目录） |

数据库无加表、无迁移；APK 内 `assets/classics/ditiansui_jiyao.json` 已确认打进包（9493 字节）。

---

## 二、通过的验证项（逐条实测，非推断）

### 自动化

| 命令 | 结果 |
|---|---|
| `python3 tools/verify_classics.py` | 28 项通过（默认门禁，不需要外部书籍目录） |
| `python3 tools/verify_classics.py --against-source` | 31 项通过（含源文件 SHA-256 未变、重新抽取结果与资产逐字节一致） |
| `python3 -m unittest discover -s tools/tests -p 'test_classics*.py'` | 58 项通过（抽取行为 33 + 门禁反例 25，合成底本，clone 后可跑） |
| `python3 tools/verify_database.py` | 31 项通过 |
| `./gradlew testDebugUnitTest` | 56 项 0 失败 0 跳过（9 个测试类） |
| `./gradlew lintDebug lintRelease` | 0 Error；20 Warning（全部为既有项：15 个 GradleDependency、1 个 UnusedResources `rank_nth` 等，本次未新增） |
| `./gradlew assembleDebug` | BUILD SUCCESSFUL |

### 模拟器（sf_avd / Android 15 / 1080×2400）

* **覆盖安装**（`adb install -r`，不卸载、保留旧库与旧收藏）后冷启动无崩溃，logcat 无 FATAL。
* **十干样本逐一走查**（首页 → 点「股票查询」文字 → 输入代码 → 点结果行的股票名称 → 滚到「典籍依据」）：
  甲000012 / 乙000011 / 丙000009 / 丁000027 / 戊000037 / 己000002 / 庚000004 / 辛000010 / 壬000016 / 癸000001
  每干校验：歌诀全文、原注节选全文、两条出处行（`篇名 天干論 · 小节 X · 扫描页码 N`）、
  书名+版本行、卡片副标题的日主、免责行、以及**不串入其他日主的歌诀** —— 全部通过。
* 详情四个标签页（基本信息 / 年度运势 / 月度运势 / 每日分析）均正常渲染；
  切走再切回「基本信息」，典籍依据卡与出处行不丢（ViewModel 未重复取数）。
* 「我的 → 算法口径说明」含新增的「三·六、典籍引文口径」，可检索到 `dtjy-v1`、扫描页码、印刷叶码、
  保持繁体原貌、日主匹配边界、「本项目概述」六处关键表述。
* 收藏：详情页点星 → 运行时库 `favorite` 表落库（`000037.SZ|深南电A`）→ 我的页收藏列表可见。
* 断网（关 wifi + 关 data）后 force-stop 重进：首页正常，引文仍可读（纯离线资产）。
* 系统字体放大到 1.6x：典籍依据卡仍在，原注节选与出处行完整换行、无省略号截断、无横向溢出。

---

## 三、未验证 / 有保留的部分（不要把上面当成全绿）

1. **只在模拟器验过，未上真机。** 系统栏、手势区、厂商字体缩放等真机变量本次没覆盖。
2. **release 包未出。** 本次计划只要求 debug；`assembleRelease` 与签名校验未跑（`build_all.sh` 里会跑）。
3. **扫描页比对的粒度有限。** 原始 OCR（`_ocr_stage/page_00NN.json`）竖排错字率约五成，
   不能作独立核对源，实际改用 PDF 渲染图逐段放大比对。歌诀与原注首句是逐字核过的，
   但**没有对 64 页全表做穷尽比对**。F1 的教训正在这里：己土原注跨页的结论当时已经写进
   审计备注，却没有回流到数据里——靠人记是记不住的，现在由 `SCAN_PAGE_EVIDENCE`
   与门禁 C25/C26 强制约束。
4. **异体字未回改，只记录。** 刊本作 溼/强/叠/眞、精校版作 濕/強/疊/真 的 4 处，
   以及刊本 己/已/巳 形近，按用户裁定沿用精校版字形，逐条见审计报告第四节。
5. **底本回改只有 1 处。** 癸水歌诀「合戊見火」→「合戊化火」（用户裁定底本优先），
   在 `SCAN_RESTORE` 里显式登记、由门禁校验，不是静默改字；除此之外全部逐字照录精校版。
6. **免责行的展示条件。** 「以下为传统文献原文…」只在有引文时出现；
   无匹配 / 加载失败两种状态下卡片改显对应提示，未强行显示这句指向空内容的声明。
7. **`tools/classics_selection.py` 里的底本路径是本机的。** 已做成 `SF_CLASSICS_BOOK_DIR` 可覆盖，
   但 clone 到别的机器上**无法重新抽取**（只能跑门禁与测试）。这与 `build_database.py` 依赖
   素材包是同一类已知限制。

---

## 三·五、独立验收发现项与处置（2026-09-30 第二轮）

外部独立验收查出三项问题，逐条复核后**三项均成立**，已全部修正并复验：

| 编号 | 问题 | 复核结论 | 处置 |
|---|---|---|---|
| F1 | 己土原注展示页码记为 11（实际 12）、辛金原注记为 12（实际 13） | 成立。重看扫描页 12／13，两页最右列页首正是「已爲田園之土…」「辛乃陰金…」 | 选段清单把页码拆成**定位页**（Markdown/JSONL 小节起始页，用于切片对账）与**展示扫描页码**（引文首字实际所在页）；资产、UI、审计报告统一用后者。跨页必须登记 `SCAN_PAGE_EVIDENCE`，否则抽取即失败。 |
| F2 | 登记过底本回改的条目绕过门禁全文比对，整段伪造仍能过 | 成立。旧逻辑 `if text != expected and not restored` 里的 `not restored` 就是漏口 | 抽出 `sel.expected_text()` 供抽取器与门禁共用，回改只能执行登记的那一次替换，全文比对不再有任何豁免。手工复现验收用的伪造串：退出码 1、C23 FAIL。 |
| 附加 | `_meta.schema_version` 改成 999 仍通过（C01 只查键存在） | 成立 | 新增 C01b 断言版本值等于当前 SCHEMA_VERSION。 |

根因值得记一笔：**MD 与 JSONL 互相自洽、重抽结果一致，都证明不了页码就是引文在 PDF 里的位置**——因为两者用的是同一套"小节标题所在页"。我自己在审计报告里已经写下"己土原注起于扫描页 12"，却没据此改数据，等于把发现记成了备注。现在这条约束由门禁强制，不再依赖人记得去对。

新增反例测试 `tools/tests/test_classics_verify.py`（25 项）：正确回改通过；插入伪造文字、加字、删句、把回改字改回对校本读法、页码退回定位页、schema 版本为 999 或缺失、原文塞回校记，全部必须失败。

修正后复验：语料门禁 31 项、Python 测试 46 项、数据库 31 项、Android 单测 56 项全绿；模拟器实测己土显示「歌诀 11 / 原注节选 12」、辛金「12 / 13」、戊土两条同页 11。

## 三·六、第三轮独立验收发现项与处置（2026-09-30）

四项 P2，逐条复核后**均成立**，已全部修正：

| 编号 | 问题 | 复核 | 处置 |
|---|---|---|---|
| P2-1 | 底本出现同名小节时 `build_corpus` 的字典推导会静默取最后一份；若被盖掉的那份带【存疑】，存疑拒绝就被绕过 | 成立 | `split_sections` 检出重复小节即报错；新增 `_reject_duplicates()` 在抽取前查清单 (小节,类型) 重复、同一天干多小节、JSONL 重复切片编号 |
| P2-2 | 门禁只比文本与页码，`chapter` / `section` / `book_id` / `day_stem` / 书目字段 / `_meta.source_file` 都不对账 | 成立 | C23 扩成逐字段对账，并新增 C24b 核 `books[]` 与 `sel.BOOK`、`_meta` 与清单。**回改查表改用清单侧小节名**，否则改 `section` 就能给别的条目发豁免 |
| P2-3 | 审计报告把校记截到 60 字 | 成立。校记是这批唯一的审计线索，截断等于销毁证据 | 表格只标条数，新增第三节逐条全文收录校记与回改，章节整体重新编号 |
| P2-4 | 「三·六、典籍引文口径」六行硬编码在 Composable 里，`ComplianceTextTest` 扫不到 | 成立，且直接违反本计划「新增界面文案统一进入字符串资源」 | 六行迁入 `strings.xml`（`doc_classics_*`），并入合规资源扫描列表 |

新增反例测试 12 项（Python 侧合计 58 项）：重复小节 / 重复小节掩盖存疑 / 清单重复登记 / 同干多小节 / JSONL 重复切片；改 section / chapter / book_id / day_stem / 书目版本 / 源文件名 / 只改 section 换预期文本 —— 全部必须失败。

### 关于组合构建的一条 lint 崩溃（非代码缺陷，但会挡验收命令）

`./gradlew testDebugUnitTest lintDebug lintRelease assembleDebug` 配 `--rerun-tasks` 在本分支稳定复现
`lintAnalyzeDebugUnitTest` 内部崩溃：`Error while resolving FirRegularClassImpl from RAW_FIR to SUPER_TYPES`，
报在 `BaziCoreTest.kt`。二分结论：

* base 提交（全新 worktree）跑同一条命令**通过**；
* 把本次新增的两个测试文件移走**仍然崩** —— 不是新测试引起的；
* `./gradlew clean` + 删 `.kotlin/` 后，本分支跑同一条命令**通过**。

所以根因是工作树里 Kotlin 分析会话缓存陈旧，不是交付代码的缺陷。但 `tools/build_all.sh` 不做 clean，
带陈旧状态的机器上会撞上，处置办法是先 `./gradlew clean`（或删 `.kotlin/`）。已记在此处，未改构建脚本。

### 同类漏项（本次范围外，需另立任务）

`AlgorithmDocScreen` 里**既有**的五张 DocCard（数据口径 / 十神规则 / 财星判定 / 月度口径 / 交易日历 / 能力边界）
同样是硬编码字符串，不在合规资源扫描覆盖内。本次只按计划把**新增**的引文口径六行入了资源，
其余留作已知缺口，未擅自扩大改动面。

## 三·七、第四轮独立验收发现项与处置（2026-10-01）

| 项 | 复核 | 处置 |
|---|---|---|
| P2：同一段内重复出现的选段仍被接受 | 成立。上一轮加的 `len(matches) > 1` 数的是**命中几个段落**，同一段里重复两遍时段落数仍是 1 | 改为数**出现次数**：`sum(n.count(excerpt) for n in hits)` 必须恰为 1，报错信息同时给出次数与跨段数。新增反例 `test_同一段内重复两遍也要报错` |
| Android 单测 54 通过 / 2 失败（跨月失效） | 成立，且**不是本次改动引起**：`DetailViewModelTest` 与 `TabContentRenderTest` 用 `LocalDate.now()` 取"当月"，却断言写死在该月的内容（21 个交易日、`2026-09-01` 那一行）。9 月 30 日全绿、10 月 1 日即红 | 两处都固定到 2026-09，并补断言 `yearValue` / `monthValue` 与输入一致，使"输入"与"断言"锁在同一个月 |

顺手排查了同类残留：`ScreenRenderTest` 也用了 `YearMonth.now()`，但它的断言全部从 `now` 推导
（`label(now)`、`prev = now.minusMonths(1)`、`next = now.plusMonths(1)`），自洽，跨月不会漂，**未改动**。

本轮复验：语料门禁 31 项、Python 测试 59 项、数据库 31 项、Android 单测 56 项全绿；
lint 各 0 Error / 20 Warning；clean 后 `--rerun-tasks --no-parallel --no-build-cache` 组合命令通过。

---


## 四、明确留在范围外的

按计划本次不含：三命通会语料加工、流月短句、古籍全文搜索、AI 解读、任何新增命理计算。

另外这些是**已知缺口**，不是本次漏做：APP 侧仍无十二长生 / 刑冲合会 / 调候 / 强弱 / 格局 / 神煞表，
所以歌诀里的「春不容金」「抱乙而孝」「合戊化火」等条件本系统算不出来 —— 这正是
「算法口径说明」里新增那条边界声明的由来，也是引文只能按日主归类、不能按命局匹配的原因。

---

## 五、改动清单

新增
```
tools/classics_selection.py            固定选段清单 + 底本回改登记 + 页码依据 + 扫描页对照结论
tools/extract_classics.py              契约式抽取器（含重复定位拒绝）+ 审计报告生成
tools/verify_classics.py               语料门禁（默认 28 项 / 带底本 31 项）
tools/tests/test_classics_extract.py   抽取行为测试（33 项，含重复定位反例）
tools/tests/test_classics_verify.py    门禁反例测试（25 项，含出处字段篡改）
app/src/main/assets/classics/ditiansui_jiyao.json
app/src/main/java/.../data/repository/ClassicQuoteRepository.kt
app/src/test/java/.../ClassicsCorpusTest.kt
app/src/test/java/.../ClassicsRenderTest.kt
docs/CLASSICS_AUDIT_ditiansui_v1.md
docs/CLASSICS_DELIVERY_ditiansui_v1.md
```

修改
```
AppContainer.kt                 注入 classicQuoteRepository
StockRepository.kt              StockDetail 增 classics 字段；detail() 按日干取引文
ui/detail/Tabs.kt               概览标「本项目概述」；新增 ClassicsCard / QuoteBlock / StateLine
FortuneText.kt                  十条日主概览改为逐条落在选定原注上
ui/profile/AlgorithmDocScreen   新增「三·六、典籍引文口径」
res/values/strings.xml          新增 14 条界面文案（删除已失效的 field_fate_feature）
ComplianceTextTest.kt           禁词补繁体形式；扩扫引文 / 概览 / 新文案
RuntimeSmokeTest.kt             构造 StockRepository 增引文仓库参数
tools/build_all.sh              新增第 [5/8] 步语料门禁，步骤重编号为 /8
```
