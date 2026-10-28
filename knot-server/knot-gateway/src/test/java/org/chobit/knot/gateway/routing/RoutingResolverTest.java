package org.chobit.knot.gateway.routing;

import org.chobit.knot.common.constants.enums.ModelApiProtocolEnum;
import org.chobit.knot.common.constants.enums.ProxyErrorCodeEnum;
import org.chobit.knot.svc.dto.routing.RoutingRuleTargetDto;
import org.chobit.knot.dal.entity.AppEntity;
import org.chobit.knot.dal.entity.ModelApiBindingEntity;
import org.chobit.knot.dal.entity.ModelEntity;
import org.chobit.knot.dal.entity.ModelPoolEntity;
import org.chobit.knot.dal.entity.ModelPoolItemEntity;
import org.chobit.knot.dal.entity.RoutingConsumerEntity;
import org.chobit.knot.dal.entity.RoutingRuleEntity;
import org.chobit.knot.dal.entity.RoutingRuleTargetEntity;
import org.chobit.knot.gateway.exception.GatewayAuthException;
import org.chobit.knot.gateway.exception.GatewayInvalidRequestException;
import org.chobit.knot.gateway.exception.GatewayUpstreamException;
import org.chobit.knot.gateway.load.ModelLoadKeys;
import org.chobit.knot.gateway.load.ModelLoadStore;
import org.chobit.knot.dal.mapper.AppMapper;
import org.chobit.knot.dal.mapper.BillingRuleMapper;
import org.chobit.knot.dal.mapper.ModelApiBindingMapper;
import org.chobit.knot.dal.mapper.ModelMapper;
import org.chobit.knot.dal.mapper.ModelPoolMapper;
import org.chobit.knot.dal.mapper.ProviderAccountMapper;
import org.chobit.knot.dal.mapper.ProviderCredentialMapper;
import org.chobit.knot.dal.mapper.RoutingConsumerMapper;
import org.chobit.knot.dal.mapper.RoutingRuleMapper;
import org.chobit.knot.dal.mapper.RoutingRuleTargetMapper;
import org.chobit.knot.gateway.model.GatewayRoutingContext;
import org.chobit.knot.gateway.model.ResolvedRouting;
import org.chobit.knot.common.model.StickyPolicy;
import org.chobit.knot.gateway.service.GatewayEntityCacheService;
import org.chobit.knot.svc.service.support.ResourceTrafficPolicySupport;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 路由解析核心 {@link RoutingResolver} 的单测：候选解析、目标排序（PRIORITY / LEAST_LOAD）、
 * 模型池展开、池内粘性、协议过滤与各类拒绝路径。
 *
 * <p>替身全部手写（JDK25 + Mockito inline 不能 mock 具体类，项目亦不引 Mockito）：
 * {@link StubEntityCache} 继承 {@link GatewayEntityCacheService} 只覆写被调用的读取方法
 * （构造器的 mapper 形参经 JDK 动态代理占位——javac 对绑定方法引用接收者有显式空值检查，
 * 详见 {@link StubEntityCache} 注释）；{@link StubLoadStore} 实现 {@link ModelLoadStore}；
 * {@link FixedStickyRouting} 覆写粘性首选，免去对一致性 hash 内部结果的依赖；
 * {@link ModelPoolSelection} / {@link StickyRouting} / {@link ModelLoadKeys} 用真实实例。</p>
 */
class RoutingResolverTest {

    private static final String SECRET_KEY = "sk-0123456789abcdef0123456789abcdef";
    private static final String CONSUMER_CODE = "consumer-a";
    private static final String RULE_CODE = "rule-a";
    private static final String LOAD_KEY_PREFIX = "knot:test:MODEL:";

    private final StubEntityCache cache = new StubEntityCache();

    private RoutingResolver resolver() {
        return resolver(new StickyRouting(), Map.of());
    }

    private RoutingResolver resolver(StickyRouting stickyRouting, Map<String, Long> inflight) {
        return new RoutingResolver(cache, new ModelPoolSelection(new Random(42)),
                new StubLoadStore(inflight), new ModelLoadKeys("knot:test"), stickyRouting);
    }

    // ---------- 拒绝路径 ----------

