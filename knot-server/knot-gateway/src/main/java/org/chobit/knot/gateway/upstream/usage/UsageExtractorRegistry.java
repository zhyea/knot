package org.chobit.knot.gateway.upstream.usage;

import com.fasterxml.jackson.core.JsonProcessingException;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.chobit.knot.adapter.upstream.UpstreamRequestContext;
import org.chobit.knot.gateway.config.UsageExtractionProperties;
import org.chobit.knot.svc.vo.gateway.BillingRuleConfigVo;
import org.chobit.knot.adapter.model.usage.NormalizedUsage;
import org.chobit.knot.adapter.model.usage.UsageAccounting;
import org.chobit.knot.adapter.usage.UsageExtractItem;
import org.chobit.knot.gateway.service.GatewayConfigCacheService;
import org.chobit.knot.adapter.model.ref.BillingRuleRef;
import org.chobit.knot.adapter.usage.UsageExtractorResolver;
import org.chobit.knot.adapter.usage.UsageNormalizationSupport;
import org.chobit.knot.adapter.usage.UsageRawReader;
import org.chobit.knot.adapter.usage.calculator.BillingModeCalculator;
import org.chobit.knot.adapter.usage.extractor.UsageExtractor;
import org.chobit.knot.adapter.usage.extractor.UsageExtractorCatalog;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Map;

/**
 * 上游响应用量提取入口：提取计费输入、计算计费结果，并保留上游原文。
 * 提取分三份：{@code rawUsage}/{@code rawBody} 原文透传、{@code billing} 内部计费输入、{@code normalized} 计费结果，三者打包成 {@link UsageAccounting} 交给 {@code ProxyResult}。
 * 用量只能由配置的 {@link UsageExtractor} 产出，不存在适配器侧的第二套解析；上游未上报 usage（或全零）属合法零用量，WARN 后出零账；非法 JSON / 提取器返回 {@code null} / 有非零用量却提取为空三处失败收敛到同一处置点，默认抛异常中断响应，{@code knot.gateway.usage.degrade-on-failure=true} 时改为 ERROR 后放行出零账并保留原文供事后重算。
 * 提取器不存在（未知 code）属配置错误、不走上述处置，恒 fail-fast。
 */
@Slf4j
@Component
public class UsageExtractorRegistry {

    private final UsageExtractorCatalog usageExtractorCatalog;
    private final GatewayConfigCacheService dataService;
    private final UsageExtractionProperties properties;

    public UsageExtractorRegistry(UsageExtractorCatalog usageExtractorCatalog,
                                  GatewayConfigCacheService dataService,
                                  UsageExtractionProperties properties) {
        this.usageExtractorCatalog = usageExtractorCatalog;
        this.dataService = dataService;
        this.properties = properties;
    }

    public UsageAccounting extract(String responseBody, UpstreamRequestContext context) {
        return extract(responseBody, null, isEventStream(responseBody), context);
    }

    /**
     * 整包提取：算账用适配后 body，留证据用 wire 原文，两者不许合成一份。
     * {@code responseBody} 只用来算账：提取器口径是统一协议（{@code prompt_tokens} / {@code completion_tokens}）。
     * {@code wireResponseBody} 只用来留证据：跨协议绑定时原文键名是供应商口径（{@code input_tokens} / {@code output_tokens}），{@code rawUsage} 记的是原文。
     *
     * @param wireResponseBody 供应商原文；{@code null} 表示手头只有一份，回落到 {@code responseBody}
     */
    public UsageAccounting extract(String responseBody, String wireResponseBody,
                                   UpstreamRequestContext context) {
        return extract(responseBody, wireResponseBody, isEventStream(responseBody), context);
    }

    /**
     * 流式转发场景：对单个 SSE {@code data:} 事件提取用量，与整包提取走同一套提取器与归一化逻辑。
     * 用量后到覆盖前一个（上游 usage 通常在最后一个事件）。
     *
     * @param wireEventData 该事件未适配前的 {@code data} 载荷，仅作证据；{@code null} 时回落 {@code eventData}
     */
    public UsageAccounting extractEvent(String eventData, String wireEventData,
                                       UpstreamRequestContext context) {
        return extract(eventData, wireEventData, true, context);
    }

