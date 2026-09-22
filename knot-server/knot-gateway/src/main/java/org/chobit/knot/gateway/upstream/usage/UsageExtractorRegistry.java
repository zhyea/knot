package org.chobit.knot.gateway.upstream.usage;

import org.apache.commons.lang3.StringUtils;
import org.chobit.knot.gateway.adapter.request.UpstreamRequestAdapter;
import org.chobit.knot.gateway.adapter.upstream.UpstreamRequestContext;
import org.chobit.knot.gateway.constants.enums.ModelApiProtocolEnum;
import org.chobit.knot.gateway.constants.enums.ProviderTypeEnum;
import org.chobit.knot.gateway.entity.BillingRuleEntity;
import org.chobit.knot.gateway.entity.ModelApiBindingEntity;
import org.chobit.knot.gateway.model.BillingUsage;
import org.chobit.knot.gateway.model.NormalizedUsage;
import org.chobit.knot.gateway.service.GatewayDataService;
import org.chobit.knot.gateway.usage.AnthropicUsageExtractor;
import org.chobit.knot.gateway.usage.ImageUsageExtractor;
import org.chobit.knot.gateway.usage.UsageExtractor;
import org.chobit.knot.gateway.usage.UsageExtractorCatalog;
import org.chobit.knot.gateway.usage.UsageNormalizationSupport;
import org.chobit.knot.gateway.usage.VideoUsageExtractor;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class UsageExtractorRegistry {

    private final UsageExtractorCatalog usageExtractorCatalog;
    private final GatewayDataService dataService;

    public UsageExtractorRegistry(UsageExtractorCatalog usageExtractorCatalog, GatewayDataService dataService) {
        this.usageExtractorCatalog = usageExtractorCatalog;
        this.dataService = dataService;
    }

    public NormalizedUsage extract(String responseBody, UpstreamRequestContext context, UpstreamRequestAdapter adapter) {
        return extract(responseBody, isEventStream(responseBody), context, adapter);
    }

    /**
     * 流式转发场景：对单个 SSE {@code data:} 事件提取用量。
     *
     * <p>与整包提取走同一套提取器与归一化逻辑，由调用方对多个事件的结果取最大值合并。</p>
     */
    public NormalizedUsage extractEvent(String eventData, UpstreamRequestContext context, UpstreamRequestAdapter adapter) {
        return extract(eventData, true, context, adapter);
    }

    private NormalizedUsage extract(String body,
                                    boolean eventStream,
                                    UpstreamRequestContext context,
                                    UpstreamRequestAdapter adapter) {
        BillingRuleEntity billingRule = resolveBillingRule(context);
        String code = resolveExtractorCode(eventStream, context);
        UsageExtractor extractor = resolve(code);
        if (extractor != null) {
            BillingUsage extracted = extractor.extractUsageBody(body);
            if (!extracted.isEmpty()) {
                return UsageNormalizationSupport.normalize(
                        extracted,
                        billingRule,
                        requestBody(context),
                        extractor.calculator()
                );
            }
        }
        return UsageNormalizationSupport.normalize(
                adapter.extractUsage(body, context),
                billingRule,
                requestBody(context),
                fallbackCalculator(context)
        );
    }

    private Map<String, Object> requestBody(UpstreamRequestContext context) {
        return context == null ? null : context.requestBody();
    }

    private UsageExtractor resolve(String code) {
        return usageExtractorCatalog.resolve(code);
    }

    private String resolveExtractorCode(boolean eventStream, UpstreamRequestContext context) {
        ModelApiBindingEntity binding = context.binding();
        if (binding != null) {
            String streamExtractor = binding.getStreamUsageExtractor();
            if (eventStream && StringUtils.isNotBlank(streamExtractor)) {
                return streamExtractor;
            }
            if (StringUtils.isNotBlank(binding.getUsageExtractor())) {
                return binding.getUsageExtractor();
            }
        }
        if (context.provider() != null
                && ProviderTypeEnum.ANTHROPIC.code().equals(StringUtils.upperCase(context.provider().getProviderCode()))) {
            return AnthropicUsageExtractor.CODE;
        }
        if (context.protocol() != null) {
            ModelApiProtocolEnum protocol = context.protocol().canonical();
            if (protocol == ModelApiProtocolEnum.IMAGE_GENERATIONS
                    || protocol == ModelApiProtocolEnum.IMAGE_EDITS
                    || protocol == ModelApiProtocolEnum.IMAGE_VARIATIONS) {
                return ImageUsageExtractor.CODE;
            }
            if (protocol == ModelApiProtocolEnum.VIDEO_GENERATIONS) {
                return VideoUsageExtractor.CODE;
            }
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

    private org.chobit.knot.gateway.usage.calculator.BillingModeCalculator fallbackCalculator(UpstreamRequestContext context) {
        if (context == null || context.protocol() == null) {
            return usageExtractorCatalog.defaultExtractor().calculator();
        }
        ModelApiProtocolEnum protocol = context.protocol().canonical();
        if (protocol == ModelApiProtocolEnum.IMAGE_GENERATIONS
                || protocol == ModelApiProtocolEnum.IMAGE_EDITS
                || protocol == ModelApiProtocolEnum.IMAGE_VARIATIONS) {
            return usageExtractorCatalog.resolve(ImageUsageExtractor.CODE).calculator();
        }
        if (protocol == ModelApiProtocolEnum.VIDEO_GENERATIONS) {
            return usageExtractorCatalog.resolve(VideoUsageExtractor.CODE).calculator();
        }
        return usageExtractorCatalog.defaultExtractor().calculator();
    }
}