    @Test
    void rejectsNonRoutingSecretKeyFormat() {
        baselineConsumerAndRule();

        GatewayAuthException error = assertThrows(GatewayAuthException.class,
                () -> resolver().resolveByRule("bad-key", RULE_CODE, null, null));

        assertEquals("Invalid consumer API key", error.getMessage());
    }

    @Test
    void rejectsBlankRuleCode() {
        baselineConsumerAndRule();

        GatewayInvalidRequestException error = assertThrows(GatewayInvalidRequestException.class,
                () -> resolver().resolveByRule(SECRET_KEY, "  ", null, null));

        assertEquals("Rule header must not be blank", error.getMessage());
    }

    @Test
    void rejectsUnknownConsumer() {
        baselineConsumerAndRule();
        cache.consumer = null;

        GatewayAuthException error = assertThrows(GatewayAuthException.class,
                () -> resolver().resolveByRule(SECRET_KEY, RULE_CODE, null, null));

        assertEquals("Consumer API key not found", error.getMessage());
    }

    @Test
    void rejectsDisabledConsumer() {
        baselineConsumerAndRule();
        cache.consumer.setStatus(0);

        GatewayAuthException error = assertThrows(GatewayAuthException.class,
                () -> resolver().resolveByRule(SECRET_KEY, RULE_CODE, null, null));

        assertEquals("Consumer is disabled", error.getMessage());
    }

    @Test
    void rejectsRuleNotAvailableForConsumer() {
        baselineConsumerAndRule();
        cache.rule = null;

        GatewayAuthException error = assertThrows(GatewayAuthException.class,
                () -> resolver().resolveByRule(SECRET_KEY, RULE_CODE, null, null));

        assertEquals("Routing rule is not available for this consumer", error.getMessage());
    }

    @Test
    void rejectsMissingBoundApp() {
        baselineConsumerAndRule();
        cache.app = null;

        GatewayAuthException error = assertThrows(GatewayAuthException.class,
                () -> resolver().resolveByRule(SECRET_KEY, RULE_CODE, null, null));

        assertEquals("Bound app not found", error.getMessage());
    }

    @Test
    void rejectsDisabledBoundApp() {
        baselineConsumerAndRule();
        cache.app.setStatus(0);

        GatewayAuthException error = assertThrows(GatewayAuthException.class,
                () -> resolver().resolveByRule(SECRET_KEY, RULE_CODE, null, null));

        assertEquals("Bound app is disabled", error.getMessage());
    }

    // ---------- requireRuleAccess：不解析候选、只做「key × 规则」鉴权 ----------

    @Test
    void requireRuleAccessPassesForUsablePair() {
        baselineConsumerAndRule();
        // 探针：鉴权不解析候选模型，故「没有任何可用目标」不影响它放行
        resolver().requireRuleAccess(SECRET_KEY, RULE_CODE);
    }

    @Test
    void requireRuleAccessSharesEveryRejectionWithResolveByRule() {
        baselineConsumerAndRule();
        assertThrows(GatewayAuthException.class, () -> resolver().requireRuleAccess("bad-key", RULE_CODE));
        assertThrows(GatewayInvalidRequestException.class, () -> resolver().requireRuleAccess(SECRET_KEY, "  "));

        baselineConsumerAndRule();
        cache.consumer = null;
        assertThrows(GatewayAuthException.class, () -> resolver().requireRuleAccess(SECRET_KEY, RULE_CODE));

        baselineConsumerAndRule();
        cache.consumer.setStatus(0);
        assertThrows(GatewayAuthException.class, () -> resolver().requireRuleAccess(SECRET_KEY, RULE_CODE));

        baselineConsumerAndRule();
        cache.rule = null;
        assertThrows(GatewayAuthException.class, () -> resolver().requireRuleAccess(SECRET_KEY, RULE_CODE));

        baselineConsumerAndRule();
        cache.app = null;
        assertThrows(GatewayAuthException.class, () -> resolver().requireRuleAccess(SECRET_KEY, RULE_CODE));

        baselineConsumerAndRule();
        cache.app.setStatus(0);
        GatewayAuthException error = assertThrows(GatewayAuthException.class,
                () -> resolver().requireRuleAccess(SECRET_KEY, RULE_CODE));
        assertEquals("Bound app is disabled", error.getMessage());
    }

