package org.chobit.knot.gateway.routing;

import org.apache.commons.lang3.StringUtils;
import org.chobit.knot.common.constants.enums.EnabledStatusEnum;
import org.chobit.knot.common.constants.enums.ModelApiProtocolEnum;
import org.chobit.knot.common.constants.enums.ModelPoolSelectionStrategyEnum;
import org.chobit.knot.common.constants.enums.ProxyErrorCodeEnum;
import org.chobit.knot.common.constants.enums.RouteTargetTypeEnum;
import org.chobit.knot.svc.dto.routing.RoutingRuleTargetDto;
import org.chobit.knot.dal.entity.AppEntity;
import org.chobit.knot.dal.entity.ModelEntity;
import org.chobit.knot.dal.entity.ModelPoolEntity;
import org.chobit.knot.dal.entity.ModelPoolItemEntity;
import org.chobit.knot.dal.entity.RoutingConsumerEntity;
import org.chobit.knot.dal.entity.RoutingRuleEntity;
import org.chobit.knot.gateway.exception.GatewayAuthException;
import org.chobit.knot.gateway.exception.GatewayInvalidRequestException;
import org.chobit.knot.gateway.exception.GatewayUpstreamException;
import org.chobit.knot.gateway.load.ModelLoadKeys;
import org.chobit.knot.gateway.load.ModelLoadStore;
import org.chobit.knot.gateway.model.GatewayRoutingContext;
import org.chobit.knot.gateway.model.ResolvedRouting;
import org.chobit.knot.common.model.StickyPolicy;
import org.chobit.knot.common.model.EjectionPolicy;
import org.chobit.knot.common.model.RetryPolicy;
import org.chobit.knot.gateway.service.GatewayEntityCacheService;
import org.chobit.knot.common.util.tools.RoutingSecretKeyGenerator;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/**
 * 根据消费者 API Key 和路由规则编码解析本次网关请求的路由信息。
 */
@Component
public class RoutingResolver {

    private final GatewayEntityCacheService dataService;
    private final ModelPoolSelection modelPoolSelection;
    private final ModelLoadStore modelLoadStore;
    private final ModelLoadKeys modelLoadKeys;
    private final StickyRouting stickyRouting;
    /**
     * 协议候选过滤器：数据源适配自 {@link GatewayEntityCacheService}（库查询 + 10 分钟缓存）。
     * 走 {@link ApiBindingSource} 函数接口而非直接依赖，是为了过滤逻辑可单测隔离。
     */
    private final ProtocolCandidateFilter protocolFilter;

    /**
     * Constructs a new instance.
     */
    public RoutingResolver(GatewayEntityCacheService dataService,
                           ModelPoolSelection modelPoolSelection,
                           ModelLoadStore modelLoadStore,
                           ModelLoadKeys modelLoadKeys,
                           StickyRouting stickyRouting) {
        this.dataService = dataService;
        this.modelPoolSelection = modelPoolSelection;
        this.modelLoadStore = modelLoadStore;
        this.modelLoadKeys = modelLoadKeys;
        this.stickyRouting = stickyRouting;
        this.protocolFilter = new ProtocolCandidateFilter(dataService::listApiBindingsByModelId);
    }

