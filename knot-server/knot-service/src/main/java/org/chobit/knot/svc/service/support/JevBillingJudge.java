package org.chobit.knot.svc.service.support;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.chobit.knot.svc.config.JevJudgeProperties;
import org.chobit.knot.common.constants.enums.EnabledStatusEnum;
import org.chobit.knot.svc.dto.routing.BillingFactAmount;
import org.chobit.knot.svc.dto.routing.BillingFactAmountLine;
import org.chobit.knot.svc.dto.routing.BillingFactRuleRef;
import org.chobit.knot.svc.dto.routing.BillingFactSubject;
import org.chobit.knot.svc.dto.routing.JevJudgeContext;
import org.chobit.knot.dal.entity.RoutingConsumerEntity;
import org.chobit.knot.dal.entity.RoutingRuleEntity;
import org.chobit.knot.dal.mapper.RoutingConsumerMapper;
import org.chobit.knot.dal.mapper.RoutingRuleMapper;
import org.chobit.knot.svc.vo.routing.JevAnswerItem;
import org.chobit.knot.svc.vo.routing.RoutingBillingVerdict;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpStatusCodeException;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * JEV 计费裁判（v7 §6）：双盲判定链的判定层。
 *
 * <p>职责边界：<b>不碰计费算术</b>（那是 {@code BillingFactChecks} 的权威区），只把被告账 + 底层依据
 * 打包成 {@code state} 送给网关 DECISIONS 端点做<b>语义嗅探</b>。state 严格不含影子重算值 /
 * 解析用量视图 / 自检结论（双盲硬约束 §4.2）。判定失败（配置缺失 / 凭据无权 / 余额不足 / 限流 /
 * 网关错误）一律降级 {@code available:false}，绝不抛异常炸抽屉。</p>
 *
 * <p>凭据纪律（§6.1）：裁判消费者 {@code secretKey} 仅经 {@link DecisionsTransport} 进入
 * {@code Authorization} 头，不进请求体、不进日志、不下前端。</p>
 */
@Slf4j
@Component
public class JevBillingJudge {

    /** state 体积控制：produced.billing.details 超过该值只保留前 N 条（§6.6） */
    private static final int DETAILS_CAP = 20;
    /** 429 退避时长（单次，最多重试 1 次） */
    private static final long RETRY_BACKOFF_MS = 200;
    /** 可观测 trace 名称 */
    private static final String DECISIONS_TRACE_NAME = "knot-billing-verdict";
    /** evidence.requestAttributes 白名单：只透传计费相关请求属性，避免泄露 prompt 等敏感内容 */
    private static final List<String> REQUEST_ATTR_KEYS = List.of(
            "size", "width", "height", "duration_seconds", "duration");

    private final JevJudgeProperties properties;
    private final RoutingConsumerMapper consumerMapper;
    private final RoutingRuleMapper ruleMapper;
    private final DecisionsTransport transport;
    private final ObjectMapper objectMapper;

    public JevBillingJudge(JevJudgeProperties properties,
                          RoutingConsumerMapper consumerMapper,
                          RoutingRuleMapper ruleMapper,
                          DecisionsTransport transport,
                          ObjectMapper objectMapper) {
        this.properties = properties;
        this.consumerMapper = consumerMapper;
        this.ruleMapper = ruleMapper;
        this.transport = transport;
        this.objectMapper = objectMapper;
    }

