package org.chobit.knot.svc.service;

import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.chobit.knot.common.constants.enums.EnabledStatusEnum;
import org.chobit.knot.common.constants.enums.ModelApiProtocolEnum;
import org.chobit.knot.common.constants.enums.ModelTypeEnum;
import org.chobit.knot.common.constants.enums.RouteTargetTypeEnum;
import org.chobit.knot.common.constants.enums.RoutingTestStatusEnum;
import org.chobit.knot.svc.dto.routing.RoutingRuleDto;
import org.chobit.knot.svc.dto.routing.RoutingRuleTargetDto;
import org.chobit.knot.svc.dto.routing.RoutingTestPreparation;
import org.chobit.knot.svc.dto.routing.RoutingTestStreamHandle;
import org.chobit.knot.dal.entity.ModelApiBindingEntity;
import org.chobit.knot.dal.entity.ModelEntity;
import org.chobit.knot.dal.entity.ModelPoolEntity;
import org.chobit.knot.dal.entity.RoutingConsumerEntity;
import org.chobit.knot.common.error.BusinessException;
import org.chobit.knot.common.error.ErrorCode;
import org.chobit.knot.dal.mapper.ModelApiBindingMapper;
import org.chobit.knot.dal.mapper.ModelMapper;
import org.chobit.knot.dal.mapper.ModelPoolMapper;
import org.chobit.knot.dal.mapper.RoutingConsumerMapper;
import org.chobit.knot.svc.service.support.RoutingRuleDebugRunner;
import org.chobit.knot.svc.service.support.RoutingRuleDebugSupport;
import org.chobit.knot.svc.vo.routing.ProtocolDebugCapabilityItem;
import org.chobit.knot.svc.vo.routing.RoutingRuleDebugProtocols;
import org.chobit.knot.svc.vo.routing.RoutingTargetDebugProtocols;
import org.chobit.knot.svc.vo.routing.RoutingTestResult;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpStatusCodeException;

/**
 * 路由规则调试域：同步测试、流式测试、调试能力下发、逐目标协议枚举。
 *
 * <p>从 {@code RoutingRuleService} 拆出的边界是「是否向网关发起真实请求」与「是否解析模型/模型池的
 * 协议绑定」：规则本身的读写（CRUD、审计快照、写路径校验）留在 {@code RoutingRuleService}，
 * 调试一侧只通过它读规则（{@link RoutingRuleService#getById}），不触碰写路径。</p>
 */
@Service
public class RoutingRuleDebugService {

    private final RoutingRuleService routingRuleService;
    private final RoutingConsumerMapper routingConsumerMapper;
    private final ModelApiBindingMapper modelApiBindingMapper;
    private final ModelMapper modelMapper;
    private final ModelPoolMapper modelPoolMapper;
    private final RoutingRuleDebugRunner debugRunner;

    public RoutingRuleDebugService(RoutingRuleService routingRuleService,
                                   RoutingConsumerMapper routingConsumerMapper,
                                   ModelApiBindingMapper modelApiBindingMapper,
                                   ModelMapper modelMapper,
                                   ModelPoolMapper modelPoolMapper,
                                   RoutingRuleDebugRunner debugRunner) {
        this.routingRuleService = routingRuleService;
        this.routingConsumerMapper = routingConsumerMapper;
        this.modelApiBindingMapper = modelApiBindingMapper;
        this.modelMapper = modelMapper;
        this.modelPoolMapper = modelPoolMapper;
        this.debugRunner = debugRunner;
    }

