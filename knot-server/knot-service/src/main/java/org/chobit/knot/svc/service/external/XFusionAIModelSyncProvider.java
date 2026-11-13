package org.chobit.knot.svc.service.external;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import org.chobit.knot.common.constants.enums.EnabledStatusEnum;
import org.chobit.knot.common.constants.enums.ModelTypeEnum;
import org.chobit.knot.dal.entity.ExternalModelItemEntity;
import org.chobit.knot.dal.entity.ExternalModelSourceEntity;
import org.chobit.knot.dal.mapper.ExternalModelMapper;
import org.chobit.knot.common.util.JsonKit;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * XFusionAI（{@code llm.azopenai.com}）模型与价目目录同步；{@code /api/pricing} 对未登录访客开放。
 * 模型名是站点内部名（如 {@code deepseek-flash}），不是厂商官方名，转统一模型时需注意。
 * 只同步目录，不落定价：{@code model_ratio / completion_ratio / cache_ratio / model_price} 留在 {@code rawJson} 里。
 */
@Component
public class XFusionAIModelSyncProvider extends AbstractExternalModelSyncProvider {

    public static final String CODE = "XFUSIONAI";

    private static final String SOURCE_NAME = "XFusionAI Pricing";
    private static final String SOURCE_URL = "https://llm.azopenai.com/pricing";
    private static final String API_URL = "https://llm.azopenai.com/api/pricing";

    /** vendor_id 缺失时的兜底：该站是聚合平台，条目确实不属于任何已登记厂商。 */
    private static final String DEFAULT_PROVIDER_NAME = "XFusionAI";

    private static final Map<String, String> MODEL_TYPES = Map.of(
            "text", ModelTypeEnum.CHAT.code(),
            "image", ModelTypeEnum.IMAGE.code(),
            "video", ModelTypeEnum.VIDEO.code(),
            "audio", ModelTypeEnum.AUDIO.code(),
            "embedding", ModelTypeEnum.EMBEDDING.code()
    );

    private static final int MAX_TAGS = 12;