    /**
     * Resolves the requested value from current context and configuration. Executes the public operation.
     *
     * <p>候选只保留「声明了当前请求协议」的模型：协议能力取池内模型的<b>并集</b>（与调试面板一致），
     * 但真正承接请求的候选必须自己支持该协议，避免把请求打到不具备该能力的上游。</p>
     *
     * @param stickyKey 粘性路由键（请求头原值，未传为 null）；仅当目标模型池开启粘性路由时参与池内首选的选择
     * @param protocol  本次请求的接口协议；为 null（未识别协议）时不作过滤，沿用原有候选链
     */
    public ResolvedRouting resolveByRule(String secretKey, String ruleCode, String stickyKey, ModelApiProtocolEnum protocol) {
        if (!RoutingSecretKeyGenerator.isRoutingSecretKey(secretKey)) {
            throw new GatewayAuthException("Invalid consumer API key");
        }
        if (StringUtils.isBlank(ruleCode)) {
            throw new GatewayInvalidRequestException("Rule header must not be blank");
        }
        RoutingConsumerEntity consumer = dataService.getConsumerBySecretKey(StringUtils.trim(secretKey));
        if (consumer == null) {
            throw new GatewayAuthException("Consumer API key not found");
        }
        if (!EnabledStatusEnum.isEnabled(consumer.getStatus())) {
            throw new GatewayAuthException("Consumer is disabled");
        }
        RoutingRuleEntity rule = dataService.getEnabledRuleByConsumerCodeAndCode(consumer.getConsumerCode(), StringUtils.trim(ruleCode));
        if (rule == null) {
            throw new GatewayAuthException("Routing rule is not available for this consumer");
        }
        AppEntity app = dataService.getAppById(rule.getAppId());
        if (app == null) {
            throw new GatewayAuthException("Bound app not found");
        }
        if (!EnabledStatusEnum.isEnabled(app.getStatus())) {
            throw new GatewayAuthException("Bound app is disabled");
        }
        List<RoutingRuleTargetDto> candidates =
                resolveCandidateModels(rule.getRuleCode(), stickyKey, rule.getSelectionStrategy());
        if (candidates.isEmpty()) {
            throw new GatewayUpstreamException(
                    "No enabled routing target is available",
                    ProxyErrorCodeEnum.NO_ROUTING_TARGET.code()
            );
        }
        candidates = filterByProtocol(candidates, protocol);
        if (candidates.isEmpty()) {
            throw new GatewayUpstreamException(
                    "No routing target supports protocol " + protocol.canonical().code(),
                    ProxyErrorCodeEnum.NO_ROUTING_TARGET.code()
            );
        }
        GatewayRoutingContext routingContext = buildRoutingContext(rule, consumer, app);
        return new ResolvedRouting(rule.getId(),
                rule.getRuleCode(),
                consumer.getId(),
                consumer.getSecretKey(),
                Boolean.TRUE.equals(consumer.getReturnUsageDetail()),
                candidates,
                routingContext,
                RetryPolicy.parse(rule.getRetryPolicy()),
                EjectionPolicy.parse(rule.getEjectionPolicy()));
    }

    /**
     * 按请求协议筛选候选模型；协议为 null（未识别）时原样返回，保持既有行为。
     */
    private List<RoutingRuleTargetDto> filterByProtocol(List<RoutingRuleTargetDto> candidates,
                                                        ModelApiProtocolEnum protocol) {
        return protocolFilter.filter(candidates, protocol);
    }

    private static GatewayRoutingContext buildRoutingContext(RoutingRuleEntity rule,
                                                       RoutingConsumerEntity consumer,
                                                       AppEntity app) {
        return new GatewayRoutingContext(
                new GatewayRoutingContext.RuleSnapshot(
                        rule.getId(),
                        rule.getRuleCode(),
                        rule.getName(),
                        rule.getAppScenario(),
                        rule.getUsername()
                ),
                new GatewayRoutingContext.ConsumerSnapshot(
                        consumer.getId(),
                        consumer.getConsumerCode(),
                        consumer.getName(),
                        consumer.getSecretKey(),
                        Boolean.TRUE.equals(consumer.getReturnUsageDetail())
                ),
                new GatewayRoutingContext.AppSnapshot(app.getId(), app.getAppCode(), app.getName(), app.getDeptCode()),
                new GatewayRoutingContext.UserSnapshot(consumer.getUserUsername(), consumer.getUserRealName()),
                new GatewayRoutingContext.UserSnapshot(app.getOwnerUsername(), app.getOwnerRealName()),
                new GatewayRoutingContext.DepartmentSnapshot(app.getDeptCode(), null)
        );
    }

