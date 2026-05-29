package org.chobit.knot.gateway.service.upstream;

import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class UpstreamProviderAdapterRegistry {

    private final List<UpstreamProviderAdapter> adapters;
    private final OpenAiCompatibleProviderAdapter fallbackAdapter;

    public UpstreamProviderAdapterRegistry(List<UpstreamProviderAdapter> adapters,
                                           OpenAiCompatibleProviderAdapter fallbackAdapter) {
        this.adapters = adapters;
        this.fallbackAdapter = fallbackAdapter;
    }

    public UpstreamProviderAdapter resolve(String providerType) {
        return adapters.stream()
                .filter(adapter -> adapter.supports(providerType))
                .findFirst()
                .orElse(fallbackAdapter);
    }
}
