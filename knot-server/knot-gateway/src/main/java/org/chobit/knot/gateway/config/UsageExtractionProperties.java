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
 * <p>默认 {@code degradeOnFailure=false}：静默零账意味着这笔上游消耗永远收不回来，且只在对账时
 * 才暴露，因此出厂默认必须让失败可见。生产若遇到上游协议变更导致成片 500（上游已生成并计费、
 * 调用方却拿不到响应，等于我们付钱），可显式打开降级换可用性——代价是该笔账目需事后重算。</p>
 */
@Getter
@Setter
@ConfigurationProperties(prefix = "knot.gateway.usage")
public class UsageExtractionProperties {

    /**
     * 提取失败时是否<b>降级放行</b>（{@code true}），而不是抛异常中断响应（{@code false}，默认）。
     *
     * <p>为什么写成「降级 = true」而不是「中断 = true」：布尔未配置时天然是 {@code false}，
     * 恰好等于安全档位，不需要依赖字段初始化那行代码兜底；反过来写成 {@code interrupt=true}
     * 就得靠显式初始化，漏一行就把红线放松了。同一道理：宽松行为必须被<b>显式打开</b>。</p>
     *
     * <p>覆盖面只有三处「提取不出用量」的失败：响应体不是合法 JSON、提取器没产出结果、
     * 上游有非零用量却提取为空（口径错配）。</p>
     *
     * <p>⚠ <b>提取器不存在（未知 code）不受本项影响</b>：那不是提取失败而是配置错误，
     * 降级它等于让该绑定下的所有请求永久零账，且没有任何自愈路径。</p>
     */
    private boolean degradeOnFailure = false;
}
