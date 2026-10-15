package org.chobit.knot.gateway.redis;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 契约测试：模式归一与旧键兼容。
 *
 * <p>兼容逻辑没有 Spring 容器可测，因此用 {@link MockEnvironment} 驱动，
 * 断言「新键 → 旧键 → 默认」三级优先级，以及「未显式设置」与「显式配置」的可区分性。</p>
 *
 * <p><b>固化两条已下线的能力</b>（2026-10-15 决策：Redis 不降级，fail-hard）：
 * {@code fallback} 档与 fail-open 口径都已移除，故 {@code fallback} 现在是<b>非法值</b>
 * ——存量配置若还写着 {@code knot.*.store=fallback}，必须启动失败让人看见，
 * 而不是被静默忽略。</p>
 */
class RedisStoreModeResolverTest {

    private RedisStoreModeResolver resolverOf(MockEnvironment environment) {
        return new RedisStoreModeResolver(environment);
    }

    @Test
    @DisplayName("新键未设置时读旧键；显式值做去空白 + 小写归一")
    void newKeyWins() {
        MockEnvironment env = new MockEnvironment();
        env.setProperty("knot.traffic.store", "redis");
        RedisStoreModeResolver resolver = resolverOf(env);

        assertEquals(RedisStoreModes.REDIS, resolver.modeOf("traffic", null),
                "新键未设置时应读旧键");
        assertEquals(RedisStoreModes.REDIS, resolver.modeOf("load", " REDIS "),
                "显式值应做去空白 + 小写归一");
    }

    @Test
    @DisplayName("新键未设置时回落旧键，并支持逐域独立回落")
    void fallsBackToLegacyKey() {
        MockEnvironment env = new MockEnvironment();
        env.setProperty("knot.traffic.store", "redis");
        env.setProperty("knot.load.store", "redis");
        RedisStoreModeResolver resolver = resolverOf(env);

        assertEquals(RedisStoreModes.REDIS, resolver.modeOf("traffic", null));
        assertEquals(RedisStoreModes.REDIS, resolver.modeOf("load", null));
    }

    @Test
    @DisplayName("新旧都未设置时取默认 caffeine")
    void defaultsToCaffeine() {
        RedisStoreModeResolver resolver = resolverOf(new MockEnvironment());

        assertEquals(RedisStoreModes.CAFFEINE, resolver.modeOf("traffic", null));
        assertEquals(RedisStoreModes.CAFFEINE, resolver.modeOf("load", null));
    }

    @Test
    @DisplayName("显式 caffeine 不会被旧键覆盖")
    void explicitCaffeineBeatsLegacy() {
        MockEnvironment env = new MockEnvironment();
        env.setProperty("knot.traffic.store", "redis");
        RedisStoreModeResolver resolver = resolverOf(env);

        assertEquals(RedisStoreModes.CAFFEINE, resolver.modeOf("traffic", RedisStoreModes.CAFFEINE),
                "显式配 caffeine 是有效选择，不能被旧键顶掉");
    }

    @Test
    @DisplayName("无法识别的模式值直接启动失败，不静默按单节点跑")
    void unknownModeFailsFast() {
        RedisStoreModeResolver resolver = resolverOf(new MockEnvironment());

        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> resolver.modeOf("traffic", "memcached"));
        assertTrue(ex.getMessage().contains("memcached"));
    }

    @Test
    @DisplayName("存量 fallback 配置必须启动失败：降级档已下线，不能被静默忽略")
    void legacyFallbackFailsFast() {
        MockEnvironment env = new MockEnvironment();
        env.setProperty("knot.traffic.store", "fallback");
        RedisStoreModeResolver resolver = resolverOf(env);

        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> resolver.modeOf("traffic", null));
        assertTrue(ex.getMessage().contains("fallback"),
                "报错信息应指出原值，便于运维定位是旧配置残留");
    }

    @Test
    @DisplayName("模式常量表：只有 caffeine / redis 两档")
    void modeConstants() {
        assertEquals("redis", RedisStoreModes.normalize("  REDIS "));
        assertNull(RedisStoreModes.normalize("  "));
        assertTrue(RedisStoreModes.valid(RedisStoreModes.CAFFEINE));
        assertTrue(RedisStoreModes.valid(RedisStoreModes.REDIS));
        assertFalse(RedisStoreModes.valid("fallback"), "降级档已下线");
        assertFalse(RedisStoreModes.valid("mysql"));
    }
}