package org.chobit.knot.gateway.adapter.usage;

import org.apache.commons.lang3.StringUtils;
import org.chobit.knot.gateway.adapter.model.ref.ModelApiBindingRef;

import java.util.List;

/**
 * 提取器选择的单一来源（改进清单 3.4，2026-10-24）：
 *
 * <p>网关 {@code UsageExtractorRegistry} 与管理端裁判 {@code RoutingTestBillingVerdictService}
 * 原先各有一份「读绑定配置选提取器」的实现，口径靠注释对齐——那种一致性是脆的，
 * 一侧改了另一侧不知道。本类把选择收敛到一处，两侧共用。</p>
 *
 * <p>口径（2.2b 移除协议/供应商猜测后的唯一路径）：<b>绑定显式配置是唯一来源</b>——
 * 流式优先 {@code streamUsageExtractor}，否则 {@code usageExtractor}；未配置（含未绑定、
 * 空白串）返回 {@code null}，由调用方回落默认提取器。默认在各侧形态不同
 * （网关是 {@code UsageExtractorCatalog}，裁判是常量），故回落不放在本类。</p>
 *
 * <p>本类不持有任何状态，纯函数；放在 knot-adapter 是因为 {@link ModelApiBindingRef}
 * 在此定义，且 gateway 与 service 两个消费方都依赖本模块。</p>
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
