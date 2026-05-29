package org.chobit.knot.gateway.service.external;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.chobit.knot.gateway.entity.ExternalModelItemEntity;
import org.chobit.knot.gateway.entity.ExternalModelSourceEntity;
import org.chobit.knot.gateway.mapper.ExternalModelMapper;
import org.chobit.knot.gateway.util.JsonKit;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class AiBaseCommercialLlmSyncProvider extends AbstractExternalModelSyncProvider {

    public static final String CODE = "AIBASE";
    private static final String API_URL = "https://modelapi.aibase.cn/api/llm/generation/search";
    private static final String SOURCE_URL = "https://model.aibase.cn/llm";
    private static final String DETAIL_RAW_KEY = "_aibaseDetailPage";
    private static final int PAGE_SIZE = 30;
    private static final int MAX_PAGES = 100;

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(15))
            .build();
    private final ObjectMapper mapper = JsonKit.mapper();

    public AiBaseCommercialLlmSyncProvider(ExternalModelMapper externalModelMapper) {
        super(externalModelMapper);
    }

    @Override
    public String sourceCode() {
        return CODE;
    }

    @Override
    protected ExternalModelSourceEntity defaultSource() {
        ExternalModelSourceEntity source = new ExternalModelSourceEntity();
        source.setSourceCode(CODE);
        source.setSourceName("AIBase 商用模型");
        source.setSourceUrl(SOURCE_URL);
        source.setApiUrl(API_URL);
        source.setSourceType("COMMERCIAL_LLM");
        source.setStatus("ENABLED");
        return source;
    }

    @Override
    protected List<ExternalModelItemEntity> fetchItems(ExternalModelSourceEntity source) {
        List<ExternalModelItemEntity> result = new ArrayList<>();
        for (int pageNo = 1; pageNo <= MAX_PAGES; pageNo++) {
            JsonNode root = requestPage(source.getApiUrl(), pageNo);
            JsonNode list = findFirstArray(root);
            if (list == null || list.isEmpty()) {
                break;
            }
            for (JsonNode item : list) {
                ExternalModelItemEntity mapped = mapItem(item);
                if (mapped != null) {
                    result.add(mapped);
                }
            }
            if (list.size() < PAGE_SIZE) {
                break;
            }
        }
        return result;
    }

    @Override
    public ExternalModelItemEntity enrichDetail(ExternalModelItemEntity item) {
        if (item == null || item.getSourceUrl() == null || item.getSourceUrl().isBlank() || hasDetailPage(item.getRawJson())) {
            return item;
        }
        JsonNode detail = requestDetailPage(item.getSourceUrl());
        if (detail == null || detail.isMissingNode() || detail.isNull()) {
            return item;
        }

        ObjectNode raw = mapper.createObjectNode();
        JsonNode searchItem = JsonKit.parse(item.getRawJson());
        if (searchItem != null) {
            raw.set("searchItem", searchItem);
        }
        raw.set(DETAIL_RAW_KEY, detail);
        item.setRawJson(JsonKit.toJson(raw));

        String description = firstTextDeep(detail, "description", "desc", "summary", "introduction", "content");
        if (description != null && !description.isBlank()) {
            item.setSourceDescription(description);
        }
        JsonNode capabilities = firstNodeDeep(detail, "capabilities", "capabilityList", "features");
        if (capabilities != null) {
            item.setSourceCapabilitiesJson(JsonKit.toJson(capabilities));
            item.setCapabilitiesJson(item.getSourceCapabilitiesJson());
        }
        String contextLength = firstTextDeep(detail, "contextLength", "contextLengthType", "contextWindow", "maxContext");
        if (contextLength != null && !contextLength.isBlank()) {
            item.setSourceContextLength(contextLength);
            item.setContextWindow(parseInt(contextLength));
        }
        item.setSyncHash(shortHash(item.getRawJson()));
        return item;
    }

    private JsonNode requestPage(String apiUrl, int pageNo) {
        String body = """
                {"langType":"zh_cn","keyword":"","providerCode":"","tagType":"","contextLengthType":"","llmType":"","llmTypeCode":"","capabilities":"","filedSort":"modelLastUpdateTime","sortType":"desc","pageNo":%d,"pageSize":%d}
                """.formatted(pageNo, PAGE_SIZE).trim();
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(apiUrl + "?t=" + System.currentTimeMillis() + "&langType=zh_cn"))
                    .timeout(Duration.ofSeconds(30))
                    .header("accept", "application/json")
                    .header("accept-language", "zh-CN,zh;q=0.9,en;q=0.8,en-US;q=0.7")
                    .header("content-type", "application/json")
                    .header("origin", "https://model.aibase.cn")
                    .header("referer", "https://model.aibase.cn/")
                    .header("user-agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/148.0.0.0 Safari/537.36")
                    .POST(HttpRequest.BodyPublishers.ofString(body))
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new IllegalStateException("AIBase request failed: HTTP " + response.statusCode());
            }
            return mapper.readTree(response.body());
        } catch (Exception e) {
            throw new IllegalStateException("AIBase request failed at page " + pageNo + ": " + e.getMessage(), e);
        }
    }

    private JsonNode requestDetailPage(String sourceUrl) {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(sourceUrl))
                    .timeout(Duration.ofSeconds(20))
                    .header("accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
                    .header("accept-language", "zh-CN,zh;q=0.9,en;q=0.8")
                    .header("referer", "https://model.aibase.cn/")
                    .header("user-agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/148.0.0.0 Safari/537.36")
                    .GET()
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                return null;
            }
            return extractDetailJson(response.body());
        } catch (Exception ignored) {
            return null;
        }
    }

    private JsonNode extractDetailJson(String html) {
        if (html == null || html.isBlank()) {
            return null;
        }
        List<Pattern> patterns = List.of(
                Pattern.compile("<script[^>]*id=[\"']__NUXT_DATA__[\"'][^>]*>(.*?)</script>", Pattern.CASE_INSENSITIVE | Pattern.DOTALL),
                Pattern.compile("<script[^>]*type=[\"']application/json[\"'][^>]*>(.*?)</script>", Pattern.CASE_INSENSITIVE | Pattern.DOTALL)
        );
        for (Pattern pattern : patterns) {
            Matcher matcher = pattern.matcher(html);
            while (matcher.find()) {
                JsonNode node = parseJsonText(matcher.group(1));
                if (node != null) {
                    return node;
                }
            }
        }
        return null;
    }

    private ExternalModelItemEntity mapItem(JsonNode item) {
        String name = firstText(item, "modelName", "name", "llmName", "title");
        if (name == null || name.isBlank()) {
            return null;
        }
        String raw = JsonKit.toJson(item);
        String id = firstText(item, "id", "modelId", "llmId", "modelCode", "code");
        if (id == null || id.isBlank()) {
            id = shortHash(name + "|" + raw);
        }
        String llmType = firstText(item, "llmType", "modelType", "typeName");
        String llmTypeCode = firstText(item, "llmTypeCode", "modelTypeCode", "typeCode");
        String detailType = resolveDetailType(llmTypeCode, llmType, firstText(item, "tagType", "tagName", "category"));
        ExternalModelItemEntity entity = new ExternalModelItemEntity();
        entity.setSourceModelId(id);
        entity.setSourceModelName(name);
        entity.setSourceProviderCode(firstText(item, "providerCode", "providerId", "vendorCode"));
        entity.setSourceProviderName(firstText(item, "providerName", "vendorName", "owner", "companyName"));
        entity.setSourceUrl(resolveSourceUrl(id, detailType, firstText(item, "sourceUrl", "url", "detailUrl")));
        entity.setSourceLlmType(llmType);
        entity.setSourceLlmTypeCode(llmTypeCode);
        entity.setSourceTagType(firstText(item, "tagType", "tagName", "category"));
        entity.setSourceContextLength(firstText(item, "contextLength", "contextLengthType", "contextWindow", "maxContext"));
        entity.setSourceCapabilitiesJson(toJsonNodeText(firstNode(item, "capabilities", "capabilityList", "features")));
        entity.setSourceLastUpdateTime(parseTime(firstNodeDeep(item,
                "modelLastUpdateTime", "lastUpdateTime", "modelUpdateTime", "lastUpdatedTime",
                "updatedAt", "updateTime", "releaseTime", "publishTime", "publishedAt")));
        entity.setSourceDescription(firstText(item, "description", "desc", "summary", "introduction"));
        entity.setRawJson(raw);
        entity.setNormalizedName(normalizeName(name));
        entity.setNormalizedFamily(normalizeFamily(name));
        entity.setModelType(resolveModelType(detailType));
        entity.setTagsJson(JsonKit.toJson(tags(item, entity)));
        entity.setCapabilitiesJson(entity.getSourceCapabilitiesJson());
        entity.setContextWindow(parseInt(entity.getSourceContextLength()));
        entity.setLanguagesJson(JsonKit.toJson(List.of("zh-CN")));
        entity.setSyncHash(shortHash(raw));
        return entity;
    }

    private JsonNode findFirstArray(JsonNode node) {
        if (node == null) {
            return null;
        }
        if (node.isArray()) {
            return node;
        }
        for (String key : List.of("data", "result", "page", "records", "list", "items", "rows")) {
            JsonNode child = node.get(key);
            JsonNode found = findFirstArray(child);
            if (found != null) {
                return found;
            }
        }
        if (node.isObject()) {
            for (JsonNode child : node) {
                JsonNode found = findFirstArray(child);
                if (found != null) {
                    return found;
                }
            }
        }
        return null;
    }

    private JsonNode firstNode(JsonNode node, String... keys) {
        if (node == null) {
            return null;
        }
        for (String key : keys) {
            JsonNode value = node.get(key);
            if (value != null && !value.isNull()) {
                return value;
            }
        }
        return null;
    }

    private JsonNode firstNodeDeep(JsonNode node, String... keys) {
        JsonNode direct = firstNode(node, keys);
        if (direct != null) {
            return direct;
        }
        if (node == null || (!node.isObject() && !node.isArray())) {
            return null;
        }
        for (JsonNode child : node) {
            if (child != null && (child.isObject() || child.isArray())) {
                JsonNode found = firstNodeDeep(child, keys);
                if (found != null) {
                    return found;
                }
            }
        }
        return null;
    }

    private String firstText(JsonNode node, String... keys) {
        JsonNode value = firstNode(node, keys);
        if (value == null) {
            return null;
        }
        if (value.isTextual()) {
            return blankToNull(value.asText());
        }
        if (value.isNumber() || value.isBoolean()) {
            return value.asText();
        }
        return blankToNull(JsonKit.toJson(value));
    }

    private String toJsonNodeText(JsonNode node) {
        return node == null ? null : JsonKit.toJson(node);
    }

    private String resolveSourceUrl(String id, String detailType, String url) {
        if (url != null && url.startsWith("http")) {
            return url;
        }
        return SOURCE_URL + "/" + detailType + "/" + id;
    }

    private String resolveDetailType(String... values) {
        for (String value : values) {
            if (value == null || value.isBlank()) {
                continue;
            }
            String normalized = value.trim().toLowerCase(Locale.ROOT);
            if (normalized.contains("audio") || normalized.contains("speech") || normalized.contains("voice")
                    || normalized.contains("语音") || normalized.contains("音频")) {
                return "audio";
            }
            if (normalized.contains("video") || normalized.contains("mov")
                    || normalized.contains("视频") || normalized.contains("影片")) {
                return "video";
            }
            if (normalized.contains("image") || normalized.contains("img")
                    || normalized.contains("图像") || normalized.contains("图片") || normalized.contains("绘图")) {
                return "image";
            }
            if (normalized.contains("text") || normalized.contains("chat") || normalized.contains("llm")
                    || normalized.contains("语言") || normalized.contains("文本") || normalized.contains("对话")) {
                return "text";
            }
        }
        return "text";
    }

    private String resolveModelType(String detailType) {
        if ("image".equals(detailType)) {
            return "IMAGE";
        }
        if ("video".equals(detailType)) {
            return "VIDEO";
        }
        if ("audio".equals(detailType)) {
            return "AUDIO";
        }
        return "CHAT";
    }

    private boolean hasDetailPage(String rawJson) {
        JsonNode raw = JsonKit.parse(rawJson);
        return raw != null && raw.has(DETAIL_RAW_KEY);
    }

    private JsonNode parseJsonText(String text) {
        if (text == null || text.isBlank()) {
            return null;
        }
        String normalized = text.trim()
                .replace("&quot;", "\"")
                .replace("&amp;", "&")
                .replace("&lt;", "<")
                .replace("&gt;", ">");
        return JsonKit.parse(normalized);
    }

    private String firstTextDeep(JsonNode node, String... keys) {
        JsonNode value = firstNodeDeep(node, keys);
        if (value == null || value.isNull()) {
            return null;
        }
        if (value.isTextual() || value.isNumber() || value.isBoolean()) {
            return blankToNull(value.asText());
        }
        return blankToNull(JsonKit.toJson(value));
    }

    private List<String> tags(JsonNode item, ExternalModelItemEntity entity) {
        Set<String> tags = new LinkedHashSet<>();
        add(tags, entity.getSourceProviderName());
        add(tags, entity.getSourceLlmType());
        add(tags, entity.getSourceLlmTypeCode());
        add(tags, entity.getSourceTagType());
        add(tags, firstText(item, "contextLengthType"));
        return tags.stream().limit(12).toList();
    }

    private void add(Set<String> set, String value) {
        if (value == null || value.isBlank()) {
            return;
        }
        String trimmed = value.trim();
        if (trimmed.length() <= 64 && !trimmed.matches("\\d+")) {
            set.add(trimmed);
        }
    }

    private String normalizeName(String name) {
        return name == null ? null : name.trim().replaceAll("\\s+", " ");
    }

    private String normalizeFamily(String name) {
        String normalized = normalizeName(name);
        if (normalized == null) {
            return null;
        }
        return normalized
                .replaceAll("(?i)\\b(instruct|chat|preview|latest|turbo)\\b", "")
                .replaceAll("\\s+", " ")
                .trim();
    }

    private Integer parseInt(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        Matcher matcher = Pattern.compile("(\\d+(?:\\.\\d+)?)\\s*([kKmM]?)").matcher(value.replace(",", ""));
        if (!matcher.find()) {
            return null;
        }
        double n = Double.parseDouble(matcher.group(1));
        String unit = matcher.group(2).toLowerCase(Locale.ROOT);
        if ("k".equals(unit)) {
            n *= 1000;
        } else if ("m".equals(unit)) {
            n *= 1_000_000;
        }
        return (int) Math.round(n);
    }

    private LocalDateTime parseTime(JsonNode node) {
        if (node == null || node.isNull()) {
            return null;
        }
        try {
            if (node.isNumber()) {
                long n = node.asLong();
                Instant instant = n > 10_000_000_000L ? Instant.ofEpochMilli(n) : Instant.ofEpochSecond(n);
                return LocalDateTime.ofInstant(instant, ZoneId.systemDefault());
            }
            String text = node.asText();
            if (text == null || text.isBlank()) {
                return null;
            }
            if (text.matches("\\d+")) {
                return parseTime(mapper.getNodeFactory().numberNode(Long.parseLong(text)));
            }
            String normalized = text.replace("/", "-").replace(" ", "T");
            if (normalized.endsWith("Z") || normalized.matches(".*[+-]\\d{2}:?\\d{2}$")) {
                try {
                    return OffsetDateTime.parse(normalized, DateTimeFormatter.ISO_OFFSET_DATE_TIME).toLocalDateTime();
                } catch (Exception ignored) {
                    return ZonedDateTime.parse(normalized, DateTimeFormatter.ISO_ZONED_DATE_TIME).toLocalDateTime();
                }
            }
            if (normalized.matches("\\d{4}-\\d{1,2}-\\d{1,2}")) {
                return LocalDate.parse(normalized, DateTimeFormatter.ISO_LOCAL_DATE).atStartOfDay();
            }
            return LocalDateTime.parse(normalized, DateTimeFormatter.ISO_LOCAL_DATE_TIME);
        } catch (Exception ignored) {
            return null;
        }
    }

    private String shortHash(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] bytes = digest.digest((value == null ? "" : value).getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < 8; i++) {
                sb.append(String.format("%02x", bytes[i]));
            }
            return sb.toString();
        } catch (Exception e) {
            return Integer.toHexString((value == null ? "" : value).hashCode());
        }
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