    @Test
    void rejectsWhenNoEnabledTargetAvailable() {
        baselineConsumerAndRule();
        cache.targets = List.of();

        GatewayUpstreamException error = assertThrows(GatewayUpstreamException.class,
                () -> resolver().resolveByRule(SECRET_KEY, RULE_CODE, null, null));

        assertEquals(ProxyErrorCodeEnum.NO_ROUTING_TARGET.code(), error.code());
    }

    @Test
    void rejectsWhenNoCandidateSupportsProtocol() {
        baselineConsumerAndRule();
        cache.targets = List.of(targetRow("MODEL", "m1", 100, true));
        cache.models.put("m1", model(11L, "m1"));

        GatewayUpstreamException error = assertThrows(GatewayUpstreamException.class,
                () -> resolver().resolveByRule(SECRET_KEY, RULE_CODE, null, ModelApiProtocolEnum.MESSAGES));

        assertEquals(ProxyErrorCodeEnum.NO_ROUTING_TARGET.code(), error.code());
        assertTrue(error.getMessage().contains("MESSAGES"), "错误信息应指出不被支持的协议");
    }

    // ---------- 目标排序 ----------

    @Test
    void pinsPrimaryTargetFirstAndOrdersRestByPriorityThenCode() {
        baselineConsumerAndRule();
        cache.targets = List.of(
                targetRow("MODEL", "b-model", 10, true),
                targetRow("MODEL", "c-model", 50, false),
                targetRow("MODEL", "a-model", 50, false));
        cache.models.put("b-model", model(1L, "b-model"));
        cache.models.put("c-model", model(2L, "c-model"));
        cache.models.put("a-model", model(3L, "a-model"));

        List<String> order = codes(resolver().resolveByRule(SECRET_KEY, RULE_CODE, null, null));

        assertEquals(List.of("b-model", "a-model", "c-model"), order,
                "主目标恒钉首位；其余优先级降序，同优先级按 targetCode 升序");
    }

    @Test
    void fallsBackToFirstTargetRowWhenNoneMarkedPrimary() {
        baselineConsumerAndRule();
        cache.targets = List.of(
                targetRow("MODEL", "x-model", 10, false),
                targetRow("MODEL", "y-model", 90, false));
        cache.models.put("x-model", model(1L, "x-model"));
        cache.models.put("y-model", model(2L, "y-model"));

        List<String> order = codes(resolver().resolveByRule(SECRET_KEY, RULE_CODE, null, null));

        assertEquals(List.of("x-model", "y-model"), order,
                "无主目标标记时首行钉首位，其余按策略排——主目标运维契约不依赖 priority 数值");
    }

    @Test
    void collapsesDuplicateRowsOfTheSameTarget() {
        baselineConsumerAndRule();
        cache.targets = List.of(
                targetRow("MODEL", "dup-model", 50, true),
                targetRow("MODEL", "dup-model", 50, true),
                targetRow("MODEL", "other-model", 10, false));
        cache.models.put("dup-model", model(1L, "dup-model"));
        cache.models.put("other-model", model(2L, "other-model"));

        List<String> order = codes(resolver().resolveByRule(SECRET_KEY, RULE_CODE, null, null));

        assertEquals(List.of("dup-model", "other-model"), order,
                "同一路由目标（targetCode + 类型）只保留一条");
    }

    @Test
    void leastLoadReordersRestByInflightButKeepsPrimaryFirst() {
        baselineConsumerAndRule();
        cache.rule.setSelectionStrategy("LEAST_LOAD");
        cache.targets = List.of(
                targetRow("MODEL", "primary-model", 10, true),
                targetRow("MODEL", "busy-model", 90, false),
                targetRow("MODEL", "idle-model", 20, false));
        cache.models.put("primary-model", model(1L, "primary-model"));
        cache.models.put("busy-model", model(2L, "busy-model"));
        cache.models.put("idle-model", model(3L, "idle-model"));

        Map<String, Long> inflight = new LinkedHashMap<>();
        inflight.put(LOAD_KEY_PREFIX + "primary-model", 7L);
        inflight.put(LOAD_KEY_PREFIX + "busy-model", 5L);
        inflight.put(LOAD_KEY_PREFIX + "idle-model", 0L);
        StubLoadStore store = new StubLoadStore(inflight);
        RoutingResolver leastLoadResolver = new RoutingResolver(cache,
                new ModelPoolSelection(new Random(42)), store, new ModelLoadKeys("knot:test"), new StickyRouting());

        List<String> order = codes(leastLoadResolver.resolveByRule(SECRET_KEY, RULE_CODE, null, null));

        assertEquals(List.of("primary-model", "idle-model", "busy-model"), order,
                "LEAST_LOAD：主目标不参与负载重排，其余按在途升序");
        assertEquals(List.of(LOAD_KEY_PREFIX + "busy-model", LOAD_KEY_PREFIX + "idle-model"), store.lastBatchKeys,
                "在途值批量预取只覆盖参与重排的非主目标");
    }

