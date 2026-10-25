package org.chobit.knot.svc.service;

import lombok.extern.slf4j.Slf4j;
import org.chobit.knot.adapter.model.ref.ModelApiBindingRef;
import org.chobit.knot.svc.dto.routing.BillingCheckResult;
import org.chobit.knot.svc.dto.routing.BillingFact;
import org.chobit.knot.svc.dto.routing.BillingFactAmount;
import org.chobit.knot.svc.dto.routing.BillingFactAssemblyInput;
import org.chobit.knot.svc.service.support.BillingFactChecks;
import org.chobit.knot.svc.dto.routing.JevJudgeContext;
import org.chobit.knot.dal.entity.ModelApiBindingEntity;
import org.chobit.knot.dal.entity.ModelEntity;
import org.chobit.knot.dal.mapper.ModelApiBindingMapper;
import org.chobit.knot.dal.mapper.ModelMapper;
import org.chobit.knot.adapter.model.usage.NormalizedUsage;
import org.chobit.knot.adapter.model.usage.NormalizedUsageDetail;
import org.chobit.knot.svc.service.support.BillingFactAssembler;
import org.chobit.knot.svc.service.support.JevBillingJudge;
import org.chobit.knot.adapter.usage.UsageExtractorResolver;
import org.chobit.knot.svc.vo.routing.RoutingBillingVerdict;
import org.chobit.knot.svc.vo.routing.RoutingBillingVerdictRequest;
import org.chobit.knot.svc.vo.routing.RoutingBillingVerdictResult;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 路由规则测试·计费裁判编排服务（v7 §3 / §8）：薄壳编排，不含业务逻辑本体。
 *
 * <p>四段流水线：被告链（已产出）→ 影子链（{@link BillingFactAssembler} 重算）
 * → 自检（{@link BillingFactChecks} 七项）→ 判定（{@link JevBillingJudge} 双盲）。
 * 判定层只看「被告账 + 底层依据」，绝不喂影子/自检结论。</p>
 *
 * <p>模型定位（{@code targetCode} 优先于 {@code modelCode}）用于确定计费规则与用量提取器，
 * 提取器选择与网关共用 {@code UsageExtractorResolver}（改进清单 3.4）；定位失败仅降级，不抛异常。</p>
 *
 * <p>编排层内部异常以 ERROR 日志报警（全仓无 webhook/Notifier，ERROR 日志即唯一报警通道）。</p>
 */
@Slf4j
@Service
public class RoutingTestBillingVerdictService {

    /** 提取器码常量（与 knot-adapter 各 UsageExtractor.CODE 一致，避免跨模块常量依赖） */
    private static final String EXTRACTOR_DEFAULT_TOKEN = "DEFAULT-TOKEN";

    private final BillingFactAssembler assembler;
    private final JevBillingJudge judge;
    private final ModelMapper modelMapper;
    private final ModelApiBindingMapper modelApiBindingMapper;

    public RoutingTestBillingVerdictService(BillingFactAssembler assembler,
                                           JevBillingJudge judge,
                                           ModelMapper modelMapper,
                                           ModelApiBindingMapper modelApiBindingMapper) {
        this.assembler = assembler;
        this.judge = judge;
        this.modelMapper = modelMapper;
        this.modelApiBindingMapper = modelApiBindingMapper;
    }

    public RoutingBillingVerdictResult verdict(RoutingBillingVerdictRequest req,
                                                String gatewayBaseUrl,
                                                String sessionId) {
        Map<String, Object> knotExtend = nonNullMap(req.knotExtend());
        Map<String, Object> gatewayUsage = castMap(knotExtend.get("usage"));
        NormalizedUsage gatewayBilling = toNormalizedUsage(knotExtend.get("billing"));

        Map<String, Object> upstreamUsage = nonNullMap(req.upstreamUsage());

        // 影子链 + 自检：任何运行时异常都降级返回（端点 500 无意义，抽屉已渲染主结果）
        BillingFact fact;
        List<BillingCheckResult> checks;
        try {
            // 定位模型 → 计费规则 + 用量提取器
            String modelKey = firstNonBlank(req.targetCode(), req.modelCode());
            ModelEntity model = modelKey == null ? null : modelMapper.getByCode(modelKey);
            String ruleCode = model == null ? null : model.getBillingRuleCode();
            String familyCode = model == null ? null : model.getModelFamilyCode();
            String extractorCode = resolveExtractorCode(model, req.protocol(), req.stream());

            // 影子链输入：upstreamRawBody 包成 {"usage": upstreamUsage}
            // （默认 token 提取器经 UsageRawReader.readUsage 读 body.usage）
            Map<String, Object> upstreamRawBody = Map.of("usage", upstreamUsage);
            BillingFactAssemblyInput input = new BillingFactAssemblyInput(
                    modelKey, familyCode, ruleCode, extractorCode, req.protocol(), req.stream(),
                    upstreamRawBody, upstreamUsage, req.requestBody(),
                    gatewayBilling, upstreamUsage, Instant.now());

            fact = assembler.assemble(input);
            checks = BillingFactChecks.runAll(fact);
        } catch (RuntimeException ex) {
            // ERROR 即报警；不落 requestBody（可能含 prompt 等敏感内容）
            log.error("计费校验影子链异常: targetCode={}, modelCode={}, {}",
                    req.targetCode(), req.modelCode(), briefMessage(ex), ex);
            // 异常归属影子链，不得写进 verdict.reason（否则区三误导成「裁判挂了」）
            return new RoutingBillingVerdictResult(
                    gatewayUsage, BillingFactAmount.from(gatewayBilling), null, upstreamUsage, null, null, List.of(),
                    "计费校验内部异常：" + briefMessage(ex),
                    RoutingBillingVerdict.unavailable("影子链未完成，未进入裁判环节"));
        }

        // 判定层（双盲）：只喂被告账 + 底层依据，绝不喂影子/自检结论
        RoutingBillingVerdict verdict;
        try {
            JevJudgeContext ctx = new JevJudgeContext(
                    fact.subject(), gatewayUsage, fact.gatewayBilling(),
                    upstreamUsage, fact.upstreamCost(), fact.ruleRef(), req.requestBody());
            verdict = judge.judge(ctx, gatewayBaseUrl, sessionId);
        } catch (RuntimeException ex) {
            log.error("计费裁判判定层异常: targetCode={}, {}", req.targetCode(), briefMessage(ex), ex);
            verdict = RoutingBillingVerdict.unavailable("裁判内部异常：" + briefMessage(ex));
        }

        return new RoutingBillingVerdictResult(
                gatewayUsage,
                fact.gatewayBilling(),
                fact.ruleRef(),
                upstreamUsage,
                fact.upstreamCost() == null ? null : fact.upstreamCost().toPlainString(),
                fact.shadowAmount(),
                checks,
                null,
                verdict);
    }

