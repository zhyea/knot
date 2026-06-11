package org.chobit.knot.gateway.usage;

import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
public class UsageExtractorCatalog {

    private final List<UsageExtractor> extractors;
    private final Map<String, UsageExtractor> extractorsByCode;
    private final Map<String, UsageExtractor> extractorsByClassName;
    private final UsageExtractor defaultExtractor;

    public UsageExtractorCatalog(List<UsageExtractor> extractors) {
        List<UsageExtractor> sorted = new ArrayList<>(extractors);
        sorted.sort(Comparator.comparing(UsageExtractor::code, String.CASE_INSENSITIVE_ORDER));
        this.extractors = List.copyOf(sorted);
        this.extractorsByCode = buildCodeIndex(this.extractors);
        this.extractorsByClassName = buildClassIndex(this.extractors);
        this.defaultExtractor = requireExtractor(DefaultUsageExtractor.CODE);
    }

    public List<UsageExtractor> extractors() {
        return extractors;
    }

    public UsageExtractor defaultExtractor() {
        return defaultExtractor;
    }

    public List<UsageExtractorDefinition> definitions() {
        return extractors.stream()
                .map(item -> new UsageExtractorDefinition(
                        item.code(),
                        item.label(),
                        item.getClass().getName(),
                        item.streamSupported()
                ))
                .toList();
    }

    public UsageExtractor resolve(String codeOrClassName) {
        if (StringUtils.isBlank(codeOrClassName)) {
            return null;
        }
        String trimmed = StringUtils.trim(codeOrClassName);
        UsageExtractor byCode = extractorsByCode.get(StringUtils.upperCase(trimmed));
        if (byCode != null) {
            return byCode;
        }
        return extractorsByClassName.get(trimmed);
    }

    private UsageExtractor requireExtractor(String code) {
        UsageExtractor extractor = resolve(code);
        if (extractor == null) {
            throw new IllegalStateException("UsageExtractor not found: " + code);
        }
        return extractor;
    }

    private Map<String, UsageExtractor> buildCodeIndex(List<UsageExtractor> extractors) {
        Map<String, UsageExtractor> index = new LinkedHashMap<>();
        for (UsageExtractor extractor : extractors) {
            putUnique(index, StringUtils.upperCase(extractor.code()), extractor, "code");
        }
        return Map.copyOf(index);
    }

    private Map<String, UsageExtractor> buildClassIndex(List<UsageExtractor> extractors) {
        Map<String, UsageExtractor> index = new LinkedHashMap<>();
        for (UsageExtractor extractor : extractors) {
            putUnique(index, extractor.getClass().getName(), extractor, "class");
            putUnique(index, extractor.getClass().getSimpleName(), extractor, "class");
        }
        return Map.copyOf(index);
    }

    private void putUnique(Map<String, UsageExtractor> index,
                           String key,
                           UsageExtractor extractor,
                           String type) {
        UsageExtractor existing = index.putIfAbsent(key, extractor);
        if (existing != null) {
            throw new IllegalStateException("Duplicate UsageExtractor " + type + ": " + key);
        }
    }
}
