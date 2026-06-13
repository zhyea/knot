package org.chobit.knot.gateway.upstream.usage;

import org.apache.commons.lang3.StringUtils;
import org.chobit.knot.gateway.adapter.request.UpstreamRequestAdapter;
import org.chobit.knot.gateway.adapter.upstream.UpstreamRequestContext;
import org.chobit.knot.gateway.constants.enums.ProviderTypeEnum;
import org.chobit.knot.gateway.entity.BillingRuleEntity;
import org.chobit.knot.gateway.entity.ModelApiBindingEntity;
import org.chobit.knot.gateway.model.NormalizedUsage;
import org.chobit.knot.gateway.service.GatewayDataService;
import org.chobit.knot.gateway.usage.AnthropicUsageExtractor;
import org.chobit.knot.gateway.usage.UsageExtractor;
import org.chobit.knot.gateway.usage.UsageExtractorCatalog;
import org.chobit.knot.gateway.usage.UsageNormalizationSupport;
import org.springframework.stereotype.Component;

@Component
public class UsageExtractorRegistry {

    private final UsageExtractorCatalog usageExtractorCatalog;
    private final GatewayDataService dataService;

    public UsageExtractorRegistry(UsageExtractorCatalog usageExtractorCatalog, GatewayDataService dataService) {
        this.usageExtractorCatalog = usageExtractorCatalog;
        this.dataService = dataService;
    }

    public NormalizedUsage extract(String responseBody, UpstreamRequestContext context, UpstreamRequestAdapter adapter) {
        BillingRuleEntity billingRule = resolveBillingRule(context);
        String code = resolveExtractorCode(responseBody, context);
        UsageExtractor extractor = resolve(code);
        if (extractor != null) {
            return extractor.extract(responseBody, billingRule);
        }
        return UsageNormalizationSupport.normalize(adapter.extractUsage(responseBody, context), billingRule);
    }

    private UsageExtractor resolve(String code) {
        return usageExtractorCatalog.resolve(code);
    }

    private String resolveExtractorCode(String responseBody, UpstreamRequestContext context) {
        ModelApiBindingEntity binding = context.binding();
        if (binding != null) {
            String streamExtractor = binding.getStreamUsageExtractor();
            if (isEventStream(responseBody) && StringUtils.isNotBlank(streamExtractor)) {
                return streamExtractor;
            }
            if (StringUtils.isNotBlank(binding.getUsageExtractor())) {
                return binding.getUsageExtractor();
            }
        }
        if (context.provider() != null
                && ProviderTypeEnum.ANTHROPIC.code().equals(StringUtils.upperCase(context.provider().getProviderType()))) {
            return AnthropicUsageExtractor.CODE;
        }
        return usageExtractorCatalog.defaultExtractor().code();
    }

    private boolean isEventStream(String value) {
        return value != null && value.lines().anyMatch(line -> StringUtils.startsWith(StringUtils.trim(line), "data:"));
    }

    private BillingRuleEntity resolveBillingRule(UpstreamRequestContext context) {
        if (context == null || context.model() == null || context.model().getBillingRuleId() == null) {
            return null;
        }
        return dataService.getActiveBillingRuleById(context.model().getBillingRuleId());
    }
}
