package org.chobit.knot.gateway.service.external;

import com.fasterxml.jackson.databind.JsonNode;
import org.chobit.knot.gateway.constants.enums.EnabledStatusEnum;
import org.chobit.knot.gateway.constants.enums.ModelTypeEnum;
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
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

@Component
public class OpenRouterModelSyncProvider extends AbstractExternalModelSyncProvider {

    /**
     * Executes the public operation. Executes the public operation.
     */
    public static final String CODE = "OPENROUTER";
    private static final String SOURCE_URL = "https://openrouter.ai/models";
    private static final String API_URL = "https://openrouter.ai/api/v1/models";

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(15))
            .build();

    /**
     * Constructs a new instance.
     */
    public OpenRouterModelSyncProvider(ExternalModelMapper externalModelMapper) {
        super(externalModelMapper);
    }

    /**
     * Executes the public operation. Executes the public operation.
     */
    @Override
    public String sourceCode() {
        return CODE;
    }

    @Override
    protected ExternalModelSourceEntity defaultSource() {
        ExternalModelSourceEntity source = new ExternalModelSourceEntity();
        source.setSourceCode(CODE);
        source.setSourceName("OpenRouter Models");
        source.setSourceUrl(SOURCE_URL);
        source.setApiUrl(API_URL);
        source.setSourceType("MODEL_CATALOG");
        source.setStatus(EnabledStatusEnum.ENABLED.code());
        return source;
    }

    @Override
    protected List<ExternalModelItemEntity> fetchItems(ExternalModelSourceEntity source) {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(source.getApiUrl()))
                    .timeout(Duration.ofSeconds(30))
                    .header("accept", "application/json")
                    .header("user-agent", "Knot-AI-Gateway/1.0")
                    .GET()
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new IllegalStateException("OpenRouter request failed: HTTP " + response.statusCode());
            }
            JsonNode root = JsonKit.parseOrThrow(response.body());
            JsonNode data = root.path("data");
            if (!data.isArray()) {
                return List.of();
            }
            List<ExternalModelItemEntity> result = new ArrayList<>();
            for (JsonNode node : data) {
                ExternalModelItemEntity item = mapItem(node);
                if (item != null) {
                    result.add(item);
                }
            }
            return result;
        } catch (Exception e) {
            throw new IllegalStateException("OpenRouter model sync failed: " + e.getMessage(), e);
        }
    }

    private ExternalModelItemEntity mapItem(JsonNode node) {
        String id = text(node, "id");
        String name = text(node, "name");
        if (id == null || id.isBlank() || name == null || name.isBlank()) {
            return null;
        }

        JsonNode architecture = node.path("architecture");
        JsonNode topProvider = node.path("top_provider");
        List<String> inputModalities = stringList(architecture.path("input_modalities"));
        List<String> outputModalities = stringList(architecture.path("output_modalities"));
        Integer contextLength = integer(node.path("context_length"));

        ExternalModelItemEntity entity = new ExternalModelItemEntity();
        entity.setModelId(id);
        entity.setCanonicalSlug(text(node, "canonical_slug"));
        entity.setModelName(name);
        entity.setProviderName(providerName(id, name));
        entity.setModelUrl(detailsUrl(node, id));
        entity.setModelCreatedAt(epochSeconds(longValue(node.path("created"))));
        entity.setDescription(text(node, "description"));
        entity.setContextLength(contextLength);
        entity.setInputModalitiesJson(JsonKit.toJson(inputModalities));
        entity.setOutputModalitiesJson(JsonKit.toJson(outputModalities));
        entity.setRawJson(JsonKit.toJson(node));
        entity.setNormalizedName(normalizeName(name));
        entity.setModelType(modelType(id, name, inputModalities, outputModalities));
        entity.setMaxCompletionTokens(integer(topProvider.path("max_completion_tokens")));
        entity.setTagsJson(JsonKit.toJson(tags(entity, inputModalities, outputModalities)));
        entity.setSyncHash(shortHash(entity.getRawJson()));
        return entity;
    }

    private String detailsUrl(JsonNode node, String id) {
        String details = text(node.path("links"), "details");
        if (details != null && details.startsWith("http")) {
            return details;
        }
        return SOURCE_URL + "/" + id;
    }

    private String providerName(String id, String name) {
        int idx = name.indexOf(':');
        if (idx > 0) {
            return name.substring(0, idx).trim();
        }
        int slash = id.indexOf('/');
        return slash > 0 ? title(id.substring(0, slash)) : "OpenRouter";
    }

    private String modelType(String id, String name, List<String> inputModalities, List<String> outputModalities) {
        String value = (id + " " + name + " " + outputModalities).toLowerCase(Locale.ROOT);
        if (value.contains("embedding")) {
            return ModelTypeEnum.EMBEDDING.code();
        }
        if (outputModalities.contains("image")) {
            return ModelTypeEnum.IMAGE.code();
        }
        if (outputModalities.contains("audio")) {
            return ModelTypeEnum.AUDIO.code();
        }
        if (outputModalities.contains("video")) {
            return ModelTypeEnum.VIDEO.code();
        }
        if (inputModalities.contains("image")) {
            return ModelTypeEnum.MULTIMODAL.code();
        }
        return ModelTypeEnum.CHAT.code();
    }

    private List<String> tags(ExternalModelItemEntity entity, List<String> inputModalities, List<String> outputModalities) {
        Set<String> tags = new LinkedHashSet<>();
        add(tags, entity.getProviderName());
        add(tags, entity.getModelType());
        inputModalities.forEach(v -> add(tags, "input:" + v));
        outputModalities.forEach(v -> add(tags, "output:" + v));
        if (entity.getContextLength() != null) {
            add(tags, "context:" + entity.getContextLength());
        }
        return tags.stream().limit(12).toList();
    }

    private List<String> stringList(JsonNode node) {
        if (node == null || !node.isArray()) {
            return List.of();
        }
        List<String> result = new ArrayList<>();
        for (JsonNode item : node) {
            if (item.isTextual() && !item.asText().isBlank()) {
                result.add(item.asText());
            }
        }
        return result;
    }

    private String text(JsonNode node, String field) {
        JsonNode value = node == null ? null : node.get(field);
        if (value == null || value.isNull()) {
            return null;
        }
        if (value.isTextual()) {
            return value.asText().isBlank() ? null : value.asText();
        }
        if (value.isNumber() || value.isBoolean()) {
            return value.asText();
        }
        return JsonKit.toJson(value);
    }

    private Integer integer(JsonNode node) {
        if (node == null || node.isNull()) {
            return null;
        }
        if (node.isNumber()) {
            return node.asInt();
        }
        try {
            return Integer.parseInt(node.asText());
        } catch (Exception ignored) {
            return null;
        }
    }

    private Long longValue(JsonNode node) {
        if (node == null || node.isNull()) {
            return null;
        }
        if (node.isNumber()) {
            return node.asLong();
        }
        try {
            return Long.parseLong(node.asText());
        } catch (Exception ignored) {
            return null;
        }
    }

    private LocalDateTime epochSeconds(Long value) {
        if (value == null) {
            return null;
        }
        return LocalDateTime.ofInstant(Instant.ofEpochSecond(value), ZoneId.systemDefault());
    }

    private String normalizeName(String name) {
        return name == null ? null : name.trim().replaceAll("\\s+", " ");
    }

    private String title(String value) {
        String normalized = value.replace('-', ' ').replace('_', ' ').trim();
        if (normalized.isEmpty()) {
            return value;
        }
        return normalized.substring(0, 1).toUpperCase(Locale.ROOT) + normalized.substring(1);
    }

    private void add(Set<String> set, String value) {
        if (value != null && !value.isBlank()) {
            set.add(value.trim());
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
}
