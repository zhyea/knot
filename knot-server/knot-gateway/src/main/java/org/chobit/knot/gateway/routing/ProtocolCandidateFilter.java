package org.chobit.knot.gateway.routing;

import org.chobit.knot.gateway.constants.enums.EnabledStatusEnum;
import org.chobit.knot.gateway.constants.enums.ModelApiProtocolEnum;
import org.chobit.knot.gateway.dto.routing.RoutingRuleTargetDto;
import org.chobit.knot.gateway.entity.ModelApiBindingEntity;

import java.util.List;
import java.util.Objects;

/**
 * 按请求协议过滤路由候选：只保留声明了「启用且协议匹配」绑定的模型。
 *
 * <p>与调试面板的协议可选项（模型池取<b>并集</b>）配套：面板按池内并集提供协议，
 * 网关只让真正支持该协议的模型进入 failover 链，避免把请求打到不具备该能力的上游。
 * 过滤后为空由调用方决定处置（{@code RoutingResolver} 抛 NO_ROUTING_TARGET）。</p>
 *
 * <p>判定口径只认显式声明：{@code kb_model_api_bindings} 中有「启用」且协议归一后匹配的绑定。
 * 无绑定即视为不支持任何协议——不回落到模型类型推导：未声明的模型不应被推测为可用，
 * 配置缺失应当显式暴露。</p>
 */
public class ProtocolCandidateFilter {

    private final ApiBindingSource bindingSource;

    public ProtocolCandidateFilter(ApiBindingSource bindingSource) {
        this.bindingSource = bindingSource;
    }

    /**
     * @param candidates 已按规则策略排序的候选链（含模型与池内展开的模型）
     * @param protocol   本次请求的接口协议；为 null（未识别协议）时不过滤，原样返回
     */
    public List<RoutingRuleTargetDto> filter(List<RoutingRuleTargetDto> candidates,
                                             ModelApiProtocolEnum protocol) {
        if (protocol == null) {
            return candidates;
        }
        return candidates.stream()
                .filter(candidate -> supports(candidate, protocol))
                .toList();
    }

    private boolean supports(RoutingRuleTargetDto candidate, ModelApiProtocolEnum protocol) {
        if (candidate.targetId() == null) {
            return false;
        }
        return bindingSource.bindingsOf(candidate.targetId()).stream()
                .filter(binding -> EnabledStatusEnum.isEnabled(binding.getStatus()))
                .map(ModelApiBindingEntity::getProtocol)
                .map(ModelApiProtocolEnum::fromCode)
                .filter(Objects::nonNull)
                .anyMatch(bindingProtocol -> bindingProtocol.canonical() == protocol.canonical());
    }
}
