package org.chobit.knot.gateway.service;

import com.github.pagehelper.PageHelper;
import com.github.pagehelper.PageInfo;
import org.chobit.knot.gateway.config.GatewayRuntimeProperties;
import org.chobit.knot.gateway.constants.enums.EntityStatusEnum;
import org.chobit.knot.gateway.constants.enums.ModelApiProtocolEnum;
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
import org.chobit.knot.gateway.mapper.UserMapper;
import org.chobit.knot.gateway.model.PageRequest;
import org.chobit.knot.gateway.model.PageResult;
import org.chobit.knot.gateway.model.QuotaPolicy;
import org.chobit.knot.gateway.model.RateLimitPolicy;
import org.chobit.knot.gateway.model.TrafficPolicies;
import org.chobit.knot.gateway.util.JsonKit;
import org.chobit.knot.gateway.util.tools.RoutingRuleCodeGenerator;
import org.chobit.knot.gateway.vo.routing.RoutingTestResult;
import org.springframework.http.MediaType;
import org.springframework.core.io.FileSystemResource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestClient;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.LinkedHashSet;
import java.util.stream.Collectors;

@Service
public class RoutingRuleService {

    private static final int RULE_CODE_MAX_LEN = 32;
    private static final String TRACEPARENT = "00-00000000000000000000000000000001-0000000000000001-01";
    private static final String DEFAULT_TEST_PROMPT = "你好，这是一条路由规则测试消息";
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
    private final RoutingRuleTargetMapper routingRuleTargetMapper;
    private final RoutingRuleConsumerMapper routingRuleConsumerMapper;
    private final RoutingConsumerMapper routingConsumerMapper;
    private final ModelApiBindingMapper modelApiBindingMapper;
    private final ModelMapper modelMapper;
    private final ModelPoolMapper modelPoolMapper;
    private final AppMapper appMapper;
    private final UserMapper userMapper;
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
                              UserMapper userMapper,
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
        this.userMapper = userMapper;
        this.routingRuleConverter = routingRuleConverter;
        this.trafficPolicySupport = trafficPolicySupport;
        this.gatewayRuntimeProperties = gatewayRuntimeProperties;
        this.restClient = RestClient.create();
    }

    /**
     * Lists matching results. Executes the public operation.
     */
    public PageResult<RoutingRuleDto> list(PageRequest pageRequest) {
        return list(pageRequest, null, null);
    }

    /**
     * Lists matching results. Executes the public operation.
     */
    public PageResult<RoutingRuleDto> list(PageRequest pageRequest, String keyword, List<String> modelTypes) {
        PageHelper.startPage(pageRequest.pageNum(), pageRequest.pageSize());
        PageInfo<RoutingRuleEntity> pageInfo = new PageInfo<>(routingRuleMapper.list(
                normalizeNullable(keyword),
                normalizeModelTypesForQuery(modelTypes)
        ));
        List<RoutingRuleDto> dtos = enrichList(pageInfo.getList());
        return PageResult.of(dtos, pageInfo.getTotal(), pageRequest.pageNum(), pageRequest.pageSize());
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
        entity.setStatus(normalized.enabled() ? "ENABLED" : "DISABLED");
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
        entity.setStatus(request.enabled() ? "ENABLED" : "DISABLED");
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
                existing.modelTypes(),
                existing.consumerIds(),
                existing.consumerNames(),
                existing.appId(),
                existing.appName(),
                existing.userId(),
                existing.userName(),
                enabled,
                existing.targets(),
                existing.rateLimitPolicy(),
                existing.quotaPolicy()
        );
        validateForSave(request, id);
        routingRuleMapper.updateStatus(id, enabled ? EntityStatusEnum.ENABLED.code() : EntityStatusEnum.DISABLED.code());
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
                                        String modelCode,
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
        String model = resolveRequestModelCode(modelCode, selectedTarget);
        String userPrompt = normalizeTestPrompt(prompt);
        Map<String, Object> body = buildRequestBody(protocol, model, userPrompt, requestBody);

        String baseUrl = normalizeGatewayBaseUrl();
        String gatewayPath = buildGatewayTestPath(protocol);
        String curl = buildTestCurl(baseUrl, gatewayPath, secretKey, rule.ruleCode(), protocol, body);

        try {
            String responseBody = executeGatewayTest(baseUrl, gatewayPath, secretKey, rule.ruleCode(), protocol, body);
            return new RoutingTestResult(
                    rule.id(),
                    selectedTarget.providerId(),
                    selectedTarget.targetId(),
                    model,
                    protocol.code(),
                    "SUCCESS",
                    curl,
                    200,
                    responseBody,
                    null
            );
        } catch (HttpStatusCodeException ex) {
            return new RoutingTestResult(
                    rule.id(),
                    selectedTarget.providerId(),
                    selectedTarget.targetId(),
                    model,
                    protocol.code(),
                    "FAILED",
                    curl,
                    ex.getStatusCode().value(),
                    ex.getResponseBodyAsString(),
                    ex.getMessage()
            );
        } catch (Exception ex) {
            return new RoutingTestResult(
                    rule.id(),
                    selectedTarget.providerId(),
                    selectedTarget.targetId(),
                    model,
                    protocol.code(),
                    "FAILED",
                    curl,
                    null,
                    null,
                    ex.getMessage()
            );
        }
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

    private String resolveRequestModelCode(String modelCode, RoutingRuleTargetDto target) {
        String normalized = modelCode == null ? "" : modelCode.trim();
        if (!normalized.isEmpty()) {
            return normalized;
        }
        return target.targetCode();
    }

    private String normalizeTestPrompt(String prompt) {
        String normalized = prompt == null ? "" : prompt.trim();
        return normalized.isEmpty() ? DEFAULT_TEST_PROMPT : normalized;
    }

    private Map<String, Object> buildRequestBody(ModelApiProtocolEnum protocol,
                                                 String model,
                                                 String prompt,
                                                 Map<String, Object> requestBody) {
        Map<String, Object> body = new LinkedHashMap<>();
        if (requestBody != null && !requestBody.isEmpty()) {
            body.putAll(requestBody);
        } else {
            body.putAll(defaultRequestBody(protocol, model, prompt));
        }
        body.put("model", model);
        fillDefaultPromptFields(protocol, body, prompt);
        return body;
    }

    private Map<String, Object> defaultRequestBody(ModelApiProtocolEnum protocol, String model, String prompt) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", model);
        switch (protocol) {
            case CHAT_COMPLETIONS -> body.put("messages", List.of(Map.of("role", "user", "content", prompt)));
            case RESPONSES -> body.put("input", prompt);
            case MESSAGES -> {
                body.put("messages", List.of(Map.of("role", "user", "content", prompt)));
                body.put("max_tokens", 1024);
            }
            case COMPLETIONS -> body.put("prompt", prompt);
            case EMBEDDINGS -> body.put("input", List.of(prompt));
            case IMAGE_GENERATIONS -> body.put("prompt", prompt);
            case IMAGE_EDITS -> {
                body.put("prompt", prompt);
                body.put("image", "https://example.com/image.png");
            }
            case IMAGE_VARIATIONS -> body.put("image", "https://example.com/image.png");
            case AUDIO_TRANSCRIPTIONS, AUDIO_TRANSLATIONS -> body.put("file", "D:/path/to/audio.mp3");
            case AUDIO_SPEECH -> {
                body.put("input", prompt);
                body.put("voice", "alloy");
            }
            case VIDEO_GENERATIONS -> body.put("prompt", prompt);
            case RERANK -> {
                body.put("query", prompt);
                body.put("documents", List.of("文档 1", "文档 2"));
                body.put("top_n", 2);
            }
            case MODERATIONS -> body.put("input", prompt);
            default -> {
            }
        }
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
        List<Long> enabledModelIds = modelPoolMapper.listItemsByPoolId(target.targetId()).stream()
                .filter(item -> "ENABLED".equals(item.getStatus()))
                .map(item -> item.getModelId())
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
                .filter(item -> "ENABLED".equals(item.getStatus()))
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
        String normalized = modelType == null ? "" : modelType.trim().toUpperCase();
        return switch (normalized) {
            case "EMBEDDING" -> Set.of(ModelApiProtocolEnum.EMBEDDINGS);
            case "IMAGE" -> Set.of(
                    ModelApiProtocolEnum.IMAGE_GENERATIONS,
                    ModelApiProtocolEnum.IMAGE_EDITS,
                    ModelApiProtocolEnum.IMAGE_VARIATIONS
            );
            case "AUDIO" -> Set.of(
                    ModelApiProtocolEnum.AUDIO_TRANSCRIPTIONS,
                    ModelApiProtocolEnum.AUDIO_TRANSLATIONS,
                    ModelApiProtocolEnum.AUDIO_SPEECH
            );
            case "VIDEO" -> Set.of(ModelApiProtocolEnum.VIDEO_GENERATIONS);
            case "RERANK" -> Set.of(ModelApiProtocolEnum.RERANK);
            case "MODERATION" -> Set.of(ModelApiProtocolEnum.MODERATIONS);
            case "UTILITY" -> Set.of(ModelApiProtocolEnum.RERANK, ModelApiProtocolEnum.MODERATIONS);
            case "DOCUMENT", "OCR" -> Set.of(
                    ModelApiProtocolEnum.CHAT_COMPLETIONS,
                    ModelApiProtocolEnum.RESPONSES,
                    ModelApiProtocolEnum.MESSAGES
            );
            case "MULTIMODAL" -> Set.of(
                    ModelApiProtocolEnum.CHAT_COMPLETIONS,
                    ModelApiProtocolEnum.RESPONSES,
                    ModelApiProtocolEnum.MESSAGES,
                    ModelApiProtocolEnum.COMPLETIONS,
                    ModelApiProtocolEnum.IMAGE_GENERATIONS,
                    ModelApiProtocolEnum.IMAGE_EDITS,
                    ModelApiProtocolEnum.IMAGE_VARIATIONS,
                    ModelApiProtocolEnum.AUDIO_TRANSCRIPTIONS,
                    ModelApiProtocolEnum.AUDIO_TRANSLATIONS,
                    ModelApiProtocolEnum.AUDIO_SPEECH,
                    ModelApiProtocolEnum.VIDEO_GENERATIONS
            );
            case "CHAT", "TEXT", "REASONING" -> Set.of(
                    ModelApiProtocolEnum.CHAT_COMPLETIONS,
                    ModelApiProtocolEnum.RESPONSES,
                    ModelApiProtocolEnum.MESSAGES,
                    ModelApiProtocolEnum.COMPLETIONS
            );
            default -> Set.of(ModelApiProtocolEnum.CHAT_COMPLETIONS);
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
                parseModelTypes(entity.getModelTypes()),
                consumerIds,
                consumerNames,
                entity.getAppId(),
                entity.getAppName(),
                entity.getUserId(),
                resolveUserName(entity),
                "ENABLED".equals(entity.getStatus()),
                targets,
                rate,
                quota
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
                entity.getTargetName(),
                entity.getModelType(),
                entity.getProviderId(),
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
            entity.setTargetId(target.targetId());
            entity.setPriority(target.priority());
            entity.setPrimary(target.primary());
            routingRuleTargetMapper.insert(entity);
        }
    }

    private void saveConsumers(Long ruleId, List<Long> consumerIds) {
        routingRuleConsumerMapper.deleteByRuleId(ruleId);
        if (consumerIds == null) {
            return;
        }
        for (Long consumerId : consumerIds.stream().distinct().toList()) {
            RoutingRuleConsumerEntity entity = new RoutingRuleConsumerEntity();
            entity.setRuleId(ruleId);
            entity.setConsumerId(consumerId);
            routingRuleConsumerMapper.insert(entity);
        }
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
                normalizeModelTypes(request.modelTypes()),
                request.consumerIds(),
                request.consumerNames(),
                request.appId(),
                request.appName(),
                request.userId(),
                request.userName(),
                request.enabled(),
                request.targets(),
                request.rateLimitPolicy(),
                request.quotaPolicy()
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
                if (request.enabled() && !"ENABLED".equals(consumer.getStatus())) {
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
        if (request.userId() != null && userMapper.getUserById(request.userId()) == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "用户不存在");
        }
        if (request.enabled()) {
            validateEnabledRule(request);
            validateTargets(request.targets(), true, normalizeModelTypes(request.modelTypes()));
        } else if (request.targets() != null && !request.targets().isEmpty()) {
            validateTargets(request.targets(), false, normalizeModelTypes(request.modelTypes()));
        }
    }

    private void validateEnabledRule(RoutingRuleDto request) {
        if (request.consumerIds() == null || request.consumerIds().isEmpty()) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "启用规则前请选择消费者");
        }
        if (request.appId() == null) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "启用规则前请选择绑定应用");
        }
        if (normalizeModelTypes(request.modelTypes()).isEmpty()) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "启用规则前请选择模型类型");
        }
        if (request.targets() == null || request.targets().isEmpty()) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "启用规则前请至少绑定一个模型");
        }
    }

    private void validateTargets(List<RoutingRuleTargetDto> targets, boolean enabledRule, List<String> modelTypes) {
        if (targets == null || targets.isEmpty()) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "请至少绑定一个模型");
        }
        long primaryCount = targets.stream().filter(RoutingRuleTargetDto::primary).count();
        if (primaryCount != 1) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "请指定且仅指定一个主模型");
        }
        long distinctTargets = targets.stream()
                .map(target -> normalizeTargetType(target.targetType()) + ":" + target.targetId())
                .distinct()
                .count();
        if (distinctTargets != targets.size()) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "模型绑定不能重复");
        }
        for (RoutingRuleTargetDto target : targets) {
            validateTarget(target, enabledRule, modelTypes);
        }
    }

    private void validateTarget(RoutingRuleTargetDto target, boolean enabledRule, List<String> modelTypes) {
        String targetType = normalizeTargetType(target.targetType());
        if (target.targetId() == null) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "please select routing target");
        }
        if ("MODEL".equals(targetType)) {
            ModelEntity model = modelMapper.getById(target.targetId());
            if (model == null) {
                throw new BusinessException(ErrorCode.NOT_FOUND, "model not found");
            }
            if (modelTypes != null && !modelTypes.isEmpty() && !modelTypes.contains(model.getModelType())) {
                throw new BusinessException(ErrorCode.VALIDATION_ERROR, "routing target model type is not allowed by rule");
            }
            if (enabledRule && !"ENABLED".equals(model.getStatus())) {
                throw new BusinessException(ErrorCode.VALIDATION_ERROR, "routing target model is disabled");
            }
            return;
        }
        if ("MODEL_POOL".equals(targetType)) {
            ModelPoolEntity pool = modelPoolMapper.getById(target.targetId());
            if (pool == null) {
                throw new BusinessException(ErrorCode.NOT_FOUND, "model pool not found");
            }
            if (modelTypes != null && !modelTypes.isEmpty() && !modelTypes.contains(pool.getModelType())) {
                throw new BusinessException(ErrorCode.VALIDATION_ERROR, "routing target model pool type is not allowed by rule");
            }
            if (enabledRule && !"ENABLED".equals(pool.getStatus())) {
                throw new BusinessException(ErrorCode.VALIDATION_ERROR, "routing target model pool is disabled");
            }
            if (enabledRule && modelPoolMapper.listItemsByPoolId(pool.getId()).stream().noneMatch(item -> "ENABLED".equals(item.getStatus()))) {
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
        entity.setModelTypes(String.join(",", normalizeModelTypes(request.modelTypes())));
        entity.setAppId(request.appId());
        entity.setUserId(request.userId());
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
                    && "ENABLED".equals(consumer.getStatus())
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

    private static String resolveUserName(RoutingRuleEntity entity) {
        if (entity == null || entity.getUserId() == null) {
            return null;
        }
        String realName = entity.getUserRealName() != null ? entity.getUserRealName().trim() : "";
        if (!realName.isEmpty()) {
            String username = entity.getUserUsername();
            if (username != null && !username.isBlank() && !realName.equals(username)) {
                return realName + "（" + username + "）";
            }
            return realName;
        }
        return entity.getUserUsername();
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

    private static List<String> parseModelTypes(String value) {
        if (value == null || value.isBlank()) {
            return List.of("CHAT");
        }
        List<String> result = value.lines()
                .flatMap(line -> List.of(line.split(",")).stream())
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .distinct()
                .toList();
        return result.isEmpty() ? List.of("CHAT") : result;
    }

    private static List<String> normalizeModelTypes(List<String> modelTypes) {
        if (modelTypes == null || modelTypes.isEmpty()) {
            return List.of("CHAT");
        }
        List<String> result = modelTypes.stream()
                .map(s -> s == null ? "" : s.trim())
                .filter(s -> !s.isEmpty())
                .distinct()
                .collect(Collectors.toList());
        return result.isEmpty() ? List.of("CHAT") : result;
    }

    private static List<String> normalizeModelTypesForQuery(List<String> modelTypes) {
        if (modelTypes == null || modelTypes.isEmpty()) {
            return null;
        }
        List<String> result = modelTypes.stream()
                .map(s -> s == null ? "" : s.trim())
                .filter(s -> !s.isEmpty())
                .distinct()
                .collect(Collectors.toList());
        return result.isEmpty() ? null : result;
    }
}