    // ---------- 模型池展开 ----------

    @Test
    void expandsPoolTargetIntoEnabledModelCandidatesWithPoolCode() {
        baselineConsumerAndRule();
        cache.targets = List.of(targetRow("MODEL_POOL", "pool-a", 50, true));
        cache.pools.put("pool-a", pool("pool-a", "PRIORITY", null));
        cache.poolItems.put("pool-a", List.of(poolItem("m1", 20, 1), poolItem("m2", 10, 1)));
        cache.models.put("m1", model(11L, "m1"));
        cache.models.put("m2", model(12L, "m2"));

        ResolvedRouting resolved = resolver().resolveByRule(SECRET_KEY, RULE_CODE, null, null);

        assertEquals(List.of("m1", "m2"), codes(resolved), "池内按 PRIORITY 展开：优先级高者先行");
        RoutingRuleTargetDto first = resolved.candidateModels().get(0);
        assertEquals("pool-a", first.poolCode(), "池内展开的候选必须带出池 code（数仓归因用）");
        assertEquals("upstream-m1", first.upstreamModelCode(), "上游模型标识取自模型实体");
        assertEquals(11L, first.targetId(), "候选携带模型主键（下游取凭据/协议绑定用）");
    }

    @Test
    void skipsDisabledPoolItemsAndDisabledModels() {
        baselineConsumerAndRule();
        cache.targets = List.of(targetRow("MODEL_POOL", "pool-a", 50, true));
        cache.pools.put("pool-a", pool("pool-a", "PRIORITY", null));
        cache.poolItems.put("pool-a", List.of(
                poolItem("m1", 10, 0),
                poolItem("m2", 20, 1),
                poolItem("m3", 30, 1)));
        cache.models.put("m1", model(11L, "m1"));
        cache.models.put("m2", model(12L, "m2"));
        ModelEntity disabledModel = model(13L, "m3");
        disabledModel.setStatus(0);
        cache.models.put("m3", disabledModel);

        assertEquals(List.of("m2"), codes(resolver().resolveByRule(SECRET_KEY, RULE_CODE, null, null)),
                "停用条目与停用模型都不进候选链");
    }

    @Test
    void skipsDisabledPool() {
        baselineConsumerAndRule();
        cache.targets = List.of(targetRow("MODEL_POOL", "pool-a", 50, true));
        ModelPoolEntity pool = pool("pool-a", "PRIORITY", null);
        pool.setStatus(0);
        cache.pools.put("pool-a", pool);

        GatewayUpstreamException error = assertThrows(GatewayUpstreamException.class,
                () -> resolver().resolveByRule(SECRET_KEY, RULE_CODE, null, null));

        assertEquals(ProxyErrorCodeEnum.NO_ROUTING_TARGET.code(), error.code(), "停用的池整体不可用");
    }

    // ---------- 池内粘性 ----------

    @Test
    void stickyMovesPreferredCandidateToFrontOnly() {
        baselineConsumerAndRule();
        cache.targets = List.of(targetRow("MODEL_POOL", "pool-a", 50, true));
        cache.pools.put("pool-a", pool("pool-a", "PRIORITY", stickyJson(true)));
        cache.poolItems.put("pool-a", List.of(poolItem("m1", 30, 1), poolItem("m2", 20, 1), poolItem("m3", 10, 1)));
        cache.models.put("m1", model(11L, "m1"));
        cache.models.put("m2", model(12L, "m2"));
        cache.models.put("m3", model(13L, "m3"));

        ResolvedRouting resolved = resolver(new FixedStickyRouting("m3"), Map.of())
                .resolveByRule(SECRET_KEY, RULE_CODE, "sticky-key", null);

        assertEquals(List.of("m3", "m1", "m2"), codes(resolved),
                "粘性只把命中候选置顶为首选，其余保持池策略原序作 failover 链");
    }

