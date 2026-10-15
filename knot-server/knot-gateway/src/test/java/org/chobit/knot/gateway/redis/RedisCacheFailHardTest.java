package org.chobit.knot.gateway.redis;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.serializer.RedisSerializer;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 契约测试：<b>fail-hard 是硬红线</b>。
 *
 * <p>2026-10-15 决策：Redis 不降级、不fail-open。共享存储承载的是跨节点全局口径，
 * 静默按放行值处理会造成看不见的损失（频控计 0 → 超发；在途记 0 → 已过载模型继续被加压）。
 * 因此本类固化两条断言：</p>
 * <ol>
 *   <li>Redis 故障时<b>必须</b>抛 {@link RedisUnavailableException}，绝不返回放行值；</li>
 *   <li>门面<b>不提供</b>任何 fail-open 入口（这是编译期保证——没有 {@code *Or} 方法，
 *       测试类里对 {@code evalOr} 的断言用反射固化，防止后人再加回来）。</li>
 * </ol>
 *
 * <p>⚠ JDK 25 + Mockito inline 不能 mock 具体类，故用子类覆写 {@code execute} 制造故障。</p>
 */
class RedisCacheFailHardTest {

    /**
     * 所有 execute 重载一律抛连接失败，模拟 Redis 不可用。
     */
    private static final class FailingTemplate extends StringRedisTemplate {

        @Override
        public <T> T execute(RedisScript<T> script, List<String> keys, Object... args) {
            throw new RedisConnectionFailureException("simulated redis down");
        }

        @Override
        public <T> T execute(RedisScript<T> script, RedisSerializer<?> argsSerializer,
                             RedisSerializer<T> resultSerializer, List<String> keys, Object... args) {
            throw new RedisConnectionFailureException("simulated redis down");
        }

        @Override
        public <T> T execute(RedisCallback<T> action) {
            throw new RedisConnectionFailureException("simulated redis down");
        }

        @Override
        public <T> T execute(RedisCallback<T> action, boolean exposeConnection) {
            throw new RedisConnectionFailureException("simulated redis down");
        }
    }

    private static final RedisScript<Long> SCRIPT = RedisScript.of("return 1", Long.class);

    @Test
    @DisplayName("Redis 故障时抛 RedisUnavailableException，绝不返回放行值")
    void evalThrowsInsteadOfReturningFallback() {
        RedisCache cache = new RedisCache(new FailingTemplate());

        RedisUnavailableException ex = assertThrows(RedisUnavailableException.class,
                () -> cache.eval("traffic", "addAndGet", SCRIPT, List.of("knot:traffic:app:1")));
        assertEquals("traffic", ex.getDomain());
        assertEquals("addAndGet", ex.getOperation());
        assertEquals("knot:traffic:app:1", ex.getKey());
    }

    @Test
    @DisplayName("异常必须带着底层 cause，便于定位是连接问题还是脚本问题")
    void exceptionKeepsCause() {
        RedisCache cache = new RedisCache(new FailingTemplate());

        RedisUnavailableException ex = assertThrows(RedisUnavailableException.class,
                () -> cache.value("load", "inflight", "knot:load:m1"));
        assertTrue(ex.getCause() instanceof RedisConnectionFailureException);
    }

    @Test
    @DisplayName("门面不得存在 fail-open / 探针入口：方法名一旦回归即为红线破防")
    void noFailOpenEntryPointsExist() {
        List<String> methodNames = java.util.Arrays.stream(RedisCache.class.getDeclaredMethods())
                .map(java.lang.reflect.Method::getName)
                .toList();

        // 这些方法一旦被加回，就意味着有人重新引入了 fail-open 语义或降级探针
        for (String forbidden : List.of("evalOr", "valueOr", "hashEntriesOr", "deleteOr", "ping")) {
            assertFalse(methodNames.contains(forbidden),
                    "RedisCache 不应再有 fail-open / 探针入口: " + forbidden);
        }
    }

    @Test
    @DisplayName("错误日志节流不影响异常上抛：每次故障都照常抛")
    void throttlingDoesNotSwallowExceptions() {
        RedisCache cache = new RedisCache(new FailingTemplate());

        for (int i = 0; i < 50; i++) {
            // 同域同操作同键连打 50 次，节流只压日志条数，异常一次都不能少
            assertThrows(RedisUnavailableException.class,
                    () -> cache.eval("traffic", "addAndGet", SCRIPT, List.of("knot:traffic:app:1")));
        }
    }

    @Test
    @DisplayName("RedisUnavailableException 继承 RuntimeException，可穿透未捕获的业务代码上抛到 HTTP 层")
    void isUnchecked() {
        RedisCache cache = new RedisCache(new FailingTemplate());

        RuntimeException thrown = assertThrows(RuntimeException.class,
                () -> cache.eval("load", "enter", SCRIPT, List.of("knot:load:m1")));
        assertSame(RedisUnavailableException.class, thrown.getClass());
    }
}