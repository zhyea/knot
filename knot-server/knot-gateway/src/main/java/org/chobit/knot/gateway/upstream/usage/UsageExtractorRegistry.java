package org.chobit.knot.gateway.upstream.usage;

import com.fasterxml.jackson.core.JsonProcessingException;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.chobit.knot.gateway.adapter.upstream.UpstreamRequestContext;
import org.chobit.knot.gateway.config.UsageExtractionProperties;
import org.chobit.knot.gateway.entity.BillingRuleEntity;
import org.chobit.knot.gateway.adapter.model.usage.NormalizedUsage;
import org.chobit.knot.gateway.adapter.model.usage.UsageAccounting;
import org.chobit.knot.gateway.adapter.usage.UsageExtractItem;
import org.chobit.knot.gateway.service.GatewayEntityCacheService;
import org.chobit.knot.gateway.adapter.model.ref.BillingRuleRef;
import org.chobit.knot.gateway.adapter.usage.UsageExtractorResolver;
import org.chobit.knot.gateway.adapter.usage.UsageNormalizationSupport;
import org.chobit.knot.gateway.adapter.usage.UsageRawReader;
import org.chobit.knot.gateway.adapter.usage.calculator.BillingModeCalculator;
import org.chobit.knot.gateway.adapter.usage.extractor.UsageExtractor;
import org.chobit.knot.gateway.adapter.usage.extractor.UsageExtractorCatalog;
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
 * 不存在适配器侧的第二套解析。提取结果按三分支处置（详见 {@code docs/计费用量解析去兜底实现方案}）：</p>
 * <ul>
 *   <li>上游未上报 usage（或全零）→ 合法零用量，WARN 后正常出零账；</li>
 *   <li>响应非法 JSON / 提取器返回 {@code null} / 上游有非零用量却提取为空（口径错配）
 *       → 三处失败收敛到同一个处置点，<b>默认抛异常中断响应</b>；</li>
 *   <li>{@code knot.gateway.usage.degrade-on-failure=true} 时改为 ERROR 后放行出零账，
 *       并保留上游原文供事后重算（见 {@link UsageAccounting#degraded}）。</li>
 * </ul>
 *
 * <p>⚠ 提取器不存在（未知 code）不走上面的处置：那是配置错误而不是提取失败，
 * 降级它等于让该绑定下的所有请求永久零账且无自愈路径，故恒 fail-fast。</p>
 */
@Slf4j
@Component
public class UsageExtractorRegistry {

    private final UsageExtractorCatalog usageExtractorCatalog;
    private final GatewayEntityCacheService dataService;
    private final UsageExtractionProperties properties;

    public UsageExtractorRegistry(UsageExtractorCatalog usageExtractorCatalog,
                                  GatewayEntityCacheService dataService,
                                  UsageExtractionProperties properties) {
        this.usageExtractorCatalog = usageExtractorCatalog;
        this.dataService = dataService;
        this.properties = properties;
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
     *
     * <p>本方法<b>恒 fail-fast，不受 {@code knot.gateway.usage.degrade-on-failure} 影响</b>：
     * 未知 code 是配置错误而非提取失败，降级它只会让该绑定下的请求静默零账。</p>
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
        // 适配层只见自有投影（改进清单 2.5）：实体到投影的转换发生在网关侧这一边界上
        BillingRuleRef billingRule = ruleRef(resolveBillingRule(context));
        // 提取器不存在属配置错误，不受降级开关影响：没有提取器就无从产出用量，放行只会静默记零账
        UsageExtractor<?> extractor = requireExtractor(eventStream, context);
        Map<String, Object> requestBody = requestBody(context);

        Map<String, Object> rawBody = null;
        ExtractFailure failure = null;
        Throwable cause = null;
        try {
            rawBody = UsageRawReader.readBody(body);
        } catch (JsonProcessingException e) {
            failure = ExtractFailure.INVALID_JSON;
            cause = e;
        }
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
     * 判定提取结果是否属于「提取不出用量」。
     *
     * <p>能否为空由提取器声明，不在这里跨协议猜——未声明允许的，上游有正数却提取为空即口径错配。
     * 静默记零账会把问题推迟到对账，而到那时候已经查不清是哪一次请求了。</p>
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
     * 提取失败的<b>唯一</b>处置点：按 {@code knot.gateway.usage.degrade-on-failure} 决定抛出还是降级。
     *
     * <p>三处失败全部收敛到这里，新增失败原因只加 {@link ExtractFailure} 枚举项，
     * 处置逻辑不再分叉——否则每加一处失败就要在三个地方各写一遍开关判断。</p>
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
     * 原始 usage 里是否存在正数量：区分「上游明确报了零」与「提取器读不出来」。
     *
     * <p>上游可能合法上报全零 usage（如 SSE 中间事件），此时不应判为口径错配；
     * 只有确实存在非零数值却被提取成空，才说明提取器配错。</p>
     *
     * <p>本方法只在提取器<b>未</b>声明 {@link UsageExtractor#allowsEmptyUsage()} 时充当门控：
     * 声明了允许全零的提取器直接放行，不再据此判错——能不能空由提取器决定，不在这里猜。</p>
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
     *
     * <p>收敛成枚举的目的是让「抛 or 降」的判定只写一遍：新增失败情形只加枚举项，
     * {@link #applyFailurePolicy} 不动。</p>
     *
     * <p>{@code detail} 沿用去兜底之前的报错原文，运维既有的 grep 习惯不用改；
     * {@code code} 是给日志用的稳定标识（日志里只有它，错误文案留给异常消息）。</p>
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
            context.model().billingRuleCode(),
            context.model().modelFamilyCode()
        );
    }

    /**
     * 计费规则实体 → 适配层投影：只带计价消费的六个字段（改进清单 2.5）。
     * 实体由 {@link GatewayEntityCacheService} 产出，投影发生在网关侧边界上。
     */
    private static BillingRuleRef ruleRef(BillingRuleEntity rule) {
        return rule == null ? null : new BillingRuleRef(
                rule.getBillingMode(), rule.getConfigJson(), rule.getCurrency(),
                rule.getPricingPlan(), rule.getUnit(), rule.getVersionCode());
    }
}