    private List<RoutingRuleTargetDto> resolveCandidateModels(String ruleCode, String stickyKey, String strategyCode) {
        List<RoutingRuleTargetDto> orderedTargets = orderTargets(ruleCode, strategyCode);
        if (orderedTargets.isEmpty()) {
            return List.of();
        }
        List<RoutingRuleTargetDto> candidates = new ArrayList<>();
        for (RoutingRuleTargetDto target : orderedTargets) {
            candidates.addAll(resolveTargetCandidates(target, stickyKey));
        }
        return candidates;
    }

    /**
     * 规则目标的选中顺序：主目标恒钉首位，其余按规则策略排序。
     *
     * <p>{@code PRIORITY}（默认）：按 priority 降序，与存量行为一致。
     * {@code LEAST_LOAD}：按目标<b>在途并发</b>升序（最空闲优先），在途相同再回落到 priority 序——
     * 主目标不参与负载重排，保留「主目标」运维契约（见 §7 子决策 1）。</p>
     */
    private List<RoutingRuleTargetDto> orderTargets(String ruleCode, String strategyCode) {
        List<RoutingRuleTargetDto> targets = listTargets(ruleCode);
        if (targets.isEmpty()) {
            return List.of();
        }
        final RoutingRuleTargetDto first = targets.stream()
                .filter(RoutingRuleTargetDto::primary)
                .findFirst()
                .orElse(targets.get(0));

        // 排序键用存储主键 targetCode（NOT NULL）；targetId 是 left join 派生的可空值，
        // 用它做 natural ordering 会在目标被删/JOIN 未命中时抛 NPE
        Comparator<RoutingRuleTargetDto> byConfiguredOrder =
                Comparator.comparingInt(RoutingRuleTargetDto::priority).reversed()
                        .thenComparing(RoutingRuleTargetDto::targetCode)
                        .thenComparing(RoutingRuleTargetDto::targetType);

        List<RoutingRuleTargetDto> rest = targets.stream()
                .filter(target -> !sameTarget(target, first))
                .toList();
        List<RoutingRuleTargetDto> ordered = new ArrayList<>();
        ordered.add(first);
        if (ModelPoolSelectionStrategyEnum.LEAST_LOAD == ModelPoolSelectionStrategyEnum.fromCode(strategyCode)) {
            // 预取本批目标涉及的全部模型在途值到本地 Map（P8）：一次批量读替代 N 次串行 GET，
            // 既消除 Comparator 副作用，又让 redis 档把「每目标 N 次串行 GET」合并为单次 MGET。
            Set<String> modelCodes = new LinkedHashSet<>();
            for (RoutingRuleTargetDto t : rest) {
                collectModelLoadKeys(t, modelCodes);
            }
            Map<String, Long> inflightByModelCode = modelLoadStore.inflightBatch(new ArrayList<>(modelCodes));
            Map<RoutingRuleTargetDto, Long> loadByTarget = rest.stream()
                    .collect(Collectors.toMap(Function.identity(), t -> targetLoad(t, inflightByModelCode)));
            rest.stream()
                    .sorted(Comparator.<RoutingRuleTargetDto>comparingLong(loadByTarget::get)
                            .thenComparing(byConfiguredOrder))
                    .forEach(ordered::add);
        } else {
            rest.stream().sorted(byConfiguredOrder).forEach(ordered::add);
        }
        return ordered;
    }

    /**
     * 单个规则目标的在途并发：模型目标取自身；模型池目标取池内启用条目之和（整池压力）。
     * 在途值从预取的 {@code inflightByModelCode} 读取（P8 批量预取，避免 N 次串行 GET）。
     */
    private long targetLoad(RoutingRuleTargetDto target, Map<String, Long> inflightByModelCode) {
        if (RouteTargetTypeEnum.MODEL.code().equals(target.targetType())) {
            return inflightByModelCode.getOrDefault(modelLoadKeys.model(target.targetCode()), 0L);
        }
        if (!RouteTargetTypeEnum.MODEL_POOL.code().equals(target.targetType())) {
            return 0L;
        }
        long sum = 0L;
        for (ModelPoolItemEntity item : dataService.listModelPoolItemsByPoolCode(target.targetCode())) {
            if (EnabledStatusEnum.isEnabled(item.getStatus())) {
                sum += inflightByModelCode.getOrDefault(modelLoadKeys.model(item.getModelCode()), 0L);
            }
        }
        return sum;
    }

