package org.chobit.knot.gateway.service;

import com.github.pagehelper.PageHelper;
import com.github.pagehelper.PageInfo;
import org.chobit.knot.gateway.constants.enums.TrafficResourceTypeEnum;
import org.chobit.knot.gateway.dto.routing.RoutingConsumerDto;
import org.chobit.knot.gateway.entity.RoutingConsumerEntity;
import org.chobit.knot.gateway.error.BusinessException;
import org.chobit.knot.gateway.error.ErrorCode;
import org.chobit.knot.gateway.mapper.RoutingConsumerMapper;
import org.chobit.knot.gateway.mapper.UserMapper;
import org.chobit.knot.gateway.model.PageRequest;
import org.chobit.knot.gateway.model.PageResult;
import org.chobit.knot.gateway.model.TrafficPolicies;
import org.chobit.knot.gateway.util.tools.RoutingRuleCodeGenerator;
import org.chobit.knot.gateway.util.tools.RoutingSecretKeyGenerator;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class RoutingConsumerService {

    private static final int CONSUMER_CODE_MAX_LEN = 32;

    private final RoutingConsumerMapper routingConsumerMapper;
    private final UserMapper userMapper;
    private final ResourceTrafficPolicySupport trafficPolicySupport;

    /**
     * Constructs a new instance.
     */
    public RoutingConsumerService(RoutingConsumerMapper routingConsumerMapper,
                                  UserMapper userMapper,
                                  ResourceTrafficPolicySupport trafficPolicySupport) {
        this.routingConsumerMapper = routingConsumerMapper;
        this.userMapper = userMapper;
        this.trafficPolicySupport = trafficPolicySupport;
    }

    /**
     * Lists matching results. Executes the public operation.
     */
    public PageResult<RoutingConsumerDto> list(PageRequest pageRequest) {
        return list(pageRequest, null);
    }

    /**
     * Lists matching results. Executes the public operation.
     */
    public PageResult<RoutingConsumerDto> list(PageRequest pageRequest, String keyword) {
        PageHelper.startPage(pageRequest.pageNum(), pageRequest.pageSize());
        PageInfo<RoutingConsumerEntity> pageInfo = new PageInfo<>(routingConsumerMapper.list(normalizeKeyword(keyword)));
        List<RoutingConsumerDto> dtos = pageInfo.getList().stream().map(entity ->
                toDto(entity, trafficPolicySupport.load(TrafficResourceTypeEnum.ROUTING_CONSUMER.code(), entity.getId()))
        ).toList();
        return PageResult.of(dtos, pageInfo.getTotal(), pageRequest.pageNum(), pageRequest.pageSize());
    }

    private static String normalizeKeyword(String keyword) {
        String value = keyword != null ? keyword.trim() : "";
        return value.isEmpty() ? null : value;
    }

    /**
     * Returns the requested value. Executes the public operation.
     */
    public RoutingConsumerDto getById(Long id) {
        RoutingConsumerEntity entity = routingConsumerMapper.getById(id);
        if (entity == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "消费者不存在");
        }
        return toDto(entity, trafficPolicySupport.load(TrafficResourceTypeEnum.ROUTING_CONSUMER.code(), entity.getId()));
    }

    /**
     * Returns whether the current condition is satisfied. Executes the public operation.
     */
    public boolean isConsumerCodeAvailable(String consumerCode, Long excludeId) {
        String normalized = normalizeConsumerCode(consumerCode);
        if (normalized.isEmpty()) {
            return false;
        }
        Long count = routingConsumerMapper.countByConsumerCode(normalized, excludeId);
        return count == null || count == 0;
    }

    /**
     * Returns the audit snapshot used by operation logging.
     */
    public Map<String, Object> consumerAuditSnapshot(Long id) {
        if (id == null) {
            return null;
        }
        try {
            RoutingConsumerDto dto = getById(id);
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", dto.id());
            m.put("consumerCode", dto.consumerCode());
            m.put("name", dto.name());
            m.put("userId", dto.userId());
            m.put("userName", dto.userName());
            m.put("secretKey", maskSecretKey(dto.secretKey()));
            m.put("returnUsageDetail", dto.returnUsageDetail());
            m.put("enabled", dto.enabled());
            m.put("ruleCount", dto.ruleCount());
            m.put("rateLimitPolicy", dto.rateLimitPolicy());
            m.put("quotaPolicy", dto.quotaPolicy());
            return m;
        } catch (BusinessException e) {
            return null;
        }
    }

    /**
     * Creates a new resource. Executes the public operation.
     */
    @Transactional
    public RoutingConsumerDto create(RoutingConsumerDto request) {
        RoutingConsumerDto normalized = ensureGeneratedFieldsForCreate(request);
        validateForSave(normalized, null);
        RoutingConsumerEntity entity = toEntity(normalized);
        entity.setSecretKey(generateUniqueSecretKey());
        entity.setStatus(normalized.enabled() ? "ENABLED" : "DISABLED");
        routingConsumerMapper.insert(entity);
        trafficPolicySupport.save(TrafficResourceTypeEnum.ROUTING_CONSUMER.code(), entity.getId(),
                normalized.rateLimitPolicy(), normalized.quotaPolicy());
        return getById(entity.getId());
    }

    /**
     * Updates the target resource. Executes the public operation.
     */
    @Transactional
    public RoutingConsumerDto update(Long id, RoutingConsumerDto request) {
        if (routingConsumerMapper.getById(id) == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "消费者不存在");
        }
        validateForSave(request, id);
        RoutingConsumerEntity entity = toEntity(request);
        entity.setId(id);
        entity.setStatus(request.enabled() ? "ENABLED" : "DISABLED");
        routingConsumerMapper.update(entity);
        trafficPolicySupport.save(TrafficResourceTypeEnum.ROUTING_CONSUMER.code(), id,
                request.rateLimitPolicy(), request.quotaPolicy());
        return getById(id);
    }

    /**
     * Rotates the consumer API key and returns the latest data.
     */
    @Transactional
    public RoutingConsumerDto rotateSecretKey(Long id) {
        RoutingConsumerEntity existing = routingConsumerMapper.getById(id);
        if (existing == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "消费者不存在");
        }
        routingConsumerMapper.updateSecretKey(id, generateUniqueSecretKey());
        return getById(id);
    }

    private RoutingConsumerDto ensureGeneratedFieldsForCreate(RoutingConsumerDto request) {
        String consumerCode = normalizeConsumerCode(request.consumerCode());
        if (!consumerCode.isEmpty()) {
            return request;
        }
        return new RoutingConsumerDto(
                request.id(),
                generateUniqueConsumerCode(),
                request.name(),
                request.userId(),
                request.userName(),
                request.secretKey(),
                request.returnUsageDetail(),
                request.enabled(),
                request.ruleCount(),
                request.rateLimitPolicy(),
                request.quotaPolicy()
        );
    }

    private void validateForSave(RoutingConsumerDto request, Long excludeId) {
        String consumerCode = normalizeConsumerCode(request.consumerCode());
        if (consumerCode.isEmpty()) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "请填写消费者编码");
        }
        if (consumerCode.length() > CONSUMER_CODE_MAX_LEN) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR,
                    "消费者编码不能超过 " + CONSUMER_CODE_MAX_LEN + " 个字符");
        }
        if (!isConsumerCodeAvailable(consumerCode, excludeId)) {
            throw new BusinessException(ErrorCode.CONFLICT,
                    "消费者编码「" + consumerCode + "」已存在，请更换后重试");
        }
        if (request.name() == null || request.name().isBlank()) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "请填写消费者名称");
        }
        if (request.userId() != null && userMapper.getUserById(request.userId()) == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "用户不存在");
        }
    }

    private RoutingConsumerDto toDto(RoutingConsumerEntity entity, TrafficPolicies traffic) {
        Long ruleCount = routingConsumerMapper.countRulesByConsumerId(entity.getId());
        return new RoutingConsumerDto(
                entity.getId(),
                entity.getConsumerCode(),
                entity.getName(),
                entity.getUserId(),
                resolveUserName(entity),
                entity.getSecretKey(),
                Boolean.TRUE.equals(entity.getReturnUsageDetail()),
                "ENABLED".equals(entity.getStatus()),
                ruleCount != null ? ruleCount : 0L,
                traffic != null ? traffic.rateLimitPolicy() : null,
                traffic != null ? traffic.quotaPolicy() : null
        );
    }

    private RoutingConsumerEntity toEntity(RoutingConsumerDto request) {
        RoutingConsumerEntity entity = new RoutingConsumerEntity();
        entity.setConsumerCode(normalizeConsumerCode(request.consumerCode()));
        entity.setName(request.name() != null ? request.name().trim() : "");
        entity.setUserId(request.userId());
        entity.setReturnUsageDetail(request.returnUsageDetail());
        return entity;
    }

    private String generateUniqueConsumerCode() {
        for (int i = 0; i < 10; i++) {
            String code = RoutingRuleCodeGenerator.generate();
            if (isConsumerCodeAvailable(code, null)) {
                return code;
            }
        }
        throw new BusinessException(ErrorCode.CONFLICT, "无法生成唯一消费者编码，请重试");
    }

    private String generateUniqueSecretKey() {
        for (int i = 0; i < 10; i++) {
            String key = RoutingSecretKeyGenerator.generate();
            if (routingConsumerMapper.getBySecretKey(key) == null) {
                return key;
            }
        }
        throw new BusinessException(ErrorCode.CONFLICT, "无法生成唯一 API Key，请重试");
    }

    private static String normalizeConsumerCode(String consumerCode) {
        return consumerCode == null ? "" : consumerCode.trim();
    }

    private static String resolveUserName(RoutingConsumerEntity entity) {
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

    private static String maskSecretKey(String secretKey) {
        if (secretKey == null || secretKey.length() <= 10) {
            return secretKey;
        }
        return secretKey.substring(0, 7) + "..." + secretKey.substring(secretKey.length() - 4);
    }
}