    /**
     * 解析本次提取要用的提取器：配置错误在此暴露，不回落到任何兜底实现。
     * 流式转发前调用它做预检（只解析不提取），让「配了未知 extractor code」在响应头写出之前就失败。
     * {@link #resolveExtractorCode} 末行恒回落到默认提取器，因此只在显式配了错误 code 时抛异常；本方法恒 fail-fast，不受 {@code knot.gateway.usage.degrade-on-failure} 影响。
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
                                    String wireBody,
                                    boolean eventStream,
                                    UpstreamRequestContext context) {
        // 适配层只见自有投影（改进清单 2.5）：实体到投影的转换发生在网关侧这一边界上
        BillingRuleRef billingRule = ruleRef(resolveBillingRule(context));
        // 提取器不存在属配置错误，不受降级开关影响：没有提取器就无从产出用量，放行只会静默记零账
        UsageExtractor<?> extractor = requireExtractor(eventStream, context);
        Map<String, Object> requestBody = requestBody(context);

        // 能不能出账只看 body：那是真正要解析出账的一份，原文畸形不该让账跑不动
        ExtractFailure failure = null;
        Throwable cause = null;
        try {
            UsageRawReader.readBody(body);
        } catch (JsonProcessingException e) {
            failure = ExtractFailure.INVALID_JSON;
            cause = e;
        }
        // 证据另取一份：原文解析失败只丢证据、不阻断计费
        Map<String, Object> rawBody = readWireBody(wireBody == null ? body : wireBody, context);
        Map<String, Object> rawUsage = rawBody == null ? null : UsageRawReader.readUsage(rawBody);
        // 已判定非法 JSON 就不再调提取器：在垃圾输入上二次解析只会换一种报错方式
        // 请求体一并给提取器：像素 / 是否有声音 / 是否有参考等计费属性只在请求侧
        UsageExtractItem<?> item = failure == null ? extractor.extractUsageBody(body, requestBody) : null;
        if (failure == null) {
            failure = judgeExtractResult(item, rawUsage, extractor);
        }
        if (failure != null) {
            return applyFailurePolicy(failure, extractor, context, rawBody, rawUsage, cause);
        }
        if (item.billing().isEmpty()) {
            // 上游没有上报用量（或全零）：合法零用量，告警但不失败
            log.warn("upstream reported no usage: extractor={} protocol={} allowsEmpty={}",
                    extractor.code(), context.protocol(), extractor.allowsEmptyUsage());
        }
        // 发生时点：一次提取一个值，整包/逐事件各归各自（首期网关进程 UTC）
        Instant occurredAt = Instant.now();
        return normalize(item, rawBody, rawUsage, billingRule, requestBody, occurredAt, extractor.calculator());
    }

    /**
     * 证据侧的原文对象：解析失败返回 {@code null}（只丢证据、不阻断计费），并留一条 WARN。
     * 与 {@link ExtractFailure#INVALID_JSON} 判定不共用一次 try —— 那份决定这单能不能出账，这份只决定事后有没有原文可查。
     */
    private static Map<String, Object> readWireBody(String wireBody, UpstreamRequestContext context) {
        if (StringUtils.isBlank(wireBody)) {
            return null;
        }
        try {
            return UsageRawReader.readBody(wireBody);
        } catch (JsonProcessingException e) {
            log.warn("wire response is not valid json, raw usage evidence dropped: protocol={}",
                    context == null ? null : context.protocol(), e);
            return null;
        }
    }

    /**
     * 判定提取结果是否属于「提取不出用量」：能否为空由提取器声明，不在这里跨协议猜；未声明允许的，上游有正数却提取为空即口径错配。
     *
     * @return 失败原因；{@code null} 表示提取成功（含合法零用量）
     */
    private ExtractFailure judgeExtractResult(UsageExtractItem<?> item,
                                             Map<String, Object> rawUsage,
                                             UsageExtractor<?> extractor) {
        if (item == null || item.billing() == null) {
            return ExtractFailure.NO_RESULT;
        }
        if (item.billing().isEmpty() && !extractor.allowsEmptyUsage() && hasPositiveNumber(rawUsage)) {
            return ExtractFailure.MISMATCH;
        }
        return null;
    }

