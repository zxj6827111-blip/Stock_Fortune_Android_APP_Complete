package com.stockfortune.app.ui.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.stockfortune.app.R
import com.stockfortune.app.ui.components.SfCard
import com.stockfortune.app.ui.components.SfDisclaimer
import com.stockfortune.app.ui.components.SfSectionTitle
import com.stockfortune.app.ui.theme.SfColors
import com.stockfortune.app.ui.theme.SfDimens

/** 算法口径说明：如实描述数据口径、十神与财星判定、交易日历来源与能力边界。 */
@Composable
fun AlgorithmDocScreen(onBack: () -> Unit) {
    Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = SfDimens.PagePadding, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = stringResource(R.string.back),
                    tint = SfColors.DeepBlue,
                )
            }
            Spacer(Modifier.width(4.dp))
            Text(
                stringResource(R.string.mine_algorithm),
                style = MaterialTheme.typography.titleLarge,
                color = SfColors.TextMain,
            )
        }

        DocCard(
            title = "一、数据口径",
            lines = listOf(
                "股票八字以该公司上市首日 09:30（巳时）为起局时刻。",
                "日柱按 60 甲子连续推进，锚点为 1949-10-01 = 甲子日；年柱以立春为界、月柱以十二节为界，均采用「交节当日即换柱」的日粒度口径。",
                "时柱由日干按五鼠遁推出，因起局时刻固定，故恒为巳时。",
            ),
        )
        DocCard(
            title = "二、十神规则",
            lines = listOf(
                "日主取日柱天干，其余天干与日主比较得出十神。",
                "同我：比肩 / 劫财；我生：食神 / 伤官；我克：偏财 / 正财；克我：七杀 / 正官；生我：偏印 / 正印。",
                "阴阳与日主同性者取前者（比肩、食神、偏财、七杀、偏印），异性者取后者（劫财、伤官、正财、正官、正印）。",
            ),
        )
        DocCard(
            title = "二·五、日主强弱（Rule v1.2）",
            lines = listOf(
                "按「得令 / 得地 / 得势」给四柱加权：同我（比劫）与生我（印）记同党取正，我生（食伤）、我克（财）、克我（官杀）记异党取负。",
                "权重：月支本气 3.0、中气 1.5、余气 0.75；年支与日支本气 1.0、中气 0.5、余气 0.25；年干与月干 0.7；日干即日主不计。总分 ≥ +2.0 记「身强」，≤ −2.0 记「身弱」，其间记「中和」。",
                "刻意不用时柱：本应用时柱取「上市日 9:30 → 巳时」，5395 只股票时支恒为巳，巳藏丙/庚/戊对每个盘是同一常数项（木 −1.75 到 土 +0.75）。含时柱时身强占比在日主之间极差 14 倍（甲 2.8% ↔ 戊 40.9%），剔除后降到 2.3 倍，而总体分布几乎不变（16.4/31.7/52.0 → 16.5/34.0/49.5）。",
                "三态而非两态：边界盘硬判强弱是假精确，「中和」用来承接确实偏不出方向的命盘。",
                "三分结构是子平通说，但上述权重与阈值是本项目的工程取值，没有任何古籍依据；由它产出的判词一律标「本项目概述」，不挂书名。《滴天髓輯要》里也没有「身强能任财 / 财多身弱」这类句式。",
            ),
        )
        DocCard(
            title = "三、财星判定（Rule v1.1）",
            lines = listOf(
                "先看流年 / 流月 / 流日天干的十神是否为财（称「透干」）；再看地支本气十神（藏支）。",
                "命中正财记「正财」，命中偏财记「偏财」，两者皆无记「其他」；非交易日记「无」。",
                "藏干十神作为独立筛选维度，覆盖四柱地支的全部藏干，与流年 / 流月 / 流日维度分开计算。",
                "结构性差异：本气为土的支有辰 / 戌 / 丑 / 未四个，其余五行各两个，故 60 甲子中木日主（以土为财）可得 28 个财日，其余日主为 20 个。财星榜单按日主分布并不均匀，比较时应对照同口径。",
            ),
        )
        DocCard(
            title = "三·五、月度页的月令口径",
            lines = listOf(
                "月度运势与每日分析按公历月排布，而干支月以十二节为界，因此每个公历月开头约 5 天仍属上一个干支月。",
                "页内「月干支 / 五行属性 / 月运简述」按该月中主导的干支月（交节后）计，跨节的月份会在卡内标注交节日与之前所属的干支月。",
                "每日行的日柱、财星逐日独立计算，不受该口径影响。",
            ),
        )
        DocCard(
            title = "三·六、年度页的逐月口径",
            lines = listOf(
                "年度页逐月行按干支月列出，行内日期区间是该干支月交节首末两日，不是公历月首末。",
                "月柱在一个干支月内恒定，故该行的月干支 / 天干十神 / 财星 / 判词取月内任一日皆同值，实现上取首日为代表。",
                "行内判词与月度页「月运简述」由同一函数产出，同一个月在两页文字一致。",
                "「其他」指月干与月支本气皆不为财星，仍是有十神当值的月份；「无」只用于非交易日，两者不同义。",
            ),
        )
        DocCard(
            title = stringResource(R.string.doc_classics_title),
            lines = listOf(
                stringResource(R.string.doc_classics_source),
                stringResource(R.string.doc_classics_page),
                stringResource(R.string.doc_classics_script),
                stringResource(R.string.doc_classics_match),
                stringResource(R.string.doc_classics_excerpt),
                stringResource(R.string.doc_classics_overview),
            ),
        )
        DocCard(
            title = "四、交易日历",
            lines = listOf(
                "周末一律休市。",
                "节假日按交易所已公布的休市安排，未公布区间按规则推算；并用 5395 个真实上市日反查校验——凡有股票上市之日必为交易日。",
                "2027 年及以后的休市安排为预估，实际以交易所公告为准。",
            ),
        )
        DocCard(
            title = "五、能力边界",
            lines = listOf(
                "本应用不做涨跌预测、不做收益回测、不输出评分或买卖信号。",
                "不接入服务器与实时行情，全部计算与数据均在设备本地完成。",
                "所有结果仅为传统命理模型下的时间推演，供文化参考。",
            ),
        )

        Spacer(Modifier.height(SfDimens.CardGap))
        SfDisclaimer(
            stringResource(R.string.disclaimer),
            modifier = Modifier.padding(horizontal = SfDimens.PagePadding),
        )
        Spacer(Modifier.height(SfDimens.CardGap))
    }
}

@Composable
private fun DocCard(title: String, lines: List<String>) {
    SfCard(
        modifier = Modifier.padding(
            horizontal = SfDimens.PagePadding,
            vertical = SfDimens.CardGap / 2,
        ),
    ) {
        SfSectionTitle(title = title)
        Spacer(Modifier.height(8.dp))
        lines.forEach { line ->
            Row(modifier = Modifier.padding(bottom = 6.dp)) {
                Box(
                    Modifier
                        .padding(top = 7.dp)
                        .size(5.dp)
                        .clip(CircleShape)
                        .background(SfColors.Gold),
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    line,
                    style = MaterialTheme.typography.bodyMedium,
                    color = SfColors.TextSub,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}
