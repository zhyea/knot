package org.chobit.knot.svc.service;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashMap;
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
import org.chobit.knot.common.util.TextSupport;
import org.chobit.knot.svc.dto.routing.RoutingRuleDto;
import org.chobit.knot.svc.dto.routing.RoutingRuleTargetDto;
import org.chobit.knot.svc.dto.routing.RoutingTestPreparation;
import org.chobit.knot.svc.dto.routing.RoutingTestStreamHandle;
import org.chobit.knot.dal.entity.ModelApiBindingEntity;
import org.chobit.knot.dal.entity.ModelEntity;
import org.chobit.knot.dal.entity.ModelPoolEntity;
import org.chobit.knot.dal.entity.ModelPoolItemEntity;
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
import org.chobit.knot.svc.vo.routing.RoutingDebugPresetItem;
import org.chobit.knot.svc.vo.routing.RoutingDebugTargetItem;
import org.chobit.knot.svc.vo.routing.RoutingRuleDebugContext;
import org.chobit.knot.svc.vo.routing.RoutingTestResult;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpStatusCodeException;

/**
 * 路由规则调试域：同步测试、流式测试、调试能力下发、调试上下文（逐目标协议 + 预设）。
 * 规则本身的读写（CRUD、审计快照、写路径校验）留在 {@code RoutingRuleService}；
 * 调试一侧只通过 {@link RoutingRuleService#getById} 读规则，不触碰写路径。
 */
@Service
public class RoutingRuleDebugService {

    /** 目标无可调试能力时的空结果（目标失效 / 类型不支持），避免各处各造一个空集 */
    private static final TargetCapability EMPTY_CAPABILITY =
        new TargetCapability(Set.of(), Set.of());

    private final RoutingRuleService routingRuleService;
    private final RoutingConsumerMapper routingConsumerMapper;
    private final ModelApiBindingMapper modelApiBindingMapper;
    private final ModelMapper modelMapper;
    private final ModelPoolMapper modelPoolMapper;
    private final RoutingTestPresetService presetService;
    private final RoutingRuleDebugRunner debugRunner;

    public RoutingRuleDebugService(RoutingRuleService routingRuleService,
                                   RoutingConsumerMapper routingConsumerMapper,
                                   ModelApiBindingMapper modelApiBindingMapper,
                                   ModelMapper modelMapper,
                                   ModelPoolMapper modelPoolMapper,
                                   RoutingTestPresetService presetService,
                                   RoutingRuleDebugRunner debugRunner) {
        this.routingRuleService = routingRuleService;
        this.routingConsumerMapper = routingConsumerMapper;
        this.modelApiBindingMapper = modelApiBindingMapper;
        this.modelMapper = modelMapper;
        this.modelPoolMapper = modelPoolMapper;
        this.presetService = presetService;
        this.debugRunner = debugRunner;
    }

