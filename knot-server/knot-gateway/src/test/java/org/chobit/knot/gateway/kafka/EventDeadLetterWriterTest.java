package org.chobit.knot.gateway.kafka;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 死信行格式契约：一条死信必须占一行、tab 分隔恒为 5 段，否则回流时无法按行切分。
 *
 * <p>异常 message 里出现 {@code \t} / {@code \n} 完全正常（上游把响应体塞进异常、
 * 或者 message 本身就是多行）。原实现直接拼进分隔符位置，会把一条死信拆成多行、
 * 打乱后续字段，而现有断言只做 {@code contains} 看不出来。</p>
 */
class EventDeadLetterWriterTest {

    @Test
    @DisplayName("key / cause 含 tab 与换行：仍是一条、仍分 5 段")
    void tabsAndNewlinesStayEscapedOnOneLine(@TempDir Path dir) throws IOException {
        Path file = dir.resolve("dlq.log");
        EventDeadLetterWriter writer = new EventDeadLetterWriter(file.toString());

        writer.write("topic-a", "key\twith\ntab", "{\"a\":1}",
                new IllegalStateException("boom\tbad\nsecond line"));

        List<String> lines = Files.readAllLines(file, StandardCharsets.UTF_8);
        assertEquals(1, lines.size(), "一条死信必须只占一行，否则回流按行解析会错位");

        String[] fields = lines.get(0).split("\t", -1);
        assertEquals(5, fields.length, "字段数恒为 5（时间 / topic / key / cause / payload）");
        assertEquals("key\\twith\\ntab", fields[2], "key 里的 tab 与换行必须转义成可见形式");
        assertTrue(fields[3].contains("boom\\tbad\\nsecond line"), fields[3]);
        assertEquals("{\"a\":1}", fields[4], "payload 是 JSON，原样落盘");
        assertEquals(1L, writer.written());
        assertEquals(0L, writer.lost());
    }
}
