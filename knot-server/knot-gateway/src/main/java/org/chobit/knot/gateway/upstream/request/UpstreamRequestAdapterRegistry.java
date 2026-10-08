package org.chobit.knot.gateway.upstream.request;

import lombok.RequiredArgsConstructor;
import org.chobit.knot.gateway.adapter.request.PassthroughRequestAdapter;
import org.chobit.knot.gateway.adapter.request.RequestAdapterCatalog;
import org.chobit.knot.gateway.adapter.request.UpstreamRequestAdapter;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class UpstreamRequestAdapterRegistry {

    private final RequestAdapterCatalog requestAdapterCatalog;

    /**
     * 解析上游请求适配器；绑定未指定（留空）时兜底为透传。
     *
     * <p>兜底与下拉首项一致（{@link PassthroughRequestAdapter} 以 @Order(0) 排在
     * {@link RequestAdapterCatalog#definitions()} 第一位），保证「留空 = 透传」在
     * 界面与运行时是同一条语义。</p>
     */
    public UpstreamRequestAdapter resolve(String requestAdapter) {
        UpstreamRequestAdapter adapter = requestAdapterCatalog.resolve(requestAdapter);
        if (adapter != null) {
            return adapter;
        }
        adapter = requestAdapterCatalog.resolve(PassthroughRequestAdapter.CODE);
        if (adapter == null) {
            throw new IllegalStateException("Default request adapter not found");
        }
        return adapter;
    }
}
