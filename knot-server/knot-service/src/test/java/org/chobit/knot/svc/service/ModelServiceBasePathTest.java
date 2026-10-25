package org.chobit.knot.svc.service;

import org.chobit.knot.gateway.error.BusinessException;
import org.chobit.knot.svc.service.ModelService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 模型级 Base Path 的归一化与校验。
 *
 * <p>这个字段是「上游 host 唯一来自账户」之后模型侧仅剩的地址配置，
 * 规则错一次就是全站打到错误路径，所以每条拒绝分支都要钉住。</p>
 */
class ModelServiceBasePathTest {

    @Test
    @DisplayName("空值 / 空白 / 单斜杠 / 纯斜杠一律收敛成空串")
    void blankValuesCollapseToEmpty() {
        assertEquals("", ModelService.normalizeBasePath(null));
        assertEquals("", ModelService.normalizeBasePath(""));
        assertEquals("", ModelService.normalizeBasePath("   "));
        assertEquals("", ModelService.normalizeBasePath("/"));
        assertEquals("", ModelService.normalizeBasePath("//"));
    }

    @Test
    @DisplayName("正常前缀保留，末尾斜杠去掉")
    void keepsPrefixAndTrimsTrailingSlash() {
        assertEquals("/ep-abc123", ModelService.normalizeBasePath("/ep-abc123"));
        assertEquals("/ep-abc123", ModelService.normalizeBasePath("/ep-abc123/"));
        assertEquals("/ep-abc123", ModelService.normalizeBasePath("  /ep-abc123/  "));
        assertEquals("/a/b/c", ModelService.normalizeBasePath("/a/b/c/"));
    }

    @Test
    @DisplayName("用户误把完整 URL 填进 Base Path 时明确拒绝")
    void rejectsAbsoluteUrl() {
        BusinessException error = assertThrows(BusinessException.class,
                () -> ModelService.normalizeBasePath("https://api.openai.com"));
        assertTrue(error.getMessage().contains("供应商账户"), "提示必须指向上游地址该配在哪里");
        assertThrows(BusinessException.class,
                () -> ModelService.normalizeBasePath("/ep?id=1"));
        assertThrows(BusinessException.class,
                () -> ModelService.normalizeBasePath("/ep#frag"));
    }

    @Test
    @DisplayName("必须以 / 开头，且不能有连续斜杠")
    void rejectsMalformedPath() {
        assertThrows(BusinessException.class, () -> ModelService.normalizeBasePath("ep-123"));
        assertThrows(BusinessException.class, () -> ModelService.normalizeBasePath("/ep//123"));
        assertThrows(BusinessException.class, () -> ModelService.normalizeBasePath("/ep 123"));
        assertThrows(BusinessException.class, () -> ModelService.normalizeBasePath("/端点"));
        assertThrows(BusinessException.class, () -> ModelService.normalizeBasePath("/ep%3A1"));
    }

    @Test
    @DisplayName("超长前缀拒绝，避免写库时被截断")
    void rejectsOverlongPath() {
        String longPath = "/" + "a".repeat(255);
        assertThrows(BusinessException.class, () -> ModelService.normalizeBasePath(longPath));
        assertEquals(255, ModelService.normalizeBasePath("/" + "a".repeat(254)).length());
    }
}
