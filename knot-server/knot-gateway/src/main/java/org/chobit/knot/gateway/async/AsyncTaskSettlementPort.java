package org.chobit.knot.gateway.async;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.chobit.knot.common.constants.enums.ModelApiProtocolEnum;
import org.chobit.knot.common.constants.enums.ProxyErrorCodeEnum;
import org.chobit.knot.common.error.BusinessException;
import org.chobit.knot.common.error.ErrorCode;
import org.chobit.knot.common.util.CallbackFieldSupport;
import org.chobit.knot.common.util.JsonKit;
import org.chobit.knot.svc.vo.gateway.BillingRuleConfigVo;
import org.chobit.knot.svc.vo.gateway.ModelApiBindingConfigVo;
import org.chobit.knot.svc.vo.gateway.ModelConfigVo;
import org.chobit.knot.gateway.exception.GatewayUpstreamException;
import org.chobit.knot.gateway.kafka.msg.GatewayTrafficMsgDto;
import org.chobit.knot.gateway.model.GatewayRoutingContext;
import org.chobit.knot.gateway.model.ProxyResult;
import org.chobit.knot.gateway.model.ResolvedRouting;
import org.chobit.knot.gateway.model.vo.response.AsyncTaskAcceptedResponse;
import org.chobit.knot.gateway.runtime.GatewayExchange;
import org.chobit.knot.gateway.runtime.GatewayRequestContext;
import org.chobit.knot.gateway.runtime.GatewayRequestHandler;
import org.chobit.knot.gateway.service.GatewayConfigCacheService;
import org.chobit.knot.svc.dto.async.AsyncTaskDraftDto;
import org.chobit.knot.svc.dto.async.AsyncTaskProfileDto;
import org.chobit.knot.svc.service.AsyncTaskProfileService;
import org.chobit.knot.svc.service.AsyncTaskService;
import org.chobit.knot.svc.dto.routing.RoutingRuleTargetDto;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;
/**
 * 异步任务的<b>提交侧</b>：读绑定 → 读档案 → 落一条待轮询任务，并给出 202 信封。
 *
 * <p>⚠ <b>判定与退避不在本类了</b>（2026-10-29 拆到同包的 {@link AsyncTaskSpecRules}）：
 * 「什么算成功、多久再看一眼」是纯函数，输入只有响应 {@code Map} 与快照，
 * 与「读绑定、读档案、建任务行」不是同一抽象层。本类留下的都是<b>提交那一刻</b>才需要的东西——
 * 模型/绑定/计费规则这些实体与本地缓存，以及判定链本身。⭐ 回执体的形状<b>不在这里</b>，
 * 归 {@link AsyncTaskAcceptedResponse}（对外契约住 {@code model.vo.response}）。</p>
 *
 * <p>本类不含任何厂商名称：查询路径、终态取值、产物位置全部来自档案
 * （{@code kb_async_task_profiles}）的配置值，新增一家异步上游 = 加一行档案，<b>不改本类</b>。</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AsyncTaskSupport {

    private final AsyncTaskProfileService profileService;
    private final GatewayConfigCacheService dataService;
    private final AsyncTaskService taskService;

    /** 读回调主机白名单（{@code callback-allowed-hosts}）；提交时用于 SSRF 守卫 */
    private final AsyncTaskProperties properties;

    /**
     * 该绑定是否为异步任务模型：只看绑定上的 {@code async_profile_code} 有没有值。
     *
     * <p>⭐ 异步与否是「绑定」的属性而不是协议的属性：同为 {@code VIDEO_GENERATIONS}，
     * 可灵 / MiniMax 是「提交 + 轮询」，其它模型可能同步返回。因此判定一律落在绑定上，
     * 不能按协议枚举猜（那会让既有同步视频一起被改造成异步）。</p>
     */
    public String profileCodeOf(ModelApiBindingConfigVo binding) {
        return binding == null ? null : StringUtils.trimToNull(binding.asyncProfileCode());
    }

    /**
     * 提交前的轻量判定：该目标是否已配置为异步任务。
     *
     * <p>只判断前两环（绑定有 {@code async_profile_code} + 档案当前可用），不读取上游响应。
     * 请求链路在拿到上游结果后需要据此决定<b>要不要在提交阶段记限额</b>：异步只完成了 create，
     * 真实用量要等终态查询，提交阶段累计只会把占位账写进额度。</p>
     */
    public boolean isAsync(RoutingRuleTargetDto target, ModelApiProtocolEnum protocol) {
        if (target == null) {
            return false;
        }
        String profileCode = profileCodeOf(bindingOf(target.targetId(), protocol));
        return profileCode != null && loadSpec(profileCode, protocol) != null;
    }

    /**
     * 按命中目标解析该协议的绑定（与 {@code UpstreamProxyClient#resolveBinding} 同口径）。
     *
     * @param modelId  命中模型主键
     * @param protocol 本次协议
     */
    public ModelApiBindingConfigVo bindingOf(Long modelId, ModelApiProtocolEnum protocol) {
        if (modelId == null || protocol == null) {
            return null;
        }
        return dataService.listApiBindingsByModelId(modelId).stream()
                .filter(item -> protocol.matches(item.protocol()))
                .findFirst()
                .orElse(null);
    }

    /**
     * 读档案并投影为不可变快照；档案不可用时返回 null（由调用方判为「配置不完整」→ 回落同步）。
     *
     * <p>⭐ <b>运行时两道防线</b>（{@code 任务协议绑定方案 §4.6}）：管理面保存绑定时会校验
     * 「档案存在且启用、协议一致」，但<b>迁移脚本与手写 SQL 是合法写入路径</b>，
     * 所以运行时必须再判一次。判据不同：管理面回答「能不能存」，这里回答「现在能不能用」。</p>
     *
     * <p>⚠ 回落而不是抛错：请求本身没坏（上游已成功、响应体在手上），只是这次不计费为异步。
     * 抛 500 会把一个纯配置错误变成用户可见故障；但日志必须是 {@code error} 且带齐三方信息，
     * 否则运维只能猜。</p>
     *
     * @param protocol 绑定行上的协议；用于与档案所属协议比对（{@code null} 表示不做比对）
     */
    public AsyncTaskSpec loadSpec(String profileCode, ModelApiProtocolEnum protocol) {
        if (StringUtils.isBlank(profileCode)) {
            return null;
        }
        String code = profileCode.trim();
        AsyncTaskProfileDto profile = profileService.getByProfileCode(code);
        if (profile == null) {
            log.error("Async task profile not found, fallback to sync: profile={}", code);
            return null;
        }
        if (profileService.getEnabledByProfileCode(code) == null) {
            log.error("Async task profile is disabled but referenced by a binding, fallback to sync: profile={}", code);
            return null;
        }
        if (protocol != null && !protocol.matches(profile.protocol())) {
            log.error("Async task profile protocol mismatch, fallback to sync: profile={} profileProtocol={} bindingProtocol={}",
                    code, profile.protocol(), protocol.code());
            return null;
        }
        return AsyncTaskSpec.of(profile);
    }

    /**
     * 组装一条待轮询任务。
     *
     * @param upstreamTaskId 上游任务 ID（从创建响应按 {@code task_id_path} 取出，由
     *                       {@link #requireUpstreamTaskId} 保证非空；取不到在那一层就抛了，
     *                       不会有「没有 ID 的任务行」落库）
     */
    public AsyncTaskDraftDto build(AsyncTaskCreation creation) {
        AsyncTaskSpec spec = creation.spec();
        LocalDateTime now = creation.now();
        Integer days = spec.resultExpiresDays();
        return new AsyncTaskDraftDto(
                creation.taskCode(),
                creation.profileCode(),
                creation.protocol() == null ? null : creation.protocol().code(),
                creation.upstreamTaskId(),
                now.plusNanos(Math.max(1L, spec.pollIntervalMs()) * 1_000_000L),
                jsonOrThrow(creation.requestBody()),
                jsonOrThrow(spec),
                jsonOrThrow(creation.context()),
                creation.billingRuleCode(),
                creation.billingRuleVersionCode(),
                now,
                now.plusMinutes(Math.max(1L, spec.timeoutMinutes())),
                days == null || days <= 0 ? null : now.plusDays(days),
                creation.callbackUrl());
    }

    /**
     * 落库字段序列化：<b>失败必须抛</b>。
     *
     * <p>旧实现用 {@code JsonKit.toJson}（失败只 log 后返回 null），requestBody / spec / context
     * 会以 NULL 落库，界面照样显示「已提交」，轮询时却无从重建上下文。
     * 宁可拒绝提交，也不能留下一个假任务。</p>
     */
    private static String jsonOrThrow(Object value) {
        try {
            return JsonKit.toJsonOrThrow(value);
        } catch (JsonProcessingException e) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR,
                    "异步任务字段序列化失败，未提交: " + e.getMessage());
        }
    }

    /** 生成对外任务句柄：32 位小写 hex，与网关 requestId 同形态，不暴露上游 ID */
    public static String newTaskCode() {
        return UUID.randomUUID().toString().replace("-", "");
    }

    /**
     * 判定本次上游结果是否为「异步任务创建成功」，是则落一条待轮询任务并给出 202 回执。
     *
     * <p>判定链（前两环不成立即返回 {@code null}，回退同步链路）：</p>
     * <ol>
     *   <li>命中目标存在 → 取该模型 × 协议的绑定；</li>
     *   <li>绑定上 {@code async_profile_code} 有值 → 读档案；</li>
     *   <li>按档案的 {@code task_id_path} 从创建响应取出上游任务 ID。</li>
     * </ol>
     *
     * <p>⭐⭐ <b>第 3 步取不到 ID 是抛而不是回退</b>（2026-11-01 robin 定）：前两环是「这次压根没打算走异步」，
     * 回 {@code null} 成立；第 3 步已经确定「该建成异步任务」，此时没有 ID 就没有东西可轮询，
     * 回退同步等于把上游「任务已创建」的响应当成最终结果交给客户端——本地无任务行，
     * 结果既查不到也不计费。详见 {@link #requireUpstreamTaskId}。</p>
     *
     * @return 202 回执体（非 {@code null} 本身即表示「已判定为异步」）；
     *         非异步任务或配置不完整时为 {@code null}
     * @throws GatewayUpstreamException 档案声明了异步，但创建响应为空或取不到上游任务 ID
     */
    public AsyncTaskAcceptedResponse submitIfAsync(GatewayRequestContext context,
                                                   GatewayExchange exchange,
                                                   ProxyResult result) {
        if (context == null) {
            return null;
        }
        RoutingRuleTargetDto target = context.attribute(GatewayRequestHandler.HIT_TARGET_ATTRIBUTE);
        ModelApiBindingConfigVo binding = bindingOf(target == null ? null : target.targetId(), context.protocol());
        String profileCode = profileCodeOf(binding);
        if (profileCode == null) {
            return null;
        }
        AsyncTaskSpec spec = loadSpec(profileCode, context.protocol());
        if (spec == null) {
            return null;
        }
        // 走到这里已经确定「本次就是异步任务」。创建回执为空时没有任务 ID 可轮询，
        // 回退同步只会把上游的空 2xx 当终态交给客户端，且本地既无任务行也无终态结算。
        if (result == null || StringUtils.isBlank(result.responseBody())) {
            throw new GatewayUpstreamException(
                    "异步任务档案【" + profileCode + "】已声明异步，但创建响应为空，无法取出上游任务 ID",
                    ProxyErrorCodeEnum.UPSTREAM_ERROR.code());
        }
        Map<String, Object> createBody = JsonKit.fromJson(result.responseBody(),
                new TypeReference<Map<String, Object>>() {
                });
        String upstreamTaskId = requireUpstreamTaskId(profileCode, createBody, spec);
        ModelConfigVo model = dataService.getModelByCode(target == null ? null : target.targetCode());
        BillingRuleConfigVo rule = model == null ? null
                : dataService.getActiveBillingRule(model.billingRuleCode(), model.modelFamilyCode());
        String taskCode = newTaskCode();
        ResolvedRouting routing = context.routing();
        GatewayRoutingContext routingContext = routing == null ? null : routing.routingContext();
        AsyncTaskContext taskContext = new AsyncTaskContext(
                taskCode,
                context.ruleCode(),
                context.stickyKey(),
                context.traceparent(),
                context.requestId(),
                context.protocol() == null ? null : context.protocol().code(),
                target == null ? null : target.targetCode(),
                target == null ? null : target.upstreamModelCode(),
                target == null ? null : target.providerAccountCode(),
                target == null ? null : target.poolCode(),
                result.upstreamUrl(),
                routing == null ? null : routing.ruleId(),
                routing == null ? null : routing.consumerId(),
                routingContext == null || routingContext.app() == null ? null : routingContext.app().id(),
                target == null ? null : target.targetId());
        AsyncTaskDraftDto task = build(new AsyncTaskCreation(
                taskCode, profileCode, spec, context.protocol(),
                exchange == null ? Map.of() : exchange.requestBody(),
                taskContext, upstreamTaskId,
                rule == null ? null : rule.code(), rule == null ? null : rule.versionCode(),
                LocalDateTime.now(), resolveCallbackUrl(exchange)));
        taskService.insert(task);
        // ⭐ 提交那条流量事件要带 task 标记（它是 1 + N 里的那个 1），但「事件长什么样」是
        // 本包的事，不是请求链路的事。写在这里而不是 GatewayRequestHandler 里，
        // 是为了让同步链路对事件格式零感知——它只负责「异步就回 202」。
        context.putAttribute(GatewayTrafficMsgDto.TaskTrace.ATTRIBUTE,
                GatewayTrafficMsgDto.TaskTrace.submit(taskCode));
        return AsyncTaskAcceptedResponse.accepted(taskCode);
    }

    /**
     * 从创建响应取上游任务 ID，<b>取不到就抛</b>（包级可见，供单测直接驱动）。
     *
     * <p>⭐⭐ <b>为什么是 502 而不是 500 / 回退同步</b>（2026-11-01）：
     * 上游回了 2xx（响应体在手上、任务已建），但里面没有档案约定的任务 ID ——
     * 这不是网关内部故障，而是「档案的 {@code task_id_path} 配错」或「上游契约变了」。
     * 用 {@link GatewayUpstreamException} 表达，客户端拿到 502 {@code UPSTREAM_ERROR}、
     * 数仓落 {@code upstream_call} 阶段，运维照消息里的档案编码去改档案即可。</p>
     *
     * <p>⚠ 消息里必须带档案编码与表达式原文：只报「取不到 ID」的话，线上有几十份档案时
     * 无从下手（绑定 → 档案 → 列，三层跳转只能靠猜）。</p>
     *
     * @param profileCode 触发本次判定的档案编码（绑定上的 {@code async_profile_code}）
     * @param createBody  上游创建响应
     * @param spec        档案快照（提供 {@code task_id_path}）
     * @return 非空的上游任务 ID
     * @throws GatewayUpstreamException 表达式为空 / 求值失败 / 取到的不是标量
     */
    String requireUpstreamTaskId(String profileCode, Map<String, Object> createBody, AsyncTaskSpec spec) {
        try {
            return AsyncTaskSpecRules.upstreamTaskIdOf(createBody, spec);
        } catch (IllegalStateException e) {
            throw new GatewayUpstreamException(
                    "异步任务档案【" + profileCode + "】已声明异步，但创建响应里取不到上游任务 ID："
                            + e.getMessage(), ProxyErrorCodeEnum.UPSTREAM_ERROR.code());
        }
    }

    /**
     * 从客户端原始请求体里取回调地址并做安全校验。
     *
     * <p>⭐ <b>取的是 {@code exchange.requestBody()}（原始体），不是转发给上游的那份</b>：
     * 上游请求体里的回推字段已被 {@code VideoRequestSupport} 剥掉（防止上游绕过结算直推客户端），
     * 所以提取必须发生在剥除之前——这一步在提交侧，剥除在适配器侧，天然满足。</p>
     *
     * <p>⚠ 校验不过时<b>只丢回调、不失败提交</b>：回调是增强能力，客户仍可轮询拿结果；
     * 为一个不安全的地址把整条视频任务判死，代价远大于少一次回调。但必须留 WARN，
     * 否则「我配了 callback_url 却没收到」会变成无从排查的黑洞。</p>
     */
    private String resolveCallbackUrl(GatewayExchange exchange) {
        String raw = CallbackFieldSupport.callbackUrlOf(exchange == null ? Map.of() : exchange.requestBody());
        String safe = AsyncTaskCallbackUrlSupport.normalize(raw, properties.getCallbackAllowedHosts());
        if (raw != null && safe == null) {
            log.warn("Async task callback url rejected by safety guard, callback disabled: url={}", raw);
        }
        return safe;
    }

    /**
     * 创建一条异步任务的输入（一次性打包，避免七、八个同类型参数排在一起被传错顺序）。
     */
    public record AsyncTaskCreation(String taskCode,
                                    String profileCode,
                                    AsyncTaskSpec spec,
                                    ModelApiProtocolEnum protocol,
                                    Map<String, Object> requestBody,
                                    AsyncTaskContext context,
                                    String upstreamTaskId,
                                    String billingRuleCode,
                                    String billingRuleVersionCode,
                                    LocalDateTime now,
                                    /**
                                     * 已校验的回调地址；{@code null} 表示不回调。
                                     *
                                     * <p>⚠ 只在这里（提交那一刻）从原始请求体提取一次并快照，
                                     * 之后不再回看请求体：档案/请求体都可能在途变化，而「这批任务回调到哪」
                                     * 必须是一个确定事实。</p>
                                     */
                                    String callbackUrl) {
    }

}