    /**
     * 执行一次裁判。
     *
     * @param context       裁判输入（被告账 + 底层依据；不含影子/自检）
     * @param gatewayBaseUrl 网关基地址（与被测同实例）
     * @param sessionId     被测请求 id（串 trace，可空）
     * @return 裁判结论（永不为 null；不可用则 {@code available=false} + reason）
     */
    public RoutingBillingVerdict judge(JevJudgeContext context, String gatewayBaseUrl, String sessionId) {
        String ruleCode = blankToNull(properties.getJudgeRuleCode());
        if (ruleCode == null) {
            return unavailable("裁判未配置：knot.jev.judge-rule-code 缺失");
        }
        RoutingRuleEntity rule = ruleMapper.getByRuleCode(ruleCode);
        if (rule == null) {
            return unavailable("裁判规则不存在：" + ruleCode);
        }
        if (!EnabledStatusEnum.isEnabled(rule.getStatus())) {
            return unavailable("裁判规则未启用：" + ruleCode);
        }
        // 消费者由裁判规则的业务码反查（收敛后 kb_routing_rules.consumer_code 承载绑定，
        // 不再依赖 knot.jev.judge-consumer-code 配置项）
        String consumerCode = blankToNull(rule.getConsumerCode());
        if (consumerCode == null) {
            return unavailable("裁判规则未绑定消费者：" + ruleCode);
        }
        RoutingConsumerEntity consumer = consumerMapper.getByCode(consumerCode);
        if (consumer == null) {
            return unavailable("裁判消费者不存在：" + consumerCode);
        }

        // 凭据仅用于鉴权头——本方法任何日志都不引用 secretKey
        String secretKey = consumer.getSecretKey();
        Map<String, Object> body = buildRequestBody(context, sessionId, false);
        try {
            String raw = transport.post(gatewayBaseUrl, secretKey, ruleCode, body);
            return parseVerdict(raw);
        } catch (HttpStatusCodeException ex) {
            int status = ex.getStatusCode().value();
            String respBody = safeBody(ex);
            // 重试策略按「有效状态码」判定：网关把上游 4xx 一律包成 502，若此处只看 HTTP 码，
            // 经网关透出的 413/429 就永远走不到截断/退避重试（Batch4 冒烟场景 7 暴露）。
            int effective = resolveEffectiveStatus(status, respBody);
            if (effective == 413) {
                // 触发 state 截断重试一次（minimal）：丢弃明细、只留汇总
                try {
                    Map<String, Object> minimal = buildRequestBody(context, sessionId, true);
                    String raw2 = transport.post(gatewayBaseUrl, secretKey, ruleCode, minimal);
                    return parseVerdict(raw2);
                } catch (HttpStatusCodeException ex2) {
                    return unavailable("裁判请求体过大（413），已截断重试仍失败");
                } catch (Exception ex2) {
                    return unavailable("裁判请求体过大（413）截断重试失败：" + ex2.getMessage());
                }
            }
            if (effective == 429) {
                // 指数退避（此处退化为固定短退避）最多 1 次
                try {
                    Thread.sleep(RETRY_BACKOFF_MS);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                }
                try {
                    String raw2 = transport.post(gatewayBaseUrl, secretKey, ruleCode, body);
                    return parseVerdict(raw2);
                } catch (HttpStatusCodeException ex2) {
                    return unavailable("裁判限流（429），已退避重试仍失败");
                } catch (Exception ex2) {
                    return unavailable("裁判限流（429）重试失败：" + ex2.getMessage());
                }
            }
            return mapErrorStatus(status, respBody);
        } catch (Exception ex) {
            return unavailable("无法连接网关裁判端点：" + ex.getMessage());
        }
    }

    /** 不可用降级 + 统一日志（仅记录 reason，绝不引用 secretKey） */
    private RoutingBillingVerdict unavailable(String reason) {
        log.warn("JEV 裁判不可用: {}", reason);
        return RoutingBillingVerdict.unavailable(reason);
    }

    // ---- 错误码映射（§6.5） ----

    /**
     * 网关对上游非 2xx 一律回 502（错误码隔离），但响应体 {@code error.upstreamStatus} 携带了
     * 上游原状态码。这里把它作为「有效状态码」参与映射——否则 402/413 分支在本架构下永远不可达，
     * 管理员只能看到「网关错误 502」，分不清余额不足与真故障。
     */
    private Integer resolveEffectiveStatus(int httpStatus, String respBody) {
        if (httpStatus != HttpStatus.BAD_GATEWAY.value()) {
            return httpStatus;
        }
        Integer upstream = readUpstreamStatus(respBody);
        return upstream != null ? upstream : httpStatus;
    }