    @Test
    void stickyDisabledKeepsPoolStrategyOrder() {
        baselineConsumerAndRule();
        cache.targets = List.of(targetRow("MODEL_POOL", "pool-a", 50, true));
        cache.pools.put("pool-a", pool("pool-a", "PRIORITY", null));
        cache.poolItems.put("pool-a", List.of(poolItem("m1", 30, 1), poolItem("m2", 20, 1), poolItem("m3", 10, 1)));
        cache.models.put("m1", model(11L, "m1"));
        cache.models.put("m2", model(12L, "m2"));
        cache.models.put("m3", model(13L, "m3"));

        ResolvedRouting resolved = resolver().resolveByRule(SECRET_KEY, RULE_CODE, "sticky-key", null);

        assertEquals(List.of("m1", "m2", "m3"), codes(resolved),
                "粘性未配置（默认关闭）时，即使传了粘性键也按池策略原序");
    }

    // ---------- 协议过滤 ----------

    @Test
    void filtersCandidatesByDeclaredProtocolBinding() {
        baselineConsumerAndRule();
        cache.targets = List.of(
                targetRow("MODEL", "m1", 10, true),
                targetRow("MODEL", "m2", 20, false),
                targetRow("MODEL", "m3", 30, false));
        cache.models.put("m1", model(11L, "m1"));
        cache.models.put("m2", model(12L, "m2"));
        cache.models.put("m3", model(13L, "m3"));
        cache.bindings.put(11L, List.of(binding("MESSAGES", 1)));
        cache.bindings.put(12L, List.of(binding("CHAT_COMPLETIONS", 1)));
        cache.bindings.put(13L, List.of(binding("MESSAGES", 0)));

        List<String> kept = codes(resolver().resolveByRule(SECRET_KEY, RULE_CODE, null, ModelApiProtocolEnum.MESSAGES));

        assertEquals(List.of("m1"), kept, "只保留显式声明且启用该协议绑定的模型；停用绑定不构成声明");
    }

    @Test
    void unrecognizedProtocolKeepsWholeCandidateChain() {
        baselineConsumerAndRule();
        cache.targets = List.of(
                targetRow("MODEL", "m1", 50, true),
                targetRow("MODEL", "m2", 50, false),
                targetRow("MODEL", "m3", 50, false));
        cache.models.put("m1", model(11L, "m1"));
        cache.models.put("m2", model(12L, "m2"));
        cache.models.put("m3", model(13L, "m3"));

        List<String> kept = codes(resolver().resolveByRule(SECRET_KEY, RULE_CODE, null, null));

        assertEquals(List.of("m1", "m2", "m3"), kept, "未识别协议不过滤，保持既有候选链");
    }

    // ---------- 上下文与策略 ----------

    @Test
    void buildsRoutingContextAndPoliciesFromRuleConsumerApp() {
        baselineConsumerAndRule();
        cache.rule.setRetryPolicy("{\"enabled\":true,\"maxAttempts\":3}");
        cache.targets = List.of(targetRow("MODEL", "m1", 100, true));
        cache.models.put("m1", model(11L, "m1"));

        ResolvedRouting resolved = resolver().resolveByRule(SECRET_KEY, RULE_CODE, null, null);

        assertEquals(200L, resolved.ruleId());
        assertEquals(RULE_CODE, resolved.ruleCode());
        assertEquals(100L, resolved.consumerId());
        assertEquals(SECRET_KEY, resolved.secretKey());
        assertTrue(resolved.returnUsageDetail(), "消费者开启用量明细");
        GatewayRoutingContext context = resolved.routingContext();
        assertEquals("规则A", context.rule().name());
        assertEquals("chat", context.rule().appScenario());
        assertEquals("alice", context.rule().username());
        assertEquals(CONSUMER_CODE, context.consumer().code());
        assertEquals("app-a", context.app().appId());
        assertEquals("dept-a", context.app().deptCode());
        assertEquals("alice", context.consumerUser().username());
        assertEquals("bob", context.appOwner().username());
        assertEquals(3, resolved.retryPolicy().maxAttempts(), "规则级重试策略按 JSON 解析");
        assertFalse(resolved.effectiveEjectionPolicy().enabled(), "未配置摘除策略时走默认（关闭）");
    }

