package org.chobit.knot.gateway.service;

import org.chobit.knot.gateway.dto.routing.BillingFact;
import org.chobit.knot.gateway.dto.routing.BillingFactAmount;
import org.chobit.knot.gateway.dto.routing.BillingFactAssemblyInput;
import org.chobit.knot.gateway.dto.routing.BillingFactRuleRef;
import org.chobit.knot.gateway.dto.routing.BillingFactSubject;
import org.chobit.knot.gateway.dto.routing.BillingFactUsage;
import org.chobit.knot.gateway.dto.routing.BillingRuleSource;
import org.chobit.knot.gateway.dto.routing.JevJudgeContext;
import org.chobit.knot.gateway.entity.ModelApiBindingEntity;
import org.chobit.knot.gateway.entity.ModelEntity;
import org.chobit.knot.gateway.mapper.ModelApiBindingMapper;
import org.chobit.knot.gateway.mapper.ModelMapper;
import org.chobit.knot.gateway.model.NormalizedUsage;
import org.chobit.knot.gateway.service.support.BillingFactAssembler;
import org.chobit.knot.gateway.service.support.JevBillingJudge;
import org.chobit.knot.gateway.vo.routing.RoutingBillingVerdict;
import org.chobit.knot.gateway.vo.routing.RoutingBillingVerdictRequest;
import org.chobit.knot.gateway.vo.routing.RoutingBillingVerdictResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link RoutingTestBillingVerdictService} 编排单测：四段流水线（被告 / 影子 / 自检 / 判定）
 * 的协作与降级边界，不依赖真实网关与 DB。
 *
 * <p>用子类替身与 JDK 动态代理桩而非 Mockito：JDK 25 下 Byte Buddy 无法改写具体类
 * （与 {@code RoutingRuleStreamControllerTest} 同款做法），保证标准 {@code mvn test} 零特殊标志可跑。</p>
 */
class RoutingTestBillingVerdictServiceTest {

    private StubAssembler assembler;
    private StubJudge judge;
    private ModelMapper modelMapper;
    private ModelApiBindingMapper bindingMapper;
    private RoutingTestBillingVerdictService service;

    /** 按 code 可查的模型表，供 {@code ModelMapper#getByCode} 桩使用 */
    private final Map<String, ModelEntity> models = new HashMap<>();
    /** 按模型 id 可查的协议绑定表，供 {@code ModelApiBindingMapper#listByModelId} 桩使用 */
    private final Map<Long, List<ModelApiBindingEntity>> bindings = new HashMap<>();

    @BeforeEach
    void setUp() {
        assembler = new StubAssembler();
        judge = new StubJudge();
        models.clear();
        bindings.clear();
        modelMapper = proxy(ModelMapper.class, (proxyObj, method, args) -> {
            if ("getByCode".equals(method.getName())) {
                return models.get(args[0]);
            }
            return defaultValue(method.getReturnType());
        });
        bindingMapper = proxy(ModelApiBindingMapper.class, (proxyObj, method, args) -> {
            if ("listByModelId".equals(method.getName())) {
                return bindings.getOrDefault(args[0], List.of());
            }
            return defaultValue(method.getReturnType());
        });
        service = new RoutingTestBillingVerdictService(assembler, judge, modelMapper, bindingMapper);
    }

    @Test
    void verdictReturnsChecksAndVerdictWhenNormal() {
        RoutingBillingVerdictRequest req = requestWithBilling("0.00129", "USD", "CHAT_COMPLETIONS");
        models.put("gpt-4", model(10L, "BR-1", "FAM-1", "OPENAI"));
        bindings.put(10L, List.of());

        NormalizedUsage gateway = normalized("0.00129", "USD", 100L);
        assembler.result = factWith(gateway);
        judge.result = RoutingBillingVerdict.available("summary", List.of());

        RoutingBillingVerdictResult result = service.verdict(req, "http://gw", "sess-1");

        assertNotNull(result.checks());
        assertTrue(result.verdict().isAvailable());
        assertEquals("0.00129", result.producedBilling().totalCost());

        // 验证 knot_extend.billing 经 toNormalizedUsage 正确传入影子链输入
        assertNotNull(assembler.captured, "影子链必须被调用");
        assertEquals("0.00129", assembler.captured.gatewayBilling().totalCost());
        assertEquals("http://gw", judge.capturedGatewayBaseUrl);
        assertEquals("sess-1", judge.capturedSessionId);
    }

