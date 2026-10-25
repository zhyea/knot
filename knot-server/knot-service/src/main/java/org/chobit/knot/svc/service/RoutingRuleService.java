package org.chobit.knot.svc.service;

import com.github.pagehelper.Page;
import com.github.pagehelper.PageHelper;
import com.github.pagehelper.PageInfo;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.chobit.knot.common.constants.enums.EnabledStatusEnum;
import org.chobit.knot.common.constants.enums.ModelPoolSelectionStrategyEnum;
import org.chobit.knot.common.constants.enums.RouteTargetTypeEnum;
import org.chobit.knot.common.constants.enums.TrafficResourceTypeEnum;
import org.chobit.knot.svc.dto.routing.RoutingRuleDto;
import org.chobit.knot.svc.dto.routing.RoutingRuleTargetDto;
import org.chobit.knot.dal.entity.AppEntity;
import org.chobit.knot.dal.entity.ModelEntity;
import org.chobit.knot.dal.entity.ModelPoolEntity;
import org.chobit.knot.dal.entity.RoutingConsumerEntity;
import org.chobit.knot.dal.entity.RoutingRuleEntity;
import org.chobit.knot.dal.entity.RoutingRuleTargetEntity;
import org.chobit.knot.common.error.BusinessException;
import org.chobit.knot.common.error.ErrorCode;
import org.chobit.knot.dal.mapper.AppMapper;
import org.chobit.knot.dal.mapper.LogicalModelMapper;
import org.chobit.knot.dal.mapper.ModelMapper;
import org.chobit.knot.dal.mapper.ModelPoolMapper;
import org.chobit.knot.dal.mapper.RoutingConsumerMapper;
import org.chobit.knot.dal.mapper.RoutingRuleMapper;
import org.chobit.knot.dal.mapper.RoutingRuleTargetMapper;
import org.chobit.knot.common.model.PageRequest;
import org.chobit.knot.common.model.PageResult;
import org.chobit.knot.common.model.QuotaPolicy;
import org.chobit.knot.common.model.RateLimitPolicy;
import org.chobit.knot.common.model.EjectionPolicy;
import org.chobit.knot.common.model.RetryPolicy;
import org.chobit.knot.svc.model.TrafficPolicies;
import org.chobit.knot.svc.service.support.CodeBindingSupport;
import org.chobit.knot.svc.service.support.ResourceTrafficPolicySupport;
import org.chobit.knot.svc.service.support.AuditSnapshotSupport;
import org.chobit.knot.common.util.JsonKit;
import org.chobit.knot.common.util.tools.RoutingRuleCodeGenerator;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;


@Service
public class RoutingRuleService {

    private static final int RULE_CODE_MAX_LEN = 64;
    /**
     * 规则级目标选择策略白名单：复用池级枚举，但只接受路由目标排序已实现的两种。
     * 池级的 WEIGHTED / RANDOM 在规则层没有语义（会被静默当成 PRIORITY），故写入口直接拒。
     */
    private static final Set<String> RULE_LEVEL_STRATEGIES = Set.of(
        ModelPoolSelectionStrategyEnum.PRIORITY.code(),
        ModelPoolSelectionStrategyEnum.LEAST_LOAD.code()
    );

    private final RoutingRuleMapper routingRuleMapper;
    private final RoutingRuleTargetMapper routingRuleTargetMapper;
    private final RoutingConsumerMapper routingConsumerMapper;
    private final ModelMapper modelMapper;
    private final ModelPoolMapper modelPoolMapper;
    private final AppMapper appMapper;
    private final LogicalModelMapper logicalModelMapper;
    private final ResourceTrafficPolicySupport trafficPolicySupport;