    // ---------- 夹具 ----------

    private void baselineConsumerAndRule() {
        RoutingConsumerEntity consumer = new RoutingConsumerEntity();
        consumer.setId(100L);
        consumer.setConsumerCode(CONSUMER_CODE);
        consumer.setName("消费者A");
        consumer.setSecretKey(SECRET_KEY);
        consumer.setUserUsername("alice");
        consumer.setUserRealName("Alice");
        consumer.setReturnUsageDetail(Boolean.TRUE);
        consumer.setStatus(1);
        cache.consumer = consumer;

        RoutingRuleEntity rule = new RoutingRuleEntity();
        rule.setId(200L);
        rule.setRuleCode(RULE_CODE);
        rule.setName("规则A");
        rule.setAppScenario("chat");
        rule.setUsername("alice");
        rule.setAppId(300L);
        rule.setAppCode("app-a");
        rule.setStatus(1);
        cache.rule = rule;

        AppEntity app = new AppEntity();
        app.setId(300L);
        app.setAppCode("app-a");
        app.setName("应用A");
        app.setDeptCode("dept-a");
        app.setOwnerUsername("bob");
        app.setOwnerRealName("Bob");
        app.setStatus(1);
        cache.app = app;
    }

    private static RoutingRuleTargetEntity targetRow(String type, String code, int priority, boolean primary) {
        RoutingRuleTargetEntity entity = new RoutingRuleTargetEntity();
        entity.setTargetType(type);
        entity.setTargetCode(code);
        entity.setTargetId(900L + priority);
        entity.setPriority(priority);
        entity.setPrimary(primary);
        entity.setTargetName("目标-" + code);
        entity.setModelType("CHAT");
        return entity;
    }

    private static ModelEntity model(long id, String code) {
        ModelEntity entity = new ModelEntity();
        entity.setId(id);
        entity.setModelCode(code);
        entity.setUpstreamModel("upstream-" + code);
        entity.setModelType("CHAT");
        entity.setProviderAccountCode("pa-" + code);
        entity.setStatus(1);
        return entity;
    }

    private static ModelPoolEntity pool(String poolCode, String strategy, String stickyPolicyJson) {
        ModelPoolEntity pool = new ModelPoolEntity();
        pool.setId(400L);
        pool.setPoolCode(poolCode);
        pool.setSelectionStrategy(strategy);
        pool.setStickyPolicy(stickyPolicyJson);
        pool.setStatus(1);
        return pool;
    }

    private static ModelPoolItemEntity poolItem(String modelCode, int priority, int status) {
        ModelPoolItemEntity item = new ModelPoolItemEntity();
        item.setId(1000L + priority);
        item.setPoolCode("pool-a");
        item.setModelCode(modelCode);
        item.setPriority(priority);
        item.setWeight(100);
        item.setStatus(status);
        item.setModelName("模型-" + modelCode);
        return item;
    }

    private static ModelApiBindingEntity binding(String protocol, int status) {
        ModelApiBindingEntity entity = new ModelApiBindingEntity();
        entity.setProtocol(protocol);
        entity.setStatus(status);
        return entity;
    }

    private static String stickyJson(boolean enabled) {
        return "{\"enabled\":" + enabled + ",\"headerName\":\"X-Knot-Sticky-Key\"}";
    }

    private static List<String> codes(ResolvedRouting resolved) {
        return resolved.candidateModels().stream()
                .map(RoutingRuleTargetDto::targetCode)
                .toList();
    }

    /**
     * 数据桩：只覆写 {@link RoutingResolver} 实际调用的缓存读取方法。
     *
     * <p>构造器的 mapper 参数不能传 null——javac 对绑定方法引用（{@code appMapper::getById}）的
     * 接收者会显式插入 {@code Objects.requireNonNull}（反汇编构造器字节码可证），故用 JDK 动态代理
     * 占位：缓存加载器只在未命中时触发，而本替身覆写了全部读取方法，代理永远不会被调用。</p>
     */
    static final class StubEntityCache extends GatewayEntityCacheService {

