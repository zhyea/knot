package org.chobit.knot.gateway.routing;

import org.apache.commons.lang3.StringUtils;
import org.chobit.knot.common.constants.enums.EnabledStatusEnum;
import org.chobit.knot.common.constants.enums.ModelApiProtocolEnum;
import org.chobit.knot.common.constants.enums.ModelPoolSelectionStrategyEnum;
import org.chobit.knot.common.constants.enums.ProxyErrorCodeEnum;
import org.chobit.knot.common.constants.enums.RouteTargetTypeEnum;
import org.chobit.knot.svc.dto.routing.RoutingRuleTargetDto;
import org.chobit.knot.svc.vo.gateway.AppConfigVo;
import org.chobit.knot.svc.vo.gateway.ModelConfigVo;
import org.chobit.knot.svc.vo.gateway.ModelPoolConfigVo;
import org.chobit.knot.svc.vo.gateway.ModelPoolItemConfigVo;
import org.chobit.knot.svc.vo.gateway.RoutingConsumerConfigVo;
import org.chobit.knot.svc.vo.gateway.RoutingRuleConfigVo;
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
import org.chobit.knot.gateway.service.GatewayConfigCacheService;
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

    private final GatewayConfigCacheService dataService;
    private final ModelPoolSelection modelPoolSelection;
    private final ModelLoadStore modelLoadStore;
    private final ModelLoadKeys modelLoadKeys;
    private final StickyRouting stickyRouting;
    /**
     * 协议候选过滤器：数据源适配自 {@link GatewayConfigCacheService}（库查询 + 10 分钟缓存）。
     * 走 {@link ApiBindingSource} 函数接口而非直接依赖，是为了过滤逻辑可单测隔离。
     */
    private final ProtocolCandidateFilter protocolFilter;

    /**
     * Constructs a new instance.
     */
    public RoutingResolver(GatewayConfigCacheService dataService,
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
        RuleAccess access = resolveAccess(secretKey, ruleCode);
        RoutingRuleConfigVo rule = access.rule();
        List<RoutingRuleTargetDto> candidates =
                resolveCandidateModels(rule.ruleCode(), stickyKey, rule.selectionStrategy());
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
        GatewayRoutingContext routingContext = buildRoutingContext(rule, access.consumer(), access.app());
        return new ResolvedRouting(rule.id(),
                rule.ruleCode(),
                access.consumer().id(),
                access.consumer().secretKey(),
                Boolean.TRUE.equals(access.consumer().returnUsageDetail()),
                candidates,
                routingContext,
                RetryPolicy.parse(rule.retryPolicy()),
                EjectionPolicy.parse(rule.ejectionPolicy()));
    }

    /**
     * 校验「API Key × 规则编码」是否是一对可用组合；不解析候选模型、不做池内选择。
     *
     * <p>供不发起上游调用、但需要同一个鉴权口径的端点使用（异步任务查询端点）。</p>
     *
     * @throws GatewayAuthException        键无效、消费者不可用、或该消费者无权使用这条规则
     * @throws GatewayInvalidRequestException 规则编码为空
     */
    public void requireRuleAccess(String secretKey, String ruleCode) {
        resolveAccess(secretKey, ruleCode);
    }

    /**
     * 「API Key × 规则」鉴权的<b>唯一实现</b>。
     *
     * <p>⭐ 抽成单一方法而不是两处各写一遍：这份判定的任何一条
     * （key 形态 / 消费者存在且启用 / 规则属于该消费者 / 应用存在且启用）漏在另一处，
     * 就等于开了一个绕开鉴权的旁路，而编译期完全看不出来。</p>
     */
    private RuleAccess resolveAccess(String secretKey, String ruleCode) {
        if (!RoutingSecretKeyGenerator.isRoutingSecretKey(secretKey)) {
            throw new GatewayAuthException("Invalid consumer API key");
        }
        if (StringUtils.isBlank(ruleCode)) {
            throw new GatewayInvalidRequestException("Rule header must not be blank");
        }
        RoutingConsumerConfigVo consumer = dataService.getConsumerBySecretKey(StringUtils.trim(secretKey));
        if (consumer == null) {
            throw new GatewayAuthException("Consumer API key not found");
        }
        if (!EnabledStatusEnum.isEnabled(consumer.status())) {
            throw new GatewayAuthException("Consumer is disabled");
        }
        RoutingRuleConfigVo rule = dataService.getEnabledRuleByConsumerCodeAndCode(consumer.consumerCode(), StringUtils.trim(ruleCode));
        if (rule == null) {
            throw new GatewayAuthException("Routing rule is not available for this consumer");
        }
        AppConfigVo app = dataService.getAppById(rule.appId());
        if (app == null) {
            throw new GatewayAuthException("Bound app not found");
        }
        if (!EnabledStatusEnum.isEnabled(app.status())) {
            throw new GatewayAuthException("Bound app is disabled");
        }
        return new RuleAccess(consumer, rule, app);
    }

    /** 一次鉴权通过后的三元组，供解析链路继续用（避免调用方再查一遍） */
    private record RuleAccess(RoutingConsumerConfigVo consumer, RoutingRuleConfigVo rule, AppConfigVo app) {
    }

    /**
     * 按请求协议筛选候选模型；协议为 null（未识别）时原样返回，保持既有行为。
     */
    private List<RoutingRuleTargetDto> filterByProtocol(List<RoutingRuleTargetDto> candidates,
                                                        ModelApiProtocolEnum protocol) {
        return protocolFilter.filter(candidates, protocol);
    }

    private static GatewayRoutingContext buildRoutingContext(RoutingRuleConfigVo rule,
                                                       RoutingConsumerConfigVo consumer,
                                                       AppConfigVo app) {
        return new GatewayRoutingContext(
                new GatewayRoutingContext.RuleSnapshot(
                        rule.id(),
                        rule.ruleCode(),
                        rule.name(),
                        rule.appScenario(),
                        rule.username()
                ),
                new GatewayRoutingContext.ConsumerSnapshot(
                        consumer.id(),
                        consumer.consumerCode(),
                        consumer.name(),
                        consumer.secretKey(),
                        Boolean.TRUE.equals(consumer.returnUsageDetail())
                ),
                new GatewayRoutingContext.AppSnapshot(app.id(), app.appCode(), app.name(), app.deptCode()),
                new GatewayRoutingContext.UserSnapshot(consumer.userUsername(), consumer.userRealName()),
                new GatewayRoutingContext.UserSnapshot(app.ownerUsername(), app.ownerRealName()),
                new GatewayRoutingContext.DepartmentSnapshot(app.deptCode(), null)
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
        for (ModelPoolItemConfigVo item : dataService.listModelPoolItemsByPoolCode(target.targetCode())) {
            if (EnabledStatusEnum.isEnabled(item.status())) {
                sum += inflightByModelCode.getOrDefault(modelLoadKeys.model(item.modelCode()), 0L);
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
        for (ModelPoolItemConfigVo item : dataService.listModelPoolItemsByPoolCode(target.targetCode())) {
            if (EnabledStatusEnum.isEnabled(item.status())) {
                modelCodes.add(modelLoadKeys.model(item.modelCode()));
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
                        entity.targetType(),
                        entity.targetId(),
                        entity.targetCode(),
                        null,
                        entity.targetName(),
                        entity.modelType(),
                        entity.providerAccountCode(),
                        entity.priority() != null ? entity.priority() : 100,
                        Boolean.TRUE.equals(entity.primary()),
                        null
                ))
                .toList();
    }

    private List<RoutingRuleTargetDto> resolveTargetCandidates(RoutingRuleTargetDto target, String stickyKey) {
        if (RouteTargetTypeEnum.MODEL.code().equals(target.targetType())) {
            // 按存储主键 targetCode 取模型：targetId 是 left join 派生的可空值（目标被删即失效）
            ModelConfigVo model = dataService.getModelByCode(target.targetCode());
            if (model == null || !EnabledStatusEnum.isEnabled(model.status())) {
                return List.of();
            }
            return List.of(new RoutingRuleTargetDto(
                    RouteTargetTypeEnum.MODEL.code(),
                    model.id(),
                    model.modelCode(),
                    model.upstreamModel(),
                    model.modelCode(),
                    model.modelType(),
                    model.providerAccountCode(),
                    target.priority(),
                    target.primary(),
                    null
            ));
        }
        if (!RouteTargetTypeEnum.MODEL_POOL.code().equals(target.targetType())) {
            return List.of();
        }
        ModelPoolConfigVo pool = dataService.getModelPoolByCode(target.targetCode());
        if (pool == null || !EnabledStatusEnum.isEnabled(pool.status())) {
            return List.of();
        }
        return resolvePoolCandidates(pool, target, stickyKey);
    }

    private List<RoutingRuleTargetDto> resolvePoolCandidates(ModelPoolConfigVo pool,
                                                             RoutingRuleTargetDto target,
                                                             String stickyKey) {
        // 池条目只存 model_code，模型实体（含主键 id，下游取凭据/协议绑定用）按 code 从缓存解析
        List<PoolItemCandidate> available = dataService.listModelPoolItemsByPoolCode(pool.poolCode()).stream()
                .filter(item -> EnabledStatusEnum.isEnabled(item.status()))
                .map(item -> new PoolItemCandidate(item, dataService.getModelByCode(item.modelCode())))
                .filter(candidate -> candidate.model() != null
                        && EnabledStatusEnum.isEnabled(candidate.model().status()))
                .toList();
        // 池内选中顺序由模型池的 selection_strategy 决定：首个即本次选中，其余为故障转移候选
        // P8：池内 N 个模型在途值一次性批量预取（redis 档合并为单次 MGET），替代逐个串行 GET
        List<String> poolModelCodes = available.stream()
                .map(candidate -> modelLoadKeys.model(candidate.item().modelCode()))
                .distinct()
                .toList();
        Map<String, Long> inflightByModelCode = modelLoadStore.inflightBatch(poolModelCodes);
        List<PoolItemCandidate> ordered = modelPoolSelection.order(
                available,
                pool.selectionStrategy(),
                candidate -> poolItemPriority(candidate.item()),
                candidate -> poolItemWeight(candidate.item()),
                candidate -> candidate.item().id() != null ? candidate.item().id() : 0L,
                candidate -> inflightByModelCode.getOrDefault(modelLoadKeys.model(candidate.item().modelCode()), 0L)
        );
        // 池内粘性：把一致性 hash 命中的候选置顶为首选，其余保持池策略原序作 failover 链
        List<PoolItemCandidate> stickyOrdered = applySticky(ordered, pool.stickyPolicy(), stickyKey);
        return stickyOrdered.stream()
                .map(candidate -> new RoutingRuleTargetDto(
                        RouteTargetTypeEnum.MODEL.code(),
                        candidate.model().id(),
                        candidate.item().modelCode(),
                        candidate.model().upstreamModel(),
                        candidate.item().modelName(),
                        candidate.item().modelType(),
                        candidate.item().providerAccountCode(),
                        target.priority(),
                        target.primary(),
                        pool.poolCode()
                ))
                .toList();
    }

    private record PoolItemCandidate(ModelPoolItemConfigVo item, ModelConfigVo model) {
    }

    private int poolItemPriority(ModelPoolItemConfigVo item) {
        return item.priority() != null ? item.priority() : 100;
    }

    private int poolItemWeight(ModelPoolItemConfigVo item) {
        return item.weight() != null ? item.weight() : 100;
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
                .map(candidate -> candidate.item().modelCode())
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
            if (preferredCode.equals(ordered.get(i).item().modelCode())) {
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