    /**
     * Sends a test request through the gateway and returns the invocation result.
     *
     * @param gatewayBaseUrl 网关基地址（管理端配置透传，已归一化）
     */
    public RoutingTestResult testInvoke(String gatewayBaseUrl,
                                        Long ruleId,
                                        String secretKey,
                                        String prompt,
                                        String protocolCode,
                                        String targetType,
                                        Long targetId,
                                        Map<String, Object> requestBody) {
        RoutingTestPreparation prep = prepareTestInvocation(
            gatewayBaseUrl, ruleId, secretKey, prompt, protocolCode, targetType, targetId, requestBody);
        try {
            String responseBody = debugRunner.invoke(prep.baseUrl(), prep.gatewayPath(), prep.secretKey(),
                prep.rule().ruleCode(), prep.body());
            return buildTestResult(prep, RoutingTestStatusEnum.SUCCESS.code(), 200, responseBody, null);
        } catch (HttpStatusCodeException ex) {
            return buildTestResult(prep, RoutingTestStatusEnum.FAILED.code(), ex.getStatusCode().value(),
                ex.getResponseBodyAsString(), ex.getMessage());
        } catch (Exception ex) {
            return buildTestResult(prep, RoutingTestStatusEnum.FAILED.code(), null, null, ex.getMessage());
        }
    }

    /**
     * 打开一条流式调试通道：完成全部校验后向网关发起请求，并持有未关闭的响应体供增量读取。
     *
     * <p>校验失败时抛出 {@link BusinessException}，由全局异常处理器在建立 SSE 之前返回
     * 4xx {@code ApiResponse}；一旦返回本方法，调用方必须关闭返回的句柄。</p>
     *
     * @param gatewayBaseUrl 网关基地址（管理端配置透传，已归一化）
     */
    public RoutingTestStreamHandle openTestStream(String gatewayBaseUrl,
                                                  Long ruleId,
                                                  String secretKey,
                                                  String prompt,
                                                  String protocolCode,
                                                  String targetType,
                                                  Long targetId,
                                                  Map<String, Object> requestBody) {
        RoutingTestPreparation prep = prepareTestInvocation(
            gatewayBaseUrl, ruleId, secretKey, prompt, protocolCode, targetType, targetId, requestBody);
        ClientHttpResponse response = debugRunner.openStream(prep.baseUrl(), prep.gatewayPath(),
            prep.secretKey(), prep.rule().ruleCode(), prep.body());
        return new RoutingTestStreamHandle(response, prep);
    }

    /**
     * 调试能力下发：网关路径、说明文案与 prompt 字段路径。
     * 默认请求体不再硬编码，改由「预设请求」用例（kb_test_request_presets）维护，
     * 调试面板按当前协议从预设中载入具体请求体。
     */
    public List<ProtocolDebugCapabilityItem> listDebugCapabilities() {
        return Arrays.stream(ModelApiProtocolEnum.values())
            .filter(RoutingRuleDebugSupport.DEBUGGABLE_PROTOCOLS::contains)
            .map(protocol -> new ProtocolDebugCapabilityItem(
                protocol.code(),
                protocol.canonical().code(),
                RoutingRuleDebugSupport.buildGatewayTestPath(protocol),
                RoutingRuleDebugSupport.hintOf(protocol),
                RoutingRuleDebugSupport.promptFieldOf(protocol)
            ))
            .toList();
    }

    /**
     * 调试面板协议下拉的数据源：逐目标下发其支持的接口协议。
     *
     * <p>与调试准入校验 {@link #resolveTestProtocol} 共用 {@link #supportedProtocolsForTarget}，
     * 前端不再自行解析模型/模型池的协议绑定，杜绝「下拉列出的协议」与「后端放行的协议」两份实现漂移。
     */
    public RoutingRuleDebugProtocols listDebugProtocols(Long ruleId) {
        List<RoutingRuleTargetDto> targets = routingRuleService.getById(ruleId).targets();
        List<RoutingTargetDebugProtocols> items = (targets == null ? List.<RoutingRuleTargetDto>of() : targets).stream()
            .filter(target -> target.targetId() != null)
            .map(target -> new RoutingTargetDebugProtocols(
                RouteTargetTypeEnum.ofOrDefault(target.targetType(), RouteTargetTypeEnum.MODEL).code(),
                target.targetId(),
                target.primary(),
                supportedProtocolsForTarget(target).stream()
                    .map(ModelApiProtocolEnum::code)
                    .toList()
            ))
            .toList();
        return new RoutingRuleDebugProtocols(items);
    }

