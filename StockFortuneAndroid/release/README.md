# 发布与安装说明 · 股运通 v1.0.0

## 一、产物

| 文件 | 说明 | 大小 |
|---|---|---|
| `stock-fortune-release.apk` | **推荐安装**。R8 混淆 + 资源压缩 + 本地自签名（v2 签名方案） | 约 12 MB |
| `stock-fortune-debug.apk` | 调试包，含 Compose 工具与可调试标志，体积较大 | 约 38 MB |

包名 `com.stockfortune.app`（debug 包为 `com.stockfortune.app.debug`，两者可共存）。
`minSdk 26`（Android 8.0）/ `targetSdk 35`（Android 15）/ `versionCode 1`。

## 二、离线与权限

- 清单中**未声明 `android.permission.INTERNET`**，也不声明定位、通讯录、存储等任何危险权限。
- 唯一出现的 `com.stockfortune.app.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION` 是 `androidx.core` 自动加入的**自签名级**权限（只对本应用自身生效），不是系统权限。
- `android.permission.DUMP` 仅作为 androidx `ProfileInstallReceiver` 的**保护权限**出现在两个变体中（用于限制该接收器），应用本身不请求任何系统权限。
- `allowBackup=false`：收藏与扫描缓存只存本机，不参与 adb backup / 云备份，避免整库被导出。
- 全部数据（5395 只股票八字、16467 天干支日历、交易日历）以 SQLite 预置库形式打包在 APK 内（`assets/databases/stock_fortune.db`，约 8 MB），飞行模式下功能完整。

## 三、安装步骤

### 方式 A：直接传 APK 到手机
1. 用数据线 / 网盘 / 微信文件传输把 `stock-fortune-release.apk` 拷到手机。
2. 点击安装；系统提示"未知来源"时，在弹窗里选择"允许本次安装"（因为这是本地自签名包，不是应用商店分发）。
3. 首次启动会自动把预置库复制到应用数据目录，约 2~5 秒，之后启动即秒开。

### 方式 B：adb 安装（需开启 USB 调试）
```bash
adb devices                       # 确认已授权本机
adb install -r stock-fortune-release.apk
adb shell am start -n com.stockfortune.app/.MainActivity
```

### 卸载 / 换包
- 从 debug 包切到 release 包（或反之）需先卸载旧包，两者签名不同，不能覆盖安装。
- 数据都在应用本地，卸载即清空；重新安装会重新复制预置库。

## 四、签名说明

- keystore：**不随交付树分发**，存放于用户目录 `~/.stockfortune-keystore/sf-release.jks`，别名 `sfrelease`，RSA 2048，有效期 30 年（2026-09-29 生成）。
- 口令只以环境变量注入：`~/.stockfortune-keystore/secrets.env`（`SF_STORE_PASSWORD` / `SF_KEY_PASSWORD` / `SF_KEY_ALIAS`，权限 600），`tools/build_all.sh` 会自动 source；工程内无任何明文口令，且 `.gitignore` 已排除 keystore 与 secrets。
- 换机器构建：把上述两个文件拷到同路径即可；也可用 `-PsfKeystoreDir=...` 指定其他目录。
- **这是本地自签名证书，仅用于真机安装与个人研究使用**，不是应用商店发布证书。若将来要上架，请重新生成并妥善保管正式证书（正式证书一旦丢失无法升级）。
- 校验签名：
  ```bash
  $ANDROID_HOME/build-tools/35.0.0/apksigner verify --print-certs stock-fortune-release.apk
  ```

## 四点五、本机模拟器测试（不占手机）

工程所在机器已配好 Android 15（API 35）arm64 模拟器，可直接在电脑上看这个 APP：

```bash
cd StockFortuneAndroid
bash tools/run_emulator.sh                 # 首次会自动创建 AVD（Pixel 7 / 1080x2400）
# 另开一个终端安装并启动
~/Library/Android/sdk/platform-tools/adb install -r release/stock-fortune-release.apk
~/Library/Android/sdk/platform-tools/adb shell am start -n com.stockfortune.app/.MainActivity
```

模拟器窗口支持鼠标点击与输入，可以完整走一遍 8 个页面。
若模拟器输入法弹窗遮住了搜索框，按一次返回键关闭即可（或执行
`adb shell settings put secure show_ime_with_hard_keyboard 0`）。

## 五、数据更新说明

数据源两份，都在 `Stock_Fortune_Android_APP_AI_Start_Kit/input_data/`：

| 文件 | 内容 | 当前状态 |
|---|---|---|
| `生辰八字.xlsx` | 主表：代码/名称/上市日期/首日开收涨跌/阴阳/四柱 | 5395 只，最晚上市日 2025-02-06 |
| `行业分类.xlsx` | 代码/名称/三级行业（`一级-二级-三级`） | 5395 只全覆盖，27 个一级行业 |

更新流程：

```bash
cd StockFortuneAndroid
# 1) 替换 xlsx（主表保持总表 12 列；行业表保持"股票代码|股票名称|所属行业"三列）
cp 新的生辰八字.xlsx ../Stock_Fortune_Android_APP_AI_Start_Kit/input_data/生辰八字.xlsx
cp 新的行业分类.xlsx ../Stock_Fortune_Android_APP_AI_Start_Kit/input_data/行业分类.xlsx
# 2) 一键重建（导入 → Room schema → 预置库 → 28 项门禁 → 测试 → 打包）
bash tools/build_all.sh
```

构建产物与签名证据会归档到 `release/verification/BUILD_EVIDENCE.txt`（apksigner 证书全文、zipalign、双包 SHA-256、权限清单）。

单独重跑某一步：
```bash
python3 tools/build_database.py --start 1990-12-01 --end 2035-12-31   # Excel → 预置库
python3 tools/inject_room_hash.py                                     # 注入 Room 身份哈希
python3 tools/verify_database.py                                      # 31 项数据门禁
python3 tools/gen_parity_fixtures.py                                  # Python↔Kotlin parity 夹具
```
`tools/verify_database.py` 会在下列情况下让构建失败：四柱复算不一致、有股票的上市日被判为休市、预置库结构与 Room 实体不一致、DAO 语句无法预编译。

## 六、已知边界

- 交易日历：2017-2026 采用已公布的放假安排，2027 年及以后为规则推算（APP 内"我的"页与日历页会显示数据可信度）。
- 行业按 `股票代码` 与主表精确匹配，以主表为准：行业表里多出的代码不会入库，主表里缺行业的会落为"未分类"（当前为 0 条）。
- 新股：数据快照止于 2025-02-06，之后上市的股票需按第五节更新。
- 覆盖安装升级：APP 启动时比对随包的 `AssetManifest`（Room identityHash + 数据内容哈希）。数据或 schema 变化时会重建本地库，**并自动把旧库里的收藏迁移回新库**；扫描缓存属可再生数据，会随重建清空。
