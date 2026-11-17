package org.chobit.knot.admin.service.schedule;

import org.chobit.knot.common.constants.enums.EnabledStatusEnum;
import org.chobit.knot.common.constants.enums.ScheduledTaskRunStatusEnum;
import org.chobit.knot.svc.dto.system.ScheduledTaskDto;
import org.chobit.knot.svc.dto.system.ScheduledTaskRunDto;
import org.chobit.knot.svc.service.ScheduledTaskService;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.annotation.Transactional;

import java.lang.reflect.Method;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 定时任务执行互斥：{@code ks_scheduled_tasks} 租约的语义与执行器的事务边界。
 *
 * <p>JDK 25 下 Mockito inline 无法 mock 具体类，故 {@link ScheduledTaskService} 与
 * {@link NodeIdentity} 都用桩子类承接：桩子按 {@code lock_owner} / {@code lease_until}
 * 两列的真实判据（空或过期才可抢、只清自己的）在内存里维护租约。</p>
 *
 * <p>SQL 文本本身由 {@code knot-dal} 的 {@code ScheduledTaskLeaseSqlTest} 守；这里守的是
 * 「执行器怎么用它」——同一拍只有一个节点真正执行、失败也要放租约、执行器不再开事务。</p>
 */
class ScheduledTaskExecutorLeaseTest {

    /** 内存租约行，字段与 {@code ks_scheduled_tasks.lock_owner / lease_until} 一一对应。 */
    private record Lease(String owner, LocalDateTime until) {
    }

    /** 桩 {@link ScheduledTaskService}：只实现执行器用到的那几个方法，租约按真实判据维护。 */
    static class StubTaskService extends ScheduledTaskService {

        ScheduledTaskDto task;
        final Map<String, Lease> leases = new HashMap<>();
        final List<Integer> finishedStatuses = new ArrayList<>();
        int acquireCalls;
        int releaseCalls;

        StubTaskService() {
            // 桩子不碰数据库，Mapper 传 null 即可
            super(null);
        }

        @Override
        public ScheduledTaskDto getTaskByCode(String taskCode) {
            return task;
        }

        @Override
        public boolean acquireLease(String taskCode, String owner) {
            acquireCalls++;
            Lease held = leases.get(taskCode);
            LocalDateTime now = LocalDateTime.now();
            if (held == null || held.until() == null || !held.until().isAfter(now)) {
                leases.put(taskCode, new Lease(owner, now.plusMinutes(ScheduledTaskService.LEASE_MINUTES)));
                return true;
            }
            return held.owner().equals(owner);
        }

        @Override
        public void releaseLease(String taskCode, String owner) {
            releaseCalls++;
            Lease held = leases.get(taskCode);
            if (held != null && held.owner().equals(owner)) {
                leases.remove(taskCode);
            }
        }

        @Override
        public Long insertRun(ScheduledTaskRunDto run) {
            return 1L;
        }

        @Override
        public void finishRun(Long id, Integer status, LocalDateTime endTime, Long durationMs,
                              Integer affectedRows, String errorMsg) {
            finishedStatuses.add(status);
        }

        @Override
        public void updateTaskFireTime(Long id, LocalDateTime lastFireAt, LocalDateTime nextFireAt) {
            // 执行时间由调度器负责，本测试不关心
        }

        boolean holdsLease(String taskCode) {
            return leases.containsKey(taskCode);
        }
    }

    /** 桩节点标识：直接给定 id，绕开主机名与 pid 探测。 */
    static class StubNodeIdentity extends NodeIdentity {
        StubNodeIdentity(String nodeId) {
            super(nodeId);
        }
    }

    /** 桩 handler：记录调用次数、可在执行途中插入另一节点的动作、可切换成抛异常。 */
    static class StubHandler implements ScheduledTaskHandler {

        private final String code;
        int invocations;
        RuntimeException error;
        Runnable duringExecute;

        StubHandler(String code) {
            this.code = code;
        }

        @Override
        public String handlerCode() {
            return code;
        }

        @Override
        public ScheduledTaskResult execute() {
            invocations++;
            if (duringExecute != null) {
                duringExecute.run();
            }
            if (error != null) {
                throw error;
            }
            return new ScheduledTaskResult(3, "ok");
        }
    }

    @Test
    void anotherNodeSkipsWhileTheLeaseIsHeld() {
        StubTaskService service = new StubTaskService();
        service.task = task("SINGLE");
        StubHandler handlerA = new StubHandler("H_STUB");
        StubHandler handlerB = new StubHandler("H_STUB");
        ScheduledTaskExecutor nodeA = executor(service, "node-a", handlerA);
        ScheduledTaskExecutor nodeB = executor(service, "node-b", handlerB);
        // 单线程里模拟「同一拍」：nodeA 执行途中 nodeB 才到点，此时租约在 node-a 手上
        handlerA.duringExecute = () -> nodeB.execute("T1", "SCHEDULE", null);

        nodeA.execute("T1", "SCHEDULE", null);

        assertEquals(1, handlerA.invocations);
        assertEquals(0, handlerB.invocations, "租约未到期时另一节点不得执行");
        assertEquals(2, service.acquireCalls, "两个节点都尝试过抢租约");
        assertFalse(service.holdsLease("T1"), "执行完必须放租约，否则下一拍永远抢不到");
    }