    /** 异常摘要（截断，避免把超长 SQL/堆栈塞进返回体） */
    private static String briefMessage(RuntimeException ex) {
        String message = ex.getMessage();
        if (message == null || message.isBlank()) {
            return ex.getClass().getSimpleName();
        }
        return message.length() > 200 ? message.substring(0, 200) + "…" : message;
    }

    // ---- 提取器解析（改进清单 3.4，2026-10-24：与网关共用 UsageExtractorResolver，绑定配置驱动） ----

    private String resolveExtractorCode(ModelEntity model, String protocol, boolean stream) {
        String configured = null;
        if (model != null && model.getId() != null) {
            // MyBatis 正常返回空集合，但桩/异常路径可能给出 null，防御后再投影
            List<ModelApiBindingEntity> bindings = modelApiBindingMapper.listByModelId(model.getId());
            configured = UsageExtractorResolver.fromBindings(toBindingRefs(bindings), protocol, stream);
        }
        if (configured != null) {
            return configured;
        }
        // 与网关同口径（改进清单 2.2b）：ANTHROPIC 供应商特例与 protocol→IMAGE/VIDEO/AUDIO
        // 家族映射已移除，未配置一律回落默认提取器。
        return EXTRACTOR_DEFAULT_TOKEN;
    }

    /**
     * 绑定实体 → 适配层投影（改进清单 2.5）：只带提取器选择消费的三个字段。
     *
     * <p>投影发生在 service 模块边界上，{@code apiPath}/{@code requestAdapter}
     * 对提取器选择无意义，传 {@code null}。空白/未配置的提取器由
     * {@link UsageExtractorResolver} 统一判定，本方法不做任何「猜配置」。</p>
     */
    private static List<ModelApiBindingRef> toBindingRefs(List<ModelApiBindingEntity> bindings) {
        if (bindings == null) {
            return null;
        }
        List<ModelApiBindingRef> refs = new ArrayList<>();
        for (ModelApiBindingEntity b : bindings) {
            refs.add(new ModelApiBindingRef(null, null, b.getProtocol(),
                    b.getUsageExtractor(), b.getStreamUsageExtractor()));
        }
        return refs;
    }

    // ---- knot_extend.billing(Map) → NormalizedUsage（手动构造，规避 record 反序列化风险） ----

    private static NormalizedUsage toNormalizedUsage(Object raw) {
        if (!(raw instanceof Map<?, ?> m)) {
            return null;
        }
        @SuppressWarnings("unchecked")
        Map<String, Object> b = (Map<String, Object>) m;
        Long totalTokens = toLong(b.get("totalTokens"));
        String totalCost = b.get("totalCost") == null ? null : String.valueOf(b.get("totalCost"));
        String currency = optString(b.get("currency"));
        String billingVersion = optString(b.get("billingVersion"));
        List<NormalizedUsageDetail> detail = toDetails(b.get("detail"));
        return new NormalizedUsage(totalTokens, totalCost, currency, billingVersion, detail);
    }

    @SuppressWarnings("unchecked")
    private static List<NormalizedUsageDetail> toDetails(Object raw) {
        if (!(raw instanceof List<?> list)) {
            return List.of();
        }
        List<NormalizedUsageDetail> out = new ArrayList<>();
        for (Object o : list) {
            if (!(o instanceof Map<?, ?> m)) {
                continue;
            }
            Map<String, Object> d = (Map<String, Object>) m;
            out.add(new NormalizedUsageDetail(
                    optString(d.get("type")),
                    toLong(d.get("tokens")),
                    optString(d.get("cost")),
                    optString(d.get("price"))));
        }
        return List.copyOf(out);
    }

    // ---- 工具 ----

    private static Map<String, Object> nonNullMap(Map<String, Object> m) {
        return m == null ? Map.of() : m;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> castMap(Object o) {
        return o instanceof Map<?, ?> m ? (Map<String, Object>) m : Map.of();
    }

    private static Long toLong(Object o) {
        if (o == null) {
            return null;
        }
        if (o instanceof Number n) {
            return n.longValue();
        }
        try {
            return Long.parseLong(o.toString().trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static String optString(Object o) {
        return o == null ? null : o.toString();
    }

    private static String firstNonBlank(String... values) {
        for (String v : values) {
            if (v != null && !v.isBlank()) {
                return v.trim();
            }
        }
        return null;
    }
}