    /** 从网关错误体 {@code {"error":{...,"upstreamStatus":402}}} 里读上游原状态码；读不到返回 null */
    private Integer readUpstreamStatus(String respBody) {
        if (respBody == null || respBody.isBlank()) {
            return null;
        }
        try {
            Map<String, Object> root = objectMapper.readValue(respBody, Map.class);
            Object error = root == null ? null : root.get("error");
            Object raw = error instanceof Map ? ((Map<?, ?>) error).get("upstreamStatus") : null;
            if (raw instanceof Number n) {
                return n.intValue();
            }
            return raw instanceof String s && s.matches("\\d{3}") ? Integer.valueOf(s) : null;
        } catch (Exception ex) {
            log.warn("网关错误体不含可解析的 upstreamStatus: {}", ex.getMessage());
            return null;
        }
    }

    private RoutingBillingVerdict mapErrorStatus(int httpStatus, String respBody) {
        int status = resolveEffectiveStatus(httpStatus, respBody);
        String fromUpstream = status != httpStatus ? "（经网关 502 透出）" : "";
        return switch (status) {
            case 401, 403 -> unavailable("裁判凭据无权（HTTP " + status + "）" + fromUpstream);
            case 402 -> unavailable("裁判余额不足（402），请充值裁判专用消费者" + fromUpstream);
            case 413 -> unavailable("裁判请求体过大（" + status + "）" + fromUpstream);
            case 429 -> unavailable("裁判限流（429），请稍后重试" + fromUpstream);
            case 400 -> unavailable("裁判契约错误（400）：" + truncate(respBody, 200));
            default -> {
                if (status >= 500) {
                    yield unavailable("裁判网关错误（HTTP " + status + "）");
                }
                yield unavailable(
                        "裁判请求错误（HTTP " + status + "）：" + truncate(respBody, 200));
            }
        };
    }

    // ---- 请求体构建 ----

    private Map<String, Object> buildRequestBody(JevJudgeContext context, String sessionId, boolean minimal) {
        Map<String, Object> request = new LinkedHashMap<>();
        request.put("state", buildState(context, minimal));
        request.put("questions", buildQuestions());
        request.put("provider", buildProvider());
        if (sessionId != null && !sessionId.isBlank()) {
            request.put("session_id", sessionId);
        }
        Map<String, Object> trace = new LinkedHashMap<>();
        trace.put("trace_name", DECISIONS_TRACE_NAME);
        request.put("trace", trace);
        return request;
    }

    /**
     * provider 段只保留下发数据合规约束。
     *
     * <p>上游 provider 白名单（{@code only}）与 {@code allow_fallbacks} 已移除（2026-10-20）：
     * 上游 provider 选择无法从 knot 侧约束，且路由规则已锁定 DECISIONS 模型，无需重复表达。</p>
     */
    private Map<String, Object> buildProvider() {
        Map<String, Object> provider = new LinkedHashMap<>();
        // data_collection:deny 防计费数据被留存训练
        provider.put("data_collection", "deny");
        return provider;
    }

