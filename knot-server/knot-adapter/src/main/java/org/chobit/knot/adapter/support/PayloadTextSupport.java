package org.chobit.knot.adapter.support;

import java.util.List;
import java.util.Map;

/**
 * content 折文本的跨协议归一原语：既吃 OpenAI 的裸字符串，也吃 Anthropic / Converse 的
 * {@code [{text: ...}]} block 列表，其余形状按 {@code toString} 兜底。
 *
 * <p>归属判据：{@code textOf} 归一的是 OpenAI / Anthropic / Converse 三种协议共有的 content
 * 形状，无供应商语义——曾作为私有辅助住在 {@code AwsBedrockConverseSupport}，并导致
 * {@code AwsBedrockMessagesToConverseUpstreamAdapter} 复制了一份逐字相同的私有副本，
 * 2026-10-25 拆出（判例：**跨类逐字重复是「通用能力住了供应商专用类」的直接后果**，
 * 见到重复实现先怀疑归属，而不是先合并）。
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