    @Test
    void verdictDegradesWhenModelMissing() {
        RoutingBillingVerdictRequest req = requestWithBilling("0.001", "USD", "CHAT_COMPLETIONS");
        // models 为空：getByCode 返回 null，提取器回落到默认 token

        NormalizedUsage gateway = normalized("0.001", "USD", 10L);
        assembler.result = factWith(gateway);
        judge.result = RoutingBillingVerdict.available("s", List.of());

        RoutingBillingVerdictResult result = service.verdict(req, "http://gw", null);

        assertTrue(result.verdict().isAvailable());
        assertNotNull(result.checks());
        // 模型缺失不抛异常，提取器回落默认 token
        assertEquals("DEFAULT-TOKEN", assembler.captured.extractorCode());
    }

    @Test
    void verdictDoesNotThrowWhenJudgeUnavailable() {
        RoutingBillingVerdictRequest req = requestWithBilling("0.001", "USD", "CHAT_COMPLETIONS");
        models.put("gpt-4", model(1L, "BR", "F", "OPENAI"));
        bindings.put(1L, List.of());
        assembler.result = factWith(normalized("0.001", "USD", 5L));
        judge.result = RoutingBillingVerdict.unavailable("裁判未配置");

        RoutingBillingVerdictResult result = service.verdict(req, "http://gw", "s");

        assertFalse(result.verdict().isAvailable());
        assertEquals("裁判未配置", result.verdict().getReason());
        assertNotNull(result.checks());
    }

    @Test
    void resolvesImageExtractorForImageProtocol() {
        RoutingBillingVerdictRequest req = requestWithBilling("0.01", "USD", "IMAGE_GENERATIONS");
        models.put("gpt-4", model(2L, "BR", "F", "STABILITY"));
        bindings.put(2L, List.of());
        assembler.result = factWith(normalized("0.01", "USD", 0L));
        judge.result = RoutingBillingVerdict.available("s", List.of());

        service.verdict(req, "http://gw", "s");

        assertEquals("IMAGE", assembler.captured.extractorCode());
    }

    @Test
    void passesNullGatewayBillingWhenBillingOmitted() {
        // knot_extend 在场但 billing 省略（无规则命中）：透传 null，不伪造金额
        RoutingBillingVerdictRequest req = requestWithoutBilling("CHAT_COMPLETIONS");
        models.put("gpt-4", model(3L, "BR", "F", "OPENAI"));
        bindings.put(3L, List.of());
        assembler.result = factWith(normalized("0.001", "USD", 10L));
        judge.result = RoutingBillingVerdict.available("s", List.of());

        RoutingBillingVerdictResult result = service.verdict(req, "http://gw", "s");

        assertNull(assembler.captured.gatewayBilling(), "billing 缺失应透传 null");
        assertNotNull(result.checks());
    }

    @Test
    void extractorFromBindingTakesPrecedenceOverProtocolDefault() {
        // 命中协议绑定的 usage_extractor 应优先于按协议推断的默认提取器（镜像网关解析顺序）
        RoutingBillingVerdictRequest req = requestWithBilling("0.01", "USD", "IMAGE_GENERATIONS");
        models.put("gpt-4", model(4L, "BR", "F", "OPENAI"));
        ModelApiBindingEntity binding = new ModelApiBindingEntity();
        binding.setProtocol("IMAGE_GENERATIONS");
        binding.setUsageExtractor("IMAGE-BINDING");
        bindings.put(4L, List.of(binding));
        assembler.result = factWith(normalized("0.01", "USD", 0L));
        judge.result = RoutingBillingVerdict.available("s", List.of());

        service.verdict(req, "http://gw", "s");

        assertEquals("IMAGE-BINDING", assembler.captured.extractorCode());
    }