    /**
     * Constructs a new instance.
     */
    public RoutingRuleService(RoutingRuleMapper routingRuleMapper,
                              RoutingRuleTargetMapper routingRuleTargetMapper,
                              RoutingConsumerMapper routingConsumerMapper,
                              ModelMapper modelMapper,
                              ModelPoolMapper modelPoolMapper,
                              AppMapper appMapper,
                              LogicalModelMapper logicalModelMapper,
                              ResourceTrafficPolicySupport trafficPolicySupport) {
        this.routingRuleMapper = routingRuleMapper;
        this.routingRuleTargetMapper = routingRuleTargetMapper;
        this.routingConsumerMapper = routingConsumerMapper;
        this.modelMapper = modelMapper;
        this.modelPoolMapper = modelPoolMapper;
        this.appMapper = appMapper;
        this.logicalModelMapper = logicalModelMapper;
        this.trafficPolicySupport = trafficPolicySupport;
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
     * 操作日志用的路由规则快照：{@code @OperationLog} 的 SpEL 在方法前后各取一次，
     * 规则不存在时返回 {@code null}——审计采集永远不得改变业务请求的结果。
     */
    public Map<String, Object> routingRuleAuditSnapshot(Long id) {
        return AuditSnapshotSupport.capture(id, key -> JsonKit.toMaskedMap(getById(key)));
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
        saveTargets(entity.getId(), normalized.targets());
        trafficPolicySupport.save(TrafficResourceTypeEnum.ROUTING_RULE.code(), entity.getId(),
            normalized.rateLimitPolicy(), normalized.quotaPolicies());
        return getById(entity.getId());
    }

    /**
     * Updates the target resource. Executes the public operation.
     */
    @Transactional
    public RoutingRuleDto update(Long id, RoutingRuleDto request) {
        RoutingRuleDto existing = getById(id);
        // 规则编码一旦被引用（有路由目标或限流绑定）就不许改：绑定列存 code，改码后目标与限流静默掉绑
        CodeBindingSupport.requireCodeChangeAllowed(existing.ruleCode(), request.ruleCode(),
            countCodeBindings(existing.ruleCode()), "路由规则编码");
        validateForSave(request, id);
        RoutingRuleEntity entity = toEntity(request);
        entity.setId(id);
        entity.setStatus(EnabledStatusEnum.codeOf(request.enabled()));
        routingRuleMapper.update(entity);
        saveTargets(id, request.targets());
        trafficPolicySupport.save(TrafficResourceTypeEnum.ROUTING_RULE.code(), id,
            request.rateLimitPolicy(), request.quotaPolicies());
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
            existing.consumerCode(),
            existing.consumerName(),
            existing.appId(),
            existing.appName(),
            existing.username(),
            existing.userRealName(),
            existing.logicalModelCode(),
            existing.logicalModelName(),
            enabled,
            existing.targets(),
            existing.rateLimitPolicy(),
            existing.quotaPolicies(),
            existing.retryPolicy(),
            existing.ejectionPolicy(),
            existing.selectionStrategy(),
            existing.createdAt(),
            existing.updatedAt(),
            existing.boundCount()
        );
        validateForSave(request, id);
        routingRuleMapper.updateStatus(id, EnabledStatusEnum.codeOf(enabled));
        return getById(id);
    }

    private List<RoutingRuleDto> enrichList(List<RoutingRuleEntity> entities) {
        if (entities == null || entities.isEmpty()) {
            return List.of();
        }
        List<String> ruleCodes = entities.stream().map(RoutingRuleEntity::getRuleCode).toList();
        Map<String, List<RoutingRuleTargetDto>> targetsByRule = loadTargetsByRuleCodes(ruleCodes);
        Map<Long, TrafficPolicies> traffic =
            trafficPolicySupport.loadBatch(TrafficResourceTypeEnum.ROUTING_RULE.code(),
                entities.stream().map(RoutingRuleEntity::getId).toList());
        List<RoutingRuleDto> result = new ArrayList<>();
        for (RoutingRuleEntity entity : entities) {
            TrafficPolicies tp = traffic.get(entity.getId());
            result.add(toDto(
                entity,
                targetsByRule.getOrDefault(entity.getRuleCode(), List.of()),
                tp
            ));
        }
        return result;
    }

