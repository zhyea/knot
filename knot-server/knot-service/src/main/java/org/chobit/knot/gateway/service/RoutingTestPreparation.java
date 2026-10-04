package org.chobit.knot.gateway.service;

import org.chobit.knot.gateway.constants.enums.ModelApiProtocolEnum;
import org.chobit.knot.gateway.dto.routing.RoutingRuleDto;
import org.chobit.knot.gateway.dto.routing.RoutingRuleTargetDto;

import java.util.Map;

/**
 * 路由规则测试的「一次性准备结果」。
 *
 * <p>同步测试（{@code testInvoke}）与流式测试（{@code openTestStream}）共用同一套校验与
 * 请求体构造逻辑，全部收敛到 {@link RoutingRuleService#prepareTestInvocation}，
 * 避免两条链路出现校验差异。</p>
 *
 * @param rule       命中的路由规则
 * @param secretKey  消费者 API Key（仅用于转发与 curl 展示，不写入日志）
 * @param target     解析后的调试目标
 * @param protocol   归一后的接口协议
 * @param model      路由目标的业务模型码（上游 model 由网关按此覆盖）
 * @param body       转发给网关的请求体（已剔除 model，保留 stream）
 * @param baseUrl    网关基地址
 * @param gatewayPath网关调试路径
 * @param curl       等价 curl 命令（供调试面板展示）
 */
public record RoutingTestPreparation(
        RoutingRuleDto rule,
        String secretKey,
        RoutingRuleTargetDto target,
        ModelApiProtocolEnum protocol,
        String model,
        Map<String, Object> body,
        String baseUrl,
        String gatewayPath,
        String curl
) {
}
