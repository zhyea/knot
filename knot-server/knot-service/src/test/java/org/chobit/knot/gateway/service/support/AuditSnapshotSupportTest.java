package org.chobit.knot.gateway.service.support;

import org.chobit.knot.gateway.error.BusinessException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link AuditSnapshotSupport} 单测：审计快照骨架的三条语义——
 * <ol>
 *   <li>id 为空直接 null，加载器根本不执行（SpEL 传 {@code #p0} 也不会走到 DB）；</li>
 *   <li>加载器抛 {@link BusinessException}（含「实体不存在」）吞成 null——
 *       审计采集永远不得改变业务请求的结果；</li>
 *   <li>加载器的其它 RuntimeException <b>不吞</b>——转换器真 bug 必须冒出来，
 *       吞了就等于把故障写进日志还假装成功。</li>
 * </ol>
 */
class AuditSnapshotSupportTest {

    @Test
    @DisplayName("id 非空：加载器结果原样透出")
    void returnsLoaderResultWhenIdPresent() {
        Map<String, Object> expected = Map.of("code", "APP-1");

        Map<String, Object> snapshot = AuditSnapshotSupport.capture(7L, id -> expected);

        assertSame(expected, snapshot, "快照内容必须由调用方加载器原样决定，骨架不做任何加工");
    }

    @Test
    @DisplayName("id 原样传给加载器：骨架不改写业务键")
    void passesIdToLoader() {
        Map<String, Object> snapshot = AuditSnapshotSupport.capture(42L, id -> Map.of("id", id));

        assertEquals(42L, snapshot.get("id"));
    }

    @Test
    @DisplayName("id 为 null：直接返回 null 且不执行加载器")
    void returnsNullWhenIdNull() {
        AtomicInteger calls = new AtomicInteger();

        Map<String, Object> snapshot = AuditSnapshotSupport.capture(null, id -> {
            calls.incrementAndGet();
            return Map.of();
        });

        assertEquals(null, snapshot);
        assertEquals(0, calls.get(), "id 为空时不应触达任何加载逻辑（含 DB）");
    }

    @Test
    @DisplayName("加载器抛 BusinessException（实体不存在）：吞成 null")
    void swallowsBusinessExceptionFromLoader() {
        Map<String, Object> snapshot = AuditSnapshotSupport.capture(7L, id -> {
            throw new BusinessException(org.chobit.knot.gateway.error.ErrorCode.NOT_FOUND, "不存在");
        });

        assertEquals(null, snapshot, "重复删除等场景下实体已不存在，审计采集不得因此改变业务结果");
    }

    @Test
    @DisplayName("加载器返回 null（实体缺失的直查形态）：原样透出")
    void passesThroughNullFromLoader() {
        Map<String, Object> snapshot = AuditSnapshotSupport.capture(7L, id -> null);

        assertEquals(null, snapshot);
    }

    @Test
    @DisplayName("加载器返回空 map（providerProfile 形态）：原样透出，不归一成 null")
    void passesThroughEmptyMapFromLoader() {
        Map<String, Object> snapshot = AuditSnapshotSupport.capture(7L, id -> Map.of());

        assertTrue(snapshot.isEmpty());
    }

    @Test
    @DisplayName("加载器抛非业务异常：不吞，原样抛出")
    void propagatesNonBusinessRuntimeException() {
        IllegalStateException boom = new IllegalStateException("converter bug");

        IllegalStateException thrown = assertThrows(IllegalStateException.class,
                () -> AuditSnapshotSupport.capture(7L, id -> {
                    throw boom;
                }),
                "只吞 BusinessException：转换器缺陷吞了就等于把故障洗成「快照为空」");

        assertSame(boom, thrown);
    }
}
