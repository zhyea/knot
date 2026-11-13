package org.chobit.knot.svc.service.support;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.mvel2.MVEL;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 异步任务档案的表达式求值（MVEL）：取值与终态判定两类。
 * {@link #text} 取不到回 {@code null}、语法错抛；{@link #requireText} 取不到必抛（用于创建响应里的上游任务 ID）；{@link #test} 必须返回布尔，否则抛 {@link IllegalStateException}。
 * 表达式取自 {@code kb_async_task_profiles}，只由管理端 / 运维脚本写入；求值时只绑定数据（响应体 Map + {@code status} 字符串），不绑定服务 bean 或类加载器，只写取值与比较。
 */
@Slf4j
public final class AsyncTaskExpressionSupport {

    /**
     * 编译缓存：表达式文本 → 编译结果；key 用表达式原文，改档案自然过期，无需失效逻辑，规模上限为档案行数 × 6。
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
     * 取值（对象）：按表达式从响应体取一个 {@code Map}，供结算读取上游嵌在 {@code data.*} 的 usage。
     *
     * @param root       响应体
     * @param expression 取值表达式，如 {@code data.usage}
     * @return 取到的 {@code Map}；root / 表达式为空、取不到、结果不是 Map（标量 / 数组 / null）时回 {@code null}
     * @throws IllegalStateException 表达式本身有语法错误（配置错，必须响）
     */
    public static Map<String, Object> object(Map<String, Object> root, String expression) {
        if (root == null || StringUtils.isBlank(expression)) {
            return null;
        }
        // ⚠ 编译必须在 try 之外：与 text() 同理，语法错要抛，不能被当成「取不到」吞掉。
        Serializable compiled = compiled(expression);
        Object value;
        try {
            value = MVEL.executeExpression(compiled, root);
        } catch (RuntimeException e) {
            log.debug("Async task expression object read miss: expression={} cause={}", expression, e.toString());
            return null;
        }
        if (value instanceof Map<?, ?> map) {
            @SuppressWarnings("unchecked")
            Map<String, Object> typed = (Map<String, Object>) map;
            return typed;
        }
        return null;
    }

    /**
     * 取值（严格）：取不到就是配置或上游契约出了问题，必须抛。
     *
     * @param root       响应体
     * @param expression 取值表达式（档案列，如 {@code data.id}）
     * @param label      出错时的定位标签（哪个档案列 / 哪一步）
     * @return 非空文本
     * @throws IllegalStateException 表达式为空 / 求值抛错 / 值不是标量（含 {@code null}）
     */
    public static String requireText(Map<String, Object> root, String expression, String label) {
        if (StringUtils.isBlank(expression)) {
            // 档案没配这一列 = 绑定说「这是异步」，但没人告诉程序 ID 在哪，轮询无从谈起
            throw new IllegalStateException(label + "未配置");
        }
        if (root == null) {
            throw new IllegalStateException(label + "无法求值：响应体为空或不是 JSON 对象");
        }
        Serializable compiled = compiled(expression);
        Object value;
        try {
            value = MVEL.executeExpression(compiled, root);
        } catch (RuntimeException e) {
            throw new IllegalStateException(label + "求值失败：【" + expression + "】原因: " + e.getMessage(), e);
        }
        String text = scalar(value);
        if (text == null) {
            throw new IllegalStateException(label + "取不到值：【" + expression + "】实际为 "
                    + describe(value) + "，响应顶层键=" + root.keySet()
                    + "（对照档案列的表达式与上游响应，可用管理端「异步任务档案 → 试跑」验证）");
        }
        return text;
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
                    + "请在管理台把该档案的判定表达式改成布尔表达式（如 ['succeeded','failed'] contains status）。原因: "
                    + e.getMessage(), e);
        }

        if (value instanceof Boolean result) {
            return result;
        }
        throw new IllegalStateException("异步任务档案的判定表达式必须返回布尔值，实际是 "
                + describe(value) + "：表达式=【" + expression + "】");
    }

    /**
     * 编译并缓存；语法错在这里抛（{@code CompileException} → 本类包成 {@link IllegalStateException}）。
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

    /**
     * 取值（多值）：表达式返回标量时得到单元素列表，返回数组时逐个取标量，其余一律空列表。
     * 与 {@link #text} 同一套失败口径：语法错抛、取不到回空列表；列表里的非标量元素（嵌套对象）直接跳过。
     */
    public static List<String> texts(Map<String, Object> root, String expression) {
        return texts(root, expression, null);
    }

    /**
     * 取值（多值，带元素字段名）：数组元素是对象时，按 {@code itemField} 取每个对象的那个字段；
     * {@code result_path} 指向数组（{@code data.task_result.videos}），{@code result_url_field} 指定字段名（{@code url}）。
     */
    public static List<String> texts(Map<String, Object> root, String expression, String itemField) {
        if (root == null || StringUtils.isBlank(expression)) {
            return List.of();
        }
        Serializable compiled = compiled(expression);
        Object value;
        try {
            value = MVEL.executeExpression(compiled, root);
        } catch (RuntimeException e) {
            log.debug("Async task expression read miss: expression={} cause={}", expression, e.toString());
            return List.of();
        }
        if (value instanceof Iterable<?> iterable) {
            List<String> values = new ArrayList<>();
            for (Object item : iterable) {
                String text = item instanceof Map<?, ?> map && StringUtils.isNotBlank(itemField)
                        ? scalar(map.get(itemField))
                        : scalar(item);
                if (text != null) {
                    values.add(text);
                }
            }
            return List.copyOf(values);
        }
        String text = scalar(value);
        return text == null ? List.of() : List.of(text);
    }

    /**
     * 只做编译校验，不求值（管理面保存档案 / 试跑前的静态检查）；能编译 ≠ 能求值，
     * 保存时挡语法错，语义错靠试跑（{@code AsyncTaskProfileService#dryRun}）暴露。
     */
    public static void validate(String expression) {
        if (StringUtils.isNotBlank(expression)) {
            compiled(expression);
        }
    }

    /** 只要标量：结构（Map / List）不是「取到的值」，按取不到处理，避免把整包 JSON 当成 URL */
    private static String scalar(Object value) {
        if (value == null || value instanceof Map<?, ?> || value instanceof Iterable<?>) {
            return null;
        }
        return StringUtils.trimToNull(String.valueOf(value));
    }

    /** 求值结果的人类可读描述（报错用）：区分「没这个字段」与「拿到的是结构」，两者处置不同 */
    private static String describe(Object value) {
        if (value == null) {
            return "null";
        }
        if (value instanceof Map<?, ?>) {
            return "对象(" + value.getClass().getSimpleName() + ")";
        }
        if (value instanceof Iterable<?>) {
            return "数组(" + value.getClass().getSimpleName() + ")";
        }
        return value.getClass().getSimpleName() + "(" + value + ")";
    }
}
