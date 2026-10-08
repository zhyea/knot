package org.chobit.knot.gateway.adapter.request;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * 守护下拉排序契约：透传（@Order(0)）必须排在所有请求适配器最前。
 */
class RequestAdapterCatalogOrderTest {

    @Test
    void passthroughAdapterIsListedFirst() {
        var catalog = new RequestAdapterCatalog(List.of(
                new OpenAiCompatibleRequestAdapter(),
                new AnthropicRequestAdapter(),
                new PassthroughRequestAdapter()
        ));
        List<RequestAdapterDefinition> defs = catalog.definitions();
        assertEquals(PassthroughRequestAdapter.CODE, defs.get(0).code(),
                "透传适配器必须排在下拉第一位");
    }
}
