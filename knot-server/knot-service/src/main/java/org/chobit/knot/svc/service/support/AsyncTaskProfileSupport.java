package org.chobit.knot.svc.service.support;

import com.fasterxml.jackson.core.type.TypeReference;
import org.apache.commons.lang3.StringUtils;
import org.chobit.knot.common.constants.enums.EnabledStatusEnum;
import org.chobit.knot.common.constants.enums.ModelApiProtocolEnum;
import org.chobit.knot.common.error.BusinessException;
import org.chobit.knot.common.error.ErrorCode;
import org.chobit.knot.common.util.JsonKit;
import org.chobit.knot.svc.dto.async.AsyncTaskProfileDryRunDto;
import org.chobit.knot.svc.dto.async.AsyncTaskProfileSaveDto;
import org.chobit.knot.svc.vo.async.AsyncTaskProfileDryRunVo;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * 异步档案的<b>归一与校验</b>，以及管理面的<b>试跑</b>。
 *
 * <p>⭐⭐ <b>为什么这里必须有校验，而运行时不能只靠它</b>：档案是「配置」，写入只有两条路
 * ——管理面与运维脚本。管理面把住一条，另一条（SQL）把不住，所以运行时仍要有防线
 * （网关遇到停用/协议不匹配的档案回落同步并 {@code log.error}）。但反过来，
 * <b>没有管理面校验</b>时的代价更高：一个把 {@code status} 写成 {@code statuses} 的档案，
 * 现象是「任务永远在轮询」，排查会先怀疑上游。</p>
 *
 * <p>⭐ <b>保存时只挡「静态可判」的错</b>：语法、必填、长度、取值范围。语义错（表达式求值抛异常、
 * 判定返回非布尔、状态文案对不上）静态判不出来，只有 {@link #dryRun} 能暴露——
 * 所以试跑不是锦上添花，它是这套配置唯一的<能看见结果的验证手段>。</p>
 *
 * <p>⚠ <b>试跑复用运行时同一份判定</b>（{@link AsyncTaskTerminalSupport#verdict}）：
 * 若试跑自己写一遍「成功→失败→进行」的顺序，试跑通过而线上判错就是早晚的事。</p>
 */
public final class AsyncTaskProfileSupport {

    /** 档案编码：大写字母开头，允许数字与下划线；绑定表按它引用，必须稳定且可读 */
    private static final Pattern PROFILE_CODE = Pattern.compile("^[A-Z][A-Z0-9_]{0,63}$");

    private static final Set<String> QUERY_METHODS = Set.of("GET", "POST");

    /** 表达式列的宽度（与 {@code kb_async_task_profiles} 一致）：超长在严格模式下会直接被 DB 拒 */
    private static final int PATH_MAX = 255;
    private static final int CONDITION_MAX = 512;

    private AsyncTaskProfileSupport() {
    }

    /**
     * 归一：去首尾空白、协议与方法转大写。
     *
     * <p>⚠ 只做「不改变语义的清洗」。空串<b>折成 null</b> 是有语义的一步：
     * 可选列（{@code status_running} / {@code error_message_path}）用 null 表示「这一档没配」，
     * 而空串进了 MVEL 会被当成空表达式——两种输入必须收敛成一种表示。</p>
     */
    public static AsyncTaskProfileSaveDto normalize(AsyncTaskProfileSaveDto dto) {
        if (dto == null) {
            return null;
        }
        return new AsyncTaskProfileSaveDto(
                StringUtils.trimToNull(dto.profileCode()),
                StringUtils.upperCase(StringUtils.trimToNull(dto.protocol())),
                StringUtils.upperCase(StringUtils.trimToNull(dto.queryMethod())),
                StringUtils.trimToNull(dto.queryPath()),
                StringUtils.trimToNull(dto.taskIdPath()),
                StringUtils.trimToNull(dto.statusPath()),
                StringUtils.trimToNull(dto.statusSucceeded()),
                StringUtils.trimToNull(dto.statusFailed()),
                StringUtils.trimToNull(dto.statusRunning()),
                StringUtils.trimToNull(dto.resultPath()),
                StringUtils.trimToNull(dto.resultUrlField()),
                StringUtils.trimToNull(dto.errorMessagePath()),
                dto.resultExpiresDays(),
                dto.pollIntervalMs(),
                dto.pollBackoffBaseMs(),
                dto.pollBackoffMaxMs(),
                dto.maxAttempts(),
                dto.timeoutMs(),
                dto.status() == null ? EnabledStatusEnum.ENABLED.code() : dto.status(),
                StringUtils.trimToNull(dto.remark()));
    }

    /**
     * 保存前校验（入参需已 {@link #normalize}）。任何一条不满足都抛 400，且文案带字段名与改法。
     */
    public static void validate(AsyncTaskProfileSaveDto dto) {
        require(dto != null, "档案内容不能为空");
        require(PROFILE_CODE.matcher(StringUtils.defaultString(dto.profileCode())).matches(),
                "档案编码必须以大写字母开头、只含大写字母/数字/下划线，最长 64 位：" + dto.profileCode());
        require(ModelApiProtocolEnum.fromCode(dto.protocol()) != null,
                "API 协议编码不合法：" + dto.protocol() + "（可选值见接口协议枚举）");
        require(QUERY_METHODS.contains(dto.queryMethod()),
                "查询方法只支持 GET / POST：" + dto.queryMethod());

        requireNotBlank(dto.queryPath(), "查询路径不能为空");
        require(dto.queryPath() == null || dto.queryPath().startsWith("/"),
                "查询路径必须以 / 开头：" + dto.queryPath());
        require(dto.queryPath() != null && dto.queryPath().contains("{task_id}"),
                "查询路径必须包含 {task_id} 占位，否则每次都在查同一个地址、永远查不到新任务");

        requireNotBlank(dto.taskIdPath(), "任务 ID 表达式（task_id_path）不能为空");
        requireNotBlank(dto.statusPath(), "状态表达式（status_path）不能为空");
        requireNotBlank(dto.statusSucceeded(), "成功判定表达式（status_succeeded）不能为空");
        requireNotBlank(dto.statusFailed(), "失败判定表达式（status_failed）不能为空");
        requireNotBlank(dto.resultPath(), "产物表达式（result_path）不能为空");

        requireLength(dto.queryPath(), PATH_MAX, "查询路径");
        requireLength(dto.taskIdPath(), PATH_MAX, "任务 ID 表达式");
        requireLength(dto.statusPath(), PATH_MAX, "状态表达式");
        requireLength(dto.resultPath(), PATH_MAX, "产物表达式");
        requireLength(dto.errorMessagePath(), PATH_MAX, "失败原因表达式");
        requireLength(dto.resultUrlField(), 64, "产物字段名");
        requireLength(dto.statusSucceeded(), CONDITION_MAX, "成功判定表达式");
        requireLength(dto.statusFailed(), CONDITION_MAX, "失败判定表达式");
        requireLength(dto.statusRunning(), CONDITION_MAX, "进行中判定表达式");
        requireLength(dto.remark(), 255, "备注");

        // 语法错必须在这里挡住：它能编译才算「配置」，否则第一次轮询就抛
        compile(dto.taskIdPath(), "任务 ID 表达式");
        compile(dto.statusPath(), "状态表达式");
        compile(dto.resultPath(), "产物表达式");
        compile(dto.errorMessagePath(), "失败原因表达式");
        compile(dto.statusSucceeded(), "成功判定表达式");
        compile(dto.statusFailed(), "失败判定表达式");
        compile(dto.statusRunning(), "进行中判定表达式");

        requireRange(dto.pollIntervalMs(), 100, 3_600_000, "首次轮询间隔（ms）");
        requireRange(dto.pollBackoffBaseMs(), 100, 3_600_000, "退避基数（ms）");
        requireRange(dto.pollBackoffMaxMs(), 100, 3_600_000, "退避上限（ms）");
        if (dto.pollBackoffBaseMs() != null && dto.pollBackoffMaxMs() != null
                && dto.pollBackoffMaxMs() < dto.pollBackoffBaseMs()) {
            throw invalid("退避上限（" + dto.pollBackoffMaxMs() + "ms）不能小于退避基数（"
                    + dto.pollBackoffBaseMs() + "ms）");
        }
        requireRange(dto.maxAttempts(), 1, 10_000, "轮询次数上限");
        requireRange(dto.resultExpiresDays(), 1, 3_650, "产物有效期（天）");
        if (dto.timeoutMs() != null && (dto.timeoutMs() < 1_000L || dto.timeoutMs() > 86_400_000L)) {
            throw invalid("任务超时必须在 1s ~ 24h 之间：" + dto.timeoutMs() + "ms");
        }
        EnabledStatusEnum.requireCode(dto.status(), "无效的启用状态：" + dto.status());
    }

    /**
     * 试跑：按档案的表达式读两段真实响应，把「取到什么、判成什么」摊开。
     *
     * <p>⚠ 这里<b>不抛业务异常</b>（除入参缺失）：试跑的价值就是「错了也要看得见」。
     * 逐条表达式包裹 try/catch 收集成 {@code errors}，让运维一次看到全部问题，
     * 而不是改一条、跑一次、再冒出下一条。</p>
     */
    public static AsyncTaskProfileDryRunVo dryRun(AsyncTaskProfileDryRunDto dto) {
        require(dto != null && dto.profile() != null, "试跑需要档案内容");
        AsyncTaskProfileSaveDto profile = dto.profile();

        List<String> errors = new ArrayList<>();
        List<String> warnings = new ArrayList<>();

        Map<String, Object> createBody = parseBody(dto.createResponseJson(), "创建响应", errors);
        Map<String, Object> queryBody = parseBody(dto.queryResponseJson(), "查询响应", errors);

        String upstreamTaskId = createBody == null
                ? null
                : readText(createBody, profile.taskIdPath(), "任务 ID 表达式（task_id_path）", errors);

        String status = null;
        String terminal = null;
        List<String> resultUrls = List.of();
        String errorMessage = null;

        if (queryBody != null) {
            status = readText(queryBody, profile.statusPath(), "状态表达式（status_path）", errors);
            if (status == null) {
                if (errors.isEmpty()) {
                    warnings.add("按 status_path 没取到状态文本：运行时会按「仍在进行」处理（不判死），"
                            + "连续取不到会一直轮询到超时");
                }
            } else {
                Map<String, Object> vars = Map.of("status", status, "body", queryBody);
                boolean succeeded = testQuietly(vars, profile.statusSucceeded(), "成功判定表达式（status_succeeded）", errors);
                boolean failed = testQuietly(vars, profile.statusFailed(), "失败判定表达式（status_failed）", errors);
                boolean running = StringUtils.isBlank(profile.statusRunning())
                        || testQuietly(vars, profile.statusRunning(), "进行中判定表达式（status_running）", errors);
                if (errors.isEmpty()) {
                    AsyncTaskTerminalSupport.Verdict verdict = AsyncTaskTerminalSupport.verdict(queryBody, expressions(profile));
                    terminal = verdict.terminal().name();
                    resultUrls = verdict.resultUrls();
                    errorMessage = verdict.errorMessage();
                    if (verdict.terminal() == AsyncTaskTerminalSupport.Terminal.RUNNING && !succeeded && !failed && running) {
                        warnings.add(StringUtils.isBlank(profile.statusRunning())
                                ? "状态「" + status + "」既不匹配成功也不匹配失败，且未配运行态 → 运行时按「进行中」继续轮询"
                                : "状态「" + status + "」命中了运行态表达式；若这是终态，请检查成功/失败表达式");
                    }
                    if (verdict.terminal() == AsyncTaskTerminalSupport.Terminal.SUCCEEDED && resultUrls.isEmpty()) {
                        warnings.add("判定为成功，但 result_path 没取到产物：运行时会落 RESULT_MISSING（按失败结算），"
                                + "这笔调用将不计费");
                    }
                }
            }
        }

        return new AsyncTaskProfileDryRunVo(upstreamTaskId, status, terminal, resultUrls, errorMessage,
                hintOf(terminal, resultUrls, errorMessage), List.copyOf(errors), List.copyOf(warnings));
    }

    // ───────────────────────── 内部 ─────────────────────────

    private static AsyncTaskExpressions expressions(AsyncTaskProfileSaveDto profile) {
        return new AsyncTaskExpressions(profile.statusPath(), profile.statusSucceeded(),
                profile.statusFailed(), profile.statusRunning(), profile.resultPath(),
                profile.resultUrlField(), profile.errorMessagePath());
    }

    private static String hintOf(String terminal, List<String> resultUrls, String errorMessage) {
        if (terminal == null) {
            return "未能判定：请确认查询响应是一段真实的上游返回，并检查 status_path";
        }
        return switch (terminal) {
            case "SUCCEEDED" -> "命中成功，" + resultUrls.size() + " 个产物";
            case "FAILED" -> errorMessage == null ? "命中失败（未取到失败原因）" : "命中失败：" + errorMessage;
            default -> "判定为进行中：运行时会按退避继续轮询";
        };
    }

    private static Map<String, Object> parseBody(String json, String label, List<String> errors) {
        if (StringUtils.isBlank(json)) {
            return null;
        }
        try {
            Map<String, Object> body = JsonKit.fromJson(json, new TypeReference<>() {
            });
            if (body == null) {
                errors.add(label + "解析结果为空，请贴完整的 JSON 对象");
            }
            return body;
        } catch (RuntimeException e) {
            errors.add(label + "不是合法的 JSON 对象：" + e.getMessage());
            return null;
        }
    }

    private static String readText(Map<String, Object> body, String expression, String label, List<String> errors) {
        if (StringUtils.isBlank(expression)) {
            errors.add(label + "为空，无法取值");
            return null;
        }
        try {
            return AsyncTaskExpressionSupport.text(body, expression);
        } catch (RuntimeException e) {
            errors.add(label + "求值失败：" + e.getMessage());
            return null;
        }
    }

    /**
     * 判定表达式试算：只回答「这条表达式能不能用」，不回答「算成功还是失败」。
     *
     * <p>返回值为 {@code false} 可能是「不成立」也可能是「出错」，两者由 {@code errors} 区分——
     * 调用方只在 {@code errors} 为空时使用结果。</p>
     */
    private static boolean testQuietly(Map<String, Object> vars, String expression, String label, List<String> errors) {
        if (StringUtils.isBlank(expression)) {
            return false;
        }
        try {
            return AsyncTaskExpressionSupport.test(vars, expression);
        } catch (RuntimeException e) {
            errors.add(label + "求值失败：" + e.getMessage());
            return false;
        }
    }

    private static void compile(String expression, String label) {
        if (StringUtils.isBlank(expression)) {
            return;
        }
        try {
            AsyncTaskExpressionSupport.validate(expression);
        } catch (RuntimeException e) {
            throw invalid(label + "无法编译：" + e.getMessage() + "（MVEL 没有 in (...) 语法，多值请写 ['a','b'] contains status）");
        }
    }

    private static void requireLength(String value, int max, String label) {
        if (value != null && value.length() > max) {
            throw invalid(label + "超长：" + value.length() + " > " + max + " 字符（DB 列宽）");
        }
    }

    private static void requireRange(Integer value, int min, int max, String label) {
        if (value == null) {
            return;
        }
        if (value < min || value > max) {
            throw invalid(label + "必须在 " + min + " ~ " + max + " 之间：" + value);
        }
    }

    private static void requireNotBlank(String value, String message) {
        require(StringUtils.isNotBlank(value), message);
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw invalid(message);
        }
    }

    private static BusinessException invalid(String message) {
        return new BusinessException(ErrorCode.VALIDATION_ERROR, message);
    }
}
