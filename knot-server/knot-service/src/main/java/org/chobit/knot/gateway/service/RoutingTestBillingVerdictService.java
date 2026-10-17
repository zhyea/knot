package org.chobit.knot.gateway.service;

import org.chobit.knot.gateway.constants.enums.ModelApiProtocolEnum;
import org.chobit.knot.gateway.dto.routing.BillingCheckResult;
import org.chobit.knot.gateway.dto.routing.BillingFact;
import org.chobit.knot.gateway.dto.routing.BillingFactAssemblyInput;
import org.chobit.knot.gateway.service.support.BillingFactChecks;
import org.chobit.knot.gateway.dto.routing.JevJudgeContext;
import org.chobit.knot.gateway.entity.ModelApiBindingEntity;
import org.chobit.knot.gateway.entity.ModelEntity;
import org.chobit.knot.gateway.mapper.ModelApiBindingMapper;
import org.chobit.knot.gateway.mapper.ModelMapper;
import org.chobit.knot.gateway.model.NormalizedUsage;
import org.chobit.knot.gateway.model.NormalizedUsageDetail;
import org.chobit.knot.gateway.service.support.BillingFactAssembler;
import org.chobit.knot.gateway.service.support.JevBillingJudge;
import org.chobit.knot.gateway.vo.routing.RoutingBillingVerdict;
import org.chobit.knot.gateway.vo.routing.RoutingBillingVerdictRequest;
import org.chobit.knot.gateway.vo.routing.RoutingBillingVerdictResult;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
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
 * 分支顺序镜像网关 {@code UsageExtractorRegistry.resolveExtractorCode}；定位失败仅降级，不抛异常。</p>
 */
@Service
public class RoutingTestBillingVerdictService {

    /** 提取器码常量（与 knot-adapter 各 UsageExtractor.CODE 一致，避免跨模块常量依赖） */
    private static final String EXTRACTOR_DEFAULT_TOKEN = "DEFAULT-TOKEN";
    private static final String EXTRACTOR_ANTHROPIC = "ANTHROPIC";
    private static final String EXTRACTOR_IMAGE = "IMAGE";
    private static final String EXTRACTOR_VIDEO = "VIDEO";
    private static final String EXTRACTOR_AUDIO = "AUDIO";

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

        BillingFact fact = assembler.assemble(input);
        List<BillingCheckResult> checks = BillingFactChecks.runAll(fact);

        // 判定层（双盲）：只喂被告账 + 底层依据，绝不喂影子/自检结论
        JevJudgeContext ctx = new JevJudgeContext(
                fact.subject(), gatewayUsage, fact.gatewayBilling(),
                upstreamUsage, fact.upstreamCost(), fact.ruleRef(), req.requestBody());
        RoutingBillingVerdict verdict = judge.judge(ctx, gatewayBaseUrl, sessionId);

        return new RoutingBillingVerdictResult(
                gatewayUsage,
                fact.gatewayBilling(),
                fact.ruleRef(),
                upstreamUsage,
                fact.upstreamCost() == null ? null : fact.upstreamCost().toPlainString(),
                fact.shadowAmount(),
                checks,
                verdict);
    }

    // ---- 提取器解析（镜像网关 UsageExtractorRegistry.resolveExtractorCode） ----

    private String resolveExtractorCode(ModelEntity model, String protocol, boolean stream) {
        if (model != null && model.getId() != null) {
            // MyBatis 正常返回空集合，但桩/异常路径可能给出 null，防御后再遍历
            List<ModelApiBindingEntity> bindings = modelApiBindingMapper.listByModelId(model.getId());
            if (bindings != null) {
                for (ModelApiBindingEntity b : bindings) {
                    if (protocol != null && protocol.equals(b.getProtocol())) {
                        String ex = stream && b.getStreamUsageExtractor() != null
                                ? b.getStreamUsageExtractor() : b.getUsageExtractor();
                        if (ex != null && !ex.isBlank()) {
                            return ex;
                        }
                    }
                }
            }
        }
        if (model != null && "ANTHROPIC".equalsIgnoreCase(model.getProviderCode())) {
            return EXTRACTOR_ANTHROPIC;
        }
        ModelApiProtocolEnum p = protocol == null ? null : ModelApiProtocolEnum.fromCode(protocol);
        if (p != null) {
            return switch (p.canonical()) {
                case IMAGE_GENERATIONS, IMAGE_EDITS, IMAGE_VARIATIONS -> EXTRACTOR_IMAGE;
                case VIDEO_GENERATIONS -> EXTRACTOR_VIDEO;
                case AUDIO_TRANSCRIPTIONS, AUDIO_TRANSLATIONS, AUDIO_SPEECH -> EXTRACTOR_AUDIO;
                default -> EXTRACTOR_DEFAULT_TOKEN;
            };
        }
        return EXTRACTOR_DEFAULT_TOKEN;
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
