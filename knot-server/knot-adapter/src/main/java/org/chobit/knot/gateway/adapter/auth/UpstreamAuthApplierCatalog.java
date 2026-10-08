package org.chobit.knot.gateway.adapter.auth;

import org.apache.commons.lang3.StringUtils;
import org.chobit.knot.gateway.constants.enums.ProviderCredentialTypeEnum;
import org.springframework.core.annotation.OrderUtils;
import org.springframework.core.Ordered;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 上游鉴权策略目录：按 code / 类名解析策略，空或未知时回退默认策略（Bearer）。
 * 对齐项目既有的 UsageExtractorCatalog 写法。
 */
@Component
public class UpstreamAuthApplierCatalog {

    private final List<UpstreamAuthApplier> appliers;
    private final Map<String, UpstreamAuthApplier> byCode;
    private final Map<String, UpstreamAuthApplier> byClassName;
    private final UpstreamAuthApplier defaultApplier;

    public UpstreamAuthApplierCatalog(List<UpstreamAuthApplier> appliers) {
        List<UpstreamAuthApplier> sorted = new ArrayList<>(appliers);
        // 排序键尊重各实现的 @Order（BEARER @Order(10) 最小，默认项置顶），同序时再按 code 兜底。
        sorted.sort(Comparator
                .comparingInt((UpstreamAuthApplier a) -> OrderUtils.getOrder(a.getClass(), Ordered.LOWEST_PRECEDENCE))
                .thenComparing(UpstreamAuthApplier::code, String.CASE_INSENSITIVE_ORDER));
        this.appliers = List.copyOf(sorted);
        this.byCode = buildCodeIndex(this.appliers);
        this.byClassName = buildClassIndex(this.appliers);
        this.defaultApplier = require(BearerAuthApplier.CODE);
    }

    public List<UpstreamAuthApplierDefinition> definitions() {
        return appliers.stream()
                .map(item -> new UpstreamAuthApplierDefinition(
                        item.code(),
                        item.label(),
                        item.credentialTypes().stream()
                                .map(ProviderCredentialTypeEnum::code)
                                .sorted()
                                .toList(),
                        item.getClass().getName()
                ))
                .toList();
    }

    public UpstreamAuthApplier defaultApplier() {
        return defaultApplier;
    }

    /**
     * 该 code（策略 code 或类名）是否为已注册策略。用于保存鉴权策略时校验，
     * 避免非法值被 {@link #resolve} 静默回退成默认策略。
     */
    public boolean supports(String codeOrClassName) {
        if (StringUtils.isBlank(codeOrClassName)) {
            return false;
        }
        String trimmed = StringUtils.trim(codeOrClassName);
        return byCode.containsKey(StringUtils.upperCase(trimmed)) || byClassName.containsKey(trimmed);
    }

    /**
     * 该策略是否适用于指定的供应商认证类型（保存时校验，防止下发不匹配的组合）。
     */
    public boolean supports(String codeOrClassName, ProviderCredentialTypeEnum credentialType) {
        if (!supports(codeOrClassName)) {
            return false;
        }
        UpstreamAuthApplier applier = resolve(codeOrClassName);
        return applier != null && (credentialType == null || applier.credentialTypes().contains(credentialType));
    }

    /**
     * 按 code 或类名解析鉴权策略；空或未知回退默认（Bearer），保证存量未配置的行也能工作。
     */
    public UpstreamAuthApplier resolve(String codeOrClassName) {
        if (StringUtils.isBlank(codeOrClassName)) {
            return defaultApplier;
        }
        String trimmed = StringUtils.trim(codeOrClassName);
        UpstreamAuthApplier applier = byCode.get(StringUtils.upperCase(trimmed));
        if (applier != null) {
            return applier;
        }
        applier = byClassName.get(trimmed);
        return applier == null ? defaultApplier : applier;
    }

    private UpstreamAuthApplier require(String code) {
        UpstreamAuthApplier applier = resolve(code);
        if (applier == null) {
            throw new IllegalStateException("UpstreamAuthApplier not found: " + code);
        }
        return applier;
    }

    private Map<String, UpstreamAuthApplier> buildCodeIndex(List<UpstreamAuthApplier> appliers) {
        Map<String, UpstreamAuthApplier> index = new LinkedHashMap<>();
        for (UpstreamAuthApplier applier : appliers) {
            putUnique(index, StringUtils.upperCase(applier.code()), applier, "code");
        }
        return Map.copyOf(index);
    }

    private Map<String, UpstreamAuthApplier> buildClassIndex(List<UpstreamAuthApplier> appliers) {
        Map<String, UpstreamAuthApplier> index = new LinkedHashMap<>();
        for (UpstreamAuthApplier applier : appliers) {
            putUnique(index, applier.getClass().getName(), applier, "class");
            putUnique(index, applier.getClass().getSimpleName(), applier, "class");
        }
        return Map.copyOf(index);
    }

    private void putUnique(Map<String, UpstreamAuthApplier> index,
                           String key,
                           UpstreamAuthApplier applier,
                           String type) {
        UpstreamAuthApplier existing = index.putIfAbsent(key, applier);
        if (existing != null) {
            throw new IllegalStateException("Duplicate UpstreamAuthApplier " + type + ": " + key);
        }
    }
}
