package org.chobit.knot.gateway.upstream.usage;

import org.apache.commons.lang3.StringUtils;
import org.chobit.knot.gateway.constants.enums.ProviderTypeEnum;
import org.chobit.knot.gateway.entity.ModelApiBindingEntity;
import org.chobit.knot.gateway.model.BillingUsage;
import org.chobit.knot.gateway.upstream.UpstreamRequestContext;
import org.chobit.knot.gateway.upstream.provider.UpstreamProviderAdapter;
import org.chobit.knot.gateway.usage.AnthropicUsageExtractor;
import org.chobit.knot.gateway.usage.UsageExtractor;
import org.chobit.knot.gateway.usage.UsageExtractorCatalog;
import org.springframework.stereotype.Component;

@Component
public class UsageExtractorRegistry {

    private final UsageExtractorCatalog usageExtractorCatalog;

    public UsageExtractorRegistry(UsageExtractorCatalog usageExtractorCatalog) {
        this.usageExtractorCatalog = usageExtractorCatalog;
    }

    public BillingUsage extract(String responseBody, UpstreamRequestContext context, UpstreamProviderAdapter adapter) {
        String code = resolveExtractorCode(responseBody, context);
        UsageExtractor extractor = resolve(code);
        if (extractor != null) {
            return extractor.extract(responseBody);
        }
        return adapter.extractUsage(responseBody, context);
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
}