    private RoutingRuleDto enrich(RoutingRuleEntity entity) {
        Map<String, List<RoutingRuleTargetDto>> targetsByRule =
            loadTargetsByRuleCodes(List.of(entity.getRuleCode()));
        TrafficPolicies traffic =
            trafficPolicySupport.load(TrafficResourceTypeEnum.ROUTING_RULE.code(), entity.getId());
        return toDto(entity,
            targetsByRule.getOrDefault(entity.getRuleCode(), List.of()),
            traffic);
    }

    private RoutingRuleDto toDto(RoutingRuleEntity entity,
                                 List<RoutingRuleTargetDto> targets,
                                 TrafficPolicies traffic) {
        RateLimitPolicy rate = traffic != null ? traffic.rateLimitPolicy() : null;
        List<QuotaPolicy> quotas = traffic != null ? traffic.quotaPolicies() : List.of();
        return new RoutingRuleDto(
            entity.getId(),
            entity.getRuleCode(),
            entity.getName(),
            entity.getAppScenario(),
            entity.getConsumerCode(),
            entity.getConsumerName(),
            entity.getAppId(),
            entity.getAppName(),
            entity.getUsername(),
            entity.getUserRealName(),
            entity.getLogicalModelCode(),
            entity.getLogicalModelName(),
            EnabledStatusEnum.isEnabled(entity.getStatus()),
            targets,
            rate,
            quotas,
            RetryPolicy.parse(entity.getRetryPolicy()),
            EjectionPolicy.parse(entity.getEjectionPolicy()),
            normalizeSelectionStrategy(entity.getSelectionStrategy()),
            entity.getCreatedAt(),
            entity.getUpdatedAt(),
            codeBoundCount(targets, rate)
        );
    }

    /**
     * 规则编码的绑定处数：路由目标行（kb_routing_rule_targets.rule_code）+ 限流行（限流只落
     * kb_resource_traffic_policies 聚合行，resource_type='ROUTING_RULE'，无额度子表）。
     *
     * <p>展示侧由已加载的行直接派生，不再单独发 count 查询（列表批量装配时避免 N+1）；
     * 写路径的拦截以 {@link #countCodeBindings(String)} 的库内计数为准。</p>
     */
    private static long codeBoundCount(List<RoutingRuleTargetDto> targets, RateLimitPolicy rate) {
        long targetCount = targets == null ? 0L : targets.size();
        return targetCount + (rate != null ? 1L : 0L);
    }

    /**
     * 库内真实计数：规则编码被多少行按 code 引用。改码守卫用（含已停用策略留下的聚合行）。
     */
    private long countCodeBindings(String ruleCode) {
        return CodeBindingSupport.countOf(routingRuleMapper.countTargetsByRuleCode(ruleCode))
            + trafficPolicySupport.countBindings(TrafficResourceTypeEnum.ROUTING_RULE.code(), ruleCode);
    }

    /**
     * 目标选择策略归一：未知 / 空白一律抛错，不做静默回退。
     *
     * <p>{@code kb_routing_rules.selection_strategy} 为 NOT NULL DEFAULT 'PRIORITY'，
     * 写入入口已强校验，库中值保证合法；与模型池的 WEIGHTED 默认由各自 schema 默认值表达。</p>
     */
    private static String normalizeSelectionStrategy(String code) {
        return ModelPoolSelectionStrategyEnum.requireCode(code, "unsupported selection strategy: " + code);
    }

    private Map<String, List<RoutingRuleTargetDto>> loadTargetsByRuleCodes(List<String> ruleCodes) {
        if (ruleCodes == null || ruleCodes.isEmpty()) {
            return Map.of();
        }
        Map<String, List<RoutingRuleTargetDto>> result = new HashMap<>();
        for (RoutingRuleTargetEntity entity : routingRuleTargetMapper.listByRuleCodes(ruleCodes)) {
            result.computeIfAbsent(entity.getRuleCode(), k -> new ArrayList<>()).add(toTargetDto(entity));
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
            Boolean.TRUE.equals(entity.getPrimary()),
            null
        );
    }

