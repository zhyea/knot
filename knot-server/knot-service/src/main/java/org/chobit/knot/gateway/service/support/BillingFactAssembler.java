package org.chobit.knot.gateway.service.support;

import org.chobit.knot.gateway.dto.routing.BillingFact;
import org.chobit.knot.gateway.dto.routing.BillingFactAssemblyInput;
import org.chobit.knot.gateway.dto.routing.BillingFactAmount;
import org.chobit.knot.gateway.dto.routing.BillingFactRuleRef;
import org.chobit.knot.gateway.dto.routing.BillingFactSubject;
import org.chobit.knot.gateway.dto.routing.BillingFactUsage;
import org.chobit.knot.gateway.dto.routing.BillingRuleSource;
import org.chobit.knot.gateway.entity.BillingRuleEntity;
import org.chobit.knot.gateway.model.AudioBillingUsage;
import org.chobit.knot.gateway.model.BillingUsage;
import org.chobit.knot.gateway.model.ImageBillingUsage;
import org.chobit.knot.gateway.model.NormalizedUsage;
import org.chobit.knot.gateway.model.TokenBillingUsage;
import org.chobit.knot.gateway.model.VideoBillingUsage;
import org.chobit.knot.gateway.model.usage.UsageExtractItem;
import org.chobit.knot.gateway.usage.UsageNormalizationSupport;
import org.chobit.knot.gateway.usage.extractor.UsageExtractor;
import org.chobit.knot.gateway.usage.extractor.UsageExtractorCatalog;
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

        UsageExtractor<?> extractor = resolveExtractor(input.extractorCode());
        BillingUsage billing = extractUsage(extractor, input);
        Instant occurredAt = input.occurredAt() == null ? java.time.Instant.now() : input.occurredAt();

        NormalizedUsage shadow = (rule == null || billing == null || billing.isEmpty())
                ? null
                : UsageNormalizationSupport.normalize(billing, rule, input.requestBody(), occurredAt, extractor.calculator());

        BillingFactUsage usage = toUsage(billing);
        BillingFactRuleRef ruleRef = toRuleRef(rule, matchedBy);
        BillingFactAmount shadowAmount = BillingFactAmount.from(shadow);
        BillingFactAmount gatewayBilling = BillingFactAmount.from(input.gatewayBilling());
        BigDecimal upstreamCost = extractUpstreamCost(input.upstreamUsage());

        BillingFactSubject subject = new BillingFactSubject(
                input.modelCode(), input.protocol(), input.stream(), matchedBy);

        return new BillingFact(subject, usage, ruleRef, shadowAmount, gatewayBilling, upstreamCost);
    }

    private UsageExtractor<?> resolveExtractor(String extractorCode) {
        UsageExtractor<?> extractor = extractorCatalog.resolve(extractorCode);
        return extractor != null ? extractor : extractorCatalog.defaultExtractor();
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
            case VideoBillingUsage v -> v.durationSeconds();
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
