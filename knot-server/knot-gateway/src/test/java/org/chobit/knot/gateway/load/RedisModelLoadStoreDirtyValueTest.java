package org.chobit.knot.gateway.load;

import org.chobit.knot.gateway.redis.RedisCache;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.script.RedisScript;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * 在途存储的脏值口径：读数兜底为 0（不拥塞），但必须留下可观测证据。
 *
 * <p>在途读数是 {@code LEAST_LOAD} 的选路输入：脏值静默变 0 会让流量持续偏向一个
 * 其实很忙的目标，而且故障恢复后不会自动纠正。删掉 {@code markDirty} 调用后本用例变红。</p>
 */
class RedisModelLoadStoreDirtyValueTest {

    @Test
    @DisplayName("非数字在途值：读作 0，但计入脏值")
    void nonNumericInflightIsCounted() {
        RedisModelLoadStore store = new RedisModelLoadStore(new StubRedisCache("not-a-number", null), 300L);

        assertEquals(0L, store.inflight("load:model-a"));
        assertEquals(1L, store.dirtyValues());
    }

    @Test
    @DisplayName("批量读里的脏值逐条计数")
    void dirtyValuesInBatchAreCounted() {
        RedisModelLoadStore store = new RedisModelLoadStore(new StubRedisCache("bad", null), 300L);

        assertEquals(0L, store.inflightBatch(List.of("a", "b")).get("a"));
        assertEquals(2L, store.dirtyValues(), "两个键都脏，各计一次");
    }

    @Test
    @DisplayName("键不存在（null）：读作 0 且不算脏值")
    void missingKeyIsNotDirty() {
        RedisModelLoadStore store = new RedisModelLoadStore(new StubRedisCache(null, null), 300L);

        assertEquals(0L, store.inflight("load:model-a"));
        assertEquals(0L, store.dirtyValues());
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

        @Override
        public List<String> multiValue(String domain, String operation, List<String> keys) {
            return keys.stream().map(ignored -> value).toList();
        }
    }
}