    /** 四条 questions。裁判结论使用固定 choice，避免 noul/score 数值输出无法稳定解释。 */
    private static Map<String, Object> buildQuestions() {
        Map<String, Object> questions = new LinkedHashMap<>();
        questions.put("usage_parse_ok", Map.of(
                "type", "choice",
                "instructions", "请只根据 state.evidence.upstreamUsage（上游原始 usage）与 "
                        + "state.produced.usage（网关归一化 usage）逐项核对输入、输出、缓存、推理与总量。"
                        + "注意：usage 的父级总量可能已包含缓存/推理等子项，子项是拆分说明，不可再与父级相加；"
                        + "若 completion_tokens 与 reasoning_tokens 同时存在，reasoning_tokens 通常是 completion_tokens 的子集，"
                        + "不能把二者相加；在这些字段存在时按以下关系核对：prompt_tokens 对应 input.tokens.text，"
                        + "completion_tokens 对应 output.tokens.text + output.tokens.reasoning，"
                        + "completion_tokens_details.reasoning_tokens 对应 output.tokens.reasoning，"
                        + "total_tokens 对应 usage.total_tokens 且等于 prompt_tokens + completion_tokens。"
                        + "缓存 token 是 prompt_tokens 的子集，不重复计入总量；只有归一化结果提供了对应维度时才比较缓存明细。"
                        + "只有能够按同一口径明确对齐时才比较数值。"
                        + "只有原始 usage 提供足够证据且维度和值一致时选 CORRECT；发现同口径下可证实的遗漏、错位或矛盾时选 INCORRECT；"
                        + "字段未提供、维度口径不一致或无法确认父子关系时必须选 INSUFFICIENT_EVIDENCE，不得仅因细分字段缺失判 INCORRECT。"
                        + "必须且只能返回一个 criteria 中的 choice。",
                "criteria", usageParseCriteria()));
        questions.put("rule_match_ok", Map.of(
                "type", "choice",
                "instructions", "本次命中（见 evidence.billingRule.matchedBy）的计费规则是否适用于"
                        + "该模型与用量形态？",
                "criteria", Map.of(
                        "APPROPRIATE", "规则与模型/用量形态相称",
                        "FALLBACK_ONLY", "只命中了全局默认/兜底规则，未命中模型专属规则",
                        "MODE_MISMATCH", "计费模式与用量形态不匹配（如媒体用量套 token 规则）",
                        "MISSING", "完全未命中任何计费规则")));
        questions.put("cost_plausible", Map.of(
                "type", "choice",
                "instructions", "请依据 state.produced.billing 的 totalCost、details（tokens/cost/price）与 "
                        + "state.evidence.billingRule、upstreamUsage、upstreamCost 判断金额的语义合理性，不做精确算术复核。"
                        + "证据足以支持金额与用量/单价相称时选 PLAUSIBLE；证据足以确认规则、币种或金额量级明显矛盾时选 SUSPICIOUS；"
                        + "必要账单或用量依据缺失、无法判断时选 INSUFFICIENT_EVIDENCE。upstreamCost 缺失本身不代表证据不足，"
                        + "请继续使用账单明细和规则依据判断。必须且只能返回一个 criteria 中的 choice。",
                "criteria", costPlausibleCriteria()));
        questions.put("risk_level", Map.of(
                "type", "choice",
                "instructions", "综合 state.subject、state.produced 与 state.evidence 给出一个离散风险等级。"
                        + "LOW 表示没有明显异常证据；MEDIUM 表示存在局部疑点或证据不完整，建议人工复核；"
                        + "HIGH 表示存在明确且重大的计费/规则矛盾，需要优先排查。不得使用自检结论，"
                        + "必须且只能返回一个 criteria 中的 choice。",
                "criteria", riskLevelCriteria()));
        return questions;
    }

    private static Map<String, String> usageParseCriteria() {
        return Map.of(
                "CORRECT", "归一化完整且与上游依据一致",
                "INCORRECT", "存在可证实的遗漏、错位或数值矛盾",
                "INSUFFICIENT_EVIDENCE", "必要的上游原始 usage 字段缺失，无法核验");
    }

    private static Map<String, String> costPlausibleCriteria() {
        return Map.of(
                "PLAUSIBLE", "账单金额与用量、价格及规则依据相称",
                "SUSPICIOUS", "存在可证实的金额量级、币种或规则矛盾",
                "INSUFFICIENT_EVIDENCE", "必要账单或用量依据缺失，无法判断");
    }

