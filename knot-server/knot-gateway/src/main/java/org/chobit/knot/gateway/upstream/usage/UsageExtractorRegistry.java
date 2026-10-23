package org.chobit.knot.gateway.upstream.usage;

import com.fasterxml.jackson.core.JsonProcessingException;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.chobit.knot.gateway.adapter.upstream.UpstreamRequestContext;
import org.chobit.knot.gateway.constants.enums.ModelApiProtocolEnum;
import org.chobit.knot.gateway.entity.BillingRuleEntity;
import org.chobit.knot.gateway.entity.ModelApiBindingEntity;
import org.chobit.knot.gateway.model.NormalizedUsage;
import org.chobit.knot.gateway.model.UsageAccounting;
import org.chobit.knot.gateway.model.usage.UsageExtractItem;
import org.chobit.knot.gateway.service.GatewayDataService;
import org.chobit.knot.gateway.usage.UsageNormalizationSupport;
import org.chobit.knot.gateway.usage.UsageRawReader;
import org.chobit.knot.gateway.usage.calculator.BillingModeCalculator;
import org.chobit.knot.gateway.usage.extractor.AnthropicUsageExtractor;
import org.chobit.knot.gateway.usage.extractor.AudioUsageExtractor;
import org.chobit.knot.gateway.usage.extractor.ImageUsageExtractor;
import org.chobit.knot.gateway.usage.extractor.UsageExtractor;
import org.chobit.knot.gateway.usage.extractor.UsageExtractorCatalog;
import org.chobit.knot.gateway.usage.extractor.VideoUsageExtractor;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Map;

/**
 * 上游响应用量提取入口：提取计费输入、计算计费结果，并保留上游原文。
 *
 * <p>提取分三份：{@code rawUsage}/{@code rawBody} 原文透传、
 * {@code billing} 内部计费输入、{@code normalized} 计费结果，
 * 三者打包成 {@link UsageAccounting} 交给 {@code ProxyResult}。</p>
 *
 * <p><b>计费不兜底（2026-10-23）</b>：用量只能由配置的 {@link UsageExtractor} 产出，
 * 不存在适配器侧的第二套解析。空用量按三分支处置（详见 {@code docs/计费用量解析去兜底实现方案}）：</p>
 * <ul>
 *   <li>提取器返回 {@code null} / 响应非法 JSON → 契约错误，抛异常；</li>
 *   <li>上游未上报 usage（或全零）→ 合法零用量，WARN 后正常出零账；</li>
 *   <li>上游回了非零 usage 却提取不出来 → 口径错配，ERROR 后抛异常。</li>
 * </ul>
 */
@Slf4j
@Component
public class UsageExtractorRegistry {

    private final UsageExtractorCatalog usageExtractorCatalog;
    private final GatewayDataService dataService;

    public UsageExtractorRegistry(UsageExtractorCatalog usageExtractorCatalog, GatewayDataService dataService) {
        this.usageExtractorCatalog = usageExtractorCatalog;
        this.dataService = dataService;
    }

    public UsageAccounting extract(String responseBody, UpstreamRequestContext context) {
        return extract(responseBody, isEventStream(responseBody), context);
    }

    /**
     * 流式转发场景：对单个 SSE {@code data:} 事件提取用量。
     *
     * <p>与整包提取走同一套提取器与归一化逻辑，由调用方对多个事件的结果取最大值合并。</p>
     */
    public UsageAccounting extractEvent(String eventData, UpstreamRequestContext context) {
        return extract(eventData, true, context);
    }

    /**
     * 解析本次提取要用的提取器：配置错误在此暴露，不回落到任何兜底实现。
     *
     * <p>流式转发前调用它做预检——预检只解析不提取（此刻还没有响应体），
     * 让「配了未知 extractor code」在响应头写出之前就失败。</p>
     *
     * <p>注意覆盖面：{@link #resolveExtractorCode} 末行恒回落到默认提取器，
     * 因此本方法<b>只在显式配了错误 code 时</b>抛异常；未配置走默认提取器，属合法路径。</p>
     */
    public UsageExtractor<?> requireExtractor(boolean eventStream, UpstreamRequestContext context) {
        String code = resolveExtractorCode(eventStream, context);
        UsageExtractor<?> extractor = resolve(code);
        if (extractor == null) {
            throw new IllegalStateException("UsageExtractor not found: " + code);
        }
        return extractor;
    }

