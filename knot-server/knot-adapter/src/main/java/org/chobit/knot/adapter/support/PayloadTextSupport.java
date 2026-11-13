package org.chobit.knot.adapter.support;

import java.util.List;
import java.util.Map;

/**
 * content 折文本的跨协议归一原语：既吃 OpenAI 的裸字符串，也吃 Anthropic / Converse 的
 * {@code [{text: ...}]} block 列表，其余形状按 {@code toString} 兜底。
 */
public final class PayloadTextSupport {

    private static final String TEXT = "text";

    private PayloadTextSupport() {
    }

    /**
     * content → 裸文本：裸串原样返回；block 列表按序拼接各 block 的 {@code text}；
     * 其余形状 {@code String.valueOf}；{@code null} → 空串。
     */
    public static String textOf(Object content) {
        if (content == null) {
            return "";
        }
        if (content instanceof String value) {
            return value;
        }
        if (content instanceof List<?> list) {
            StringBuilder builder = new StringBuilder();
            for (Object item : list) {
                if (item instanceof Map<?, ?> raw) {
                    Object text = PayloadMapSupport.cast(raw).get(TEXT);
                    if (text != null) {
                        builder.append(text);
                    }
                }
            }
            return builder.toString();
        }
        return String.valueOf(content);
    }
}
