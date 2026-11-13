package org.chobit.knot.adapter.usage;

import org.apache.commons.lang3.StringUtils;
import org.chobit.knot.adapter.model.ref.ModelApiBindingRef;

import java.util.List;

/**
 * 提取器选择的单一来源：网关 {@code UsageExtractorRegistry} 与管理端 {@code RoutingTestBillingVerdictService} 共用。
 * 绑定显式配置是唯一来源：流式优先 {@code streamUsageExtractor}，否则 {@code usageExtractor}。
 * 未配置（含未绑定、空白串）返回 {@code null}，由调用方回落默认提取器。
 * 本类不持有任何状态，纯函数。
 */
public final class UsageExtractorResolver {

    private UsageExtractorResolver() {
    }

    /**
     * 网关形态：单个已选定的绑定 → 提取器编码。
     *
     * @param binding    本次调用选定的绑定（由 {@code UpstreamAuthApplier.prepare()} 产出）；
     *                   未绑定时传 {@code null}
     * @param eventStream 是否流式（SSE）场景
     * @return 提取器编码；未配置时 {@code null}（调用方回落默认）
     */
    public static String fromBinding(ModelApiBindingRef binding, boolean eventStream) {
        if (binding == null) {
            return null;
        }
        if (eventStream && StringUtils.isNotBlank(binding.streamUsageExtractor())) {
            return binding.streamUsageExtractor();
        }
        return StringUtils.isBlank(binding.usageExtractor()) ? null : binding.usageExtractor();
    }

    /**
     * 裁判形态：一组绑定按协议匹配 → 提取器编码（管理端无 {@code prepare} 阶段，
     * 由调用方给出候选集，本类只负责「按 protocol 取第一个显式配置」）。
     *
     * @param bindings  模型名下的全部绑定
     * @param protocol  本次裁判请求的协议编码
     * @param stream    是否流式场景
     * @return 提取器编码；无匹配或均未配置时 {@code null}（调用方回落默认）
     */
    public static String fromBindings(List<ModelApiBindingRef> bindings, String protocol, boolean stream) {
        if (bindings == null || protocol == null) {
            return null;
        }
        for (ModelApiBindingRef binding : bindings) {
            if (protocol.equals(binding.protocol())) {
                String code = fromBinding(binding, stream);
                if (code != null) {
                    return code;
                }
            }
        }
        return null;
    }
}
