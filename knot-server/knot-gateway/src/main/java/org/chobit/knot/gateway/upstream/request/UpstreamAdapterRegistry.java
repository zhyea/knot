package org.chobit.knot.gateway.upstream.request;

import lombok.RequiredArgsConstructor;
import org.chobit.knot.adapter.protocol.PassthroughUpstreamAdapter;
import org.chobit.knot.adapter.protocol.UpstreamAdapterCatalog;
import org.chobit.knot.adapter.protocol.UpstreamAdapter;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class UpstreamAdapterRegistry {

    private final UpstreamAdapterCatalog upstreamAdapterCatalog;

    /**
     * 解析上游适配器；绑定未指定（留空）时兜底为透传。
     * 兜底与下拉首项一致：{@link PassthroughUpstreamAdapter} 以 {@code @Order(0)} 排在 {@link UpstreamAdapterCatalog#definitions()} 第一位。
     */
    public UpstreamAdapter resolve(String requestAdapter) {
        UpstreamAdapter adapter = upstreamAdapterCatalog.resolve(requestAdapter);
        if (adapter != null) {
            return adapter;
        }
        adapter = upstreamAdapterCatalog.resolve(PassthroughUpstreamAdapter.CODE);
        if (adapter == null) {
            throw new IllegalStateException("Default upstream adapter not found");
        }
        return adapter;
    }
}