    // ---- helpers ----

    private static RoutingBillingVerdictRequest requestWithBilling(String totalCost, String currency, String protocol) {
        Map<String, Object> billing = Map.of(
                "totalTokens", 100L,
                "totalCost", totalCost,
                "currency", currency);
        Map<String, Object> knotExtend = Map.of("usage", Map.of(), "billing", billing);
        return new RoutingBillingVerdictRequest(
                "gpt-4", "gpt-4", protocol, false, knotExtend, Map.of(), Map.of(), "sess-1");
    }

    /** knot_extend 在场但 billing 省略（无规则命中）：被告账为 null 的降级路径 */
    private static RoutingBillingVerdictRequest requestWithoutBilling(String protocol) {
        Map<String, Object> knotExtend = Map.of("usage", Map.of());
        return new RoutingBillingVerdictRequest(
                "gpt-4", "gpt-4", protocol, false, knotExtend, Map.of(), Map.of(), "sess-1");
    }

    private static ModelEntity model(Long id, String ruleCode, String family, String provider) {
        ModelEntity m = new ModelEntity();
        m.setId(id);
        m.setBillingRuleCode(ruleCode);
        m.setModelFamilyCode(family);
        m.setProviderCode(provider);
        return m;
    }

    private static NormalizedUsage normalized(String totalCost, String currency, long totalTokens) {
        return new NormalizedUsage(totalTokens, totalCost, currency, null, List.of());
    }

    private static BillingFact factWith(NormalizedUsage gatewayBilling) {
        BillingFactAmount amount = BillingFactAmount.from(gatewayBilling);
        BillingFactSubject subject = new BillingFactSubject("gpt-4", "CHAT_COMPLETIONS", false, BillingRuleSource.EXPLICIT);
        BillingFactUsage usage = new BillingFactUsage(true, 0L, 0L, 0L, 0L, 0L, 0L);
        BillingFactRuleRef ruleRef = new BillingFactRuleRef("BR-1", "TOKEN", "1M_TOKENS", "USD",
                "FIXED", BillingRuleSource.EXPLICIT, "v1");
        return new BillingFact(subject, usage, ruleRef, amount, amount, null);
    }

    @SuppressWarnings("unchecked")
    private static <T> T proxy(Class<T> type, InvocationHandler handler) {
        return (T) Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{type}, handler);
    }

    private static Object defaultValue(Class<?> type) {
        if (!type.isPrimitive()) {
            return null;
        }
        if (type == boolean.class) {
            return false;
        }
        if (type == byte.class) {
            return (byte) 0;
        }
        if (type == short.class) {
            return (short) 0;
        }
        if (type == int.class) {
            return 0;
        }
        if (type == long.class) {
            return 0L;
        }
        if (type == float.class) {
            return 0f;
        }
        if (type == double.class) {
            return 0d;
        }
        if (type == char.class) {
            return (char) 0;
        }
        return null;
    }

    /** 影子链替身：记录入参，返回预设事实 */
    static class StubAssembler extends BillingFactAssembler {

        BillingFact result;
        BillingFactAssemblyInput captured;

        StubAssembler() {
            super(null, null);
        }

        @Override
        public BillingFact assemble(BillingFactAssemblyInput input) {
            this.captured = input;
            return result;
        }
    }

    /** 判定层替身：记录入参，返回预设结论 */
    static class StubJudge extends JevBillingJudge {

        RoutingBillingVerdict result;
        String capturedGatewayBaseUrl;
        String capturedSessionId;
        JevJudgeContext capturedContext;

        StubJudge() {
            super(null, null, null, null, null);
        }

        @Override
        public RoutingBillingVerdict judge(JevJudgeContext context, String gatewayBaseUrl, String sessionId) {
            this.capturedContext = context;
            this.capturedGatewayBaseUrl = gatewayBaseUrl;
            this.capturedSessionId = sessionId;
            return result;
        }
    }
}
