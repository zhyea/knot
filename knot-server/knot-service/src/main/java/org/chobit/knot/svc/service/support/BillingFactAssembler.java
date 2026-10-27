package org.chobit.knot.svc.service.support;

import org.chobit.knot.svc.dto.routing.BillingFact;
import org.chobit.knot.svc.dto.routing.BillingFactAssemblyInput;
import org.chobit.knot.svc.dto.routing.BillingFactAmount;
import org.chobit.knot.svc.dto.routing.BillingFactRuleRef;
import org.chobit.knot.svc.dto.routing.BillingFactSubject;
import org.chobit.knot.svc.dto.routing.BillingFactUsage;
import org.chobit.knot.svc.dto.routing.BillingRuleSource;
import org.chobit.knot.dal.entity.BillingRuleEntity;
import org.chobit.knot.adapter.model.usage.AudioBillingUsage;
import org.chobit.knot.adapter.model.usage.BillingUsage;
import org.chobit.knot.adapter.model.usage.ImageBillingUsage;
import org.chobit.knot.adapter.model.usage.NormalizedUsage;
import org.chobit.knot.adapter.model.usage.TokenBillingUsage;
import org.chobit.knot.adapter.model.usage.VideoBillingUsage;
import org.chobit.knot.adapter.usage.UsageExtractItem;
import org.chobit.knot.adapter.model.ref.BillingRuleRef;
import org.chobit.knot.adapter.usage.UsageNormalizationSupport;
import org.chobit.knot.adapter.usage.extractor.UsageExtractor;
import org.chobit.knot.adapter.usage.extractor.UsageExtractorCatalog;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * 计费影子链组装器（管理端离线）：复用生产链路口径产出 {@link BillingFact}。
 *
 * <p>与生产链路 {@code UsageExtractorRegistry.extract} 同构：提取器取量 → 三级回退取规则 → 归一化计价。
 * 关键差异：本组装器是「离线复算」，不污染网关响应热路径，且把命中层级、用量视图、被告账一并结构化，
 * 供 {@link BillingFactChecks} 做同口径对照。</p>
 *
 * <p>设计纪律（v7 §5）：影子链与生产读同一份 {@code kb_billing_rules}，配置级错误两边同错，
 * 故 {@code GATEWAY_RECOMPUTE_MATCH} 只抓「当次调用算错」（规则取错 / 单价版本错），
 * 配置错配交给 JEV 语义层——这是双盲判定的分工。</p>
 */
@Component
public class BillingFactAssembler {

    private final UsageExtractorCatalog extractorCatalog;
    private final BillingRuleFallbackResolver ruleResolver;

    public BillingFactAssembler(UsageExtractorCatalog extractorCatalog, BillingRuleFallbackResolver ruleResolver) {
        this.extractorCatalog = extractorCatalog;
        this.ruleResolver = ruleResolver;
    }

    public BillingFact assemble(BillingFactAssemblyInput input) {
        BillingRuleResolution resolution = ruleResolver.resolve(input.ruleCode(), input.modelFamilyCode());
        BillingRuleEntity rule = resolution.rule();
        BillingRuleSource matchedBy = resolution.matchedBy();

        ResolvedExtractor resolved = resolveExtractor(input.extractorCode());
        BillingUsage billing = extractUsage(resolved.extractor(), input);
        // 影子链不抛异常，但把「回落」与「空用量」原样带出，供裁判层/UI 判断是否告警
        boolean usageAbsent = billing == null || billing.isEmpty();
        Instant occurredAt = input.occurredAt() == null ? java.time.Instant.now() : input.occurredAt();

        NormalizedUsage shadow = (rule == null || usageAbsent)
                ? null
                : UsageNormalizationSupport.normalize(billing, toBillingRuleRef(rule), input.requestBody(), occurredAt,
                        resolved.extractor().calculator());

        BillingFactUsage usage = toUsage(billing);
        BillingFactRuleRef ruleRef = toRuleRef(rule, matchedBy);
        BillingFactAmount shadowAmount = BillingFactAmount.from(shadow);
        BillingFactAmount gatewayBilling = BillingFactAmount.from(input.gatewayBilling());
        BigDecimal upstreamCost = extractUpstreamCost(input.upstreamUsage());

        BillingFactSubject subject = new BillingFactSubject(
                input.modelCode(), input.protocol(), input.stream(), matchedBy);

        return new BillingFact(subject, usage, ruleRef, shadowAmount, gatewayBilling, upstreamCost,
                resolved.fallback(), usageAbsent);
    }

