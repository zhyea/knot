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
 *   <li><b>严格取值（{@link #requireText}）</b>：取不到必抛，只有「创建响应里的上游任务 ID」用它
 *       （2026-11-01 定）。它不是「还没出结果」，而是「这次有没有建成异步任务」的唯一判据——
 *       回 null 会让调用方回退同步，把上游已建任务的响应当成最终结果交出去，钱花了却什么都没记。</li>
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
     * 规模上限就是档案行数 × 6，无需淘汰。</p>
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
     * <p>⭐⭐ <b>为什么不能复用 {@link #text}</b>：{@code text} 的结果要过 {@code scalar}，
     * 而 {@code scalar} 明确把 {@code Map} / {@code Iterable} 判成「取不到」回 {@code null}
     * —— {@code AsyncTaskExpressionSupportTest#structureIsNotAScalar} 钉死了这条行为，不能为了取 usage
     * 去改它，否则 {@code result_path} 填错时就会把整段 JSON 当产物地址交出去。取对象要的正是「结构本身」，
     * 与取标量是两种相反的结果口径。</p>
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
     * 取值（严格）：取不到就是配置或上游契约出了问题，<b>必须抛</b>。
     *
     * <p>⭐⭐ <b>与 {@link #text} 的分工</b>（2026-11-01 robin 定）：{@code text} 服务轮询期读
     * 状态 / 产物 / 失败原因——「这次还没出」是常态，回 {@code null} 由调用方解释成「还没出结果」。
     * 但<b>「创建响应里取上游任务 ID」不属于这一类</b>：它不是「还没出结果」，而是
     * 「这次到底有没有建成异步任务」的唯一判据。取不到就没有东西可轮询，回 {@code null}
     * 会让调用方回退同步、把「任务已创建」的响应当成最终结果交给客户端——
     * 上游的钱已经花了，本地却什么都没记下来，是这个能力里最坏的一种结局。所以这一处必须响。</p>
     *
     * <p>⚠ 编译 / 求值 / 非标量三条分支都不能并成「取不到」：它们分别对应
     * 「表达式写错」「上游少了字段或结构变了」「填的是整包 JSON」，
     * 处置方案完全不同（改档案 / 找上游 / 改表达式），合并后只剩一条无从下手的信息。</p>
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

    /**
     * 取值（多值）：表达式返回标量时得到单元素列表，返回数组时逐个取标量，其余一律空列表。
     *
     * <p>⭐ <b>为什么需要它</b>（2026-10-29）：视频协议一次生成 N 个产物（可灵
     * {@code data.task_result.videos[*].url}、即梦 {@code content.videos[*]}），而
     * {@link #text} 只认标量、遇数组回 {@code null}。让「一条表达式既能表达单产物也能表达多产物」
     * 比再加一列 {@code result_paths} 更省：档案作者只学一种写法。</p>
     *
     * <p>与 {@link #text} 同一套失败口径：语法错抛、取不到回空列表；列表里的非标量元素
     * （嵌套对象）直接跳过，不把整段 JSON 当 URL。</p>
     */
    public static List<String> texts(Map<String, Object> root, String expression) {
        return texts(root, expression, null);
    }

    /**
     * 取值（多值，带元素字段名）：数组元素是对象时，按 {@code itemField} 取每个对象的那个字段。
     *
     * <p>⭐⭐ <b>为什么需要 {@code itemField} 而不是一条纯表达式</b>（2026-10-29 实测）：MVEL 2.5.2
     * 在本项目的求值路径下<b>不支持投影/选择</b>——{@code videos.![url]} 抛
     * {@code could not access: !}、{@code videos.?[url != null]} 抛 {@code could not access: [url}、
     * lambda 与 {@code stream().map(...)} 全部 {@code CompileException}。而视频协议恰好都是
     * 「数组里套对象」（可灵 {@code data.task_result.videos[].url}、即梦 {@code content.videos[].url}），
     * 纯表达式只能取到 {@code [0]}，n>1 的产物会被静默丢掉。</p>
     *
     * <p>所以把它拆成两个<b>都能试跑验证</b>的事实：{@code result_path} 指向数组
     * （{@code data.task_result.videos}），{@code result_url_field} 指定字段名（{@code url}）。
     * 这比在 Java 里猜字段名（尝试 url/video_url/file_url…）可解释得多——猜错的后果是
     * 「产物为空 → RESULT_MISSING → 不计费」。</p>
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
     * 只做编译校验，不求值（管理面保存档案 / 试跑前的静态检查）。
     *
     * <p>⚠ <b>能编译 ≠ 能求值</b>：裸标识符（{@code succeed}）编译通过、求值才抛，判定表达式
     * 返回非布尔也是求值期才发现。所以保存时用它挡语法错，语义错必须靠试跑
     * （{@code AsyncTaskProfileService#dryRun}）暴露。</p>
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