    @Test
    void liveLeaseIsNotClearedByANodeThatFailedToTakeIt() {
        StubTaskService service = new StubTaskService();
        service.task = task("SINGLE");
        service.leases.put("T1", new Lease("node-a", LocalDateTime.now().plusMinutes(5)));
        StubHandler handler = new StubHandler("H_STUB");
        ScheduledTaskExecutor nodeB = executor(service, "node-b", handler);

        nodeB.execute("T1", "SCHEDULE", null);

        assertEquals(0, handler.invocations, "别人的租约未到期时不该执行");
        assertEquals(0, service.releaseCalls, "没抢到就不能释放——那是别人的租约");
        assertEquals("node-a", service.leases.get("T1").owner(), "别人的租约必须原样留在表里");
    }

    @Test
    void expiredLeaseIsClaimableByAnotherNode() {
        StubTaskService service = new StubTaskService();
        service.task = task("SINGLE");
        service.leases.put("T1", new Lease("node-a", LocalDateTime.now().minusSeconds(1)));
        StubHandler handler = new StubHandler("H_STUB");
        ScheduledTaskExecutor nodeB = executor(service, "node-b", handler);

        nodeB.execute("T1", "SCHEDULE", null);

        assertEquals(1, handler.invocations, "租约过期后另一节点应能接手");
        assertFalse(service.holdsLease("T1"));
    }

    @Test
    void failureIsRecordedAndLeaseIsReleasedForTheNextFire() {
        StubTaskService service = new StubTaskService();
        service.task = task("SINGLE");
        StubHandler handler = new StubHandler("H_STUB");
        handler.error = new IllegalStateException("boom");
        ScheduledTaskExecutor nodeA = executor(service, "node-a", handler);

        nodeA.execute("T1", "SCHEDULE", null);

        assertEquals(List.of(ScheduledTaskRunStatusEnum.FAILURE.code()), service.finishedStatuses,
                "handler 抛异常要落一条 FAILURE 流水");
        assertFalse(service.holdsLease("T1"), "失败也必须放租约，否则任务从此不再执行");

        handler.error = null;
        nodeA.execute("T1", "SCHEDULE", null);
        assertEquals(2, handler.invocations, "失败之后的下一拍仍应能执行");
    }

    @Test
    void broadcastModeRunsEveryNodeWithoutTakingTheLease() {
        StubTaskService service = new StubTaskService();
        service.task = task("BROADCAST");
        StubHandler handler = new StubHandler("H_STUB");
        ScheduledTaskExecutor nodeA = executor(service, "node-a", handler);
        ScheduledTaskExecutor nodeB = executor(service, "node-b", handler);

        nodeA.execute("T1", "SCHEDULE", null);
        nodeB.execute("T1", "SCHEDULE", null);

        assertEquals(0, service.acquireCalls, "BROADCAST 不抢租约");
        assertEquals(2, handler.invocations, "BROADCAST 每个节点各跑一次");
    }

    @Test
    void executeDoesNotOpenATransaction() throws NoSuchMethodException {
        Method execute = ScheduledTaskExecutor.class.getMethod(
                "execute", String.class, String.class, Date.class);
        assertNull(execute.getAnnotation(Transactional.class),
                "execute 不能再带 @Transactional：handler 会被关进长事务，抢租约/释放也随之与连接绑定");
    }

    /** 桩子必须按「空或过期才可抢」判，否则上面几个用例其实在测一句恒真的实现。 */
    @Test
    void stubItselfHonoursTheLeaseRule() {
        StubTaskService service = new StubTaskService();
        assertTrue(service.acquireLease("T1", "node-a"));
        assertFalse(service.acquireLease("T1", "node-b"));
        assertTrue(service.acquireLease("T1", "node-a"), "同一节点重入应视为仍持有");
        service.releaseLease("T1", "node-b");
        assertTrue(service.holdsLease("T1"), "非持有者释放不应生效");
        service.releaseLease("T1", "node-a");
        assertFalse(service.holdsLease("T1"));
    }

    private static ScheduledTaskExecutor executor(StubTaskService service, String nodeId,
                                                  ScheduledTaskHandler handler) {
        return new ScheduledTaskExecutor(service, new StubNodeIdentity(nodeId), List.of(handler));
    }

    private static ScheduledTaskDto task(String executionMode) {
        return new ScheduledTaskDto(
                1L,
                "T1",
                "桩任务",
                "H_STUB",
                "0 */5 * * * ?",
                executionMode,
                EnabledStatusEnum.ENABLED.code(),
                null,
                null,
                null,
                null,
                null);
    }
}
