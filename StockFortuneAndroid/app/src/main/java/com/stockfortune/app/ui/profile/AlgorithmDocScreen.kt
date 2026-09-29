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
