package org.chobit.knot.gateway.aspect;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.reflect.MethodSignature;
import org.chobit.knot.gateway.annotation.OperationLog;
import org.chobit.knot.gateway.entity.OperationLogEntity;
import org.chobit.knot.gateway.mapper.OperationLogMapper;
import org.chobit.knot.gateway.service.OperationLogService;
import org.chobit.knot.gateway.util.JsonKit;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;

import java.lang.reflect.Method;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link OperationLogAspect} 旧值/新值快照与无变化更新抑制的回归单测。
 *
 * <p>覆盖「预设用例更新后不做任何调整也会记日志、且旧值为空」这一缺陷的两条根因：
 * 更新前用 {@code oldValueSpel} 取到真实旧值（不再为空），更新后快照与旧值一致时
 * 抑制日志（不再产生无意义审计）。注解与 SpEL 镜像
 * {@code TestRequestPresetController#update} 的接线方式。</p>
 */
class OperationLogAspectTest {

    private final OperationLogMapper logMapper = mock(OperationLogMapper.class);

    @Test
    void suppressesUpdateLogWhenSnapshotUnchanged() throws Throwable {
        // 前后两次快照内容相同但为不同实例：应依据序列化后的 JSON 判等，而非对象引用
        OperationLogAspect aspect = aspectWith(
                new AuditStub(snapshot("预设A", "PAYLOAD"), snapshot("预设A", "PAYLOAD")));

        aspect.recordLog(joinPoint(7L), annotationOf("update"));

        verify(logMapper, never()).insert(any());
    }

    @Test
    void recordsUpdateLogWithRealOldAndNewValue() throws Throwable {
        Map<String, Object> before = snapshot("预设A", "OLD-BODY");
        Map<String, Object> after = snapshot("预设A", "NEW-BODY");
        OperationLogAspect aspect = aspectWith(new AuditStub(before, after));

        aspect.recordLog(joinPoint(7L), annotationOf("update"));

        OperationLogEntity saved = captureSavedLog();
        assertNotNull(saved.getOldValue(), "旧值不应为空");
        assertEquals(JsonKit.toJson(before), saved.getOldValue());
        assertEquals(JsonKit.toJson(after), saved.getNewValue());
    }

    @Test
    void doesNotSuppressNonUpdateOperationsWithEqualSnapshots() throws Throwable {
        OperationLogAspect aspect = aspectWith(
                new AuditStub(snapshot("预设A", "PAYLOAD"), snapshot("预设A", "PAYLOAD")));

        aspect.recordLog(joinPoint(7L), annotationOf("delete"));

        assertNotNull(captureSavedLog().getOldValue());
    }

    private OperationLogAspect aspectWith(AuditStub audit) {
        DefaultListableBeanFactory beanFactory = new DefaultListableBeanFactory();
        beanFactory.registerSingleton("snapshotSupport", audit);
        return new OperationLogAspect(new OperationLogService(logMapper), beanFactory);
    }

    private OperationLogEntity captureSavedLog() {
        ArgumentCaptor<OperationLogEntity> captor = ArgumentCaptor.forClass(OperationLogEntity.class);
        verify(logMapper).insert(captor.capture());
        return captor.getValue();
    }

    private ProceedingJoinPoint joinPoint(Long id) throws Throwable {
        ProceedingJoinPoint joinPoint = mock(ProceedingJoinPoint.class);
        MethodSignature signature = mock(MethodSignature.class);
        when(joinPoint.getSignature()).thenReturn(signature);
        when(signature.getParameterNames()).thenReturn(new String[]{"id", "request"});
        when(joinPoint.getArgs()).thenReturn(new Object[]{id, new Object()});
        when(joinPoint.proceed()).thenReturn(new PresetResult("预设A"));
        return joinPoint;
    }

    private static OperationLog annotationOf(String methodName) throws Exception {
        Method method = PresetFixture.class.getMethod(methodName, Long.class, Object.class);
        return method.getAnnotation(OperationLog.class);
    }

    private static Map<String, Object> snapshot(String name, String requestBody) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("id", 7L);
        map.put("name", name);
        map.put("requestBody", requestBody);
        return map;
    }

    /** 每次求值按顺序返回一个快照，模拟切面在 proceed 前后各读一次持久化状态。 */
    static class AuditStub {
        private final Deque<Map<String, Object>> values = new ArrayDeque<>();

        @SafeVarargs
        AuditStub(Map<String, Object>... snapshots) {
            for (Map<String, Object> snapshot : snapshots) {
                values.add(snapshot);
            }
        }

        public Map<String, Object> auditSnapshot(Long id) {
            return values.size() > 1 ? values.poll() : values.peek();
        }
    }

    /** 镜像 TestRequestPresetController 的注解接线（旧值/新值均取持久化快照）。 */
    static class PresetFixture {
        @OperationLog(module = "routing", operation = "UPDATE", entityType = "TestRequestPreset",
                entityId = "#id",
                entityNameAfter = "#result.name()",
                oldValueSpel = "@snapshotSupport.auditSnapshot(#p0)",
                newValueSpel = "@snapshotSupport.auditSnapshot(#p0)")
        public PresetResult update(Long id, Object request) {
            return new PresetResult("预设A");
        }

        @OperationLog(module = "routing", operation = "DELETE", entityType = "TestRequestPreset",
                entityId = "#id",
                oldValueSpel = "@snapshotSupport.auditSnapshot(#p0)",
                newValueSpel = "@snapshotSupport.auditSnapshot(#p0)")
        public PresetResult delete(Long id, Object request) {
            return new PresetResult("预设A");
        }
    }

    record PresetResult(String name) {
    }
}
