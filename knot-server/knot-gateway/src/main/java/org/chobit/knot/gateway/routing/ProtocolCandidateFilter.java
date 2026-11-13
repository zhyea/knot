package org.chobit.knot.gateway.routing;

import org.chobit.knot.common.constants.enums.EnabledStatusEnum;
import org.chobit.knot.common.constants.enums.ModelApiProtocolEnum;
import org.chobit.knot.svc.dto.routing.RoutingRuleTargetDto;
import org.chobit.knot.svc.vo.gateway.ModelApiBindingConfigVo;

import java.util.List;
import java.util.Objects;

/**
 * 按请求协议过滤路由候选：只保留在 {@code kb_model_api_bindings} 中声明了「启用且协议归一后匹配」绑定的模型。
 * 无绑定即视为不支持任何协议，不回落模型类型推导；过滤后为空由调用方处置（{@code RoutingResolver} 抛 NO_ROUTING_TARGET）。
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
                .filter(binding -> EnabledStatusEnum.isEnabled(binding.status()))
                .map(ModelApiBindingConfigVo::protocol)
                .map(ModelApiProtocolEnum::fromCode)
                .filter(Objects::nonNull)
                .anyMatch(bindingProtocol -> bindingProtocol.canonical() == protocol.canonical());
    }
}