    /**
     * 收集单个目标涉及的在途计量键：模型目标取自身；模型池目标取池内启用条目之和。
     * 与 {@link #targetLoad} 的键集合保持一致，保证预取覆盖选路所需的全部键。
     */
    private void collectModelLoadKeys(RoutingRuleTargetDto target, Set<String> modelCodes) {
        if (RouteTargetTypeEnum.MODEL.code().equals(target.targetType())) {
            modelCodes.add(modelLoadKeys.model(target.targetCode()));
            return;
        }
        if (!RouteTargetTypeEnum.MODEL_POOL.code().equals(target.targetType())) {
            return;
        }
        for (ModelPoolItemEntity item : dataService.listModelPoolItemsByPoolCode(target.targetCode())) {
            if (EnabledStatusEnum.isEnabled(item.getStatus())) {
                modelCodes.add(modelLoadKeys.model(item.getModelCode()));
            }
        }
    }

    /**
     * 身份去重：同一路由目标（存储主键 targetCode + 目标类型）只保留一条。
     * 用存储主键而非派生的 targetId，对齐「跨模块绑定存 code 不存 id」口径。
     * 不手写整对象字段比对——那是字段清单的第二份真相，加字段必漏且编译器不报。
     */
    private boolean sameTarget(RoutingRuleTargetDto left, RoutingRuleTargetDto right) {
        if (left == null || right == null) {
            return false;
        }
        return Objects.equals(left.targetCode(), right.targetCode())
                && Objects.equals(left.targetType(), right.targetType());
    }

    private List<RoutingRuleTargetDto> listTargets(String ruleCode) {
        return dataService.listTargetsByRuleCode(ruleCode).stream()
                .map(entity -> new RoutingRuleTargetDto(
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
                ))
                .toList();
    }

    private List<RoutingRuleTargetDto> resolveTargetCandidates(RoutingRuleTargetDto target, String stickyKey) {
        if (RouteTargetTypeEnum.MODEL.code().equals(target.targetType())) {
            // 按存储主键 targetCode 取模型：targetId 是 left join 派生的可空值（目标被删即失效）
            ModelEntity model = dataService.getModelByCode(target.targetCode());
            if (model == null || !EnabledStatusEnum.isEnabled(model.getStatus())) {
                return List.of();
            }
            return List.of(new RoutingRuleTargetDto(
                    RouteTargetTypeEnum.MODEL.code(),
                    model.getId(),
                    model.getModelCode(),
                    model.getUpstreamModel(),
                    model.getModelCode(),
                    model.getModelType(),
                    model.getProviderAccountCode(),
                    target.priority(),
                    target.primary(),
                    null
            ));
        }
        if (!RouteTargetTypeEnum.MODEL_POOL.code().equals(target.targetType())) {
            return List.of();
        }
        ModelPoolEntity pool = dataService.getModelPoolByCode(target.targetCode());
        if (pool == null || !EnabledStatusEnum.isEnabled(pool.getStatus())) {
            return List.of();
        }
        return resolvePoolCandidates(pool, target, stickyKey);
    }

