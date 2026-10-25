package org.chobit.knot.gateway.adapter.protocol;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * 守护下拉排序契约：透传（@Order(0)）必须排在所有上游适配器最前。
 */
class UpstreamAdapterCatalogOrderTest {

    @Test
    void passthroughAdapterIsListedFirst() {
        var catalog = new UpstreamAdapterCatalog(List.of(
                new MessagesToChatCompletionsUpstreamAdapter(),
                new ChatCompletionsToMessagesUpstreamAdapter(),
                new PassthroughUpstreamAdapter()
        ));
        List<UpstreamAdapterDefinition> defs = catalog.definitions();
        assertEquals(PassthroughUpstreamAdapter.CODE, defs.get(0).code(),
                "透传适配器必须排在下拉第一位");
    }
}