    /**
     * 外部目录同步专用客户端（JDK 同步 {@link HttpClient}）。
     */
    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(15))
            .build();

    public XFusionAIModelSyncProvider(ExternalModelMapper externalModelMapper) {
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
        source.setSourceName(SOURCE_NAME);
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
                    // New-API 系站点的匿名访客标识：-1 = 未登录，返回默认分组的价目。
                    // 实测不带这两个头也能 200，带上是为了让拿到的分组与浏览器一致。
                    // ⚠ 不带 acw_tc cookie：那是 CDN 会话凭据，写死必然过期，且实测非必需。
                    .header("New-API-User", "-1")
                    .header("xfusionai-user", "-1")
                    .GET()
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new IllegalStateException("XFusionAI request failed: HTTP " + response.statusCode());
            }
            return parseItems(response.body());
        } catch (Exception e) {
            throw new IllegalStateException("XFusionAI model sync failed: " + e.getMessage(), e);
        }
    }

    /**
     * 解析响应体（与抓取分离，测试可以不碰网络）。
     */
    protected List<ExternalModelItemEntity> parseItems(String body) {
        JsonNode root;
        try {
            root = JsonKit.parseOrThrow(body);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("XFusionAI pricing response is not valid JSON", e);
        }
        JsonNode data = root.path("data");
        if (!data.isArray()) {
            return List.of();
        }
        Map<Integer, String> vendors = vendorNames(root.path("vendors"));
        List<ExternalModelItemEntity> result = new ArrayList<>();
        for (JsonNode node : data) {
            ExternalModelItemEntity item = mapItem(node, vendors);
            if (item != null) {
                result.add(item);
            }
        }
        return result;
    }

    private Map<Integer, String> vendorNames(JsonNode vendors) {
        Map<Integer, String> result = new LinkedHashMap<>();
        if (vendors == null || !vendors.isArray()) {
            return result;
        }
        for (JsonNode node : vendors) {
            Integer id = ExternalModelJsonSupport.integer(node.get("id"));
            String name = ExternalModelJsonSupport.text(node, "name");
            if (id != null && name != null) {
                result.put(id, name);
            }
        }
        return result;
    }

    private ExternalModelItemEntity mapItem(JsonNode node, Map<Integer, String> vendors) {
        String modelId = ExternalModelJsonSupport.text(node, "model_name");
        if (modelId == null || modelId.isBlank()) {
            return null;
        }
        Integer vendorId = ExternalModelJsonSupport.integer(node.get("vendor_id"));
        String providerName = vendorId == null
                ? DEFAULT_PROVIDER_NAME
                : vendors.getOrDefault(vendorId, DEFAULT_PROVIDER_NAME);
        String modelType = modelType(ExternalModelJsonSupport.text(node, "model_type"));
        String region = ExternalModelJsonSupport.text(node, "model_region");
        List<String> endpoints = ExternalModelJsonSupport.stringList(node.get("supported_endpoint_types"));

        ExternalModelItemEntity entity = new ExternalModelItemEntity();
        entity.setModelId(modelId);
        entity.setCanonicalSlug(modelId);
        entity.setModelName(modelId);
        entity.setProviderName(providerName);
        // 该站没有模型详情页（价目都堆在 /pricing 一页上），拼 /pricing/<name> 会 404，
        // 所以全部指向源站本身——宁可少一个精确链接，也不给一个必然打不开的链接。
        entity.setModelUrl(SOURCE_URL);
        entity.setModelCreatedAt(ExternalModelJsonSupport.epochSeconds(
                ExternalModelJsonSupport.longValue(node.get("created_time"))));
        entity.setDescription(ExternalModelJsonSupport.text(node, "description"));
        // ⚠ context_length / max_completion_tokens 一律留空：源没有这两个字段，上下文只以中文
        //   写在 description / tags 里（「上下文 1M,最大输出384K」）。刻意不解析——那是给人看的
        //   文案，格式不固定，正则抠出来的数会静默变成假的上下文长度。
        entity.setInputModalitiesJson(JsonKit.toJson(List.of()));
        entity.setOutputModalitiesJson(JsonKit.toJson(List.of()));
        entity.setRawJson(JsonKit.toJson(node));
        entity.setNormalizedName(ExternalModelJsonSupport.normalizeName(modelId));
        entity.setModelType(modelType);
        entity.setTagsJson(JsonKit.toJson(tags(node, providerName, modelType, region, endpoints)));
        entity.setSyncHash(ExternalModelJsonSupport.shortHash(entity.getRawJson()));
        return entity;
    }

    /**
     * {@code text/image/video/audio/embedding} → {@link ModelTypeEnum} 码；未命中落 CHAT，不返回 {@code null}。
     */
    private String modelType(String raw) {
        if (raw == null || raw.isBlank()) {
            return ModelTypeEnum.CHAT.code();
        }
        return MODEL_TYPES.getOrDefault(raw.trim().toLowerCase(Locale.ROOT), ModelTypeEnum.CHAT.code());
    }

    /**
     * 标签：源自带的中文标签优先（最有信息量），再补厂商 / 类型 / 区域 / 支持的端点。
     */
    private List<String> tags(JsonNode node, String providerName, String modelType, String region, List<String> endpoints) {
        Set<String> tags = new LinkedHashSet<>();
        String raw = ExternalModelJsonSupport.text(node, "tags");
        if (raw != null) {
            for (String part : raw.split(",")) {
                ExternalModelJsonSupport.add(tags, part);
            }
        }
        ExternalModelJsonSupport.add(tags, providerName);
        ExternalModelJsonSupport.add(tags, modelType);
        ExternalModelJsonSupport.add(tags, region == null ? null : "region:" + region);
        endpoints.forEach(endpoint -> ExternalModelJsonSupport.add(tags, "endpoint:" + endpoint));
        return tags.stream().limit(MAX_TAGS).toList();
    }
}
