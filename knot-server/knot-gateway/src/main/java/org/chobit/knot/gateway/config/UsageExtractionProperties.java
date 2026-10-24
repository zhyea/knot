package org.chobit.knot.gateway.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 用量提取的运行时配置（{@code knot.gateway.usage.*}）。
 *
 * <p>只管一件事：提取失败时<b>中断响应</b>还是<b>放行出零账</b>。这是可用性与计费准确性的取舍，
 * 不是提取口径——口径由绑定在模型上的 {@code UsageExtractor} 决定，不在这里配。</p>
 *
 * <p>默认 {@code fail}：静默零账意味着这笔上游消耗永远收不回来，且只在对账时才暴露，
 * 因此出厂默认必须让失败可见。生产若遇到上游协议变更导致成片 500（上游已生成并计费、调用方却
 * 拿不到响应，等于我们付钱），可显式配 {@code degrade} 换可用性——代价是该笔账目需事后重算。</p>
 */
@Getter
@Setter
@ConfigurationProperties(prefix = "knot.gateway.usage")
public class UsageExtractionProperties {

    /**
     * 提取失败时的处置，取值见 {@link OnExtractFailure}：{@code fail}（默认）/ {@code degrade}。
     *
     * <p>覆盖面只有三处「提取不出用量」的失败：响应体不是合法 JSON、提取器没产出结果、
     * 上游有非零用量却提取为空（口径错配）。</p>
     *
     * <p>⚠ <b>提取器不存在（未知 code）不受本项影响</b>：那不是提取失败而是配置错误，
     * 降级它等于让该绑定下的所有请求永久零账，且没有任何自愈路径。</p>
     */
    private OnExtractFailure onExtractFailure = OnExtractFailure.FAIL;

    /**
     * 提取失败是否中断响应。
     *
     * <p>用枚举而非布尔：将来出现「重试一次」「降级并入重算队列」这类档位时，
     * 调用方的判定不用改。<b>{@code null} 按 {@code fail} 处理</b>——配置缺失不该变成放松。</p>
     */
    public boolean interruptOnFailure() {
        return onExtractFailure != OnExtractFailure.DEGRADE;
    }

    /**
     * 提取失败的处置档位。
     *
     * <p>枚举与配置项同名（{@code knot.gateway.usage.on-extract-failure}），
     * 便于运维文档与代码互查；Spring 的宽松绑定允许写成 {@code fail} / {@code DEGRADE} 等形态，
     * 写错值启动即失败，不会静默按某个档位跑。</p>
     */
    public enum OnExtractFailure {

        /** 抛出异常中断响应：调用方拿到错误，账目不会被静默记成零 */
        FAIL,

        /** 放行并出零账：保住响应可用性，代价是该笔上游消耗需要事后重算 */
        DEGRADE
    }
}
