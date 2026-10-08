package org.chobit.knot.gateway.upstream.request;

import org.chobit.knot.gateway.adapter.request.AnthropicRequestAdapter;
import org.chobit.knot.gateway.adapter.request.OpenAiCompatibleRequestAdapter;
import org.chobit.knot.gateway.adapter.request.PassthroughRequestAdapter;
import org.chobit.knot.gateway.adapter.request.RequestAdapterCatalog;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * 守护「留空 = 透传」契约：绑定未指定请求适配器时运行时必须落到透传，而非 OpenAI Compatible。
 */
class UpstreamRequestAdapterRegistryTest {

    private final UpstreamRequestAdapterRegistry registry = new UpstreamRequestAdapterRegistry(
            new RequestAdapterCatalog(List.of(
                    new OpenAiCompatibleRequestAdapter(),
                    new AnthropicRequestAdapter(),
                    new PassthroughRequestAdapter()
            ))
    );

    @Test
    void blankRequestAdapterFallsBackToPassthrough() {
        assertEquals(PassthroughRequestAdapter.CODE, registry.resolve(null).code());
        assertEquals(PassthroughRequestAdapter.CODE, registry.resolve("").code());
        assertEquals(PassthroughRequestAdapter.CODE, registry.resolve("   ").code());
    }

    @Test
    void unknownRequestAdapterFallsBackToPassthrough() {
        assertEquals(PassthroughRequestAdapter.CODE, registry.resolve("NOT_EXIST").code());
    }

    @Test
    void explicitRequestAdapterWins() {
        assertEquals(OpenAiCompatibleRequestAdapter.CODE, registry.resolve("OPENAI_COMPATIBLE").code());
        assertEquals(AnthropicRequestAdapter.CODE, registry.resolve("anthropic").code());
    }
}