    /**
     * 路由目标整表替换：按 {@code ruleCode} 先删后插。
     *
     * <p><b>刻意与 {@code ModelService#saveApiBindings} 的「按 id 增量 diff」不同，不要统一</b>
     * （改进清单 4.7 决议）：目标列表的提交顺序不承载语义、行本身也没被按 id 外部引用，
     * 全量替换一次落地最省心；反观 API 绑定的行 id 会进操作日志与后续引用，整批换 id 是噪音。
     * 两者共用的是「编码改码守卫」这一份能力（{@link CodeBindingSupport}），
     * 而不是明细保存策略。</p>
     */
    private void saveTargets(Long ruleId, List<RoutingRuleTargetDto> targets) {
        RoutingRuleEntity rule = routingRuleMapper.getById(ruleId);
        if (rule == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "路由规则不存在");
        }
        String ruleCode = rule.getRuleCode();
        routingRuleTargetMapper.deleteByRuleCode(ruleCode);
        if (targets == null) {
            return;
        }
        for (RoutingRuleTargetDto target : targets) {
            RoutingRuleTargetEntity entity = new RoutingRuleTargetEntity();
            entity.setRuleCode(ruleCode);
            entity.setTargetType(targetTypeCodeOf(target));
            entity.setTargetCode(resolveTargetCode(target));
            entity.setPriority(target.priority());
            entity.setPrimary(target.primary());
            routingRuleTargetMapper.insert(entity);
        }
    }

    /**
     * 解析路由目标的业务 code：只接受提交方直传的 {@code targetCode}
     * （前端 options loader 已改为返回 code）；缺失直接拒绝。
     */
    private String resolveTargetCode(RoutingRuleTargetDto target) {
        String targetCode = target.targetCode();
        if (targetCode == null || targetCode.isBlank()) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "routing target code is required");
        }
        return targetCode.trim();
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
            request.consumerCode(),
            request.consumerName(),
            request.appId(),
            request.appName(),
            request.username(),
            request.userRealName(),
            request.logicalModelCode(),
            request.logicalModelName(),
            request.enabled(),
            request.targets(),
            request.rateLimitPolicy(),
            request.quotaPolicies(),
            request.retryPolicy(),
            request.ejectionPolicy(),
            request.selectionStrategy(),
            request.createdAt(),
            request.updatedAt(),
            request.boundCount()
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
        if (request.consumerCode() != null && !request.consumerCode().isBlank()) {
            RoutingConsumerEntity consumer = routingConsumerMapper.getByCode(request.consumerCode().trim());
            if (consumer == null) {
                throw new BusinessException(ErrorCode.NOT_FOUND, "消费者不存在");
            }
            if (request.enabled() && !EnabledStatusEnum.isEnabled(consumer.getStatus())) {
                throw new BusinessException(ErrorCode.VALIDATION_ERROR, "消费者未启用");
            }
        }
        if (request.appId() != null) {
            AppEntity app = appMapper.getById(request.appId());
            if (app == null) {
                throw new BusinessException(ErrorCode.NOT_FOUND, "绑定应用不存在");
            }
        }
        // 统一模型非必填；传了则必须是有效 code（与 RoutingTestPresetService 同口径）
        if (request.logicalModelCode() != null && !request.logicalModelCode().isBlank()
            && logicalModelMapper.getByCode(request.logicalModelCode().trim()) == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "统一模型不存在");
        }
        // 目标选择策略：空/缺省按 PRIORITY 落库；规则级只接受已实现的两种，
        // 池级的 WEIGHTED/RANDOM 在规则层没有对应分支（会被静默当成 PRIORITY），故直接拒绝
        if (request.selectionStrategy() != null && !request.selectionStrategy().isBlank()
            && !RULE_LEVEL_STRATEGIES.contains(request.selectionStrategy().trim().toUpperCase())) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "目标选择策略不支持");
        }
        if (request.enabled()) {
            validateEnabledRule(request);
            validateTargets(request.targets(), true);
        } else if (request.targets() != null && !request.targets().isEmpty()) {
            validateTargets(request.targets(), false);
        }
    }

    private void validateEnabledRule(RoutingRuleDto request) {
        if (request.consumerCode() == null || request.consumerCode().isBlank()) {
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
            .map(target -> targetTypeCodeOf(target) + ":" + resolveTargetCode(target))
            .distinct()
            .count();
        if (distinctTargets != targets.size()) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "模型绑定不能重复");
        }
        for (RoutingRuleTargetDto target : targets) {
            validateTarget(target, enabledRule);
        }
    }

    /**
     * 目标类型 code：空白/未知按 {@link RouteTargetTypeEnum#MODEL} 处理（历史数据与前端缺省同口径）。
     */
    private static String targetTypeCodeOf(RoutingRuleTargetDto target) {
        return RouteTargetTypeEnum.ofOrDefault(target.targetType(), RouteTargetTypeEnum.MODEL).code();
    }

    private void validateTarget(RoutingRuleTargetDto target, boolean enabledRule) {
        RouteTargetTypeEnum targetType =
                RouteTargetTypeEnum.ofOrDefault(target.targetType(), RouteTargetTypeEnum.MODEL);
        if (target.targetCode() == null && target.targetId() == null) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "please select routing target");
        }
        String code = resolveTargetCode(target);
        if (targetType == RouteTargetTypeEnum.MODEL) {
            ModelEntity model = modelMapper.getByCode(code);
            if (model == null) {
                throw new BusinessException(ErrorCode.NOT_FOUND, "model not found");
            }
            if (enabledRule && !EnabledStatusEnum.isEnabled(model.getStatus())) {
                throw new BusinessException(ErrorCode.VALIDATION_ERROR, "routing target model is disabled");
            }
            return;
        }
        if (targetType == RouteTargetTypeEnum.MODEL_POOL) {
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
        // 路由规则按应用业务码绑定（kb_apps.app_code），跨平台可移植；VO 仍以 appId(PK) 传值，此处解析为 code
        if (request.appId() != null) {
            AppEntity app = appMapper.getById(request.appId());
            if (app == null) {
                throw new BusinessException(ErrorCode.NOT_FOUND, "绑定应用不存在");
            }
            entity.setAppCode(app.getAppCode());
        } else {
            entity.setAppCode(null);
        }
        // 路由规则按 username 绑定用户（业务码，非主键 id）
        entity.setUsername(normalizeNullable(request.username()));
        // 路由规则按消费者业务码绑定（kb_routing_consumers.consumer_code），一对一，非主键 id
        entity.setConsumerCode(normalizeNullable(request.consumerCode()));
        // 统一模型非必填：未配置（null/空）落库为 NULL，仅作为目标选择器的优先排序上下文
        entity.setLogicalModelCode(normalizeNullable(request.logicalModelCode()));
        // 未配置（null）落库为 NULL，运行时按内置默认策略处理
        entity.setRetryPolicy(RetryPolicy.serialize(request.retryPolicy()));
        // 未配置（null）落库为 NULL，运行时按内置默认策略处理（默认关闭）
        entity.setEjectionPolicy(EjectionPolicy.serialize(request.ejectionPolicy()));
        // 目标选择策略：缺省 PRIORITY（保持既有固定序行为），非法值由 validateForSave 先行拒绝
        entity.setSelectionStrategy(normalizeSelectionStrategy(request.selectionStrategy()));
        return entity;
    }

    private static String normalizeRuleCode(String ruleCode) {
        return ruleCode == null ? "" : ruleCode.trim();
    }

    private static String normalizeNullable(String value) {
        String normalized = value == null ? "" : value.trim();
        return normalized.isEmpty() ? null : normalized;
    }

}
