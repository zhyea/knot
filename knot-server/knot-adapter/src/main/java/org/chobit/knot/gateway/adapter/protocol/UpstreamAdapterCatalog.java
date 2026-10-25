package org.chobit.knot.gateway.adapter.protocol;

import org.apache.commons.lang3.StringUtils;
import org.springframework.core.annotation.OrderUtils;
import org.springframework.core.Ordered;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
public class UpstreamAdapterCatalog {

    private final List<UpstreamAdapter> adapters;
    private final Map<String, UpstreamAdapter> adaptersByCode;
    private final Map<String, UpstreamAdapter> adaptersByClassName;

    public UpstreamAdapterCatalog(List<UpstreamAdapter> adapters) {
        List<UpstreamAdapter> sorted = new ArrayList<>(adapters);
        // 排序键尊重各实现的 @Order（透传 @Order(0) 置顶），同序时再按 code 兜底，保证确定性。
        sorted.sort(Comparator
                .comparingInt((UpstreamAdapter a) -> OrderUtils.getOrder(a.getClass(), Ordered.LOWEST_PRECEDENCE))
                .thenComparing(UpstreamAdapter::code, String.CASE_INSENSITIVE_ORDER));
        this.adapters = List.copyOf(sorted);
        this.adaptersByCode = buildCodeIndex(this.adapters);
        this.adaptersByClassName = buildClassIndex(this.adapters);
    }

    public List<UpstreamAdapterDefinition> definitions() {
        return adapters.stream()
                .map(item -> new UpstreamAdapterDefinition(
                        item.code(),
                        item.label(),
                        item.getClass().getName(),
                        item.protocol()
                ))
                .toList();
    }

    public UpstreamAdapter resolve(String codeOrClassName) {
        if (StringUtils.isBlank(codeOrClassName)) {
            return null;
        }
        String trimmed = StringUtils.trim(codeOrClassName);
        UpstreamAdapter byCode = adaptersByCode.get(StringUtils.upperCase(trimmed));
        if (byCode != null) {
            return byCode;
        }
        return adaptersByClassName.get(trimmed);
    }

    private Map<String, UpstreamAdapter> buildCodeIndex(List<UpstreamAdapter> adapters) {
        Map<String, UpstreamAdapter> index = new LinkedHashMap<>();
        for (UpstreamAdapter adapter : adapters) {
            putUnique(index, StringUtils.upperCase(adapter.code()), adapter, "code");
        }
        return Map.copyOf(index);
    }

    private Map<String, UpstreamAdapter> buildClassIndex(List<UpstreamAdapter> adapters) {
        Map<String, UpstreamAdapter> index = new LinkedHashMap<>();
        for (UpstreamAdapter adapter : adapters) {
            putUnique(index, adapter.getClass().getName(), adapter, "class");
            putUnique(index, adapter.getClass().getSimpleName(), adapter, "class");
        }
        return Map.copyOf(index);
    }

    private void putUnique(Map<String, UpstreamAdapter> index,
                           String key,
                           UpstreamAdapter adapter,
                           String type) {
        UpstreamAdapter existing = index.putIfAbsent(key, adapter);
        if (existing != null) {
            throw new IllegalStateException("Duplicate upstream adapter " + type + ": " + key);
        }
    }
}
