package org.chobit.knot.gateway.upstream.protocol;

import org.chobit.knot.gateway.constants.enums.ModelApiProtocolEnum;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Decisions 协议的执行器归属：与 rerank / moderations 同属「非流式整包」工具类。
 *
 * <p>这里钉死两件事：DECISIONS 由 {@link UtilityProtocolExecutor} 承接（走 buffered 而非流式），
 * 且不会串到文本生成执行器（该执行器会在请求体带 {@code stream} 时走 SSE 转发）。</p>
 */
class UtilityProtocolExecutorTest {

    @Test
    void supportsDecisionsAlongsideRerankAndModerations() {
        UtilityProtocolExecutor executor = new UtilityProtocolExecutor(null, null, null, null);

        assertTrue(executor.supports(ModelApiProtocolEnum.DECISIONS));
        assertTrue(executor.supports(ModelApiProtocolEnum.RERANK));
        assertTrue(executor.supports(ModelApiProtocolEnum.MODERATIONS));
        assertFalse(executor.supports(ModelApiProtocolEnum.CHAT_COMPLETIONS));
        assertFalse(executor.supports(null));
    }

    @Test
    void registryResolvesDecisionsToUtilityExecutor() {
        UpstreamProtocolExecutorRegistry registry =
                new UpstreamProtocolExecutorRegistry(List.of(new UtilityProtocolExecutor(null, null, null, null)));

        assertTrue(registry.resolve(ModelApiProtocolEnum.DECISIONS) instanceof UtilityProtocolExecutor,
                "DECISIONS 必须落到整包执行的 utility 执行器，不能落进流式文本执行器");
    }
}
