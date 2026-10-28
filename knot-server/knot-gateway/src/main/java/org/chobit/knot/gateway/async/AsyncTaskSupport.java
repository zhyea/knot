package org.chobit.knot.gateway.async;

import com.fasterxml.jackson.core.type.TypeReference;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.chobit.knot.common.constants.enums.ModelApiProtocolEnum;
import org.chobit.knot.common.util.JsonKit;
import org.chobit.knot.dal.entity.AsyncTaskEntity;
import org.chobit.knot.dal.entity.AsyncTaskProfileEntity;
import org.chobit.knot.dal.entity.BillingRuleEntity;
import org.chobit.knot.dal.entity.ModelApiBindingEntity;
import org.chobit.knot.dal.entity.ModelEntity;
import org.chobit.knot.dal.mapper.AsyncTaskProfileMapper;
import org.chobit.knot.gateway.kafka.msg.GatewayTrafficMsgDto;
import org.chobit.knot.gateway.model.ProxyResult;
import org.chobit.knot.gateway.runtime.GatewayExchange;
import org.chobit.knot.gateway.runtime.GatewayRequestContext;
import org.chobit.knot.gateway.runtime.GatewayRequestHandler;
import org.chobit.knot.gateway.service.GatewayEntityCacheService;
import org.chobit.knot.svc.dto.routing.RoutingRuleTargetDto;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * 异步任务的创建映射与终态判定：把「数据」翻译成「行为」，不含任何厂商名称。
 *
 * <p>本类是异步能力唯一的厂商知识边界：查询路径、终态取值、产物位置全部来自档案
 * （{@code kb_async_task_profiles}）的配置值，新增一家异步上游 = 加一行档案，<b>不改本类</b>。</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AsyncTaskSupport {

    private final AsyncTaskProfileMapper profileMapper;
    private final GatewayEntityCacheService dataService;
    private final AsyncTaskStore store;

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

    /** 读档案并投影为不可变快照；档案缺失返回 null（由调用方判为「配置不完整」） */
    public AsyncTaskSpec loadSpec(String profileCode) {
        if (StringUtils.isBlank(profileCode)) {
            return null;
        }
        AsyncTaskProfileEntity profile = profileMapper.getByProfileCode(profileCode.trim());
        if (profile == null) {
            log.error("Async task profile not found: {}", profileCode);
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
    public AsyncTaskEntity build(AsyncTaskCreation creation) {
        AsyncTaskSpec spec = creation.spec();
        LocalDateTime now = creation.now();
        AsyncTaskEntity entity = new AsyncTaskEntity();
        entity.setTaskCode(creation.taskCode());
        entity.setProfileCode(creation.profileCode());
        entity.setProtocol(creation.protocol() == null ? null : creation.protocol().code());
        entity.setUpstreamTaskId(creation.upstreamTaskId());
        entity.setStatus(AsyncTaskStatusEnum.PENDING.code());
        entity.setAttempts(0);
        entity.setNextPollAt(now.plusNanos(Math.max(1L, spec.pollIntervalMs()) * 1_000_000L));
        entity.setRequestJson(JsonKit.toJson(creation.requestBody()));
        entity.setPollSpecJson(JsonKit.toJson(spec));
        entity.setContextJson(JsonKit.toJson(creation.context()));
        entity.setBillingRuleCode(creation.billingRuleCode());
        entity.setBillingRuleVersionCode(creation.billingRuleVersionCode());
        entity.setSubmittedAt(now);
        entity.setExpiresAt(now.plusNanos(Math.max(1L, spec.timeoutMs()) * 1_000_000L));
        Integer days = spec.resultExpiresDays();
        entity.setResultExpiresAt(days == null || days <= 0 ? null : now.plusDays(days));
        return entity;
    }

    /** 生成对外任务句柄：32 位小写 hex，与网关 requestId 同形态，不暴露上游 ID */
    public static String newTaskCode() {
        return UUID.randomUUID().toString().replace("-", "");
    }

    /**
     * 判定本次上游结果是否为「异步任务创建成功」，是则落一条待轮询任务并返回 202 信封。
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
     * @return 202 响应信封；非异步任务或配置不完整时为 {@code null}
     */
    public Map<String, Object> submitIfAsync(GatewayRequestContext context,
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
        AsyncTaskSpec spec = loadSpec(profileCode);
        if (spec == null) {
            return null;
        }
        Map<String, Object> createBody = JsonKit.fromJson(result.responseBody(),
                new TypeReference<Map<String, Object>>() {
                });
        String upstreamTaskId = upstreamTaskIdOf(createBody, spec);
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
        AsyncTaskEntity task = build(new AsyncTaskCreation(
                taskCode, profileCode, spec, context.protocol(),
                exchange == null ? Map.of() : exchange.requestBody(),
                taskContext, upstreamTaskId,
                rule == null ? null : rule.getCode(), rule == null ? null : rule.getVersionCode(),
                LocalDateTime.now()));
        store.insert(task);
        // ⭐ 提交那条流量事件要带 task 标记（它是 1 + N 里的那个 1），但「事件长什么样」是
        // 本包的事，不是请求链路的事。写在这里而不是 GatewayRequestHandler 里，
        // 是为了让同步链路对事件格式零感知——它只负责「异步就回 202」。
        context.putAttribute(GatewayTrafficMsgDto.TaskTrace.ATTRIBUTE,
                GatewayTrafficMsgDto.TaskTrace.submit(taskCode));
        Map<String, Object> envelope = new LinkedHashMap<>();
        envelope.put("task_id", taskCode);
        envelope.put("status", "queued");
        envelope.put("query_url", "/openai/v1/tasks/" + taskCode);
        return envelope;
    }

    /**
     * 从创建响应里取上游任务 ID；取不到返回 null。
     *
     * <p>⚠ 用的是档案的 {@code task_id_path}（MVEL 取值表达式），与拼查询 URL 的
     * {@code query_path} 是两条不同的路径（一个读创建响应、一个拼请求地址）。
     * 混用会让「取不到 ID」表现为「查不到任务」，排查方向整个偏掉。</p>
     */
    public String upstreamTaskIdOf(Map<String, Object> createBody, AsyncTaskSpec spec) {
        return AsyncTaskExpressionSupport.text(createBody, spec.taskIdPath());
    }

    /** 查询路径占位替换：委托 {@link AsyncTaskSpec#resolvePath}，保留入口便于单测与调用方统一取用 */
    public String resolveQueryPath(AsyncTaskSpec spec, String upstreamTaskId) {
        return spec.resolvePath(upstreamTaskId);
    }

    /**
     * 终态判定：成功 / 失败 / 仍在进行。
     *
     * <p>判定口径全部来自档案里的三条 MVEL 表达式（{@code status_succeeded} / {@code status_failed} /
     * {@code status_running}），变量是 {@code status}（{@link AsyncTaskSpec#statusPath()} 取到的文本）
     * 与 {@code body}（整个查询响应，供「成功态与失败原因不在同一层」这类档案使用）。</p>
     *
     * <p>顺序即优先级：成功 → 失败 → 进行。<b>取不到状态时按「仍在进行」处理</b>：
     * 上游瞬时抖动、响应体尚未成形都不该把任务判死；连续取不到会被 {@code max_attempts} / 超时兜住。</p>
     */
    public Terminal terminalOf(Map<String, Object> queryBody, AsyncTaskSpec spec) {
        String status = AsyncTaskExpressionSupport.text(queryBody, spec.statusPath());
        if (status == null) {
            return Terminal.RUNNING;
        }
        Map<String, Object> vars = Map.of("status", status, "body", queryBody);
        if (AsyncTaskExpressionSupport.test(vars, spec.statusSucceeded())) {
            return Terminal.SUCCEEDED;
        }
        if (AsyncTaskExpressionSupport.test(vars, spec.statusFailed())) {
            return Terminal.FAILED;
        }
        if (StringUtils.isBlank(spec.statusRunning())) {
            // 没配 = 「除成功/失败外一律非终态」：上游新增中间态（queued 等）不需要改档案
            return Terminal.RUNNING;
        }
        if (AsyncTaskExpressionSupport.test(vars, spec.statusRunning())) {
            return Terminal.RUNNING;
        }
        // 配了运行态却不匹配：上游多了一个谁都不认识的状态。仍然按「进行」处理（不判死），
        // 但必须留下信号——否则这个状态会一直被当成正常轮询，直到超时才显形。
        log.warn("Async task status matched neither terminal nor running expression: status={} profile={}",
                status, spec.statusRunning());
        return Terminal.RUNNING;
    }

    /** 终态产物 URL（MVEL 取值表达式）；取不到返回 null（成功但无产物由调用方按失败处理） */
    public String resultUrlOf(Map<String, Object> queryBody, AsyncTaskSpec spec) {
        return AsyncTaskExpressionSupport.text(queryBody, spec.resultPath());
    }

    /**
     * 下一次轮询的退避时长（毫秒）：指数退避 + 上限。
     *
     * <p>与 {@code BackoffCalculator} 同公式（{@code min(cap, base × 2^(n-1))}），
     * 不复用它，是因为那条链按规则级 {@code RetryPolicy} 建模（含 jitter / Retry-After / 状态码判定），
     * 而轮询的节奏参数来自任务档案，语义是「多久再看一眼」而不是「失败重试」。</p>
     */
    public long nextDelayMs(int attempts, AsyncTaskSpec spec) {
        int exponent = Math.max(0, attempts - 1);
        long delay = (long) (spec.pollBackoffBaseMs() * Math.pow(2, Math.min(exponent, 10)));
        return Math.max(spec.pollIntervalMs(), Math.min(spec.pollBackoffMaxMs(), delay));
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
                                    LocalDateTime now) {
    }

    /** 单次查询的判定结果 */
    public enum Terminal {
        RUNNING, SUCCEEDED, FAILED
    }
}
