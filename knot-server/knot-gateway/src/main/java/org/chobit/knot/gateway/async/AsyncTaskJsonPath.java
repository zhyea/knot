package org.chobit.knot.gateway.async;

import org.apache.commons.lang3.StringUtils;

import java.util.List;
import java.util.Map;

/**
 * 异步任务响应取值：按档案声明的 JSON 路径取一个标量（{@code data.id} / {@code data[0].status}）。
 *
 * <p><b>为什么只支持这么点语法</b>：路径是给运维在管理端填的配置，不是表达式引擎。
 * 需要的能力只有两个——「取对象字段」与「取数组第 n 项」，两者足以覆盖已知厂商
 * （可灵 {@code data[0].outputs[0].url}、MiniMax {@code content.url}）。
 * 引入完整 JSONPath 会让「填错路径」从启动即失败变成运行期静默。</p>
 *
 * <p>取不到一律返回 {@code null}，由调用方决定是「还没出结果」还是「配置填错」。
 * 本类不做兜底，也不猜字段名。</p>
 */
public final class AsyncTaskJsonPath {

    private AsyncTaskJsonPath() {
    }

    /**
     * 按路径取文本；取不到、非标量、空白一律返回 {@code null}。
     */
    public static String text(Object root, String path) {
        Object value = read(root, path);
        if (value == null) {
            return null;
        }
        return StringUtils.trimToNull(String.valueOf(value));
    }

    /**
     * 按路径取节点：逐段下钻，段名支持 {@code name} 与 {@code name[index]} 两种形态。
     */
    public static Object read(Object root, String path) {
        if (root == null || StringUtils.isBlank(path)) {
            return null;
        }
        Object current = root;
        for (String segment : StringUtils.split(path.trim(), '.')) {
            current = step(current, StringUtils.trimToEmpty(segment));
            if (current == null) {
                return null;
            }
        }
        return current;
    }

    private static Object step(Object current, String segment) {
        if (current == null || segment.isEmpty()) {
            return null;
        }
        int bracket = segment.indexOf('[');
        if (bracket < 0) {
            return field(current, segment);
        }
        String name = segment.substring(0, bracket);
        Object node = name.isEmpty() ? current : field(current, name);
        int cursor = bracket;
        while (node != null && cursor >= 0 && cursor < segment.length()) {
            int end = segment.indexOf(']', cursor);
            if (end < 0) {
                return null;
            }
            int index = parseInt(segment.substring(cursor + 1, end));
            if (index < 0) {
                return null;
            }
            node = element(node, index);
            cursor = segment.indexOf('[', end);
        }
        return node;
    }

    private static Object field(Object current, String name) {
        if (current instanceof Map<?, ?> map) {
            return map.get(name);
        }
        return null;
    }

    private static Object element(Object current, int index) {
        if (current instanceof List<?> list) {
            return index < list.size() ? list.get(index) : null;
        }
        return null;
    }

    private static int parseInt(String value) {
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException ignored) {
            return -1;
        }
    }
}
