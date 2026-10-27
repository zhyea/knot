package org.chobit.knot.adapter.model.usage;

/**
 * VIDEO 模式计费输入：输出时长 + 输入时长 + 输入图片张数 + 输出分辨率 + 生成属性。
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
 * <p><b>刻意不含「采集阶段」</b>（提交态 / 完成态）：那是提取层的判定，与「怎么计价」无关。
 * 计费模型只回答「量是多少、按哪一档」，不回答「这份量该不该出账」。</p>
 */
public record VideoBillingUsage(TokenBillingUsage tokens,
                                long outputVideoMillis,
                                long inputVideoMillis,
                                long inputImageCount,
                                String outputResolution,
                                boolean sound,
                                boolean referenceVideo) implements BillingUsage {

    /** 毫秒 → 秒的进位基数：按秒向上取整，不截断 */
    private static final long MILLIS_PER_SECOND = 1_000L;

    public VideoBillingUsage {
        tokens = tokens == null ? TokenBillingUsage.empty() : tokens;
        outputResolution = outputResolution == null ? "" : outputResolution.trim();
        outputVideoMillis = Math.max(outputVideoMillis, 0L);
        inputVideoMillis = Math.max(inputVideoMillis, 0L);
        inputImageCount = Math.max(inputImageCount, 0L);
    }

    public static VideoBillingUsage empty() {
        return new VideoBillingUsage(TokenBillingUsage.empty(), 0L, 0L, 0L, "", false, false);
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
        // 与 v1 等价：v1 的 `pixels > 0` 兜的正是「请求体声明了 size 但响应没给时长」这一档，
        // 现在由 resolution 非空承接。isEmpty 是提取层的「有没有拿到量」，它的口径一旦变化
        // 会连带改变 UsageExtractorRegistry 的 MISMATCH 失败策略——那是另一条链路。
        return tokens.isEmpty() && outputVideoMillis <= 0 && outputResolution.isBlank();
    }

    @Override
    public VideoBillingUsage mergeMax(BillingUsage other) {
        if (!(other instanceof VideoBillingUsage that)) {
            return this;
        }
        return new VideoBillingUsage(
                tokens.mergeMax(that.tokens()),
                Math.max(outputVideoMillis, that.outputVideoMillis),
                Math.max(inputVideoMillis, that.inputVideoMillis),
                Math.max(inputImageCount, that.inputImageCount),
                // 分辨率取先出现的非空值：同一请求的多事件必然一致，空值只可能来自缺字段的事件
                outputResolution.isBlank() ? that.outputResolution : outputResolution,
                sound || that.sound,
                referenceVideo || that.referenceVideo
        );
    }

    private static long ceilSeconds(long millis) {
        return millis <= 0 ? 0L : (millis + MILLIS_PER_SECOND - 1) / MILLIS_PER_SECOND;
    }
}
