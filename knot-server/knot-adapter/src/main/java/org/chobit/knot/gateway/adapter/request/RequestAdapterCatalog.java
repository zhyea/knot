package org.chobit.knot.gateway.adapter.request;

import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
public class RequestAdapterCatalog {

    private final List<UpstreamRequestAdapter> adapters;
    private final Map<String, UpstreamRequestAdapter> adaptersByCode;
    private final Map<String, UpstreamRequestAdapter> adaptersByClassName;

    public RequestAdapterCatalog(List<UpstreamRequestAdapter> adapters) {
        List<UpstreamRequestAdapter> sorted = new ArrayList<>(adapters);
        sorted.sort(Comparator.comparing(UpstreamRequestAdapter::code, String.CASE_INSENSITIVE_ORDER));
        this.adapters = List.copyOf(sorted);
        this.adaptersByCode = buildCodeIndex(this.adapters);
        this.adaptersByClassName = buildClassIndex(this.adapters);
    }

    public List<RequestAdapterDefinition> definitions() {
        return adapters.stream()
                .map(item -> new RequestAdapterDefinition(
                        item.code(),
                        item.label(),
                        item.getClass().getName()
                ))
                .toList();
    }

    public UpstreamRequestAdapter resolve(String codeOrClassName) {
        if (StringUtils.isBlank(codeOrClassName)) {
            return null;
        }
        String trimmed = StringUtils.trim(codeOrClassName);
        UpstreamRequestAdapter byCode = adaptersByCode.get(StringUtils.upperCase(trimmed));
        if (byCode != null) {
            return byCode;
        }
        return adaptersByClassName.get(trimmed);
    }

    public UpstreamRequestAdapter resolveByProviderType(String providerType) {
        return adapters.stream()
                .filter(adapter -> adapter.supports(providerType))
                .findFirst()
                .orElse(null);
    }

    private Map<String, UpstreamRequestAdapter> buildCodeIndex(List<UpstreamRequestAdapter> adapters) {
        Map<String, UpstreamRequestAdapter> index = new LinkedHashMap<>();
        for (UpstreamRequestAdapter adapter : adapters) {
            putUnique(index, StringUtils.upperCase(adapter.code()), adapter, "code");
        }
        return Map.copyOf(index);
    }

    private Map<String, UpstreamRequestAdapter> buildClassIndex(List<UpstreamRequestAdapter> adapters) {
        Map<String, UpstreamRequestAdapter> index = new LinkedHashMap<>();
        for (UpstreamRequestAdapter adapter : adapters) {
            putUnique(index, adapter.getClass().getName(), adapter, "class");
            putUnique(index, adapter.getClass().getSimpleName(), adapter, "class");
        }
        return Map.copyOf(index);
    }

    private void putUnique(Map<String, UpstreamRequestAdapter> index,
                           String key,
                           UpstreamRequestAdapter adapter,
                           String type) {
        UpstreamRequestAdapter existing = index.putIfAbsent(key, adapter);
        if (existing != null) {
            throw new IllegalStateException("Duplicate request adapter " + type + ": " + key);
        }
    }
}
