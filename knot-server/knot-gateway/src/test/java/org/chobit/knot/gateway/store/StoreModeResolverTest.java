package org.chobit.knot.gateway.store;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 契约测试：业务域存储模式的归一口径。
 *
 * <p>归一只做三件事：未配置回落 caffeine、显式值去空白 + 小写归一、
 * 怪值直接抛异常（宁可启动失败，也不静默按单节点跑）。无历史键兼容层——
 * 配置键 {@code knot.<domain>.store} 一次定型，没有兼容对象。</p>
 *
 * <p><b>固化两条已下线的能力</b>（2026-10-15 决策：Redis 不降级，fail-hard）：
 * {@code fallback} 档与 fail-open 口径都已移除，故 {@code fallback} 现在是<b>非法值</b>
 * ——存量配置若还写着 {@code store=fallback}，必须启动失败让人看见，
 * 而不是被静默忽略。</p>
 */
class StoreModeResolverTest {

    private final StoreModeResolver resolver = new StoreModeResolver();

    @Test
    @DisplayName("未配置（null / 空白）回落 caffeine")
    void defaultsToCaffeineWhenUnset() {
        assertEquals(StoreMode.CAFFEINE, resolver.modeOf("traffic", null));
        assertEquals(StoreMode.CAFFEINE, resolver.modeOf("load", "   "),
                "空白串等同于未配置");
    }

    @Test
    @DisplayName("显式值做去空白 + 小写归一，逐域独立")
    void normalizesExplicitValue() {
        assertEquals(StoreMode.REDIS, resolver.modeOf("traffic", " REDIS "));
        assertEquals(StoreMode.CAFFEINE, resolver.modeOf("load", "Caffeine"));
    }

    @Test
    @DisplayName("无法识别的模式值直接启动失败，报错带域名与原值")
    void unknownModeFailsFast() {
        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> resolver.modeOf("load", "memcached"));
        assertTrue(ex.getMessage().contains("load"), "报错应指出域名，便于定位是哪一域配错");
        assertTrue(ex.getMessage().contains("memcached"), "报错应指出原值，便于运维定位");
    }

    @Test
    @DisplayName("存量 fallback 配置必须启动失败：降级档已下线，不能被静默忽略")
    void legacyFallbackFailsFast() {
        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> resolver.modeOf("traffic", "fallback"));
        assertTrue(ex.getMessage().contains("fallback"),
                "报错信息应指出原值，便于运维定位是旧配置残留");
    }

    @Test
    @DisplayName("词表只有 caffeine / redis 两档，code 与枚举一一对应")
    void modeVocabulary() {
        assertEquals(2, StoreMode.values().length, "只有两档：caffeine / redis");
        assertEquals("caffeine", StoreMode.CAFFEINE.code());
        assertEquals("redis", StoreMode.REDIS.code());
        assertEquals(StoreMode.REDIS, StoreMode.fromConfigured(StoreMode.REDIS.code()),
                "每档的 code 必须能归一回自身");
        assertEquals(StoreMode.CAFFEINE, StoreMode.fromConfigured(StoreMode.CAFFEINE.code()));
    }

    @Test
    @DisplayName("归一对无法识别的值返回 null，交由调用方决定报错或回落")
    void fromConfiguredReturnsNullForUnknown() {
        assertNull(StoreMode.fromConfigured("fallback"), "降级档已下线，不是合法模式");
        assertNull(StoreMode.fromConfigured("mysql"));
        assertNull(StoreMode.fromConfigured("  "));
        assertNull(StoreMode.fromConfigured(null));
    }
}
