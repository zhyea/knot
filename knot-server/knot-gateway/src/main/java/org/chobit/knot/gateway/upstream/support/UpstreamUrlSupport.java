package org.chobit.knot.gateway.upstream.support;

import org.apache.commons.lang3.StringUtils;

import java.util.ArrayList;
import java.util.List;

/**
 * 上游 URL 拼接：把「账户 base URL + 模型 base_path + 协议路径」组合成最终请求地址。
 * 两个纯函数各管一件事：{@link #joinPath} 做模型级前缀（{@code kb_models.base_path}）与协议路径的斜杠拼接；{@link #joinUrl} 做绝对 base URL 与路径的拼接，并消除「base 尾段与 path 首段重复」。
 * 重复按「完整路径段」逐段比较后吸收重叠，段相等才算重复（{@code /v10} 与 {@code /v1} 不会互相误判）。
 * 禁止对本类做 URI 编解码或规范化：Bedrock 的路径里含已编码的 {@code %3A}，{@code URI.normalize()} / {@code URLDecoder} / 重新编码会让签名覆盖的路径与实际发出的路径不一致。
 */
public final class UpstreamUrlSupport {

    private static final String SCHEME_SEPARATOR = "://";
    private static final char SLASH = '/';

    private UpstreamUrlSupport() {
    }

    /**
     * 拼接模型级前缀与协议路径：{@code joinPath("/ep-abc", "/v1/chat/completions")} → {@code /ep-abc/v1/chat/completions}。
     * 前缀为空（{@code null} / 空白 / {@code "/"}）时原样返回协议路径。
     */
    public static String joinPath(String basePath, String protocolPath) {
        String prefix = normalizePrefix(basePath);
        String path = normalizePath(protocolPath);
        if (prefix.isEmpty()) {
            return path;
        }
        return path.isEmpty() ? prefix : prefix + path;
    }

    /**
     * 拼接绝对 base URL 与路径，消除 base 尾段与 path 首段的重叠。
     * 例：{@code joinUrl("https://openrouter.ai/api/v1", "/v1/decisions")} → {@code https://openrouter.ai/api/v1/decisions}。
     * 重叠段的实体属于 base，只从 path 削掉重叠前缀；path 至少保留最后一段，不被完全吸收。
     */
    public static String joinUrl(String baseUrl, String path) {
        String base = StringUtils.removeEnd(StringUtils.trimToEmpty(baseUrl), String.valueOf(SLASH));
        String p = normalizePath(path);
        if (base.isEmpty()) {
            return p;
        }
        int schemeEnd = base.indexOf(SCHEME_SEPARATOR);
        int pathStart = schemeEnd < 0 ? -1 : base.indexOf(SLASH, schemeEnd + SCHEME_SEPARATOR.length());
        if (pathStart < 0) {
            // base 只有 scheme + host（可能带端口），无路径可重叠
            return base + p;
        }
        String origin = base.substring(0, pathStart);
        String basePath = StringUtils.removeEnd(base.substring(pathStart), String.valueOf(SLASH));
        List<String> baseSegments = segments(basePath);
        List<String> pathSegments = segments(p);
        if (baseSegments.isEmpty() || pathSegments.isEmpty()) {
            return base + p;
        }
        int overlap = overlap(baseSegments, pathSegments);
        if (overlap == 0) {
            return base + p;
        }
        // 保留 base 的全部段（含重叠段），只从 path 里削掉重叠的前缀——
        // 重叠段的「实体」属于 base，削 base 会把 /api/v1 削成 /api
        String remainder = joinSegments(pathSegments.subList(overlap, pathSegments.size()));
        return origin + basePath + remainder;
    }

    /**
     * base 尾段与 path 首段的重叠段数：从两侧向内逐段比较，遇到不等立即停止。
     * path 至少保留一段，故上限是 {@code pathSegments.size() - 1}。
     */
    private static int overlap(List<String> baseSegments, List<String> pathSegments) {
        int max = Math.min(baseSegments.size(), pathSegments.size() - 1);
        int matched = 0;
        for (int i = 1; i <= max; i++) {
            String fromBaseTail = baseSegments.get(baseSegments.size() - i);
            String fromPathHead = pathSegments.get(i - 1);
            if (!fromBaseTail.equals(fromPathHead)) {
                break;
            }
            matched = i;
        }
        return matched;
    }

    /** 模型级前缀归一：{@code null} / 空白 / {@code "/"} 一律视为「无前缀」。 */
    private static String normalizePrefix(String basePath) {
        String value = StringUtils.trimToEmpty(basePath);
        if (value.isEmpty() || "/".equals(value)) {
            return "";
        }
        String withLeading = value.charAt(0) == SLASH ? value : SLASH + value;
        return StringUtils.removeEnd(withLeading, String.valueOf(SLASH));
    }

    private static String normalizePath(String path) {
        String value = StringUtils.trimToEmpty(path);
        if (value.isEmpty()) {
            return "";
        }
        return value.charAt(0) == SLASH ? value : SLASH + value;
    }

    private static List<String> segments(String path) {
        List<String> result = new ArrayList<>();
        for (String segment : StringUtils.split(path, SLASH)) {
            if (StringUtils.isNotEmpty(segment)) {
                result.add(segment);
            }
        }
        return result;
    }

    private static String joinSegments(List<String> segments) {
        return SLASH + String.join(String.valueOf(SLASH), segments);
    }
}
