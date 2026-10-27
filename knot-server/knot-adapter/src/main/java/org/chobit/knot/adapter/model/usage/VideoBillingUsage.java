package org.chobit.knot.adapter.model.usage;

import lombok.Builder;

/**
 * VIDEO 模式计费输入：输出量（时长 / 条数 / 档位）+ 输入量（时长 / 张数）+ 生成属性（有声 / 音色 / 参考视频）。
 *
 * <p><b>为什么分辨率是字符串而不是宽高</b>：厂商声明的就是<b>档位名</b>（{@code 768P}、{@code 540P}、
 * {@code 2K}、{@code 4K} 乃至自造名），不是像素尺寸。宽高是「由档位名反推」的中间产物——
 * 反推只认标准尺寸表，{@code 768P} 这类非标档会退化成裸串或空值，配了价也吃不到。
 * Calculator 侧的分档键因此必须直接用厂商的写法，不能用宽高重算一遍。
 * 空串表示「没有声明分辨率」，取价时回落默认档。</p>
 *
 * <p><b>v2 相对 v1 的两处实质变化</b></p>
 * <ol>
 *   <li><b>时长改毫秒</b>：{@code long} 秒会直接截断 {@code 5.9s → 5s}（少收 15%），
 *       并把 {@code 0.5s} 截成 0 再被「至少一秒」兜成 1 秒（多收 100%）。
 *       毫秒只作<b>存储精度</b>，计费与展示统一经 {@link #outputSeconds()} 向上取整到秒
 *       （业界按整秒计价；向上取整让金额方向恒定——宁可多 1 秒也不漏收）。</li>
 *   <li><b>{@code reference} → {@code referenceVideo}</b>：只表示「参考 / 输入视频」。
 *       旧字段同时承载参考图，导致图生视频误命中 {@code :REFERENCE} 档；参考图走
 *       {@link #inputImageCount} 的张数口径（robin 2026-10-26 拍板「有无输入视频 ≡ 有无参考视频」）。</li>
 * </ol>
 *
 * <p>{@code outputVideoMillis} 是主计费量；{@code inputVideoMillis} 与
 * {@code inputImageCount} 支撑「合成量」与「多分量求和」两种业界口径：</p>
 * <ul>
 *   <li>合成量（Kling）：把输入秒按公式折进输出秒，仍只有一个分量、一个单价；</li>
 *   <li>多分量（MiniMax）：输出秒 / 输入秒 / 输入图片张各自计价后求和。</li>
 * </ul>
 *
 * <p>{@code sound} / {@code referenceVideo} 是请求侧生成属性，既是<b>维度分档键</b>的组成部分
 * （豆包「分辨率 × 是否含视频输入」定价），也是<b>附加费触发条件</b>。</p>
 *
 * <p>{@code tokens} 内嵌上游可能同时上报的 token 用量，TOKEN 类规则走它计费；
 * 纯媒体响应为空用量。</p>
 *
 * <p>{@code outputAspectRatio} / {@code outputMode} 是厂商声明的价目表档名：前者如 {@code 16:9}、
 * {@code 1:1}（与分辨率正交），后者如 {@code wan-std} / {@code wan-pro}、{@code OFFLINE}、
 * Kling 的 {@code MOTION_CONTROL}（动作控制）。两者都<b>原样保存厂商写法</b>——键空间的归一
 * （大写、{@code :} → {@code X}、{@code MODE=} / {@code ASPECT=} 前缀）由 Calculator 做；
 * record 一旦参与键拼接，就等于把计价规则搬进了用量模型。</p>
 *
 * <p>{@code outputVideoCount} 是 Veo 这类「按条计价」的计费量；{@code 0} 表示上游没报，
 * 取价时经 {@link #billedVideoCount()} 兜成 1 条——按条计价的乘数是 0 时整单会算成 0 元。</p>
 *
 * <p>{@code voiceSpecified} 与 {@code sound} 正交：Kling 2.6 在「有声」档内再按「是否指定音色」
 * 分两价，它是独立事实，不是 {@code sound} 的子状态。</p>
 *
 * <p><b>刻意不含「采集阶段」</b>（提交态 / 完成态）：那是提取层的判定，与「怎么计价」无关。
 * 计费模型只回答「量是多少、按哪一档」，不回答「这份量该不该出账」。</p>
 */
