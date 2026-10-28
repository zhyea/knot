package org.chobit.knot.gateway.async;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.mvel2.MVEL;

import java.io.Serializable;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 异步任务档案的表达式求值（MVEL）：<b>取值</b>与<b>终态判定</b>两类。
 *
 * <p><b>为什么用 MVEL 而不是自带的 JSON 路径语法</b>（2026-10-28 robin 定，原
 * {@code AsyncTaskJsonPath} 已删）：路径语法只能「下钻」，而真实档案需要的是
 * <b>表达式</b>——终态判定本身就不是「取值相等」，而是「这个状态算不算成功」；
 * 各家还会出现「成功态在 {@code data[0].status}、失败原因在 {@code data[0].error.code}」
 * 这类条件取值。路径语法每加一种写法就要改一次 Java 代码，表达式则只改档案配置。</p>
 *
 * <p><b>两类求值的失败口径不同，这是刻意的</b>：</p>
 * <ul>
 *   <li><b>取值（{@link #text}）</b>：取不到 = {@code null}，由调用方解释成「还没出结果」。
 *       上游响应尚未成形、字段暂时缺失都会走这里——判成 null 比判死任务安全。
 *       语法错误仍然抛（配置错必须响）。</li>
 *   <li><b>判定（{@link #test}）</b>：<b>必须返回布尔</b>，返回别的（字符串 / 数字 / null）
 *       一律抛 {@link IllegalStateException}。这一条是本次改造最要紧的护栏：
 *       旧档案里 {@code succeeded,failed} 这类逗号列表在 MVEL 下是「变量名」，
 *       求值会抛「unresolvable property」；若这里宽容成 false，任务会永远 RUNNING，
 *       直到超时才结单——配置错被伪装成上游慢，是最难查的一类事故。</li>
 * </ul>
 *
 * <p><b>⚠ 表达式来自哪里</b>：{@code kb_async_task_profiles}，只由管理端 / 运维脚本写入，
 * 与 SQL 同权限级别。求值时<b>只绑定数据</b>（响应体 Map + {@code status} 字符串），
 * 不绑定任何服务 bean 或类加载器。表达式里只应写取值与比较，不要写方法调用/构造对象。</p>
 */
@Slf4j
public final class AsyncTaskExpressionSupport {

    /**
     * 编译缓存：表达式文本 → 编译结果。
     *
     * <p>轮询线程每拍都要对每条任务求值若干次，不缓存等于每次都解析一遍字符串。
     * key 用表达式原文，因此改档案（换表达式）自然过期，不存在失效逻辑。
     * 规模上限就是档案行数 × 5，无需淘汰。</p>
     */
    private static final Map<String, Serializable> COMPILED = new ConcurrentHashMap<>();

    private AsyncTaskExpressionSupport() {
    }

    /**
     * 取值：按表达式从响应体里取一个标量。
     *
     * @param root       响应体（{@code Map} —— 其键即表达式里可用的变量）
     * @param expression 取值表达式，如 {@code data[0].outputs[0].url}
     * @return 取到的文本；表达式空 / 取不到 / 值非标量结构时返回 {@code null}
     * @throws IllegalStateException 表达式本身有语法错误（配置错，必须响）
     */
    public static String text(Map<String, Object> root, String expression) {
        if (root == null || StringUtils.isBlank(expression)) {
            return null;
        }
        // ⚠ 编译必须在 try 之外：编译失败抛的也是 RuntimeException，
        // 放进下面那个 catch 就会被当成「取不到」静静吞掉——档案写错和上游没出字段
        // 是两件事，前者必须响。
        Serializable compiled = compiled(expression);
        Object value;
        try {
            value = MVEL.executeExpression(compiled, root);
        } catch (RuntimeException e) {
            // 取不到是常态：上游还没出字段、数组下标越界、上层节点为 null 都会抛。
            // 这里必须回 null 而不是抛——「还没出结果」和「配置写错」在轮询里是两种命运。
            log.debug("Async task expression read miss: expression={} cause={}", expression, e.toString());
            return null;
        }
        return scalar(value);
    }

    /**
     * 判定：按表达式判断是否成立。
     *
     * @param vars       可用变量（{@code status} 状态文本、{@code body} 整个响应体）
     * @param expression 判定表达式，如 {@code status == 'succeeded'}；
     *                   多值写 {@code ['a','b'] contains status}（MVEL 没有 {@code in (...)} 元组语法）
     * @return 是否成立；表达式为空时 {@code false}（表示「这一档没配」）
     * @throws IllegalStateException 语法错，或结果不是布尔（旧逗号列表配置会走到这里）
     */
    public static boolean test(Map<String, Object> vars, String expression) {
        if (StringUtils.isBlank(expression)) {
            return false;
        }
        Serializable compiled = compiled(expression);
        Object value;
        try {
            value = MVEL.executeExpression(compiled, vars);
        } catch (RuntimeException e) {
            throw new IllegalStateException("异步任务档案的判定表达式无法求值: 【" + expression
                    + "】—— 旧版逗号列表（如 succeeded,failed）在 MVEL 下是变量名，"
                    + "请按 docs/database/migration/2026-10-28-async-task-mvel-expressions.sql 迁移。原因: "
                    + e.getMessage(), e);
        }

        if (value instanceof Boolean result) {
            return result;
        }
        throw new IllegalStateException("异步任务档案的判定表达式必须返回布尔值，实际是 "
                + (value == null ? "null" : value.getClass().getSimpleName() + "(" + value + ")")
                + "：表达式=【" + expression + "】");
    }

    /**
     * 编译并缓存。
     *
     * <p>语法错在这里抛（{@code CompileException} → 本类包成 {@link IllegalStateException}），
     * 于是「档案写错」在第一次用到时就炸，而不是求值到一半才发现。</p>
     */
    private static Serializable compiled(String expression) {
        return COMPILED.computeIfAbsent(expression, key -> {
            try {
                return MVEL.compileExpression(key);
            } catch (RuntimeException e) {
                throw new IllegalStateException("异步任务档案的表达式无法编译: 【" + key + "】", e);
            }
        });
    }

    /** 只要标量：结构（Map / List）不是「取到的值」，按取不到处理，避免把整包 JSON 当成 URL */
    private static String scalar(Object value) {
        if (value == null || value instanceof Map<?, ?> || value instanceof Iterable<?>) {
            return null;
        }
        return StringUtils.trimToNull(String.valueOf(value));
    }
}
