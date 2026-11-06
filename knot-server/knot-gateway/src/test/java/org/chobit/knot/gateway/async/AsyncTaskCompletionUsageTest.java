package org.chobit.knot.gateway.async;

import org.chobit.knot.adapter.model.usage.NormalizedUsage;
import org.chobit.knot.adapter.model.usage.UsageAccounting;
import org.chobit.knot.gateway.model.ResolvedRouting;
import org.chobit.knot.gateway.traffic.CaffeineTrafficCounterStore;
import org.chobit.knot.gateway.traffic.GatewayTrafficGuard;
import org.chobit.knot.gateway.traffic.TrafficCounterKeys;
import org.chobit.knot.gateway.traffic.TrafficPolicySource;
import org.chobit.knot.gateway.traffic.TrafficUsage;
import org.chobit.knot.svc.dto.routing.RoutingRuleTargetDto;
import org.chobit.knot.svc.model.TrafficPolicies;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.ZoneId;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * 异步终态记账必须使用提交时落下的资源主键，不能因为任务表不存 API key 就整笔漏记。
 */
class AsyncTaskCompletionUsageTest {

    @Test
    @DisplayName("终态成功：按任务快照里的路由/应用/消费者/模型 id 记一次实际用量")
    void recordsActualUsageWithPersistedResourceIds() {
        RecordingGuard guard = new RecordingGuard();
        AsyncTaskCompletion completion = new AsyncTaskCompletion(null, null, null, null,
                "knot.gateway.traffic", guard);

        completion.recordUsage(null, context(), usage());

        assertEquals(1, guard.records);
        assertEquals(1L, guard.routing.ruleId());
        assertEquals(2L, guard.routing.consumerId());
        assertEquals(3L, guard.routing.routingContext().app().id());
        assertEquals(4L, guard.target.targetId());
        assertEquals("0.8", guard.usage.cost().toPlainString());
        assertEquals("USD", guard.usage.currency());
    }

    @Test
    @DisplayName("失败/超时没有实际用量：不记账")
    void nullUsageDoesNotRecord() {
        RecordingGuard guard = new RecordingGuard();
        AsyncTaskCompletion completion = new AsyncTaskCompletion(null, null, null, null,
                "knot.gateway.traffic", guard);

        completion.recordUsage(null, context(), null);

        assertEquals(0, guard.records);
        assertNull(guard.usage);
    }

    private static AsyncTaskContext context() {
        return new AsyncTaskContext("tk", "rule-a", null, null, "req",
                "VIDEO_GENERATIONS", "model-a", "upstream-a", "pa-1", "pool-1", "https://x",
                1L, 2L, 3L, 4L);
    }

    private static UsageAccounting usage() {
        return UsageAccounting.of(null, null, null, null,
                new NormalizedUsage(0L, "0.8", "USD", "v1", List.of()));
    }

    private static final class RecordingGuard extends GatewayTrafficGuard {

        private int records;
        private ResolvedRouting routing;
        private RoutingRuleTargetDto target;
        private TrafficUsage usage;

        private RecordingGuard() {
            super(new TrafficPolicySource() {
                @Override
                public TrafficPolicies policiesOf(String resourceType, Long resourceId) {
                    return null;
                }

                @Override
                public Long providerAccountIdOf(String providerAccountCode) {
                    return 8L;
                }
            }, new CaffeineTrafficCounterStore(100L),
                    new TrafficCounterKeys("test", ZoneId.of("Asia/Shanghai")));
        }

        @Override
        public void record(ResolvedRouting routing, RoutingRuleTargetDto target,
                           TrafficUsage usage, TrafficCheckContext context) {
            records++;
            this.routing = routing;
            this.target = target;
            this.usage = usage;
        }
    }
}
