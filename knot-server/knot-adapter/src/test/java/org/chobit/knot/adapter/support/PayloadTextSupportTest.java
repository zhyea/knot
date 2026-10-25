package org.chobit.knot.adapter.support;

import org.chobit.knot.adapter.support.PayloadTextSupport;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * content 折文本（跨协议归一）的契约测试：裸串透传、block 列表按序拼接、
 * 非文本 block 与异形值安全兜底。
 */
class PayloadTextSupportTest {

    @Test
    void textOfShouldPassBareStringThrough() {
        assertEquals("hi", PayloadTextSupport.textOf("hi"));
    }

    @Test
    void textOfShouldConcatenateTextBlocksInOrder() {
        Map<String, Object> first = new LinkedHashMap<>();
        first.put("text", "Hello");
        Map<String, Object> second = new LinkedHashMap<>();
        second.put("text", " world");
        Map<String, Object> nonTextBlock = new LinkedHashMap<>();
        nonTextBlock.put("type", "image");

        // 非文本 block（无 text 键）与异形元素跳过，不影响拼接
        assertEquals("Hello world", PayloadTextSupport.textOf(List.of(first, second, nonTextBlock, "ignored")));
    }

    @Test
    void textOfShouldFallbackForNullNumberAndEmptyList() {
        assertEquals("", PayloadTextSupport.textOf(null));
        assertEquals("42", PayloadTextSupport.textOf(42));
        assertEquals("", PayloadTextSupport.textOf(List.of()));
    }
}