    /**
     * 同步 / 流式共用的准备步骤：查规则、校验消费者、校验启用状态、解析目标与协议、
     * 构造请求体（保留 stream、剔除 model）、生成网关地址与 curl。
     *
     * @param gatewayBaseUrl 网关基地址（管理端配置透传，已归一化）
     */
    private RoutingTestPreparation prepareTestInvocation(String gatewayBaseUrl,
                                                         Long ruleId,
                                                         String secretKey,
                                                         String prompt,
                                                         String protocolCode,
                                                         String targetType,
                                                         Long targetId,
                                                         Map<String, Object> requestBody) {
        RoutingRuleDto rule = routingRuleService.getById(ruleId);
        RoutingConsumerEntity consumer = findBoundConsumerBySecretKey(rule.consumerCode(), secretKey);
        if (consumer == null) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "消费者不存在或未启用");
        }
        if (!rule.enabled()) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "路由规则未启用");
        }
        RoutingRuleTargetDto selectedTarget = resolveTestTarget(rule, targetType, targetId);
        ModelApiProtocolEnum protocol = resolveTestProtocol(protocolCode, selectedTarget);
        // 客户端无需传 model：请求体不带 model，网关按路由目标的上游模型（kb_models.upstream_model）覆盖
        String model = selectedTarget.targetCode();
        String userPrompt = RoutingRuleDebugSupport.normalizeTestPrompt(prompt);
        Map<String, Object> body = RoutingRuleDebugSupport.buildRequestBody(protocol, userPrompt, requestBody);

        String gatewayPath = RoutingRuleDebugSupport.buildGatewayTestPath(protocol);
        String curl = RoutingRuleDebugSupport.buildTestCurl(
            gatewayBaseUrl, gatewayPath, secretKey, rule.ruleCode(), body);
        return new RoutingTestPreparation(rule, secretKey, selectedTarget, protocol, model,
            body, gatewayBaseUrl, gatewayPath, curl);
    }

    private RoutingTestResult buildTestResult(RoutingTestPreparation prep,
                                              String status,
                                              Integer httpStatus,
                                              String responseBody,
                                              String errorMessage) {
        return new RoutingTestResult(
            prep.rule().id(),
            prep.target().providerAccountCode(),
            prep.target().targetId(),
            prep.model(),
            prep.protocol().code(),
            status,
            prep.curl(),
            httpStatus,
            responseBody,
            errorMessage
        );
    }

    private RoutingRuleTargetDto resolveTestTarget(RoutingRuleDto rule, String targetType, Long targetId) {
        List<RoutingRuleTargetDto> targets = rule.targets() == null ? List.of() : rule.targets();
        if (targetId != null) {
            RouteTargetTypeEnum expected =
                    RouteTargetTypeEnum.ofOrDefault(targetType, RouteTargetTypeEnum.MODEL);
            return targets.stream()
                .filter(target -> targetId.equals(target.targetId()))
                .filter(target -> RouteTargetTypeEnum.ofOrDefault(
                        target.targetType(), RouteTargetTypeEnum.MODEL) == expected)
                .findFirst()
                .orElseThrow(() -> new BusinessException(ErrorCode.VALIDATION_ERROR, "调试目标不存在"));
        }
        return targets.stream()
            .filter(RoutingRuleTargetDto::primary)
            .findFirst()
            .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "路由规则未配置主模型"));
    }

    private ModelApiProtocolEnum resolveTestProtocol(String protocolCode, RoutingRuleTargetDto target) {
        ModelApiProtocolEnum protocol = ModelApiProtocolEnum.fromCode(protocolCode);
        if (protocol == null) {
            protocol = firstSupportedProtocol(target);
        } else {
            protocol = protocol.canonical();
        }
        if (protocol == null || !RoutingRuleDebugSupport.DEBUGGABLE_PROTOCOLS.contains(protocol)) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "当前调试暂不支持该接口协议");
        }
        Set<ModelApiProtocolEnum> supported = supportedProtocolsForTarget(target);
        if (!supported.contains(protocol)) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "所选目标不支持该接口协议");
        }
        return protocol;
    }

    private ModelApiProtocolEnum firstSupportedProtocol(RoutingRuleTargetDto target) {
        return supportedProtocolsForTarget(target).stream().findFirst().orElse(ModelApiProtocolEnum.CHAT_COMPLETIONS);
    }

    private Set<ModelApiProtocolEnum> supportedProtocolsForTarget(RoutingRuleTargetDto target) {
        RouteTargetTypeEnum targetType =
                RouteTargetTypeEnum.ofOrDefault(target.targetType(), RouteTargetTypeEnum.MODEL);
        if (targetType == RouteTargetTypeEnum.MODEL) {
            return supportedProtocolsForModel(target.targetId(), target.modelType());
        }
        if (targetType != RouteTargetTypeEnum.MODEL_POOL) {
            return Set.of();
        }
        // 池条目只存 model_code，协议绑定仍按模型主键 id 查询，这里做一次 code → id 解析
        ModelPoolEntity protocolPool = modelPoolMapper.getById(target.targetId());
        if (protocolPool == null) {
            return Set.of();
        }
        List<Long> enabledModelIds = modelPoolMapper.listItemsByPoolCode(protocolPool.getPoolCode()).stream()
            .filter(item -> EnabledStatusEnum.isEnabled(item.getStatus()))
            .map(item -> modelMapper.getByCode(item.getModelCode()))
            .filter(model -> model != null)
            .map(ModelEntity::getId)
            .distinct()
            .toList();
        if (enabledModelIds.isEmpty()) {
            return fallbackProtocolsForModelType(target.modelType());
        }
        // 池内模型能力允许不一致，取并集：只要池内任一模型支持该协议即可发起调试。
        // 实际由哪个模型承接，由网关按请求协议筛选候选（见 RoutingResolver 的协议过滤）。
        Set<ModelApiProtocolEnum> union = new LinkedHashSet<>();
        for (Long modelId : enabledModelIds) {
            union.addAll(supportedProtocolsForModel(modelId, target.modelType()));
        }
        return union.isEmpty() ? fallbackProtocolsForModelType(target.modelType()) : union;
    }

    private Set<ModelApiProtocolEnum> supportedProtocolsForModel(Long modelId, String modelType) {
        Set<ModelApiProtocolEnum> bindings = modelApiBindingMapper.listByModelId(modelId).stream()
            .filter(item -> EnabledStatusEnum.isEnabled(item.getStatus()))
            .map(ModelApiBindingEntity::getProtocol)
            .map(ModelApiProtocolEnum::fromCode)
            .filter(item -> item != null && item.defaultPath() != null)
            .map(ModelApiProtocolEnum::canonical)
            .filter(RoutingRuleDebugSupport.DEBUGGABLE_PROTOCOLS::contains)
            .collect(Collectors.toCollection(LinkedHashSet::new));
        if (!bindings.isEmpty()) {
            return bindings;
        }
        return fallbackProtocolsForModelType(modelType);
    }

    private Set<ModelApiProtocolEnum> fallbackProtocolsForModelType(String modelType) {
        return ModelTypeEnum.canonicalProtocolsOf(modelType);
    }

    /**
     * 密钥与规则绑定的消费者比对：消费者未启用、密钥不匹配一律视为「不存在」。
     */
    private RoutingConsumerEntity findBoundConsumerBySecretKey(String consumerCode, String secretKey) {
        String normalized = secretKey != null ? secretKey.trim() : "";
        if (consumerCode == null || consumerCode.isBlank() || normalized.isEmpty()) {
            return null;
        }
        RoutingConsumerEntity consumer = routingConsumerMapper.getByCode(consumerCode.trim());
        if (consumer != null
            && EnabledStatusEnum.isEnabled(consumer.getStatus())
            && normalized.equals(consumer.getSecretKey())) {
            return consumer;
        }
        return null;
    }
}