    /**
     * 单个调试目标解析出的能力：可调试协议 + 模型族。两者同源（都由目标实际关联的模型推导），
     * 分开只是为了让「协议并集」与「族并集」各自聚合。
     */
    private record TargetCapability(Set<ModelApiProtocolEnum> protocols, Set<String> families) {
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
     * 校验失败时抛出 {@link BusinessException}，由全局异常处理器在建立 SSE 之前返回 4xx {@code ApiResponse}；调用方必须关闭返回的句柄。
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
     * 默认请求体由「预设请求」用例（kb_test_request_presets）维护，调试面板按当前协议从预设中载入具体请求体。
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
     * 调试面板的上下文：逐目标下发可调试协议、模型族与该作用域内已筛好的预设请求，
     * 再给出「全部目标」的协议与预设并集。
     *
     * <p>协议与调试准入校验 {@link #resolveTestProtocol} 共用 {@link #capabilityOf}，
     * 前端不自行解析模型 / 模型池的协议绑定；预设筛选也只在后端一处实现，
     * 避免「面板能选、发出去被拒」这类两份实现漂移。</p>
     */
    public RoutingRuleDebugContext listDebugContext(Long ruleId) {
        List<RoutingRuleTargetDto> targets = routingRuleService.getById(ruleId).targets();
        List<RoutingDebugPresetItem> activePresets = activePresets();
        List<RoutingDebugTargetItem> items = new ArrayList<>();
        Set<ModelApiProtocolEnum> allProtocols = new LinkedHashSet<>();
        Set<String> allFamilies = new LinkedHashSet<>();
        for (RoutingRuleTargetDto target : targets == null ? List.<RoutingRuleTargetDto>of() : targets) {
            if (target.targetId() == null) {
                continue;
            }
            TargetCapability capability = capabilityOf(target);
            allProtocols.addAll(capability.protocols());
            allFamilies.addAll(capability.families());
            items.add(new RoutingDebugTargetItem(
                RouteTargetTypeEnum.ofOrDefault(target.targetType(), RouteTargetTypeEnum.MODEL).code(),
                target.targetId(),
                target.targetCode(),
                target.targetName(),
                target.primary(),
                List.copyOf(capability.families()),
                protocolCodes(capability.protocols()),
                filterPresets(activePresets, capability.families(), capability.protocols())
            ));
        }
        return new RoutingRuleDebugContext(
            items,
            protocolCodes(allProtocols),
            filterPresets(activePresets, allFamilies, allProtocols)
        );
    }

    /** 调试面板可选的全部预设（仅启用中的用例）；请求体不进上下文，选中后按 id 懒加载。 */
    private List<RoutingDebugPresetItem> activePresets() {
        return presetService.listActiveOptions().stream()
            .map(dto -> new RoutingDebugPresetItem(dto.id(), dto.name(), dto.protocolCode(), dto.modelFamily()))
            .toList();
    }

    private static List<String> protocolCodes(Set<ModelApiProtocolEnum> protocols) {
        return protocols.stream().map(ModelApiProtocolEnum::code).toList();
    }

    /**
     * 预设筛选口径（模型族优先，协议兜底）：
     * 目标（或全部目标）解析出模型族时，只留同族用例与通用用例（{@code modelFamily == null}），
     * 并把同族用例排在通用用例之前——下拉默认选中第一项，顺序即「默认优先展示绑定模型族的预设」；
     * 一个族都没绑定时，仅按协议筛且保持原顺序。协议集合为空则无可选用例。
     */
    private static List<RoutingDebugPresetItem> filterPresets(List<RoutingDebugPresetItem> presets,
                                                              Set<String> families,
                                                              Set<ModelApiProtocolEnum> protocols) {
        if (protocols.isEmpty()) {
            return List.of();
        }
        List<RoutingDebugPresetItem> filtered = presets.stream()
            .filter(preset -> protocols.stream()
                .anyMatch(protocol -> protocol.code().equals(preset.protocolCode())))
            .filter(preset -> families.isEmpty()
                || preset.modelFamily() == null
                || families.contains(preset.modelFamily()))
            .toList();
        if (families.isEmpty()) {
            return filtered;
        }
        // 族命中优先（通用用例 modelFamily == null 退后）：稳定排序保持同组内原有顺序（预设按 id 倒序）。
        // 判据用「非空」而不是 families.contains：上面已把异族筛掉，留到这里的非空族必然命中。
        return filtered.stream()
            .sorted(Comparator.comparingInt(
                (RoutingDebugPresetItem preset) -> preset.modelFamily() == null ? 1 : 0))
            .toList();
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

    /**
     * 目标可调试协议 + 模型族：两者都必须由<b>实际关联的模型</b>推导。
     * 直挂模型取自身；模型池取池内启用模型的并集（池内能力允许不一致，任一模型支持即可发起调试，
     * 实际由哪个模型承接由网关按请求协议筛候选）。
     */
    private TargetCapability capabilityOf(RoutingRuleTargetDto target) {
        RouteTargetTypeEnum targetType =
            RouteTargetTypeEnum.ofOrDefault(target.targetType(), RouteTargetTypeEnum.MODEL);
        if (targetType == RouteTargetTypeEnum.MODEL) {
            ModelEntity model = modelMapper.getByCode(target.targetCode());
            if (model == null) {
                // 模型已删 / code 失效：退回模型类型的协议口径，族无从解析
                return new TargetCapability(fallbackProtocolsForModelType(target.modelType()), Set.of());
            }
            return new TargetCapability(
                supportedProtocolsForModel(model.getId(), target.modelType()),
                familyCodesOf(model)
            );
        }
        if (targetType != RouteTargetTypeEnum.MODEL_POOL) {
            return EMPTY_CAPABILITY;
        }
        ModelPoolEntity pool = modelPoolMapper.getById(target.targetId());
        if (pool == null) {
            return EMPTY_CAPABILITY;
        }
        List<ModelEntity> models = enabledModelsOf(pool);
        if (models.isEmpty()) {
            return new TargetCapability(fallbackProtocolsForModelType(target.modelType()), Set.of());
        }
        Set<ModelApiProtocolEnum> protocols = new LinkedHashSet<>();
        Set<String> families = new LinkedHashSet<>();
        for (ModelEntity model : models) {
            protocols.addAll(supportedProtocolsForModel(model.getId(), target.modelType()));
            families.addAll(familyCodesOf(model));
        }
        return new TargetCapability(protocols, families);
    }

    /** 池内启用模型（按模型主键去重，保持池内优先级顺序） */
    private List<ModelEntity> enabledModelsOf(ModelPoolEntity pool) {
        Map<Long, ModelEntity> models = new LinkedHashMap<>();
        for (ModelPoolItemEntity item : modelPoolMapper.listItemsByPoolCode(pool.getPoolCode())) {
            if (!EnabledStatusEnum.isEnabled(item.getStatus())) {
                continue;
            }
            ModelEntity model = modelMapper.getByCode(item.getModelCode());
            if (model != null) {
                models.putIfAbsent(model.getId(), model);
            }
        }
        return List.copyOf(models.values());
    }

    /** 模型绑定的模型族 code；未绑定返回空集合（预设筛选随之退回「只按协议」） */
    private static Set<String> familyCodesOf(ModelEntity model) {
        String family = TextSupport.blankToNull(model.getModelFamilyCode());
        return family == null ? Set.of() : Set.of(family);
    }

    private Set<ModelApiProtocolEnum> supportedProtocolsForTarget(RoutingRuleTargetDto target) {
        return capabilityOf(target).protocols();
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
