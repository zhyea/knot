package org.chobit.knot.gateway.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 用量提取的运行时配置（{@code knot.gateway.usage.*}）。
 * 只控制提取失败时中断响应还是放行出零账；提取口径由模型绑定的 {@code UsageExtractor} 决定，不在此配置。
 * 默认 {@code degradeOnFailure=false}；显式开启后降级放行。
 */
@Getter
@Setter
@ConfigurationProperties(prefix = "knot.gateway.usage")
public class UsageExtractionProperties {

    /**
     * 提取失败时降级放行（{@code true}）或抛异常中断响应（{@code false}，默认）。
     * 覆盖三处提取不出用量的失败：响应体不是合法 JSON、提取器没产出结果、上游有非零用量却提取为空。
     * 提取器不存在（未知 code）不受本项影响。
     */
    private boolean degradeOnFailure = false;
}
