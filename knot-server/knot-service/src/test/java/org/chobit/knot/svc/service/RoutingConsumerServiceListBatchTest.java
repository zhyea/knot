package org.chobit.knot.svc.service;

import com.github.pagehelper.Page;
import org.chobit.knot.common.constants.enums.TrafficResourceTypeEnum;
import org.chobit.knot.common.model.PageRequest;
import org.chobit.knot.dal.entity.CodeCountRow;
import org.chobit.knot.dal.entity.RoutingConsumerEntity;
import org.chobit.knot.dal.mapper.RoutingConsumerMapper;
import org.chobit.knot.svc.dto.routing.RoutingConsumerDto;
import org.chobit.knot.svc.model.TrafficPolicies;
import org.chobit.knot.svc.service.support.ResourceTrafficPolicySupport;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 路由消费者列表：频控/额度一次批量取、绑定处数一次分组计数，逐行查询必须消失。
 *
 * <p>JDK 25 下 Mockito 不能 mock 具体类，{@link ResourceTrafficPolicySupport} 用子类覆写做替身。</p>
 */
class RoutingConsumerServiceListBatchTest {

    private final RoutingConsumerMapper consumerMapper = mock(RoutingConsumerMapper.class);
    private final StubTrafficPolicySupport traffic = new StubTrafficPolicySupport();

    @Test
    void loadsTrafficAndRuleCountsInOneQueryPerConcern() {
        Page<RoutingConsumerEntity> page = new Page<>(1, 20);
        page.setTotal(2);
        page.add(consumer(1L, "c_1"));
        page.add(consumer(2L, "c_2"));
        when(consumerMapper.list(null)).thenReturn(page);
        when(consumerMapper.countRulesByConsumerCodes(anyList())).thenReturn(List.of(count("c_1", 3L)));

        List<RoutingConsumerDto> rows = new RoutingConsumerService(consumerMapper, null, traffic)
                .list(new PageRequest(1, 20), null)
                .list();

        assertEquals(3L, rows.get(0).ruleCount(), "绑定处数取自分组计数");
        assertEquals(0L, rows.get(1).ruleCount(), "结果里没有的编码按 0 兜底");
        assertEquals(1, traffic.batchCalls, "整页只应批量取一次");
        assertEquals(0, traffic.singleCalls, "逐行 load 必须彻底消失");
        assertEquals(TrafficResourceTypeEnum.ROUTING_CONSUMER.code(), traffic.batchResourceType);
        verify(consumerMapper, times(1)).countRulesByConsumerCodes(anyList());
        verify(consumerMapper, never()).countRulesByConsumerCode(anyString());
    }

    private RoutingConsumerEntity consumer(Long id, String code) {
        RoutingConsumerEntity entity = new RoutingConsumerEntity();
        entity.setId(id);
        entity.setConsumerCode(code);
        entity.setName("消费者" + id);
        return entity;
    }

    private CodeCountRow count(String code, long total) {
        CodeCountRow row = new CodeCountRow();
        row.setCode(code);
        row.setTotal(total);
        return row;
    }

    private static final class StubTrafficPolicySupport extends ResourceTrafficPolicySupport {

        private int batchCalls;
        private int singleCalls;
        private String batchResourceType;
        private List<Long> batchIds = new ArrayList<>();

        private StubTrafficPolicySupport() {
            super(null, null, null, null);
        }

        @Override
        public Map<Long, TrafficPolicies> loadBatch(String resourceType, List<Long> resourceIds) {
            batchCalls++;
            batchResourceType = resourceType;
            batchIds = resourceIds;
            return Map.of();
        }

        @Override
        public TrafficPolicies load(String resourceType, Long resourceId) {
            singleCalls++;
            return null;
        }
    }
}
