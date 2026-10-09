package com.stockfortune.app.domain.model

/**
 * 股运通 V1.3 Phase 4 离线文案与五段式输出核心模型。
 *
 * 规范遵循：
 * 1. 严格区分算法计算可用状态（AlgorithmAvailability）与文案审核状态（ReviewStatus）；
 * 2. 只有经人工终审通过的文案才允许标记为 APPROVED，Phase 4 候选文案全部为 PENDING_REVIEW；
 * 3. 严格落实独立分项提示逻辑（SLOT_PRECISE_ADVANCED_STATUS），三项均可用时输出空串，避免交叉否定；
 * 4. 五段式结构严格对应：命理依据、本月主题、潜在矛盾、企业经营观察、综合解释。
 */

enum class ReviewStatus(val cn: String) {
    PENDING_REVIEW("待人工终审"),
    RETURNED_FOR_REVISION("退回修订"),
    APPROVED("终审通过");

    val isProductionAllowed: Boolean
        get() = this == APPROVED
}

/**
 * 首日命别与极性（FirstDayPolarity，契约 3.1 节规范）。
 */
enum class FirstDayPolarity(val code: String, val label: String) {
    YANG("yang", "阳"),          // 首日收盘价 > 开盘价
    YIN("yin", "阴"),            // 首日收盘价 < 开盘价
    FLAT("flat", "平"),          // 首日收盘价 == 开盘价（315 只样本）
    MISSING("missing", "缺失"),  // 首日行情数据缺失（如 000004.SZ）
    CONFLICT("conflict", "冲突"); // 多源数据自洽校验冲突

    companion object {
        fun fromCode(code: String?): FirstDayPolarity {
            return entries.firstOrNull { it.code.equals(code, ignoreCase = true) } ?: MISSING
        }
    }
}

enum class ProductionGate(val label: String) {
    AUDIT_ONLY("仅供审核"),
    CANDIDATE_ONLY("候审候选"),
    PRODUCTION_BLOCKED("禁止进入生产库"),
    PRODUCTION_RELEASE("正式发布");

    val isDeployable: Boolean
        get() = this == PRODUCTION_RELEASE
}

enum class AlgorithmAvailability(val code: String, val label: String) {
    AVAILABLE("AVAILABLE", "已形成有效推导"),
    UNAVAILABLE("UNAVAILABLE", "暂无可用有效证据"),
    PENDING("PENDING", "依赖项计算中或待确认"),
    CONFLICT("CONFLICT", "多源证据互斥冲突"),
    NOT_IMPLEMENTED("NOT_IMPLEMENTED", "算法未实现");

    companion object {
        fun fromCode(code: String?): AlgorithmAvailability {
            return entries.firstOrNull { it.code.equals(code, ignoreCase = true) } ?: UNAVAILABLE
        }
    }
}

enum class CopySection(val cn: String, val order: Int) {
    BASIS("命理依据", 1),
    THEME("本月主题", 2),
    CONTRADICTION("潜在矛盾", 3),
    BUSINESS("企业经营观察", 4),
    SYNTHESIS("综合解释", 5);

    companion object {
        fun fromCn(cn: String?): CopySection? = entries.firstOrNull { it.cn == cn }
    }
}

/**
 * 离线规则条目定义（解耦化规则库条目）。
 */
data class CopyRuleDefinition(
    val ruleId: String,
    val section: CopySection,
    val triggerDsl: String,
    val priority: Int,
    val conflictGroup: String,
    val evidenceKeys: List<String>,
    val text: String,
    val ruleVersion: String,
    val reviewStatus: ReviewStatus = ReviewStatus.PENDING_REVIEW,
    val productionGate: ProductionGate = ProductionGate.PRODUCTION_BLOCKED,
    val isLegacyNoRender: Boolean = false,
)

/**
 * 精确分项提示生成器（E-01/E-02 勘误落地）。
 * 针对大运、原局关系、喜用候选三项状态独立评估提示：
 * 只有不具备有效推导项才给出说明，三项均可用时提示为空串，绝不交叉否定。
 */
