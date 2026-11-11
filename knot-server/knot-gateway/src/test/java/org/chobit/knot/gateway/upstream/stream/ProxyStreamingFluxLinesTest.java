package org.chobit.knot.gateway.upstream.stream;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DataBufferFactory;
import org.springframework.core.io.buffer.DefaultDataBufferFactory;
import reactor.core.publisher.Flux;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@code lines(...)} 的<b>长度保护</b>：超限必须报错，且不能先构造上兆的字符串。
 *
 * <p>修复前的判据只在「整个 chunk 完全没有换行」时生效：一个含换行、但单行远超上限的 chunk
 * 会直接走 {@code new String(combined, 0, lastBreak)} + {@code split}，长度检查被绕过，
 * 畸形上游（或异常代理一次性吐大块）就能把堆推上去并制造海量行对象。</p>
 *
 * <p>第三条用例是防回归对照：分块与半行拼接的正常语义不能被长度校验改掉。</p>
 */
class ProxyStreamingFluxLinesTest {

    /** 与 {@code ProxyStreamingFlux#MAX_LINE_BYTES} 同源（1 MiB）；private 常量不对外暴露，此处按字面量对照。 */
    private static final int MAX_LINE_BYTES = 1 << 20;

    private static final DataBufferFactory FACTORY = new DefaultDataBufferFactory();

    @Test
    @DisplayName("含换行但单行超限：直接报错，不构造大字符串")
    void rejectsOversizedLineEvenWhenChunkContainsNewline() {
        byte[] payload = oversizedLineWithNewline();

        IllegalStateException error = assertThrows(IllegalStateException.class,
                () -> ProxyStreamingFlux.lines(Flux.<DataBuffer>just(FACTORY.wrap(payload))).blockLast(),
                "单行超限必须失败——修复前这条路径会先构造完整字符串再 split，长度保护形同虚设");

        assertTrue(error.getMessage().contains("exceeds"), "错误文本要能直接看出是行长超限");
    }

    @Test
    @DisplayName("残行（无换行尾巴）超限：同样报错")
    void rejectsOversizedTailWithoutNewline() {
        byte[] payload = new byte[MAX_LINE_BYTES + 16];
        Arrays.fill(payload, (byte) 'a');

        assertThrows(IllegalStateException.class,
                () -> ProxyStreamingFlux.lines(Flux.<DataBuffer>just(FACTORY.wrap(payload))).blockLast());
    }

    @Test
    @DisplayName("对照：分块与半行拼接的语义不变")
    void keepsChunkingSemantics() {
        Flux<DataBuffer> body = Flux.<DataBuffer>just(
                FACTORY.wrap("data: a\n".getBytes(StandardCharsets.UTF_8)),
                FACTORY.wrap("da".getBytes(StandardCharsets.UTF_8)),
                FACTORY.wrap("ta: b\n".getBytes(StandardCharsets.UTF_8)));

        List<String> lines = ProxyStreamingFlux.lines(body)
                .filter(line -> !line.isEmpty())
                .collectList()
                .block();

        assertEquals(List.of("data: a", "data: b"), lines, "跨 chunk 的半行必须拼回完整行");
    }

    private static byte[] oversizedLineWithNewline() {
        byte[] prefix = new byte[MAX_LINE_BYTES + 1];
        Arrays.fill(prefix, (byte) 'a');
        byte[] suffix = "\nshort line\n".getBytes(StandardCharsets.UTF_8);
        byte[] payload = new byte[prefix.length + suffix.length];
        System.arraycopy(prefix, 0, payload, 0, prefix.length);
        System.arraycopy(suffix, 0, payload, prefix.length, suffix.length);
        return payload;
    }
}