    private static Map<String, String> riskLevelCriteria() {
        return Map.of(
                "LOW", "没有明显异常证据",
                "MEDIUM", "存在局部疑点或证据不完整，建议人工复核",
                "HIGH", "存在明确且重大的计费或规则矛盾，需优先排查");
    }

    /**
     * state 构建（§4.2 终态契约，硬约束）：只承载「已产出 + 底层依据」；
     * 绝不放入影子重算值（computed）、解析用量视图（parsedUsage）或自检结论（双盲）。
     *
     * @param minimal 是否极简（413 截断重试）：丢弃明细、只留汇总
     */
    private Map<String, Object> buildState(JevJudgeContext context, boolean minimal) {
        BillingFactSubject subject = context.subject();
        Map<String, Object> subjectMap = new LinkedHashMap<>();
        subjectMap.put("modelCode", subject.modelCode());
        subjectMap.put("protocol", subject.protocol());
        subjectMap.put("stream", subject.stream());
        subjectMap.put("ruleSource", subject.ruleSource() == null ? null : subject.ruleSource().name());

        Map<String, Object> produced = new LinkedHashMap<>();
        produced.put("usage", context.gatewayUsage() == null ? Map.of() : context.gatewayUsage());
        produced.put("billing", toBillingMap(context.gatewayBilling(), minimal));

        Map<String, Object> evidence = new LinkedHashMap<>();
        evidence.put("upstreamUsage", filterNumericKeys(context.upstreamUsage()));
        evidence.put("upstreamCost", context.upstreamCost() == null ? null : context.upstreamCost().toPlainString());
        evidence.put("billingRule", toRuleRefMap(context.ruleRef()));
        evidence.put("requestAttributes", extractRequestAttributes(context.requestBody()));

        Map<String, Object> state = new LinkedHashMap<>();
        state.put("subject", subjectMap);
        state.put("produced", produced);
        state.put("evidence", evidence);
        return state;
    }

    private static Map<String, Object> toBillingMap(BillingFactAmount amount, boolean minimal) {
        if (amount == null) {
            return null;
        }
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("totalTokens", amount.totalTokens());
        map.put("totalCost", amount.totalCost());
        map.put("currency", amount.currency());
        List<BillingFactAmountLine> details = amount.details() == null ? List.of() : amount.details();
        if (minimal) {
            map.put("details", List.of());
        } else if (details.size() > DETAILS_CAP) {
            map.put("details", details.subList(0, DETAILS_CAP).stream().map(JevBillingJudge::toLineMap).toList());
        } else {
            map.put("details", details.stream().map(JevBillingJudge::toLineMap).toList());
        }
        return map;
    }

