package org.chobit.knot.gateway.upstream.request;

import lombok.RequiredArgsConstructor;
import org.chobit.knot.gateway.adapter.request.OpenAiCompatibleRequestAdapter;
import org.chobit.knot.gateway.adapter.request.RequestAdapterCatalog;
import org.chobit.knot.gateway.adapter.request.UpstreamRequestAdapter;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class UpstreamRequestAdapterRegistry {

    private final RequestAdapterCatalog requestAdapterCatalog;

    public UpstreamRequestAdapter resolve(String requestAdapter, String providerType) {
        UpstreamRequestAdapter adapter = requestAdapterCatalog.resolve(requestAdapter);
        if (adapter != null) {
            return adapter;
        }
        adapter = requestAdapterCatalog.resolveByProviderType(providerType);
        if (adapter != null) {
            return adapter;
        }
        adapter = requestAdapterCatalog.resolve(OpenAiCompatibleRequestAdapter.CODE);
        if (adapter == null) {
            throw new IllegalStateException("Default request adapter not found");
        }
        return adapter;
    }
}
