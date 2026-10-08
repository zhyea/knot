package org.chobit.knot.gateway.service;

import com.github.pagehelper.Page;
import com.github.pagehelper.PageHelper;
import com.github.pagehelper.PageInfo;
import org.chobit.knot.gateway.config.GatewayRuntimeProperties;
import org.chobit.knot.gateway.constants.enums.EnabledStatusEnum;
import org.chobit.knot.gateway.constants.enums.ModelApiProtocolEnum;
import org.chobit.knot.gateway.constants.enums.ModelTypeEnum;
import org.chobit.knot.gateway.constants.enums.RoutingTestStatusEnum;
import org.chobit.knot.gateway.constants.enums.TrafficResourceTypeEnum;
import org.chobit.knot.gateway.converter.RoutingRuleConverter;
import org.chobit.knot.gateway.dto.routing.RoutingRuleDto;
import org.chobit.knot.gateway.dto.routing.RoutingRuleTargetDto;
import org.chobit.knot.gateway.entity.AppEntity;
import org.chobit.knot.gateway.entity.ModelApiBindingEntity;
import org.chobit.knot.gateway.entity.ModelEntity;
import org.chobit.knot.gateway.entity.ModelPoolEntity;
import org.chobit.knot.gateway.entity.RoutingConsumerEntity;
import org.chobit.knot.gateway.entity.RoutingRuleConsumerEntity;
import org.chobit.knot.gateway.entity.RoutingRuleEntity;
import org.chobit.knot.gateway.entity.RoutingRuleTargetEntity;
import org.chobit.knot.gateway.error.BusinessException;
import org.chobit.knot.gateway.error.ErrorCode;
import org.chobit.knot.gateway.mapper.AppMapper;
import org.chobit.knot.gateway.mapper.ModelApiBindingMapper;
import org.chobit.knot.gateway.mapper.ModelMapper;
import org.chobit.knot.gateway.mapper.ModelPoolMapper;
import org.chobit.knot.gateway.mapper.RoutingConsumerMapper;
import org.chobit.knot.gateway.mapper.RoutingRuleConsumerMapper;
import org.chobit.knot.gateway.mapper.RoutingRuleMapper;
import org.chobit.knot.gateway.mapper.RoutingRuleTargetMapper;
import org.chobit.knot.gateway.model.PageRequest;
import org.chobit.knot.gateway.model.PageResult;
import org.chobit.knot.gateway.model.QuotaPolicy;
import org.chobit.knot.gateway.model.RateLimitPolicy;
import org.chobit.knot.gateway.model.RetryPolicy;
import org.chobit.knot.gateway.model.TrafficPolicies;
import org.chobit.knot.gateway.util.JsonKit;
import org.chobit.knot.gateway.util.tools.RoutingRuleCodeGenerator;
import org.chobit.knot.gateway.vo.routing.ProtocolDebugCapabilityItem;
import org.chobit.knot.gateway.vo.routing.RoutingTestResult;
import org.springframework.http.MediaType;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.core.io.FileSystemResource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestClient;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class RoutingRuleService {

    private static final int RULE_CODE_MAX_LEN = 64;
    private static final String TRACEPARENT = "00-00000000000000000000000000000001-0000000000000001-01";
    private static final String DEFAULT_TEST_PROMPT = "你好，这是一条路由规则测试消息";
    /** 调试链路连接网关的超时 */
    private static final int CONNECT_TIMEOUT_MS = 5_000;
    /** 流式读取的空闲超时（按相邻数据间隔计算，不限制整包总时长） */
    private static final int READ_IDLE_TIMEOUT_MS = 120_000;
    private static final Set<ModelApiProtocolEnum> DEBUGGABLE_PROTOCOLS = Set.of(
            ModelApiProtocolEnum.CHAT_COMPLETIONS,
            ModelApiProtocolEnum.RESPONSES,
            ModelApiProtocolEnum.MESSAGES,
            ModelApiProtocolEnum.COMPLETIONS,
            ModelApiProtocolEnum.EMBEDDINGS,
            ModelApiProtocolEnum.IMAGE_GENERATIONS,
            ModelApiProtocolEnum.IMAGE_EDITS,
            ModelApiProtocolEnum.IMAGE_VARIATIONS,
            ModelApiProtocolEnum.AUDIO_TRANSCRIPTIONS,
            ModelApiProtocolEnum.AUDIO_TRANSLATIONS,
            ModelApiProtocolEnum.AUDIO_SPEECH,
            ModelApiProtocolEnum.VIDEO_GENERATIONS,
            ModelApiProtocolEnum.RERANK,
            ModelApiProtocolEnum.MODERATIONS
    );

    private final RoutingRuleMapper routingRuleMapper;

    /**
     * 调试面板的协议说明文案。与能力接口一并下发，前端不再自带 PROTOCOL_HINTS。
     */
    private static final Map<ModelApiProtocolEnum, String> PROTOCOL_DEBUG_HINTS = Map.ofEntries(
            Map.entry(ModelApiProtocolEnum.CHAT_COMPLETIONS, "适用于标准对话请求，通常需要 messages。"),
            Map.entry(ModelApiProtocolEnum.RESPONSES, "适用于 OpenAI Responses 协议，通常使用 input。"),
            Map.entry(ModelApiProtocolEnum.MESSAGES, "适用于 Anthropic Messages 协议，通常需要 messages 和 max_tokens。"),
            Map.entry(ModelApiProtocolEnum.COMPLETIONS, "适用于传统文本补全协议，通常使用 prompt。"),
            Map.entry(ModelApiProtocolEnum.EMBEDDINGS, "适用于向量化请求，通常使用 input。"),
            Map.entry(ModelApiProtocolEnum.IMAGE_GENERATIONS, "适用于文生图，通常使用 prompt。"),
            Map.entry(ModelApiProtocolEnum.IMAGE_EDITS, "适用于图像编辑，通常需要 prompt 和 image。image 可先用 URL、data URL 或占位值维护模板。"),
            Map.entry(ModelApiProtocolEnum.IMAGE_VARIATIONS, "适用于图像变体生成，通常需要 image。"),
            Map.entry(ModelApiProtocolEnum.AUDIO_TRANSCRIPTIONS, "适用于语音转录，通常需要 file。"),
            Map.entry(ModelApiProtocolEnum.AUDIO_TRANSLATIONS, "适用于语音翻译，通常需要 file。"),
            Map.entry(ModelApiProtocolEnum.AUDIO_SPEECH, "适用于语音合成，通常使用 input 和 voice。"),
            Map.entry(ModelApiProtocolEnum.VIDEO_GENERATIONS, "适用于视频生成，通常使用 prompt。"),
            Map.entry(ModelApiProtocolEnum.RERANK, "适用于重排序，通常使用 query 和 documents。"),
            Map.entry(ModelApiProtocolEnum.MODERATIONS, "适用于内容安全审核，通常使用 input。")
    );

    private final RoutingRuleTargetMapper routingRuleTargetMapper;
    private final RoutingRuleConsumerMapper routingRuleConsumerMapper;
    private final RoutingConsumerMapper routingConsumerMapper;
    private final ModelApiBindingMapper modelApiBindingMapper;
    private final ModelMapper modelMapper;
    private final ModelPoolMapper modelPoolMapper;
    private final AppMapper appMapper;
    private final RoutingRuleConverter routingRuleConverter;
    private final ResourceTrafficPolicySupport trafficPolicySupport;
    private final GatewayRuntimeProperties gatewayRuntimeProperties;
    private final RestClient restClient;

    /**
     * Constructs a new instance.
     */
    public RoutingRuleService(RoutingRuleMapper routingRuleMapper,
                              RoutingRuleTargetMapper routingRuleTargetMapper,
                              RoutingRuleConsumerMapper routingRuleConsumerMapper,
                              RoutingConsumerMapper routingConsumerMapper,
                              ModelApiBindingMapper modelApiBindingMapper,
                              ModelMapper modelMapper,
                              ModelPoolMapper modelPoolMapper,
                              AppMapper appMapper,
                              RoutingRuleConverter routingRuleConverter,
                              ResourceTrafficPolicySupport trafficPolicySupport,
                              GatewayRuntimeProperties gatewayRuntimeProperties) {
        this.routingRuleMapper = routingRuleMapper;
        this.routingRuleTargetMapper = routingRuleTargetMapper;
        this.routingRuleConsumerMapper = routingRuleConsumerMapper;
        this.routingConsumerMapper = routingConsumerMapper;
        this.modelApiBindingMapper = modelApiBindingMapper;
        this.modelMapper = modelMapper;
        this.modelPoolMapper = modelPoolMapper;
        this.appMapper = appMapper;
        this.routingRuleConverter = routingRuleConverter;
        this.trafficPolicySupport = trafficPolicySupport;
        this.gatewayRuntimeProperties = gatewayRuntimeProperties;
        this.restClient = createRestClient();
    }

    /**
     * 调试链路专用客户端：显式设置连接超时与流式读取空闲超时。
     */
    private static RestClient createRestClient() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(CONNECT_TIMEOUT_MS);
        factory.setReadTimeout(READ_IDLE_TIMEOUT_MS);
        return RestClient.builder()
                .requestFactory(factory)
                .build();
    }

    /**
     * Lists matching results. Executes the public operation.
     */
    public PageResult<RoutingRuleDto> list(PageRequest pageRequest) {
        return list(pageRequest, null);
    }

    /**
     * Lists matching results. Executes the public operation.
     */
    public PageResult<RoutingRuleDto> list(PageRequest pageRequest, String keyword) {
        try (Page<?> ignored = PageHelper.startPage(pageRequest.pageNum(), pageRequest.pageSize())) {
            PageInfo<RoutingRuleEntity> pageInfo = new PageInfo<>(routingRuleMapper.list(
                    normalizeNullable(keyword)
            ));
            List<RoutingRuleDto> dtos = enrichList(pageInfo.getList());
            return PageResult.of(dtos, pageInfo.getTotal(), pageRequest.pageNum(), pageRequest.pageSize());
        }
    }

    /**
     * Returns the requested value. Executes the public operation.
     */
    public RoutingRuleDto getById(Long id) {
        RoutingRuleEntity entity = routingRuleMapper.getById(id);
        if (entity == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "路由规则不存在");
        }
        return enrich(entity);
    }

    /**
     * Returns the audit snapshot used by {@code @OperationLog} SpEL expressions.
     *
     * <p>The operation log aspect evaluates this method both before and after
     * routing rule updates. A missing rule is treated as an empty snapshot so
     * that audit collection never changes the outcome of the business request.</p>
     */
    public Map<String, Object> routingRuleAuditSnapshot(Long id) {
        if (id == null) {
            return null;
        }
        try {
            return JsonKit.toMaskedMap(getById(id));
        } catch (BusinessException e) {
            return null;
        }
    }

    /**
     * Returns whether the current condition is satisfied. Executes the public operation.
     */
    public boolean isRuleCodeAvailable(String ruleCode, Long excludeId) {
        String normalized = normalizeRuleCode(ruleCode);
        if (normalized.isEmpty()) {
            return false;
        }
        Long count = routingRuleMapper.countByRuleCode(normalized, excludeId);
        return count == null || count == 0;
    }

    /**
     * Creates a new resource. Executes the public operation.
     */
    @Transactional
    public RoutingRuleDto create(RoutingRuleDto request) {
        RoutingRuleDto normalized = ensureGeneratedFieldsForCreate(request);
        validateForSave(normalized, null);
        RoutingRuleEntity entity = toEntity(normalized);
        entity.setStatus(EnabledStatusEnum.codeOf(normalized.enabled()));
        routingRuleMapper.insert(entity);
        saveConsumers(entity.getId(), normalized.consumerIds());
        saveTargets(entity.getId(), normalized.targets());
        trafficPolicySupport.save(TrafficResourceTypeEnum.ROUTING_RULE.code(), entity.getId(),
                normalized.rateLimitPolicy(), normalized.quotaPolicy());
        return getById(entity.getId());
    }

    /**
     * Updates the target resource. Executes the public operation.
     */
    @Transactional
    public RoutingRuleDto update(Long id, RoutingRuleDto request) {
        if (routingRuleMapper.getById(id) == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "路由规则不存在");
        }
        validateForSave(request, id);
        RoutingRuleEntity entity = toEntity(request);
        entity.setId(id);
        entity.setStatus(EnabledStatusEnum.codeOf(request.enabled()));
        routingRuleMapper.update(entity);
        saveConsumers(id, request.consumerIds());
        saveTargets(id, request.targets());
        trafficPolicySupport.save(TrafficResourceTypeEnum.ROUTING_RULE.code(), id,
                request.rateLimitPolicy(), request.quotaPolicy());
        return getById(id);
    }

    /**
     * Updates the routing rule enabled status only.
     */
    @Transactional
    public RoutingRuleDto updateStatus(Long id, boolean enabled) {
        RoutingRuleDto existing = getById(id);
        RoutingRuleDto request = new RoutingRuleDto(
                existing.id(),
                existing.ruleCode(),
                existing.name(),
                existing.appScenario(),
                existing.consumerIds(),
                existing.consumerNames(),
                existing.appId(),
                existing.appName(),
                enabled,
                existing.targets(),
                existing.rateLimitPolicy(),
                existing.quotaPolicy(),
                existing.retryPolicy()
        );
        validateForSave(request, id);
        routingRuleMapper.updateStatus(id, EnabledStatusEnum.codeOf(enabled));
        return getById(id);
    }

    /**
     * Returns the primary routing target for the given rule.
     */
    public RoutingRuleTargetDto getPrimaryTarget(Long ruleId) {
        return getById(ruleId).targets().stream()
                .filter(RoutingRuleTargetDto::primary)
                .findFirst()
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "路由规则未配置主模型"));
    }

    /**
     * Sends a test request through the gateway and returns the invocation result.
     */
    public RoutingTestResult testInvoke(Long ruleId,
                                        String secretKey,
                                        String prompt,
                                        String protocolCode,
                                        String targetType,
                                        Long targetId,
                                        Map<String, Object> requestBody) {
        RoutingTestPreparation prep = prepareTestInvocation(
                ruleId, secretKey, prompt, protocolCode, targetType, targetId, requestBody);
        try {
            String responseBody = executeGatewayTest(prep.baseUrl(), prep.gatewayPath(), prep.secretKey(),
                    prep.rule().ruleCode(), prep.protocol(), prep.body());
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
     */
    public RoutingTestStreamHandle openTestStream(Long ruleId,
                                                 String secretKey,
                                                 String prompt,
                                                 String protocolCode,
                                                 String targetType,
                                                 Long targetId,
                                                 Map<String, Object> requestBody) {
        RoutingTestPreparation prep = prepareTestInvocation(
                ruleId, secretKey, prompt, protocolCode, targetType, targetId, requestBody);
        // exchange(fn, false) 不自动关闭响应：既不应用默认错误处理器（网关 4xx/5xx 需要原样读取响应体），
        // 也让响应体保持可增量读取
        ClientHttpResponse response = restClient.post()
                .uri(prep.baseUrl() + prep.gatewayPath())
                .header("Authorization", "Bearer " + prep.secretKey())
                .header("Rule", prep.rule().ruleCode())
                .header("traceparent", TRACEPARENT)
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.ALL)
                .body(prep.body())
                .exchange((clientRequest, clientResponse) -> clientResponse, false);
        return new RoutingTestStreamHandle(response, prep);
    }

    /**
     * 同步 / 流式共用的准备步骤：查规则、校验消费者、校验启用状态、解析目标与协议、
     * 构造请求体（保留 stream、剔除 model）、生成网关地址与 curl。
     */
    private RoutingTestPreparation prepareTestInvocation(Long ruleId,
                                                         String secretKey,
                                                         String prompt,
                                                         String protocolCode,
                                                         String targetType,
                                                         Long targetId,
                                                         Map<String, Object> requestBody) {
        RoutingRuleDto rule = getById(ruleId);
        RoutingConsumerEntity consumer = findBoundConsumerBySecretKey(rule.consumerIds(), secretKey);
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
        String userPrompt = normalizeTestPrompt(prompt);
        Map<String, Object> body = buildRequestBody(protocol, userPrompt, requestBody);

        String baseUrl = normalizeGatewayBaseUrl();
        String gatewayPath = buildGatewayTestPath(protocol);
        String curl = buildTestCurl(baseUrl, gatewayPath, secretKey, rule.ruleCode(), protocol, body);
        return new RoutingTestPreparation(rule, secretKey, selectedTarget, protocol, model,
                body, baseUrl, gatewayPath, curl);
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

    private String normalizeGatewayBaseUrl() {
        String base = gatewayRuntimeProperties.getBaseUrl();
        if (base == null || base.isBlank()) {
            return "http://127.0.0.1:9090";
        }
        return base.endsWith("/") ? base.substring(0, base.length() - 1) : base;
    }

    private String executeGatewayTest(String baseUrl,
                                      String gatewayPath,
                                      String secretKey,
                                      String ruleCode,
                                      ModelApiProtocolEnum protocol,
                                      Map<String, Object> body) {
        RestClient.RequestBodySpec request = restClient.post()
                .uri(baseUrl + gatewayPath)
                .header("Authorization", "Bearer " + secretKey)
                .header("Rule", ruleCode)
                .header("traceparent", TRACEPARENT);
        if (ModelApiProtocolEnum.IMAGE_EDITS == protocol) {
            return request.contentType(MediaType.MULTIPART_FORM_DATA)
                    .body(toMultipartBody(body))
                    .retrieve()
                    .body(String.class);
        }
        return request.contentType(MediaType.APPLICATION_JSON)
                .body(body)
                .retrieve()
                .body(String.class);
    }

    private RoutingRuleTargetDto resolveTestTarget(RoutingRuleDto rule, String targetType, Long targetId) {
        List<RoutingRuleTargetDto> targets = rule.targets() == null ? List.of() : rule.targets();
        if (targetId != null) {
            String normalizedType = normalizeTargetType(targetType);
            return targets.stream()
                    .filter(target -> targetId.equals(target.targetId()))
                    .filter(target -> normalizeTargetType(target.targetType()).equals(normalizedType))
                    .findFirst()
                    .orElseThrow(() -> new BusinessException(ErrorCode.VALIDATION_ERROR, "调试目标不存在"));
        }
        return targets.stream()
                .filter(RoutingRuleTargetDto::primary)
                .findFirst()
                .orElseGet(() -> getPrimaryTarget(rule.id()));
    }

    private ModelApiProtocolEnum resolveTestProtocol(String protocolCode, RoutingRuleTargetDto target) {
        ModelApiProtocolEnum protocol = ModelApiProtocolEnum.fromCode(protocolCode);
        if (protocol == null) {
            protocol = firstSupportedProtocol(target);
        } else {
            protocol = protocol.canonical();
        }
        if (protocol == null || !DEBUGGABLE_PROTOCOLS.contains(protocol)) {
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

    private String normalizeTestPrompt(String prompt) {
        String normalized = prompt == null ? "" : prompt.trim();
        return normalized.isEmpty() ? DEFAULT_TEST_PROMPT : normalized;
    }

    private Map<String, Object> buildRequestBody(ModelApiProtocolEnum protocol,
                                                 String prompt,
                                                 Map<String, Object> requestBody) {
        Map<String, Object> body = new LinkedHashMap<>();
        if (requestBody != null && !requestBody.isEmpty()) {
            body.putAll(requestBody);
        }
        body.remove("model");
        fillDefaultPromptFields(protocol, body, prompt);
        return body;
    }

    private void fillDefaultPromptFields(ModelApiProtocolEnum protocol, Map<String, Object> body, String prompt) {
        switch (protocol) {
            case CHAT_COMPLETIONS, MESSAGES -> {
                if (!body.containsKey("messages")) {
                    body.put("messages", List.of(Map.of("role", "user", "content", prompt)));
                }
            }
            case RESPONSES, EMBEDDINGS, AUDIO_SPEECH, MODERATIONS -> body.putIfAbsent("input", prompt);
            case COMPLETIONS, IMAGE_GENERATIONS, IMAGE_EDITS, VIDEO_GENERATIONS -> body.putIfAbsent("prompt", prompt);
            case RERANK -> {
                body.putIfAbsent("query", prompt);
                body.putIfAbsent("documents", List.of("文档 1", "文档 2"));
                body.putIfAbsent("top_n", 2);
            }
            default -> {
            }
        }
    }

    private Set<ModelApiProtocolEnum> supportedProtocolsForTarget(RoutingRuleTargetDto target) {
        String targetType = normalizeTargetType(target.targetType());
        if ("MODEL".equals(targetType)) {
            return supportedProtocolsForModel(target.targetId(), target.modelType());
        }
        if (!"MODEL_POOL".equals(targetType)) {
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
        Set<ModelApiProtocolEnum> intersection = null;
        for (Long modelId : enabledModelIds) {
            Set<ModelApiProtocolEnum> current = supportedProtocolsForModel(modelId, target.modelType());
            if (intersection == null) {
                intersection = new LinkedHashSet<>(current);
            } else {
                intersection.retainAll(current);
            }
        }
        return intersection == null ? Set.of() : intersection;
    }

    private Set<ModelApiProtocolEnum> supportedProtocolsForModel(Long modelId, String modelType) {
        Set<ModelApiProtocolEnum> bindings = modelApiBindingMapper.listByModelId(modelId).stream()
                .filter(item -> EnabledStatusEnum.isEnabled(item.getStatus()))
                .map(ModelApiBindingEntity::getProtocol)
                .map(ModelApiProtocolEnum::fromCode)
                .filter(item -> item != null && item.defaultPath() != null)
                .map(ModelApiProtocolEnum::canonical)
                .filter(DEBUGGABLE_PROTOCOLS::contains)
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
     * 调试能力下发：网关路径、说明文案与 prompt 字段路径。
     * 默认请求体不再硬编码，改由「预设请求」用例（kb_test_request_presets）维护，
     * 调试面板按当前协议从预设中载入具体请求体。
     */
    public List<ProtocolDebugCapabilityItem> listDebugCapabilities() {
        return Arrays.stream(ModelApiProtocolEnum.values())
                .filter(DEBUGGABLE_PROTOCOLS::contains)
                .map(protocol -> new ProtocolDebugCapabilityItem(
                        protocol.code(),
                        protocol.canonical().code(),
                        buildGatewayTestPath(protocol),
                        PROTOCOL_DEBUG_HINTS.get(protocol),
                        promptFieldOf(protocol)
                ))
                .toList();
    }

    /**
     * 请求体中承载 prompt 的字段路径；无 prompt 语义的协议（语音转录、图像变体）返回 null。
     * 与 {@link #fillDefaultPromptFields} 的取值口径保持一致。
     */
    private String promptFieldOf(ModelApiProtocolEnum protocol) {
        return switch (protocol) {
            case CHAT_COMPLETIONS, MESSAGES -> "messages[0].content";
            case RESPONSES, EMBEDDINGS, AUDIO_SPEECH, MODERATIONS -> "input";
            case COMPLETIONS, IMAGE_GENERATIONS, IMAGE_EDITS, VIDEO_GENERATIONS -> "prompt";
            case RERANK -> "query";
            default -> null;
        };
    }

    private String buildGatewayTestPath(ModelApiProtocolEnum protocol) {
        return switch (protocol) {
            case CHAT_COMPLETIONS -> "/openai/v1/chat/completions";
            case RESPONSES -> "/openai/v1/responses";
            case MESSAGES -> "/anthropic/v1/messages";
            case COMPLETIONS -> "/openai/v1/completions";
            case EMBEDDINGS -> "/openai/v1/embeddings";
            case IMAGE_GENERATIONS -> "/openai/v1/images/generations";
            case IMAGE_EDITS -> "/openai/v1/images/edits";
            case IMAGE_VARIATIONS -> "/openai/v1/images/variations";
            case AUDIO_TRANSCRIPTIONS -> "/openai/v1/audio/transcriptions";
            case AUDIO_TRANSLATIONS -> "/openai/v1/audio/translations";
            case AUDIO_SPEECH -> "/openai/v1/audio/speech";
            case VIDEO_GENERATIONS -> "/openai/v1/videos/generations";
            case RERANK -> "/v1/rerank";
            case MODERATIONS -> "/openai/v1/moderations";
            default -> throw new BusinessException(ErrorCode.VALIDATION_ERROR, "当前调试暂不支持该接口协议");
        };
    }

    private static String buildTestCurl(String baseUrl,
                                        String gatewayPath,
                                        String secretKey,
                                        String ruleCode,
                                        ModelApiProtocolEnum protocol,
                                        Map<String, Object> body) {
        if (ModelApiProtocolEnum.IMAGE_EDITS == protocol) {
            return buildMultipartCurl(baseUrl, gatewayPath, secretKey, ruleCode, body);
        }
        String json = JsonKit.toJson(body);
        String escapedJson = json == null ? "{}" : json.replace("'", "'\\''");
        return "curl -X POST '" + baseUrl + gatewayPath + "' \\\n"
                + "  -H 'Authorization: Bearer " + secretKey + "' \\\n"
                + "  -H 'Rule: " + ruleCode + "' \\\n"
                + "  -H 'traceparent: " + TRACEPARENT + "' \\\n"
                + "  -H 'Content-Type: application/json' \\\n"
                + "  -d '" + escapedJson + "'";
    }

    private static String buildMultipartCurl(String baseUrl,
                                             String gatewayPath,
                                             String secretKey,
                                             String ruleCode,
                                             Map<String, Object> body) {
        StringBuilder curl = new StringBuilder()
                .append("curl -X POST '").append(baseUrl).append(gatewayPath).append("' \\\n")
                .append("  -H 'Authorization: Bearer ").append(secretKey).append("' \\\n")
                .append("  -H 'Rule: ").append(ruleCode).append("' \\\n")
                .append("  -H 'traceparent: ").append(TRACEPARENT).append("' ");
        appendMultipartFields(curl, "model", body.get("model"));
        appendMultipartFields(curl, "prompt", body.get("prompt"));
        appendMultipartFields(curl, "image", body.get("image"));
        appendMultipartFields(curl, "mask", body.get("mask"));
        for (Map.Entry<String, Object> entry : body.entrySet()) {
            String key = entry.getKey();
            if (Set.of("model", "prompt", "image", "mask").contains(key)) {
                continue;
            }
            appendMultipartFields(curl, key, entry.getValue());
        }
        return curl.toString().trim();
    }

    private static void appendMultipartFields(StringBuilder curl, String key, Object value) {
        if (value == null) {
            return;
        }
        if (value instanceof Iterable<?> iterable) {
            for (Object item : iterable) {
                appendMultipartFields(curl, key, item);
            }
            return;
        }
        String text = String.valueOf(value);
        if (("image".equals(key) || "mask".equals(key)) && new File(text).exists()) {
            curl.append("\\\n  -F '").append(key).append("=@").append(text.replace("\\", "/")).append("' ");
            return;
        }
        curl.append("\\\n  -F '").append(key).append("=").append(text.replace("'", "'\\''")).append("' ");
    }

    private MultiValueMap<String, Object> toMultipartBody(Map<String, Object> body) {
        MultiValueMap<String, Object> parts = new LinkedMultiValueMap<>();
        for (Map.Entry<String, Object> entry : body.entrySet()) {
            addMultipartPart(parts, entry.getKey(), entry.getValue());
        }
        return parts;
    }

    private void addMultipartPart(MultiValueMap<String, Object> parts, String key, Object value) {
        if (value == null) {
            return;
        }
        if (value instanceof Iterable<?> iterable) {
            for (Object item : iterable) {
                addMultipartPart(parts, key, item);
            }
            return;
        }
        if ("image".equals(key) || "mask".equals(key)) {
            FileSystemResource resource = toFileResource(value, key);
            parts.add(key, resource);
            return;
        }
        parts.add(key, value);
    }

    private FileSystemResource toFileResource(Object value, String fieldName) {
        File file = new File(String.valueOf(value));
        if (!file.exists() || !file.isFile()) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, fieldName + " 需要提供可访问的本地文件路径");
        }
        return new FileSystemResource(file);
    }

    private List<RoutingRuleDto> enrichList(List<RoutingRuleEntity> entities) {
        if (entities == null || entities.isEmpty()) {
            return List.of();
        }
        List<Long> ruleIds = entities.stream().map(RoutingRuleEntity::getId).toList();
        Map<Long, List<RoutingRuleTargetDto>> targetsByRule = loadTargetsByRuleIds(ruleIds);
        Map<Long, List<RoutingRuleConsumerEntity>> consumersByRule = loadConsumersByRuleIds(ruleIds);
        Map<Long, TrafficPolicies> traffic =
                trafficPolicySupport.loadBatch(TrafficResourceTypeEnum.ROUTING_RULE.code(), ruleIds);
        List<RoutingRuleDto> result = new ArrayList<>();
        for (RoutingRuleEntity entity : entities) {
            TrafficPolicies tp = traffic.get(entity.getId());
            result.add(toDto(
                    entity,
                    consumersByRule.getOrDefault(entity.getId(), List.of()),
                    targetsByRule.getOrDefault(entity.getId(), List.of()),
                    tp
            ));
        }
        return result;
    }

    private RoutingRuleDto enrich(RoutingRuleEntity entity) {
        Map<Long, List<RoutingRuleTargetDto>> targetsByRule =
                loadTargetsByRuleIds(List.of(entity.getId()));
        Map<Long, List<RoutingRuleConsumerEntity>> consumersByRule =
                loadConsumersByRuleIds(List.of(entity.getId()));
        TrafficPolicies traffic =
                trafficPolicySupport.load(TrafficResourceTypeEnum.ROUTING_RULE.code(), entity.getId());
        return toDto(entity,
                consumersByRule.getOrDefault(entity.getId(), List.of()),
                targetsByRule.getOrDefault(entity.getId(), List.of()),
                traffic);
    }

    private RoutingRuleDto toDto(RoutingRuleEntity entity,
                                 List<RoutingRuleConsumerEntity> consumers,
                                 List<RoutingRuleTargetDto> targets,
                                 TrafficPolicies traffic) {
        RateLimitPolicy rate = traffic != null ? traffic.rateLimitPolicy() : null;
        QuotaPolicy quota = traffic != null ? traffic.quotaPolicy() : null;
        List<Long> consumerIds = consumers.stream().map(RoutingRuleConsumerEntity::getConsumerId).toList();
        List<String> consumerNames = consumers.stream()
                .map(this::consumerDisplayName)
                .filter(name -> name != null && !name.isBlank())
                .toList();
        return new RoutingRuleDto(
                entity.getId(),
                entity.getRuleCode(),
                entity.getName(),
                entity.getAppScenario(),
                consumerIds,
                consumerNames,
                entity.getAppId(),
                entity.getAppName(),
                EnabledStatusEnum.isEnabled(entity.getStatus()),
                targets,
                rate,
                quota,
                RetryPolicy.parse(entity.getRetryPolicy())
        );
    }

    private Map<Long, List<RoutingRuleConsumerEntity>> loadConsumersByRuleIds(List<Long> ruleIds) {
        if (ruleIds == null || ruleIds.isEmpty()) {
            return Map.of();
        }
        Map<Long, List<RoutingRuleConsumerEntity>> result = new HashMap<>();
        for (RoutingRuleConsumerEntity entity : routingRuleConsumerMapper.listByRuleIds(ruleIds)) {
            result.computeIfAbsent(entity.getRuleId(), k -> new ArrayList<>()).add(entity);
        }
        return result;
    }

    private Map<Long, List<RoutingRuleTargetDto>> loadTargetsByRuleIds(List<Long> ruleIds) {
        if (ruleIds == null || ruleIds.isEmpty()) {
            return Map.of();
        }
        Map<Long, List<RoutingRuleTargetDto>> result = new HashMap<>();
        for (RoutingRuleTargetEntity entity : routingRuleTargetMapper.listByRuleIds(ruleIds)) {
            result.computeIfAbsent(entity.getRuleId(), k -> new ArrayList<>()).add(toTargetDto(entity));
        }
        return result;
    }

    private RoutingRuleTargetDto toTargetDto(RoutingRuleTargetEntity entity) {
        return new RoutingRuleTargetDto(
                entity.getTargetType(),
                entity.getTargetId(),
                entity.getTargetCode(),
                null,
                entity.getTargetName(),
                entity.getModelType(),
                entity.getProviderAccountCode(),
                entity.getPriority() != null ? entity.getPriority() : 100,
                Boolean.TRUE.equals(entity.getPrimary())
        );
    }

    private void saveTargets(Long ruleId, List<RoutingRuleTargetDto> targets) {
        routingRuleTargetMapper.deleteByRuleId(ruleId);
        if (targets == null) {
            return;
        }
        for (RoutingRuleTargetDto target : targets) {
            RoutingRuleTargetEntity entity = new RoutingRuleTargetEntity();
            entity.setRuleId(ruleId);
            entity.setTargetType(normalizeTargetType(target.targetType()));
            entity.setTargetCode(resolveTargetCode(target));
            entity.setPriority(target.priority());
            entity.setPrimary(target.primary());
            routingRuleTargetMapper.insert(entity);
        }
    }

    /**
     * 解析路由目标的业务 code。优先使用提交方直传的 {@code targetCode}（前端 options loader 已改为返回 code）；
     * 兼容仅传数字主键 {@code targetId} 的历史调用方，按目标类型反查 model_code / pool_code。
     */
    private String resolveTargetCode(RoutingRuleTargetDto target) {
        if (target.targetCode() != null && !target.targetCode().isBlank()) {
            return target.targetCode().trim();
        }
        if (target.targetId() != null) {
            String type = normalizeTargetType(target.targetType());
            if ("MODEL".equals(type)) {
                ModelEntity model = modelMapper.getById(target.targetId());
                if (model != null) {
                    return model.getModelCode();
                }
            } else if ("MODEL_POOL".equals(type)) {
                ModelPoolEntity pool = modelPoolMapper.getById(target.targetId());
                if (pool != null) {
                    return pool.getPoolCode();
                }
            }
        }
        throw new BusinessException(ErrorCode.VALIDATION_ERROR, "routing target code is required");
    }

    private void saveConsumers(Long ruleId, List<Long> consumerIds) {
        routingRuleConsumerMapper.deleteByRuleId(ruleId);
        if (consumerIds == null || consumerIds.isEmpty()) {
            return;
        }
        Long consumerId = consumerIds.stream()
                .filter(Objects::nonNull)
                .findFirst()
                .orElse(null);
        if (consumerId == null) {
            return;
        }
        RoutingRuleConsumerEntity entity = new RoutingRuleConsumerEntity();
        entity.setRuleId(ruleId);
        entity.setConsumerId(consumerId);
        routingRuleConsumerMapper.insert(entity);
    }

    private RoutingRuleDto ensureGeneratedFieldsForCreate(RoutingRuleDto request) {
        String ruleCode = normalizeRuleCode(request.ruleCode());
        if (!ruleCode.isEmpty()) {
            return request;
        }
        return new RoutingRuleDto(
                request.id(),
                generateUniqueRuleCode(),
                request.name(),
                request.appScenario(),
                request.consumerIds(),
                request.consumerNames(),
                request.appId(),
                request.appName(),
                request.enabled(),
                request.targets(),
                request.rateLimitPolicy(),
                request.quotaPolicy(),
                request.retryPolicy()
        );
    }

    private String generateUniqueRuleCode() {
        for (int i = 0; i < 10; i++) {
            String code = RoutingRuleCodeGenerator.generate();
            if (isRuleCodeAvailable(code, null)) {
                return code;
            }
        }
        throw new BusinessException(ErrorCode.CONFLICT, "无法生成唯一规则编码，请重试");
    }

    private void validateForSave(RoutingRuleDto request, Long excludeId) {
        String ruleCode = normalizeRuleCode(request.ruleCode());
        if (ruleCode.isEmpty()) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "请填写规则编码");
        }
        if (ruleCode.length() > RULE_CODE_MAX_LEN) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR,
                    "规则编码不能超过 " + RULE_CODE_MAX_LEN + " 个字符");
        }
        if (!isRuleCodeAvailable(ruleCode, excludeId)) {
            throw new BusinessException(ErrorCode.CONFLICT,
                    "规则编码「" + ruleCode + "」已存在，请更换后重试");
        }
        if (request.consumerIds() != null) {
            for (Long consumerId : request.consumerIds().stream().distinct().toList()) {
                RoutingConsumerEntity consumer = routingConsumerMapper.getById(consumerId);
                if (consumer == null) {
                    throw new BusinessException(ErrorCode.NOT_FOUND, "消费者不存在");
                }
                if (request.enabled() && !EnabledStatusEnum.isEnabled(consumer.getStatus())) {
                    throw new BusinessException(ErrorCode.VALIDATION_ERROR, "消费者未启用");
                }
            }
        }
        if (request.appId() != null) {
            AppEntity app = appMapper.getById(request.appId());
            if (app == null) {
                throw new BusinessException(ErrorCode.NOT_FOUND, "绑定应用不存在");
            }
        }
        if (request.enabled()) {
            validateEnabledRule(request);
            validateTargets(request.targets(), true);
        } else if (request.targets() != null && !request.targets().isEmpty()) {
            validateTargets(request.targets(), false);
        }
    }

    private void validateEnabledRule(RoutingRuleDto request) {
        if (request.consumerIds() == null || request.consumerIds().isEmpty()) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "启用规则前请选择消费者");
        }
        if (request.appId() == null) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "启用规则前请选择绑定应用");
        }
        if (request.targets() == null || request.targets().isEmpty()) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "启用规则前请至少绑定一个模型");
        }
    }

    private void validateTargets(List<RoutingRuleTargetDto> targets, boolean enabledRule) {
        if (targets == null || targets.isEmpty()) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "请至少绑定一个模型");
        }
        long primaryCount = targets.stream().filter(RoutingRuleTargetDto::primary).count();
        if (primaryCount != 1) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "请指定且仅指定一个主模型");
        }
        long distinctTargets = targets.stream()
                .map(target -> normalizeTargetType(target.targetType()) + ":" + resolveTargetCode(target))
                .distinct()
                .count();
        if (distinctTargets != targets.size()) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "模型绑定不能重复");
        }
        for (RoutingRuleTargetDto target : targets) {
            validateTarget(target, enabledRule);
        }
    }

    private void validateTarget(RoutingRuleTargetDto target, boolean enabledRule) {
        String targetType = normalizeTargetType(target.targetType());
        if (target.targetCode() == null && target.targetId() == null) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "please select routing target");
        }
        String code = resolveTargetCode(target);
        if ("MODEL".equals(targetType)) {
            ModelEntity model = modelMapper.getByCode(code);
            if (model == null) {
                throw new BusinessException(ErrorCode.NOT_FOUND, "model not found");
            }
            if (enabledRule && !EnabledStatusEnum.isEnabled(model.getStatus())) {
                throw new BusinessException(ErrorCode.VALIDATION_ERROR, "routing target model is disabled");
            }
            return;
        }
        if ("MODEL_POOL".equals(targetType)) {
            ModelPoolEntity pool = modelPoolMapper.getByCode(code);
            if (pool == null) {
                throw new BusinessException(ErrorCode.NOT_FOUND, "model pool not found");
            }
            if (enabledRule && !EnabledStatusEnum.isEnabled(pool.getStatus())) {
                throw new BusinessException(ErrorCode.VALIDATION_ERROR, "routing target model pool is disabled");
            }
            if (enabledRule && modelPoolMapper.listItemsByPoolCode(pool.getPoolCode()).stream().noneMatch(item -> EnabledStatusEnum.isEnabled(item.getStatus()))) {
                throw new BusinessException(ErrorCode.VALIDATION_ERROR, "routing target model pool has no enabled model");
            }
            return;
        }
        throw new BusinessException(ErrorCode.VALIDATION_ERROR, "invalid routing target type");
    }

    private RoutingRuleEntity toEntity(RoutingRuleDto request) {
        RoutingRuleEntity entity = new RoutingRuleEntity();
        entity.setRuleCode(normalizeRuleCode(request.ruleCode()));
        entity.setName(request.name() != null ? request.name().trim() : "");
        entity.setAppScenario(normalizeNullable(request.appScenario()));
        entity.setAppId(request.appId());
        // 未配置（null）落库为 NULL，运行时按内置默认策略处理
        entity.setRetryPolicy(RetryPolicy.serialize(request.retryPolicy()));
        return entity;
    }

    private RoutingConsumerEntity findBoundConsumerBySecretKey(List<Long> consumerIds, String secretKey) {
        String normalized = secretKey != null ? secretKey.trim() : "";
        if (consumerIds == null || consumerIds.isEmpty() || normalized.isEmpty()) {
            return null;
        }
        for (Long consumerId : consumerIds) {
            RoutingConsumerEntity consumer = routingConsumerMapper.getById(consumerId);
            if (consumer != null
                    && EnabledStatusEnum.isEnabled(consumer.getStatus())
                    && normalized.equals(consumer.getSecretKey())) {
                return consumer;
            }
        }
        return null;
    }

    private String consumerDisplayName(RoutingRuleConsumerEntity entity) {
        if (entity == null) {
            return null;
        }
        String name = entity.getConsumerName();
        if (name != null && !name.isBlank()) {
            return name;
        }
        return entity.getConsumerCode();
    }

    private static String normalizeRuleCode(String ruleCode) {
        return ruleCode == null ? "" : ruleCode.trim();
    }

    private static String normalizeNullable(String value) {
        String normalized = value == null ? "" : value.trim();
        return normalized.isEmpty() ? null : normalized;
    }

    private static String normalizeTargetType(String value) {
        String normalized = value == null ? "" : value.trim().toUpperCase();
        return normalized.isEmpty() ? "MODEL" : normalized;
    }

}
