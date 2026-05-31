package org.chobit.knot.gateway.upstream;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
public class UpstreamProviderAdapterRegistry {

    private final List<UpstreamProviderAdapter> adapters;
    private final OpenAiCompatibleProviderAdapter fallbackAdapter;

    /**
     * Resolves the requested value from current context and configuration. Executes the public operation.
     */
    public UpstreamProviderAdapter resolve(String providerType) {
        return adapters.stream()
                .filter(adapter -> adapter.supports(providerType))
                .findFirst()
                .orElse(fallbackAdapter);
    }
}
