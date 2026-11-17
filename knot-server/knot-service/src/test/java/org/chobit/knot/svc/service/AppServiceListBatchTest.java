package org.chobit.knot.svc.service;

import com.github.pagehelper.Page;
import org.chobit.knot.common.constants.enums.TrafficResourceTypeEnum;
import org.chobit.knot.common.model.PageRequest;
import org.chobit.knot.dal.entity.AppEntity;
import org.chobit.knot.dal.mapper.AppMapper;
import org.chobit.knot.svc.converter.AppConverter;
import org.chobit.knot.svc.dto.app.AppDto;
import org.chobit.knot.svc.model.TrafficPolicies;
import org.chobit.knot.svc.service.support.ResourceTrafficPolicySupport;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 应用列表的频控/额度查询必须是整页一次批量取，不能逐行 {@code load}。
 * 逐行会让单页 20 条放大成 2 + 20×4 次 DB 往返。
 *
 * <p>JDK 25 下 Mockito 不能 mock 具体类，{@link ResourceTrafficPolicySupport} 用子类覆写做替身。</p>
 */
class AppServiceListBatchTest {

    private final AppMapper appMapper = mock(AppMapper.class);
    private final AppConverter appConverter = mock(AppConverter.class);
    private final StubTrafficPolicySupport traffic = new StubTrafficPolicySupport();

    @Test
    void loadsTrafficPoliciesOnceForTheWholePage() {
        Page<AppEntity> page = new Page<>(1, 20);
        page.setTotal(2);
        page.add(app(1L));
        page.add(app(2L));
        when(appMapper.list(null)).thenReturn(page);
        when(appConverter.toDto(any(AppEntity.class))).thenAnswer(invocation -> dto(invocation.getArgument(0)));

        new AppService(appMapper, appConverter, null, traffic, null)
                .list(new PageRequest(1, 20), null);

        assertEquals(1, traffic.batchCalls, "整页只应批量取一次");
        assertEquals(0, traffic.singleCalls, "逐行 load 必须彻底消失");
        assertEquals(TrafficResourceTypeEnum.APP.code(), traffic.batchResourceType);
        assertEquals(List.of(1L, 2L), traffic.batchIds);
    }

    private AppEntity app(Long id) {
        AppEntity entity = new AppEntity();
        entity.setId(id);
        entity.setAppCode("app_" + id);
        return entity;
    }

    private AppDto dto(AppEntity entity) {
        return new AppDto(entity.getId(), entity.getAppCode(), null, null, null,
                null, null, null, null, List.of(), 0L);
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