object PreciseAdvancedNotice {

    fun formatNotice(
        dayunState: AlgorithmAvailability,
        natalState: AlgorithmAvailability,
        yongshenState: AlgorithmAvailability,
    ): String {
        if (dayunState == AlgorithmAvailability.AVAILABLE &&
            natalState == AlgorithmAvailability.AVAILABLE &&
            yongshenState == AlgorithmAvailability.AVAILABLE
        ) {
            return ""
        }

        val items = mutableListOf<String>()

        when (dayunState) {
            AlgorithmAvailability.UNAVAILABLE ->
                items.add("大运的区间及方向暂无可核对证据，本次不展开该维度。")
            AlgorithmAvailability.PENDING ->
                items.add("大运的区间及方向仍在核验，未完成前不作确定判断。")
            AlgorithmAvailability.CONFLICT ->
                items.add("大运的区间及方向存在来源冲突，相关断语留待复核。")
            AlgorithmAvailability.NOT_IMPLEMENTED ->
                items.add("大运的区间及方向尚未接入已冻结计算口径，本次不展开该维度。")
            else -> {}
        }

        when (natalState) {
            AlgorithmAvailability.UNAVAILABLE ->
                items.add("原局关系的事件及参与位置暂无可核对证据，本次不展开该维度。")
            AlgorithmAvailability.PENDING ->
                items.add("原局关系的事件及参与位置仍在核验，未完成前不作确定判断。")
            AlgorithmAvailability.CONFLICT ->
                items.add("原局关系的事件及参与位置存在来源冲突，相关断语留待复核。")
            AlgorithmAvailability.NOT_IMPLEMENTED ->
                items.add("原局关系的事件及参与位置尚未接入已冻结计算口径，本次不展开该维度。")
            else -> {}
        }

        when (yongshenState) {
            AlgorithmAvailability.UNAVAILABLE ->
                items.add("喜用候选的方法、角色与依据暂无可核对证据，本次不展开该维度。")
            AlgorithmAvailability.PENDING ->
                items.add("喜用候选的方法、角色与依据仍在核验，未完成前不作确定判断。")
            AlgorithmAvailability.CONFLICT ->
                items.add("喜用候选的方法、角色与依据存在来源冲突，相关断语留待复核。")
            AlgorithmAvailability.NOT_IMPLEMENTED ->
                items.add("喜用候选的方法、角色与依据尚未接入已冻结计算口径，本次不展开该维度。")
            else -> {}
        }

        return items.joinToString("")
    }
}

/**
 * 五段式月度结构化解读输出数据结构。
 */
data class FiveParagraphInterpretation(
    val stockId: Long,
    val stockCode: String,
    val year: Int,
    val month: Int,
    val basisText: String,
    val themeText: String,
    val contradictionText: String,
    val businessText: String,
    val synthesisText: String,
    val preciseAdvancedNotice: String = "",
    val hitRuleIds: List<String> = emptyList(),
    val reviewStatus: ReviewStatus = ReviewStatus.PENDING_REVIEW,
    val isMock: Boolean = false,
) {
    /** 检查是否全部五段内容均齐全 */
    val isComplete: Boolean
        get() = basisText.isNotBlank() &&
                themeText.isNotBlank() &&
                contradictionText.isNotBlank() &&
                businessText.isNotBlank() &&
                synthesisText.isNotBlank()

    /** 格式化生成完整展示文本（如用于测试或审阅） */
    fun toFormattedMarkdown(): String {
        val sb = StringBuilder()
        sb.append("### 【命理依据】\n").append(basisText).append("\n\n")
        sb.append("### 【本月主题】\n").append(themeText).append("\n\n")
        sb.append("### 【潜在矛盾】\n").append(contradictionText).append("\n\n")
        sb.append("### 【企业经营观察】\n").append(businessText).append("\n\n")
        sb.append("### 【综合解释】\n").append(synthesisText)
        if (preciseAdvancedNotice.isNotBlank()) {
            sb.append("\n\n> **说明**：").append(preciseAdvancedNotice)
        }
        return sb.toString()
    }
}