@Builder
public record VideoBillingUsage(TokenBillingUsage tokens,
                                long outputVideoMillis,
                                String outputResolution,
                                String outputAspectRatio,
                                String outputMode,
                                long outputVideoCount,
                                long inputVideoMillis,
                                long inputImageCount,
                                boolean sound,
                                boolean voiceSpecified,
                                boolean referenceVideo) implements BillingUsage {

    /** 毫秒 → 秒的进位基数：按秒向上取整，不截断 */
    private static final long MILLIS_PER_SECOND = 1_000L;

    public VideoBillingUsage {
        tokens = tokens == null ? TokenBillingUsage.empty() : tokens;
        outputResolution = text(outputResolution);
        outputAspectRatio = text(outputAspectRatio);
        outputMode = text(outputMode);
        outputVideoMillis = Math.max(outputVideoMillis, 0L);
        outputVideoCount = Math.max(outputVideoCount, 0L);
        inputVideoMillis = Math.max(inputVideoMillis, 0L);
        inputImageCount = Math.max(inputImageCount, 0L);
    }

    public static VideoBillingUsage empty() {
        return builder().build();
    }

    @Override
    public TokenBillingUsage tokenUsage() {
        return tokens;
    }

    /**
     * 输出时长（秒，向上取整）：计费与展示的唯一口径。
     *
     * <p>放在记录上而不是各调用点：截断 / 取整 / 进位策略只应有一个定义，
     * 散在计算器、投影、影子链各写一份必然漂移。</p>
     */
    public long outputSeconds() {
        return ceilSeconds(outputVideoMillis);
    }

    /** 输入时长（秒，向上取整） */
    public long inputSeconds() {
        return ceilSeconds(inputVideoMillis);
    }

    /**
     * 计费条数：按条计价（Veo）的乘数。
     *
     * <p>{@code outputVideoCount} 为 0 表示上游没上报条数，按 1 条兜底——0 会让整单算出 0 元。</p>
     */
    public long billedVideoCount() {
        return outputVideoCount <= 0 ? 1L : outputVideoCount;
    }

    /**
     * ⭐ 有无输入视频的唯一判据（全仓共用，含 Kling 分支与通用路径的维度键 / 分量 / 附加费）。
     *
     * <p>两个信号取或：{@code inputVideoMillis > 0} 是<b>知道时长</b>，
     * {@code referenceVideo} 是<b>知道存在但可能不知道秒数</b>（Kling 传 URL / id，
     * 请求体里根本没有时长）。只用其中一个都会让这一维恒为假。</p>
     */
    public boolean hasInputVideo() {
        return inputVideoMillis > 0 || referenceVideo;
    }

    @Override
    public boolean isEmpty() {
        // 只认「量」：token / 秒数 / 档位名 / 条数。属性（画幅、模式、有声、音色、参考）一律不参与，
        // 否则「请求声明了属性但响应没给量」的提交态回执会被判成有量，A2 的 MISMATCH 语义失效。
        return tokens.isEmpty()
                && outputVideoMillis <= 0
                && outputResolution.isBlank()
                && outputVideoCount <= 0;
    }

    @Override
    public VideoBillingUsage mergeMax(BillingUsage other) {
        if (!(other instanceof VideoBillingUsage that)) {
            return this;
        }
        return new VideoBillingUsage(
                tokens.mergeMax(that.tokens()),
                Math.max(outputVideoMillis, that.outputVideoMillis),
                // 字符串取先出现的非空值：同一请求的多事件必然一致，空值只可能来自缺字段的事件
                firstText(outputResolution, that.outputResolution),
                firstText(outputAspectRatio, that.outputAspectRatio),
                firstText(outputMode, that.outputMode),
                Math.max(outputVideoCount, that.outputVideoCount),
                Math.max(inputVideoMillis, that.inputVideoMillis),
                Math.max(inputImageCount, that.inputImageCount),
                sound || that.sound,
                voiceSpecified || that.voiceSpecified,
                referenceVideo || that.referenceVideo
        );
    }

    private static String text(String value) {
        return value == null ? "" : value.trim();
    }

    private static String firstText(String current, String other) {
        return current.isBlank() ? other : current;
    }

    private static long ceilSeconds(long millis) {
        return millis <= 0 ? 0L : (millis + MILLIS_PER_SECOND - 1) / MILLIS_PER_SECOND;
    }
}