    private List<RoutingRuleTargetDto> resolvePoolCandidates(ModelPoolEntity pool,
                                                             RoutingRuleTargetDto target,
                                                             String stickyKey) {
        // 池条目只存 model_code，模型实体（含主键 id，下游取凭据/协议绑定用）按 code 从缓存解析
        List<PoolItemCandidate> available = dataService.listModelPoolItemsByPoolCode(pool.getPoolCode()).stream()
                .filter(item -> EnabledStatusEnum.isEnabled(item.getStatus()))
                .map(item -> new PoolItemCandidate(item, dataService.getModelByCode(item.getModelCode())))
                .filter(candidate -> candidate.model() != null
                        && EnabledStatusEnum.isEnabled(candidate.model().getStatus()))
                .toList();
        // 池内选中顺序由模型池的 selection_strategy 决定：首个即本次选中，其余为故障转移候选
        // P8：池内 N 个模型在途值一次性批量预取（redis 档合并为单次 MGET），替代逐个串行 GET
        List<String> poolModelCodes = available.stream()
                .map(candidate -> modelLoadKeys.model(candidate.item().getModelCode()))
                .distinct()
                .toList();
        Map<String, Long> inflightByModelCode = modelLoadStore.inflightBatch(poolModelCodes);
        List<PoolItemCandidate> ordered = modelPoolSelection.order(
                available,
                pool.getSelectionStrategy(),
                candidate -> poolItemPriority(candidate.item()),
                candidate -> poolItemWeight(candidate.item()),
                candidate -> candidate.item().getId() != null ? candidate.item().getId() : 0L,
                candidate -> inflightByModelCode.getOrDefault(modelLoadKeys.model(candidate.item().getModelCode()), 0L)
        );
        // 池内粘性：把一致性 hash 命中的候选置顶为首选，其余保持池策略原序作 failover 链
        List<PoolItemCandidate> stickyOrdered = applySticky(ordered, pool.getStickyPolicy(), stickyKey);
        return stickyOrdered.stream()
                .map(candidate -> new RoutingRuleTargetDto(
                        RouteTargetTypeEnum.MODEL.code(),
                        candidate.model().getId(),
                        candidate.item().getModelCode(),
                        candidate.model().getUpstreamModel(),
                        candidate.item().getModelName(),
                        candidate.item().getModelType(),
                        candidate.item().getProviderAccountCode(),
                        target.priority(),
                        target.primary(),
                        pool.getPoolCode()
                ))
                .toList();
    }

    private record PoolItemCandidate(ModelPoolItemEntity item, ModelEntity model) {
    }

    private int poolItemPriority(ModelPoolItemEntity item) {
        return item.getPriority() != null ? item.getPriority() : 100;
    }

    private int poolItemWeight(ModelPoolItemEntity item) {
        return item.getWeight() != null ? item.getWeight() : 100;
    }

    /**
     * 按池级粘性策略把一致性 hash 命中的候选置顶为首选。
     *
     * <p>只改首选，不改 failover 链：其余候选仍按 {@code selection_strategy} 的原顺序排列，
     * 因此首选被频控拒绝或上游失败时照常顺延，请求不会因粘性而失败。</p>
     *
     * <p>策略关闭、粘性键为空、候选不足两个时原样返回——粘性是显式 opt-in 的能力。</p>
     */
    private List<PoolItemCandidate> applySticky(List<PoolItemCandidate> ordered,
                                                String stickyPolicyJson,
                                                String stickyKey) {
        if (ordered == null || ordered.size() < 2) {
            return ordered;
        }
        List<String> codes = ordered.stream()
                .map(candidate -> candidate.item().getModelCode())
                .toList();
        String preferred = stickyRouting.preferred(codes, StickyPolicy.parse(stickyPolicyJson), stickyKey);
        return preferred == null ? ordered : moveToFront(ordered, preferred);
    }

    /**
     * 把指定编码的候选移到首位（已在首位或不存在时原样返回）。
     */
    private static List<PoolItemCandidate> moveToFront(List<PoolItemCandidate> ordered, String preferredCode) {
        int index = -1;
        for (int i = 0; i < ordered.size(); i++) {
            if (preferredCode.equals(ordered.get(i).item().getModelCode())) {
                index = i;
                break;
            }
        }
        if (index <= 0) {
            return ordered;
        }
        List<PoolItemCandidate> result = new ArrayList<>(ordered);
        result.add(0, result.remove(index));
        return List.copyOf(result);
    }
}
