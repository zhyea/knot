package org.chobit.knot.gateway.adapter.auth;

import org.chobit.knot.gateway.adapter.upstream.UpstreamAccess;
import org.chobit.knot.gateway.adapter.upstream.UpstreamRequestContext;
import org.chobit.knot.gateway.constants.enums.ModelApiProtocolEnum;
import org.chobit.knot.gateway.constants.enums.ProviderCredentialTypeEnum;
import org.chobit.knot.gateway.entity.ProviderAccountEntity;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;

import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 通用接入策略的默认行为：{@code prepare()} 只认账户配置的上游地址。
 *
 * <p>这组用例是「泛化契约里塞特例」的防回归门禁：凭据里存在 {@code region} /
 * {@code endpointType} 时，<b>通用策略不得推导 AWS 端点</b>——那是
 * {@code AwsSigV4AuthApplier} 的职责，有它自己的用例守着。</p>
 */
class UpstreamAuthApplierDefaultTest {

    private final UpstreamAuthApplier bearer = new UpstreamAuthApplier() {
        @Override
        public String code() {
            return "TEST_BEARER";
        }

        @Override
        public String label() {
            return "测试用 Bearer";
        }

        @Override
        public Set<ProviderCredentialTypeEnum> credentialTypes() {
            return Set.of(ProviderCredentialTypeEnum.API_KEY);
        }

        @Override
        public void apply(HttpHeaders headers, UpstreamRequestContext context) {
            headers.setBearerAuth("sk-test");
        }
    };

    @Test
    @DisplayName("prepare 同时产出账户 base URL 与静态头，执行器不再各自解析")
    void prepareReturnsAccountBaseUrlAndStaticHeaders() {
        UpstreamRequestContext context = context(account("https://api.openai.com"), Map.of());

        UpstreamAccess access = bearer.prepare(context);

        assertEquals("https://api.openai.com", access.baseUrl());
        assertEquals("Bearer sk-test", access.headers().getFirst(HttpHeaders.AUTHORIZATION));
    }

    @Test
    @DisplayName("凭据里有 region / endpointType 也不得推导 AWS 端点")
    void baseUrlIgnoresAwsCredentialFields() {
        UpstreamRequestContext context = context(account(null), Map.of(
                "region", "us-east-1",
                "endpointType", "bedrock-runtime"));

        assertNull(bearer.baseUrl(context),
                "通用策略读 AWS 字段就等于把供应商特例塞回通用契约");
    }

    @Test
    @DisplayName("账户为空的 base URL 原样返回 null，由执行器统一报配置错误")
    void baseUrlIsNullWhenAccountHasNoBaseUrl() {
        assertNull(bearer.baseUrl(context(account(null), Map.of())));
        assertNull(bearer.baseUrl(context(null, Map.of())), "provider 缺失不能 NPE");
        assertNull(bearer.baseUrl(null));
    }

    @Test
    @DisplayName("默认要求账户填写 base URL；不要求 payload 定稿")
    void defaultsAreConservative() {
        assertTrue(bearer.requiresBaseUrl());
        assertTrue(!bearer.requiresFinalizedPayload());
    }

    @Test
    @DisplayName("返回的头是只读快照，策略之外的代码不能偷偷改鉴权头")
    void preparedHeadersAreReadOnly() {
        UpstreamAccess access = bearer.prepare(context(account("https://api.openai.com"), Map.of()));
        assertThrows(UnsupportedOperationException.class,
                () -> access.headers().set(HttpHeaders.AUTHORIZATION, "Bearer hacked"),
                "UpstreamAccess 的头必须是只读快照");
    }

    private static ProviderAccountEntity account(String baseUrl) {
        ProviderAccountEntity provider = new ProviderAccountEntity();
        provider.setCode("test-account");
        provider.setBaseUrl(baseUrl);
        return provider;
    }

    private static UpstreamRequestContext context(ProviderAccountEntity provider, Map<String, Object> authConfig) {
        return new UpstreamRequestContext(ModelApiProtocolEnum.CHAT_COMPLETIONS, Map.of(), null,
                null, provider, authConfig, null, null, null);
    }
}