    /**
     * 提取失败的唯一处置点：按 {@code knot.gateway.usage.degrade-on-failure} 决定抛出还是降级。
     * 三处失败全部收敛到这里，新增失败原因只加 {@link ExtractFailure} 枚举项，处置逻辑不再分叉。
     */
    private UsageAccounting applyFailurePolicy(ExtractFailure failure,
                                              UsageExtractor<?> extractor,
                                              UpstreamRequestContext context,
                                              Map<String, Object> rawBody,
                                              Map<String, Object> rawUsage,
                                              Throwable cause) {
        // 降级也是故障：只在「抛 vs 放」上有区别，告警级别与可定位信息必须一致
        boolean degrade = properties.isDegradeOnFailure();
        log.error("usage extract failed: reason={} extractor={} protocol={} rawUsageKeys={} degrade={}",
                failure.code,
                extractor.code(),
                context == null ? null : context.protocol(),
                rawUsage == null ? null : rawUsage.keySet(),
                degrade,
                cause);
        if (!degrade) {
            throw new IllegalStateException(failure.detail + ": " + extractor.code(), cause);
        }
        // 降级：保留上游原文证据供事后重算，计费输入置空即零账
        return UsageAccounting.degraded(rawUsage, rawBody);
    }

    /**
     * 原始 usage 里是否存在正数量：区分「上游明确报了零」与「提取器读不出来」，嵌套 map 逐层检查。
     * 上游可能合法上报全零 usage（如 SSE 中间事件）；本方法只在提取器未声明 {@link UsageExtractor#allowsEmptyUsage()} 时充当门控。
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

    /**
     * 一次提取失败的原因。
     * {@code detail} 是报错原文（异常消息），{@code code} 是给日志用的稳定标识。
     */
    private enum ExtractFailure {

        /** 上游响应不是合法 JSON（根因由日志的 throwable 给出） */
        INVALID_JSON("invalid-json", "upstream response is not valid json"),

        /** 提取器没产出结果：返回 {@code null}，或 {@code billing} 为 {@code null} */
        NO_RESULT("no-result", "UsageExtractor returned no result"),

        /** 上游有非零用量却提取为空：提取器口径配错 */
        MISMATCH("extractor-mismatch", "UsageExtractor cannot read upstream usage");

        private final String code;

        private final String detail;

        ExtractFailure(String code, String detail) {
            this.code = code;
            this.detail = detail;
        }
    }

    private UsageAccounting normalize(UsageExtractItem<?> item,
                                      Map<String, Object> rawBody,
                                      Map<String, Object> rawUsage,
                                      BillingRuleRef billingRule,
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
        // 绑定配置驱动（改进清单 3.4）：选择口径收敛到共享 UsageExtractorResolver，
        // 与管理端裁判同源——「注释对齐」换成「实现对齐」，一侧改另一侧立刻可见
        String configured = UsageExtractorResolver.fromBinding(context.binding(), eventStream);
        if (configured != null) {
            return configured;
        }
        // 未配置（含未绑定）回落默认提取器。提取器选择一律由绑定配置驱动——
        // ANTHROPIC provider 特例与 protocol→IMAGE/VIDEO/AUDIO 家族映射已于 2026-10-24 移除
        // （改进清单 2.2b）：那是「配了 DEFAULT-TOKEN 实际走 IMAGE」的同类隐患，
        // 图像等家族的提取器改由 kb_model_api_bindings.usage_extractor 显式配置。
        return usageExtractorCatalog.defaultExtractor().code();
    }

    private boolean isEventStream(String value) {
        return value != null && value.lines().anyMatch(line -> StringUtils.startsWith(StringUtils.trim(line), "data:"));
    }

    /**
     * 计费规则解析：模型显式绑定优先，未绑定时按模型族回退（族精确匹配 → 默认规则）；{@code kb_models.billing_rule_code} 允许为空。
     */
    private BillingRuleConfigVo resolveBillingRule(UpstreamRequestContext context) {
        if (context == null || context.model() == null) {
            return null;
        }
        return dataService.getActiveBillingRule(
            context.model().billingRuleCode(),
            context.model().modelFamilyCode()
        );
    }

    /**
     * 计费规则快照 → 适配层投影：只带计价消费的字段。
     * 快照由 {@link GatewayConfigCacheService} 产出，投影发生在网关侧边界上。
     */
    private static BillingRuleRef ruleRef(BillingRuleConfigVo rule) {
        return rule == null ? null : new BillingRuleRef(
                rule.billingMode(), rule.configJson(), rule.currency(),
                rule.pricingPlan(), rule.unit(), rule.versionCode(), rule.code());
    }
}
