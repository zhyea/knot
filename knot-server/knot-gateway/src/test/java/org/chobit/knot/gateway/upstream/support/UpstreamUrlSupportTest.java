package org.chobit.knot.gateway.upstream.support;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * 上游 URL 拼接规则。
 *
 * <p>这套规则决定「请求打到哪个地址」，一旦回归就是全站 404 —— 所以每个分支都要钉死，
 * 尤其是「账户 base URL 带路径前缀」与「Bedrock 已编码路径」两条真实踩过的坑。</p>
 */
class UpstreamUrlSupportTest {

    // ==================== joinPath：模型级前缀 + 协议路径 ====================

    @Test
    @DisplayName("空前缀（null / 空白 / 单斜杠）一律原样返回协议路径")
    void joinPathWithoutPrefix() {
        String protocolPath = "/v1/chat/completions";
        assertEquals(protocolPath, UpstreamUrlSupport.joinPath(null, protocolPath));
        assertEquals(protocolPath, UpstreamUrlSupport.joinPath("", protocolPath));
        assertEquals(protocolPath, UpstreamUrlSupport.joinPath("   ", protocolPath));
        assertEquals(protocolPath, UpstreamUrlSupport.joinPath("/", protocolPath));
    }

    @Test
    @DisplayName("前缀作为路径段追加，两侧斜杠不重复也不缺失")
    void joinPathWithPrefix() {
        assertEquals("/ep-123/v1/chat/completions",
                UpstreamUrlSupport.joinPath("/ep-123", "/v1/chat/completions"));
        assertEquals("/ep-123/v1/chat/completions",
                UpstreamUrlSupport.joinPath("/ep-123/", "/v1/chat/completions"));
        assertEquals("/ep-123/v1/chat/completions",
                UpstreamUrlSupport.joinPath("/ep-123", "v1/chat/completions"));
        assertEquals("/a/b/v1/messages",
                UpstreamUrlSupport.joinPath("/a/b/", "/v1/messages"));
    }

    @Test
    @DisplayName("协议路径为空时只留前缀——CUSTOM 协议可能就是这种形态")
    void joinPathWithEmptyProtocolPath() {
        assertEquals("/ep-123", UpstreamUrlSupport.joinPath("/ep-123", ""));
        assertEquals("", UpstreamUrlSupport.joinPath("", ""));
    }

    // ==================== joinUrl：base URL + 路径 ====================

    @Test
    @DisplayName("base 只有 host 时直接拼接")
    void joinUrlWithoutBasePath() {
        assertEquals("https://api.openai.com/v1/chat/completions",
                UpstreamUrlSupport.joinUrl("https://api.openai.com", "/v1/chat/completions"));
        assertEquals("http://127.0.0.1:8090/v1/decisions",
                UpstreamUrlSupport.joinUrl("http://127.0.0.1:8090/", "/v1/decisions"),
                "带端口的 base 不能把端口当路径段");
    }

    @Test
    @DisplayName("base 自带路径前缀且与协议路径不重叠时，前缀保留")
    void joinUrlWithNonOverlappingBasePath() {
        assertEquals("https://gateway.example.com/proxy/openai/v1/chat/completions",
                UpstreamUrlSupport.joinUrl("https://gateway.example.com/proxy/openai", "/v1/chat/completions"));
    }

    @Test
    @DisplayName("base 尾段与路径首段重复时按段吸收：openrouter /api/v1 + /v1/decisions")
    void joinUrlAbsorbsOverlappingSegment() {
        assertEquals("https://openrouter.ai/api/v1/decisions",
                UpstreamUrlSupport.joinUrl("https://openrouter.ai/api/v1", "/v1/decisions"));
        assertEquals("https://openrouter.ai/api/v1/chat/completions",
                UpstreamUrlSupport.joinUrl("https://openrouter.ai/api/v1/", "/v1/chat/completions"));
    }

    @Test
    @DisplayName("吸收按完整路径段比较：/v10 与 /v1 不是同一段")
    void joinUrlDoesNotMatchPartialSegment() {
        assertEquals("https://host/v10/v1/chat",
                UpstreamUrlSupport.joinUrl("https://host/v10", "/v1/chat"),
                "按字符串前缀判断会把 /v10 误当成 /v1");
    }

    @Test
    @DisplayName("段级比较不会越界吸收：base /api/v1 + path /v1/chat/completions 只吸 v1")
    void joinUrlAbsorbsOnlyOverlappingSegments() {
        assertEquals("https://host/api/v1/chat/completions",
                UpstreamUrlSupport.joinUrl("https://host/api/v1", "/v1/chat/completions"),
                "chat / completions 与 base 无关，不能被一并吞掉");
    }

    @Test
    @DisplayName("path 至少保留一段：不允许把端点整个吸收掉")
    void joinUrlKeepsAtLeastOneSegment() {
        assertEquals("https://host/v1/v1",
                UpstreamUrlSupport.joinUrl("https://host/v1", "/v1"),
                "吸收到 path 为空等于静默改了端点，宁可留下可见的重复");
    }

    @Test
    @DisplayName("已编码路径原样透传：Bedrock 的 %3A 不能被二次编码或解码")
    void joinUrlKeepsEncodedPath() {
        String path = "/model/anthropic.claude-sonnet-4-20250514-v1%3A0/converse";
        assertEquals("https://bedrock-runtime.us-east-1.amazonaws.com" + path,
                UpstreamUrlSupport.joinUrl("https://bedrock-runtime.us-east-1.amazonaws.com", path));
        assertEquals("https://bedrock-runtime.us-east-1.amazonaws.com/ep-1" + path,
                UpstreamUrlSupport.joinUrl("https://bedrock-runtime.us-east-1.amazonaws.com/ep-1", path));
    }

    @Test
    @DisplayName("base 为空时只返回路径，由调用方兜住配置缺失")
    void joinUrlWithBlankBase() {
        assertEquals("/v1/chat", UpstreamUrlSupport.joinUrl(null, "/v1/chat"));
        assertEquals("/v1/chat", UpstreamUrlSupport.joinUrl("  ", "v1/chat"));
    }
}
