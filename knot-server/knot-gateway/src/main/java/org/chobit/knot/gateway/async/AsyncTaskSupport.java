package org.chobit.knot.gateway.async;

import com.fasterxml.jackson.core.type.TypeReference;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.chobit.knot.common.constants.enums.ModelApiProtocolEnum;
import org.chobit.knot.common.util.CallbackFieldSupport;
import org.chobit.knot.common.util.JsonKit;
import org.chobit.knot.dal.entity.BillingRuleEntity;
import org.chobit.knot.dal.entity.ModelApiBindingEntity;
import org.chobit.knot.dal.entity.ModelEntity;
import org.chobit.knot.gateway.kafka.msg.GatewayTrafficMsgDto;
import org.chobit.knot.gateway.model.ProxyResult;
import org.chobit.knot.gateway.model.vo.response.AsyncTaskAcceptedResponse;
import org.chobit.knot.gateway.runtime.GatewayExchange;
import org.chobit.knot.gateway.runtime.GatewayRequestContext;
import org.chobit.knot.gateway.runtime.GatewayRequestHandler;
import org.chobit.knot.gateway.service.GatewayEntityCacheService;
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
    private final GatewayEntityCacheService dataService;
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
    public String profileCodeOf(ModelApiBindingEntity binding) {
        return binding == null ? null : StringUtils.trimToNull(binding.getAsyncProfileCode());
    }

    /**
     * 按命中目标解析该协议的绑定（与 {@code UpstreamProxyClient#resolveBinding} 同口径）。
     *
     * @param modelId  命中模型主键
     * @param protocol 本次协议
     */
    public ModelApiBindingEntity bindingOf(Long modelId, ModelApiProtocolEnum protocol) {
        if (modelId == null || protocol == null) {
            return null;
        }
        return dataService.listApiBindingsByModelId(modelId).stream()
                .filter(item -> protocol.matches(item.getProtocol()))
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
     * @param upstreamTaskId 上游任务 ID（从创建响应按 {@code task_id_path} 取出）；取不到返回 null，
     *                       调用方应视为「异步判定不成立」而回退同步链路 —— 没有任务 ID 就无从轮询。
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
                JsonKit.toJson(creation.requestBody()),
                JsonKit.toJson(spec),
                JsonKit.toJson(creation.context()),
                creation.billingRuleCode(),
                creation.billingRuleVersionCode(),
                now,
                now.plusNanos(Math.max(1L, spec.timeoutMs()) * 1_000_000L),
                days == null || days <= 0 ? null : now.plusDays(days),
                creation.callbackUrl());
    }

    /** 生成对外任务句柄：32 位小写 hex，与网关 requestId 同形态，不暴露上游 ID */
    public static String newTaskCode() {
        return UUID.randomUUID().toString().replace("-", "");
    }

    /**
     * 判定本次上游结果是否为「异步任务创建成功」，是则落一条待轮询任务并给出 202 回执。
     *
     * <p>判定链（任一环不成立即返回 {@code null}，回退同步链路）：</p>
     * <ol>
     *   <li>命中目标存在 → 取该模型 × 协议的绑定；</li>
     *   <li>绑定上 {@code async_profile_code} 有值 → 读档案；</li>
     *   <li>按档案的 {@code task_id_path} 从创建响应取出上游任务 ID。</li>
     * </ol>
     *
     * <p>第 3 步取不到 ID 时回退同步（并 ERROR 日志）：没有任务 ID 就无从轮询，
     * 与其落一条永远查不到的任务，不如明确走同步路径让问题立刻暴露。</p>
     *
     * @return 202 回执体（非 {@code null} 本身即表示「已判定为异步」）；
     *         非异步任务或配置不完整时为 {@code null}
     */
    public AsyncTaskAcceptedResponse submitIfAsync(GatewayRequestContext context,
                                                   GatewayExchange exchange,
                                                   ProxyResult result) {
        if (result == null || StringUtils.isBlank(result.responseBody())) {
            return null;
        }
        RoutingRuleTargetDto target = context.attribute(GatewayRequestHandler.HIT_TARGET_ATTRIBUTE);
        ModelApiBindingEntity binding = bindingOf(target == null ? null : target.targetId(), context.protocol());
        String profileCode = profileCodeOf(binding);
        if (profileCode == null) {
            return null;
        }
        AsyncTaskSpec spec = loadSpec(profileCode, context.protocol());
        if (spec == null) {
            return null;
        }
        Map<String, Object> createBody = JsonKit.fromJson(result.responseBody(),
                new TypeReference<Map<String, Object>>() {
                });
        String upstreamTaskId = AsyncTaskSpecRules.upstreamTaskIdOf(createBody, spec);
        if (StringUtils.isBlank(upstreamTaskId)) {
            log.error("Async task profile configured but upstream task id is missing, fallback to sync: profile={} path={}",
                    profileCode, spec.taskIdPath());
            return null;
        }
        ModelEntity model = dataService.getModelByCode(target == null ? null : target.targetCode());
        BillingRuleEntity rule = model == null ? null
                : dataService.getActiveBillingRule(model.getBillingRuleCode(), model.getModelFamilyCode());
        String taskCode = newTaskCode();
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
                result.upstreamUrl());
        AsyncTaskDraftDto task = build(new AsyncTaskCreation(
                taskCode, profileCode, spec, context.protocol(),
                exchange == null ? Map.of() : exchange.requestBody(),
                taskContext, upstreamTaskId,
                rule == null ? null : rule.getCode(), rule == null ? null : rule.getVersionCode(),
                LocalDateTime.now(), resolveCallbackUrl(exchange)));
        taskService.insert(task);
        // ⭐ 提交那条流量事件要带 task 标记（它是 1 + N 里的那个 1），但「事件长什么样」是
        // 本包的事，不是请求链路的事。写在这里而不是 GatewayRequestHandler 里，
        // 是为了让同步链路对事件格式零感知——它只负责「异步就回 202」。
        context.putAttribute(GatewayTrafficMsgDto.TaskTrace.ATTRIBUTE,
                GatewayTrafficMsgDto.TaskTrace.submit(taskCode));
        return AsyncTaskAcceptedResponse.queued(taskCode);
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