    private static Map<String, Object> toLineMap(BillingFactAmountLine line) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("type", line.type());
        map.put("tokens", line.tokens());
        map.put("cost", line.cost());
        map.put("price", line.price());
        return map;
    }

    private static Map<String, Object> toRuleRefMap(BillingFactRuleRef ref) {
        if (ref == null) {
            return null;
        }
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("code", ref.code());
        map.put("billingMode", ref.billingMode());
        map.put("unit", ref.unit());
        map.put("currency", ref.currency());
        map.put("pricingPlan", ref.pricingPlan());
        map.put("matchedBy", ref.matchedBy() == null ? null : ref.matchedBy().name());
        map.put("versionCode", ref.versionCode());
        return map;
    }

    /** upstreamUsage 只保留数值叶子（§6.6），保留嵌套 usage 维度并剔除文本原文 */
    private static Map<String, Object> filterNumericKeys(Map<String, Object> upstreamUsage) {
        if (upstreamUsage == null || upstreamUsage.isEmpty()) {
            return Map.of();
        }
        return filterNumericMap(upstreamUsage);
    }

    private static Map<String, Object> filterNumericMap(Map<?, ?> values) {
        Map<String, Object> out = new LinkedHashMap<>();
        for (Map.Entry<?, ?> entry : values.entrySet()) {
            if (!(entry.getKey() instanceof String key)) {
                continue;
            }
            Object filtered = filterNumericValue(entry.getValue());
            if (filtered != null) {
                out.put(key, filtered);
            }
        }
        return out;
    }

    private static Object filterNumericValue(Object value) {
        if (value instanceof Number) {
            return value;
        }
        if (value instanceof String s) {
            return isNumeric(s) ? s : null;
        }
        if (value instanceof Map<?, ?> map) {
            Map<String, Object> filtered = filterNumericMap(map);
            return filtered.isEmpty() ? null : filtered;
        }
        if (value instanceof List<?> list) {
            List<Object> filtered = list.stream()
                    .map(JevBillingJudge::filterNumericValue)
                    .filter(v -> v != null)
                    .toList();
            return filtered.isEmpty() ? null : filtered;
        }
        return null;
    }

    private static Map<String, Object> extractRequestAttributes(Map<String, Object> requestBody) {
        if (requestBody == null || requestBody.isEmpty()) {
            return Map.of();
        }
        Map<String, Object> out = new LinkedHashMap<>();
        for (String key : REQUEST_ATTR_KEYS) {
            Object v = requestBody.get(key);
            if (v != null) {
                out.put(key, v);
            }
        }
        return out;
    }

    // ---- 响应解析（§6.4 防御式） ----

    @SuppressWarnings("unchecked")
    private RoutingBillingVerdict parseVerdict(String raw) {
        List<JevAnswerItem> answers = new ArrayList<>();
        try {
            Map<String, Object> root = objectMapper.readValue(raw, Map.class);
            Object answersObj = root == null ? null : root.get("answers");
            Map<String, Object> answersMap = answersObj instanceof Map
                    ? (Map<String, Object>) answersObj : Map.of();
            Map<String, Object> ruleCriteria = Map.of(
                    "APPROPRIATE", "规则与模型/用量相称",
                    "FALLBACK_ONLY", "只命中默认/兜底规则",
                    "MODE_MISMATCH", "计费模式与用量不匹配",
                    "MISSING", "完全未命中规则");
            answers.add(parseAnswer("usage_parse_ok", "choice", answersMap.get("usage_parse_ok"), usageParseCriteria()));
            answers.add(parseAnswer("rule_match_ok", "choice", answersMap.get("rule_match_ok"), ruleCriteria));
            answers.add(parseAnswer("cost_plausible", "choice", answersMap.get("cost_plausible"), costPlausibleCriteria()));
            answers.add(parseAnswer("risk_level", "choice", answersMap.get("risk_level"), riskLevelCriteria()));
        } catch (Exception ex) {
            log.warn("裁判响应解析失败，按未返回处理: {}", ex.getMessage());
            answers = defaultNotReturned();
        }
        RoutingBillingVerdict verdict = RoutingBillingVerdict.available(buildSummary(answers), answers);
        verdict.setRawResponse(raw);
        return verdict;
    }

    @SuppressWarnings("unchecked")
    private static JevAnswerItem parseAnswer(String key, String type, Object rawAnswer, Map<String, ?> criteria) {
        if (!(rawAnswer instanceof Map)) {
            return new JevAnswerItem(key, type, null, null, null, "NOT_RETURNED", null);
        }
        Map<String, Object> ans = (Map<String, Object>) rawAnswer;
        try {
            return switch (type) {
                case "noul" -> {
                    Object v = ans.get("noul");
                    yield new JevAnswerItem(key, type, asBoolean(v), null, null, "ANSWERED", null);
                }
                case "choice" -> {
                    Object v = ans.get("choice");
                    String selected = v == null ? null : v.toString();
                    Double confidence = readDouble(ans, "confidence");
                    Object description = (selected != null && criteria != null) ? criteria.get(selected) : null;
                    String note = description == null ? null : description.toString();
                    boolean recognized = selected != null && (criteria == null || criteria.containsKey(selected));
                    yield new JevAnswerItem(key, type, recognized ? selected : null, confidence, null,
                            recognized ? "ANSWERED" : "NOT_RETURNED", note);
                }
                case "score" -> {
                    Double v = readDouble(ans, "score");
                    List<String> legend = readStringList(ans, "legend");
                    yield new JevAnswerItem(key, type, v, null, legend,
                            v != null ? "ANSWERED" : "NOT_RETURNED", null);
                }
                default -> new JevAnswerItem(key, type, null, null, null, "NOT_RETURNED", null);
            };
        } catch (Exception ex) {
            return new JevAnswerItem(key, type, null, null, null, "NOT_RETURNED", null);
        }
    }

    private static Boolean asBoolean(Object v) {
        if (v instanceof Boolean b) {
            return b;
        }
        if (v instanceof String s) {
            String t = s.trim().toLowerCase();
            if ("true".equals(t)) {
                return Boolean.TRUE;
            }
            if ("false".equals(t)) {
                return Boolean.FALSE;
            }
        }
        return null;
    }

    private static Double readDouble(Map<String, Object> map, String key) {
        Object v = map.get(key);
        if (v == null) {
            return null;
        }
        if (v instanceof Number n) {
            return n.doubleValue();
        }
        try {
            return Double.parseDouble(v.toString().trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static List<String> readStringList(Map<String, Object> map, String key) {
        Object v = map.get(key);
        if (v == null) {
            return null;
        }
        if (v instanceof List<?> list) {
            List<String> out = new ArrayList<>();
            for (Object o : list) {
                if (o != null) {
                    out.add(o.toString());
                }
            }
            return out;
        }
        return null;
    }

    private static String buildSummary(List<JevAnswerItem> answers) {
        List<String> parts = new ArrayList<>();
        for (JevAnswerItem a : answers) {
            String label = switch (a.getKey()) {
                case "usage_parse_ok" -> "用量解析";
                case "rule_match_ok" -> "规则匹配";
                case "cost_plausible" -> "费用相称";
                case "risk_level" -> "风险";
                default -> a.getKey();
            };
            String val;
            if (!"ANSWERED".equals(a.getStatus())) {
                val = "未返回";
            } else if (a.getValue() instanceof Boolean b) {
                val = b ? "OK" : "异常";
            } else if (a.getValue() instanceof Number n) {
                val = String.valueOf(n);
            } else if (a.getNote() != null) {
                val = a.getNote();
            } else {
                val = String.valueOf(a.getValue());
            }
            parts.add(label + ": " + val);
        }
        return String.join("；", parts);
    }

    private static List<JevAnswerItem> defaultNotReturned() {
        return List.of(
                new JevAnswerItem("usage_parse_ok", "choice", null, null, null, "NOT_RETURNED", null),
                new JevAnswerItem("rule_match_ok", "choice", null, null, null, "NOT_RETURNED", null),
                new JevAnswerItem("cost_plausible", "choice", null, null, null, "NOT_RETURNED", null),
                new JevAnswerItem("risk_level", "choice", null, null, null, "NOT_RETURNED", null));
    }

    // ---- 工具 ----

    private static boolean isNumeric(String s) {
        if (s == null || s.isBlank()) {
            return false;
        }
        try {
            new BigDecimal(s.trim());
            return true;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    private static String safeBody(HttpStatusCodeException ex) {
        try {
            return ex.getResponseBodyAsString();
        } catch (Exception e) {
            return "";
        }
    }

    private static String truncate(String s, int max) {
        if (s == null) {
            return "";
        }
        return s.length() <= max ? s : s.substring(0, max) + "...";
    }

    private static String blankToNull(String s) {
        return (s == null || s.isBlank()) ? null : s.trim();
    }
}
