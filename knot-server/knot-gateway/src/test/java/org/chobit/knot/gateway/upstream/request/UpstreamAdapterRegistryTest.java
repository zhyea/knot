package org.chobit.knot.gateway.upstream.request;

import org.chobit.knot.gateway.adapter.protocol.ChatCompletionsToMessagesUpstreamAdapter;
import org.chobit.knot.gateway.adapter.protocol.MessagesToChatCompletionsUpstreamAdapter;
import org.chobit.knot.gateway.adapter.protocol.PassthroughUpstreamAdapter;
import org.chobit.knot.gateway.adapter.protocol.UpstreamAdapterCatalog;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * 守护「留空 = 透传」契约：绑定未指定请求适配器时运行时必须落到透传。
 */
class UpstreamAdapterRegistryTest {

    private final UpstreamAdapterRegistry registry = new UpstreamAdapterRegistry(
            new UpstreamAdapterCatalog(List.of(
                    new MessagesToChatCompletionsUpstreamAdapter(),
                    new ChatCompletionsToMessagesUpstreamAdapter(),
                    new PassthroughUpstreamAdapter()
            ))
    );

    @Test
    void blankRequestAdapterFallsBackToPassthrough() {
        assertEquals(PassthroughUpstreamAdapter.CODE, registry.resolve(null).code());
        assertEquals(PassthroughUpstreamAdapter.CODE, registry.resolve("").code());
        assertEquals(PassthroughUpstreamAdapter.CODE, registry.resolve("   ").code());
    }

    @Test
    void unknownRequestAdapterFallsBackToPassthrough() {
        assertEquals(PassthroughUpstreamAdapter.CODE, registry.resolve("NOT_EXIST").code());
    }

    @Test
    void explicitRequestAdapterWins() {
        assertEquals(MessagesToChatCompletionsUpstreamAdapter.CODE,
                registry.resolve("MESSAGES_TO_CHAT_COMPLETIONS").code());
        assertEquals(ChatCompletionsToMessagesUpstreamAdapter.CODE,
                registry.resolve("chat_completions_to_messages").code());
    }
}