    private UsageAccounting extract(String body,
                                    boolean eventStream,
                                    UpstreamRequestContext context) {
        Map<String, Object> rawBody = readRawBody(body);
        Map<String, Object> rawUsage = UsageRawReader.readUsage(rawBody);
        BillingRuleEntity billingRule = resolveBillingRule(context);
        UsageExtractor<?> extractor = requireExtractor(eventStream, context);
        Map<String, Object> requestBody = requestBody(context);
        // 发生时点：一次提取一个值，整包/逐事件各归各自（首期网关进程 UTC）
        Instant occurredAt = Instant.now();
        // 请求体一并给提取器：像素 / 是否有声音 / 是否有参考等计费属性只在请求侧
        UsageExtractItem<?> item = extractor.extractUsageBody(body, requestBody);
        if (item == null || item.billing() == null) {
            throw new IllegalStateException("UsageExtractor returned no result: " + extractor.code());
        }
        if (item.billing().isEmpty() && hasPositiveNumber(rawUsage)) {
            // 上游回了非零用量却提取不出来：提取器口径配错，静默记零账会把问题推迟到对账
            log.error("usage extractor mismatch: extractor={} rawUsageKeys={}",
                    extractor.code(), rawUsage == null ? null : rawUsage.keySet());
            throw new IllegalStateException("UsageExtractor cannot read upstream usage: " + extractor.code());
        }
        if (item.billing().isEmpty()) {
            // 上游没有上报用量（或全零）：合法零用量，告警但不失败
            log.warn("upstream reported no usage: extractor={} protocol={}",
                    extractor.code(), context.protocol());
        }
        return normalize(item, rawBody, rawUsage, billingRule, requestBody, occurredAt, extractor.calculator());
    }

    /**
     * 读原文载体：非法 JSON 直接失败。
     *
     * <p>静默降级成 {@code null} 会让「解析失败」与「上游没回 usage」无法区分——
     * 后者是合法零用量，前者必须暴露。</p>
     */
    private Map<String, Object> readRawBody(String body) {
        try {
            return UsageRawReader.readBody(body);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("upstream response is not valid json", e);
        }
    }

    /**
     * 原始 usage 里是否存在正数量：区分「上游明确报了零」与「提取器读不出来」。
     *
     * <p>上游可能合法上报全零 usage（如 SSE 中间事件），此时不应判为口径错配；
     * 只有确实存在非零数值却被提取成空，才说明提取器配错。</p>
     */
    private static boolean hasPositiveNumber(Map<String, Object> usage) {
        if (usage == null || usage.isEmpty()) {
            return false;
        }
        for (Object value : usage.values()) {
            if (value instanceof Number number && number.doubleValue() > 0) {
                return true;
            }
            if (value instanceof Map<?, ?> nested) {
                @SuppressWarnings("unchecked")
                Map<String, Object> typed = (Map<String, Object>) nested;
                if (hasPositiveNumber(typed)) {
                    return true;
                }
            }
        }
        return false;
    }

    private UsageAccounting normalize(UsageExtractItem<?> item,
                                      Map<String, Object> rawBody,
                                      Map<String, Object> rawUsage,
                                      BillingRuleEntity billingRule,
                                      Map<String, Object> requestBody,
                                      Instant occurredAt,
                                      BillingModeCalculator calculator) {
        NormalizedUsage normalized =
                UsageNormalizationSupport.normalize(item.billing(), billingRule, requestBody, occurredAt, calculator);
        return UsageAccounting.of(rawUsage, rawBody, item.billing(), item.usage(), normalized);
    }

    private Map<String, Object> requestBody(UpstreamRequestContext context) {
        return context == null ? null : context.requestBody();
    }

    private UsageExtractor<?> resolve(String code) {
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
        // 已知特例：Anthropic 的缓存口径只在专用提取器里处理，未绑定时按 provider 兜到它。
        // 这与「配置驱动」原则相悖，彻底移除需改为绑定配置驱动，见方案 §12 第 4 项。
        if (context.provider() != null
                && "ANTHROPIC".equals(StringUtils.upperCase(context.provider().getProviderCode()))) {
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
            if (protocol == ModelApiProtocolEnum.AUDIO_TRANSCRIPTIONS
                    || protocol == ModelApiProtocolEnum.AUDIO_TRANSLATIONS
                    || protocol == ModelApiProtocolEnum.AUDIO_SPEECH) {
                return AudioUsageExtractor.CODE;
            }
        }
        return usageExtractorCatalog.defaultExtractor().code();
    }

    private boolean isEventStream(String value) {
        return value != null && value.lines().anyMatch(line -> StringUtils.startsWith(StringUtils.trim(line), "data:"));
    }

    /**
     * 计费规则解析：模型显式绑定优先，未绑定时按模型族回退（族精确匹配 → 默认规则）。
     *
     * <p>回退链必须存在——{@code kb_models.billing_rule_code} 允许为空，
     * 只认显式绑定会让未绑模型的计费整体消失（明细与总价全无）。</p>
     */
    private BillingRuleEntity resolveBillingRule(UpstreamRequestContext context) {
        if (context == null || context.model() == null) {
            return null;
        }
        return dataService.getActiveBillingRule(
                context.model().getBillingRuleCode(),
                context.model().getModelFamilyCode()
        );
    }
}