    /** 提取器解析结果：携带「是否回落」标记，让配置错误在影子链里可见而不炸链路。 */
    private record ResolvedExtractor(UsageExtractor<?> extractor, boolean fallback) {
    }

    private ResolvedExtractor resolveExtractor(String extractorCode) {
        UsageExtractor<?> extractor = extractorCatalog.resolve(extractorCode);
        return extractor == null
                ? new ResolvedExtractor(extractorCatalog.defaultExtractor(), true)
                : new ResolvedExtractor(extractor, false);
    }

    private BillingUsage extractUsage(UsageExtractor<?> extractor, BillingFactAssemblyInput input) {
        Map<String, Object> body = input.upstreamRawBody() == null ? Map.of() : input.upstreamRawBody();
        Map<String, Object> requestBody = input.requestBody() == null ? Map.of() : input.requestBody();
        // 影子链只消费计费半边：提取器返回双视图，这里取 billing
        UsageExtractItem<?> item = extractor.extractUsage(body, requestBody);
        BillingUsage billing = item == null ? null : item.billing();
        return billing == null ? TokenBillingUsage.empty() : billing;
    }

    private BillingFactUsage toUsage(BillingUsage billing) {
        if (billing instanceof TokenBillingUsage t) {
            return new BillingFactUsage(true, t.inputTokens(), t.outputTokens(), t.totalTokens(),
                    t.cacheReadTokens(), t.cacheWriteTokens(), 0L);
        }
        TokenBillingUsage embedded = billing.tokenUsage();
        long generic = switch (billing) {
            case ImageBillingUsage i -> i.count();
            case VideoBillingUsage v -> v.outputSeconds();
            case AudioBillingUsage a -> a.durationSeconds();
            default -> 0L;
        };
        return new BillingFactUsage(false, embedded.inputTokens(), embedded.outputTokens(), embedded.totalTokens(),
                embedded.cacheReadTokens(), embedded.cacheWriteTokens(), generic);
    }

    private BillingFactRuleRef toRuleRef(BillingRuleEntity rule, BillingRuleSource matchedBy) {
        if (rule == null) {
            return BillingFactRuleRef.absent();
        }
        return new BillingFactRuleRef(rule.getCode(), rule.getBillingMode(), rule.getUnit(), rule.getCurrency(),
                rule.getPricingPlan(), matchedBy, rule.getVersionCode());
    }

    /**
     * 计费规则实体 → 适配层投影（改进清单 2.5）：与 {@link #toRuleRef} 是两份不同投影——
     * 本份供计价计算器解析价格（必须带 configJson），那份供被告链结构化展示。
     */
    private static BillingRuleRef toBillingRuleRef(BillingRuleEntity rule) {
        return new BillingRuleRef(rule.getBillingMode(), rule.getConfigJson(), rule.getCurrency(),
                rule.getPricingPlan(), rule.getUnit(), rule.getVersionCode());
    }

    /**
     * 从上游 usage 原文抽取 cost（部分供应商回传，缺失 → null 触发 SKIP）。
     * 取键顺序：cost / total_cost。
     */
    private static BigDecimal extractUpstreamCost(Map<String, Object> upstreamUsage) {
        if (upstreamUsage == null) {
            return null;
        }
        for (String key : List.of("cost", "total_cost")) {
            Object value = upstreamUsage.get(key);
            if (value != null) {
                try {
                    return new BigDecimal(value.toString().trim());
                } catch (NumberFormatException ignored) {
                    // 继续尝试下一个键
                }
            }
        }
        return null;
    }
}
