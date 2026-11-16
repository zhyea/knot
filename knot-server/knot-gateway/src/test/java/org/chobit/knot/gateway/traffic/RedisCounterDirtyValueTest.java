package org.chobit.knot.gateway.traffic;

import org.chobit.knot.gateway.redis.RedisCache;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.script.RedisScript;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * 计数存储的脏值口径：兜底值不变（缺省按 0 / 增量），但<b>必须留下可观测证据</b>。
 *
 * <p>「键不存在返回 null」是正常语义，不计数；「值不是数字」「Lua 返回 null」是异常，
 * 前者会被当成「还没产生用量」，后者会被当成增量——两者都会让频控与额度少计，
 * 静默吞掉等于没有告警。删掉 {@code markDirty} 调用后本用例立刻变红。</p>
 */
class RedisCounterDirtyValueTest {

    @Test
    @DisplayName("非数字值：仍返回 0，但计入脏值")
    void nonNumericValueIsCountedWhileFallingBackToZero() {
        RedisTrafficCounterStore store = new RedisTrafficCounterStore(new StubRedisCache("abc", null));

        assertEquals(0L, store.get("traffic:key"));
        assertEquals(1L, store.dirtyValues());
    }

    @Test
    @DisplayName("键不存在（null）：返回 0 且不算脏值")
    void missingKeyIsNotDirty() {
        RedisTrafficCounterStore store = new RedisTrafficCounterStore(new StubRedisCache(null, null));

        assertEquals(0L, store.get("traffic:key"));
        assertEquals(0L, store.dirtyValues());
    }

    @Test
    @DisplayName("Lua 返回 null：按增量兜底，但计入脏值")
    void nullLuaResultIsCounted() {
        RedisTrafficCounterStore store = new RedisTrafficCounterStore(new StubRedisCache(null, null));

        assertEquals(7L, store.addAndGet("traffic:key", 7L, 1L));
        assertEquals(1L, store.dirtyValues());
    }

    /** 只覆写读写方法：构造器纯赋值，可传 null 模板。 */
    private static final class StubRedisCache extends RedisCache {

        private final String value;
        private final Long evalResult;

        private StubRedisCache(String value, Long evalResult) {
            super(null);
            this.value = value;
            this.evalResult = evalResult;
        }

        @Override
        @SuppressWarnings("unchecked")
        public <T> T eval(String domain, String operation, RedisScript<T> script, List<String> keys,
                          Object... args) {
            return (T) evalResult;
        }

        @Override
        public String value(String domain, String operation, String key) {
            return value;
        }
    }
}
