package org.chobit.knot.svc.service.external;

import com.fasterxml.jackson.core.type.TypeReference;
import org.chobit.knot.common.constants.enums.ModelTypeEnum;
import org.chobit.knot.common.util.JsonKit;
import org.chobit.knot.dal.entity.ExternalModelItemEntity;
import org.chobit.knot.dal.entity.ExternalModelSourceEntity;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * XFusionAI 价目响应的解析契约。
 *
 * <p>fixture 是 2026-11-01 从 {@code https://llm.azopenai.com/api/pricing} 实抓后裁出来的
 * 6 条真实条目（5 有效 + 1 条 model_name 为空），厂商表按这 6 条用到的 id 裁剪。
 * 解析与抓取分开就是为了测这里——不依赖网络，也不依赖数据库。</p>
 */
class XFusionAIModelSyncProviderTest {

    private static final String FIXTURE = "/external/xfusionai-pricing.json";

    /** 解析链路不碰 Mapper，传 null 即可。 */
    private final XFusionAIModelSyncProvider provider = new XFusionAIModelSyncProvider(null);

    @Test
    @DisplayName("sourceCode 与默认源：编码 XFUSIONAI，类型 MODEL_CATALOG")
    void sourceCodeAndDefaultSource() {
        assertEquals("XFUSIONAI", provider.sourceCode());

        ExternalModelSourceEntity source = provider.defaultSource();
        assertEquals("XFUSIONAI", source.getSourceCode());
        assertEquals("XFusionAI Pricing", source.getSourceName());
        assertEquals("https://llm.azopenai.com/pricing", source.getSourceUrl());
        assertEquals("https://llm.azopenai.com/api/pricing", source.getApiUrl());
        assertEquals("MODEL_CATALOG", source.getSourceType());
        assertEquals(1, source.getStatus());
    }

    @Test
    @DisplayName("解析全部条目：模型名为唯一标识，空名条目被丢弃")
    void parsesItemsAndDropsBlankModelName() {
        Map<String, ExternalModelItemEntity> items = parse();

        assertEquals(5, items.size(), "model_name 为空的条目必须被丢弃");
        assertTrue(items.containsKey("deepseek-flash"));
        assertTrue(items.containsKey("kling-video-o1"));
        assertTrue(items.containsKey("mai-image-2.5"));
        assertTrue(items.containsKey("text-embedding-3-small"));
        assertTrue(items.containsKey("speech-2.8-hd"));
    }

    @Test
    @DisplayName("vendor_id 映射成厂商名；缺失时兜底 XFusionAI")
    void mapsVendorName() {
        Map<String, ExternalModelItemEntity> items = parse();

        assertEquals("DeepSeek", items.get("deepseek-flash").getProviderName());
        assertEquals("Kuaishou", items.get("kling-video-o1").getProviderName());
        assertEquals("OpenAI", items.get("text-embedding-3-small").getProviderName());
        // mai-image-2.5 在源里没有 vendor_id
        assertEquals("XFusionAI", items.get("mai-image-2.5").getProviderName());
    }

    @Test
    @DisplayName("model_type 映射：text→CHAT video→VIDEO image→IMAGE audio→AUDIO embedding→EMBEDDING")
    void mapsModelType() {
        Map<String, ExternalModelItemEntity> items = parse();

        assertEquals(ModelTypeEnum.CHAT.code(), items.get("deepseek-flash").getModelType());
        assertEquals(ModelTypeEnum.VIDEO.code(), items.get("kling-video-o1").getModelType());
        assertEquals(ModelTypeEnum.IMAGE.code(), items.get("mai-image-2.5").getModelType());
        assertEquals(ModelTypeEnum.AUDIO.code(), items.get("speech-2.8-hd").getModelType());
        assertEquals(ModelTypeEnum.EMBEDDING.code(), items.get("text-embedding-3-small").getModelType());
    }

    @Test
    @DisplayName("创建时间：正常 epoch 转本地时间，0 视为「没有」")
    void mapsCreatedTime() {
        Map<String, ExternalModelItemEntity> items = parse();

        assertNotNull(items.get("deepseek-flash").getModelCreatedAt(), "created_time=1789107617 应转成时间");
        // 源里 created_time=0 的条目（mai-image-2.5 / speech-2.8-hd）不能落出 1970-01-01
        assertNull(items.get("mai-image-2.5").getModelCreatedAt());
        assertNull(items.get("speech-2.8-hd").getModelCreatedAt());
    }

    @Test
    @DisplayName("上下文与最大输出刻意留空：源只把上下文写在中文文案里，不解析")
    void leavesContextAndCompletionEmpty() {
        Map<String, ExternalModelItemEntity> items = parse();

        for (ExternalModelItemEntity item : items.values()) {
            assertNull(item.getContextLength(), item.getModelId() + " 不应凭文案猜出上下文长度");
            assertNull(item.getMaxCompletionTokens(), item.getModelId() + " 不应凭文案猜出最大输出");
        }
        assertEquals("[]", items.get("deepseek-flash").getInputModalitiesJson());
        assertEquals("[]", items.get("deepseek-flash").getOutputModalitiesJson());
    }

    @Test
    @DisplayName("标签：源自带中文标签在前，再补厂商 / 类型 / 区域 / 端点，且不超过 12 条")
    void buildsTags() {
        List<String> tags = tagsOf(parse().get("deepseek-flash"));

        assertEquals("上下文 1M", tags.get(0));
        assertEquals("最大输出384K", tags.get(1));
        assertEquals("文本与代码", tags.get(2));
        assertTrue(tags.contains("DeepSeek"));
        assertTrue(tags.contains("CHAT"));
        assertTrue(tags.contains("region:domestic"));
        assertTrue(tags.contains("endpoint:openai"));
        assertTrue(tags.contains("endpoint:openai-response"));
        assertTrue(tags.contains("endpoint:anthropic"));
        assertTrue(tags.size() <= 12, "标签上限 12，实际 " + tags.size());
    }

    @Test
    @DisplayName("定价留在 rawJson 里不结构化：本次只同步目录")
    void keepsPricingInRawJson() {
        ExternalModelItemEntity item = parse().get("deepseek-flash");

        assertNotNull(item.getRawJson());
        assertTrue(item.getRawJson().contains("\"model_ratio\":0.075"), "倍率必须原样留在 rawJson");
        assertTrue(item.getRawJson().contains("\"completion_ratio\":4"));
        assertTrue(item.getRawJson().contains("\"quota_type\":0"));
    }

    @Test
    @DisplayName("syncHash：同内容同指纹，不同条目不同指纹")
    void buildsSyncHash() {
        Map<String, ExternalModelItemEntity> items = parse();

        assertNotNull(items.get("deepseek-flash").getSyncHash());
        assertEquals(16, items.get("deepseek-flash").getSyncHash().length());
        assertEquals(
                ExternalModelJsonSupport.shortHash(items.get("deepseek-flash").getRawJson()),
                items.get("deepseek-flash").getSyncHash()
        );
        long distinct = items.values().stream().map(ExternalModelItemEntity::getSyncHash).distinct().count();
        assertEquals(items.size(), distinct, "每个条目的指纹都应不同");
    }

    @Test
    @DisplayName("模型链接指向源站：该站没有模型详情页，不拼必然 404 的 URL")
    void modelUrlPointsToSourceSite() {
        for (ExternalModelItemEntity item : parse().values()) {
            assertEquals("https://llm.azopenai.com/pricing", item.getModelUrl());
        }
    }

    @Test
    @DisplayName("data 不是数组时返回空列表，不抛异常")
    void returnsEmptyWhenDataIsNotArray() {
        assertTrue(provider.parseItems("{\"success\":true,\"data\":null}").isEmpty());
        assertTrue(provider.parseItems("{\"success\":false}").isEmpty());
    }

    private Map<String, ExternalModelItemEntity> parse() {
        return provider.parseItems(fixture()).stream()
                .collect(Collectors.toMap(ExternalModelItemEntity::getModelId, Function.identity()));
    }

    private List<String> tagsOf(ExternalModelItemEntity item) {
        List<String> tags = JsonKit.fromJson(item.getTagsJson(), new TypeReference<>() {
        });
        assertNotNull(tags, "tagsJson 应解析成数组");
        return tags;
    }

    private String fixture() {
        try (InputStream stream = getClass().getResourceAsStream(FIXTURE)) {
            assertNotNull(stream, "缺少测试夹具: " + FIXTURE);
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException("读取测试夹具失败: " + FIXTURE, e);
        }
    }
}