        private RoutingConsumerEntity consumer;
        private RoutingRuleEntity rule;
        private AppEntity app;
        private List<RoutingRuleTargetEntity> targets = List.of();
        private final Map<String, ModelEntity> models = new LinkedHashMap<>();
        private final Map<String, ModelPoolEntity> pools = new LinkedHashMap<>();
        private final Map<String, List<ModelPoolItemEntity>> poolItems = new LinkedHashMap<>();
        private final Map<Long, List<ModelApiBindingEntity>> bindings = new LinkedHashMap<>();

        StubEntityCache() {
            super(nullReturningProxy(AppMapper.class),
                    nullReturningProxy(RoutingConsumerMapper.class),
                    nullReturningProxy(RoutingRuleMapper.class),
                    nullReturningProxy(RoutingRuleTargetMapper.class),
                    nullReturningProxy(ModelMapper.class),
                    nullReturningProxy(ModelPoolMapper.class),
                    nullReturningProxy(ProviderAccountMapper.class),
                    nullReturningProxy(ProviderCredentialMapper.class),
                    nullReturningProxy(ModelApiBindingMapper.class),
                    new ResourceTrafficPolicySupport(null, null, null, null),
                    nullReturningProxy(BillingRuleMapper.class));
        }

        @Override
        public RoutingConsumerEntity getConsumerBySecretKey(String secretKey) {
            return consumer;
        }

        @Override
        public RoutingRuleEntity getEnabledRuleByConsumerCodeAndCode(String consumerCode, String ruleCode) {
            return rule;
        }

        @Override
        public AppEntity getAppById(Long id) {
            return app;
        }

        @Override
        public List<RoutingRuleTargetEntity> listTargetsByRuleCode(String ruleCode) {
            return targets;
        }

        @Override
        public ModelEntity getModelByCode(String modelCode) {
            return models.get(modelCode);
        }

        @Override
        public ModelPoolEntity getModelPoolByCode(String poolCode) {
            return pools.get(poolCode);
        }

        @Override
        public List<ModelPoolItemEntity> listModelPoolItemsByPoolCode(String poolCode) {
            return poolItems.getOrDefault(poolCode, List.of());
        }

        @Override
        public List<ModelApiBindingEntity> listApiBindingsByModelId(Long modelId) {
            return bindings.getOrDefault(modelId, List.of());
        }
    }

    /** 在途计量桩：记录最近一次批量读取的键集合（保序），按配置返回在途值 */
    static final class StubLoadStore implements ModelLoadStore {

        private final Map<String, Long> inflight;
        private List<String> lastBatchKeys = List.of();

        StubLoadStore(Map<String, Long> inflight) {
            this.inflight = inflight;
        }

        @Override
        public String id() {
            return "stub";
        }

        @Override
        public long enter(String key) {
            throw new UnsupportedOperationException("选路热路径只读在途值");
        }

        @Override
        public long leave(String key) {
            throw new UnsupportedOperationException("选路热路径只读在途值");
        }

        @Override
        public long inflight(String key) {
            return inflight.getOrDefault(key, 0L);
        }

        @Override
        public Map<String, Long> inflightBatch(List<String> keys) {
            lastBatchKeys = List.copyOf(keys);
            Map<String, Long> result = new LinkedHashMap<>();
            for (String key : keys) {
                result.put(key, inflight.getOrDefault(key, 0L));
            }
            return result;
        }
    }

    /**
     * 接口桩：任何方法调用返回 null（基本型返回零值）。
     * 只用于满足构造器的非空参数约束，本替身生命周期内不应被触发。
     */
    @SuppressWarnings("unchecked")
    private static <T> T nullReturningProxy(Class<T> type) {
        return (T) Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{type},
                (proxy, method, args) -> switch (method.getReturnType().getName()) {
                    case "boolean" -> false;
                    case "int" -> 0;
                    case "long" -> 0L;
                    case "double" -> 0d;
                    case "float" -> 0f;
                    case "short" -> (short) 0;
                    case "byte" -> (byte) 0;
                    case "char" -> (char) 0;
                    default -> null;
                });
    }

    /** 粘性桩：固定返回指定首选，免于测试依赖一致性 hash 的内部结果 */
    static final class FixedStickyRouting extends StickyRouting {

        private final String preferred;

        FixedStickyRouting(String preferred) {
            this.preferred = preferred;
        }

        @Override
        public String preferred(Collection<String> candidates, StickyPolicy policy, String stickyKey) {
            return preferred;
        }
    }
}
