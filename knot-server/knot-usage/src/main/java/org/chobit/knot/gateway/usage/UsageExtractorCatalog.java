package org.chobit.knot.gateway.usage;

import org.apache.commons.lang3.StringUtils;

import java.util.List;

public final class UsageExtractorCatalog {

    private static final List<UsageExtractor> EXTRACTORS = List.of(
            new DefaultUsageExtractor(),
            new AnthropicUsageExtractor()
    );

    private UsageExtractorCatalog() {
    }

    public static List<UsageExtractor> extractors() {
        return EXTRACTORS;
    }

    public static UsageExtractor defaultExtractor() {
        return resolve(DefaultUsageExtractor.CODE);
    }

    public static List<UsageExtractorDefinition> definitions() {
        return EXTRACTORS.stream()
                .map(item -> new UsageExtractorDefinition(
                        item.code(),
                        item.label(),
                        item.getClass().getName(),
                        item.streamSupported()
                ))
                .toList();
    }

    public static UsageExtractor resolve(String codeOrClassName) {
        if (StringUtils.isBlank(codeOrClassName)) {
            return null;
        }
        String normalized = StringUtils.upperCase(StringUtils.trim(codeOrClassName));
        return EXTRACTORS.stream()
                .filter(item -> normalized.equals(StringUtils.upperCase(item.code()))
                        || item.getClass().getName().equals(codeOrClassName)
                        || item.getClass().getSimpleName().equals(codeOrClassName))
                .findFirst()
                .orElse(null);
    }
}
