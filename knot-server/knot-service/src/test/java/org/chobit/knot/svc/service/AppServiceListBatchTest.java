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
 * 列表与详情的数据边界（铁律 12）：列表只给列表列，不加载额度 / 频控策略；
 * 策略只在按 id 取详情时加载。
 *
 * <p>JDK 25 下 Mockito 不能 mock 具体类，{@link ResourceTrafficPolicySupport} 用子类覆写做替身。</p>
 */
class AppServiceListBatchTest {

    private final AppMapper appMapper = mock(AppMapper.class);
    private final AppConverter appConverter = mock(AppConverter.class);
    private final StubTrafficPolicySupport traffic = new StubTrafficPolicySupport();

    @Test
    void listDoesNotLoadTrafficPolicies() {
        Page<AppEntity> page = new Page<>(1, 20);
        page.setTotal(2);
        page.add(app(1L));
        page.add(app(2L));
        when(appMapper.list(null)).thenReturn(page);
        when(appConverter.toDtoList(any())).thenReturn(List.of(dto(1L), dto(2L)));
        when(appConverter.toDto(any(AppEntity.class)))
                .thenAnswer(invocation -> dto(((AppEntity) invocation.getArgument(0)).getId()));

        new AppService(appMapper, appConverter, null, traffic, null)
                .list(new PageRequest(1, 20), null);

        assertEquals(0, traffic.batchCalls, "列表不得批量加载仅供编辑的策略");
        assertEquals(0, traffic.singleCalls, "列表也不得逐行加载策略");
    }

    @Test
    void detailLoadsTrafficPoliciesById() {
        when(appMapper.getById(1L)).thenReturn(app(1L));
        when(appConverter.toDto(any(AppEntity.class)))
                .thenAnswer(invocation -> dto(((AppEntity) invocation.getArgument(0)).getId()));

        new AppService(appMapper, appConverter, null, traffic, null).getById(1L);

        assertEquals(1, traffic.singleCalls, "详情必须按 id 加载一次策略");
        assertEquals(0, traffic.batchCalls, "单条详情不需要批量加载");
        assertEquals(TrafficResourceTypeEnum.APP.code(), traffic.singleResourceType);
        assertEquals(1L, traffic.singleResourceId);
    }

    private AppEntity app(Long id) {
        AppEntity entity = new AppEntity();
        entity.setId(id);
        entity.setAppCode("app_" + id);
        return entity;
    }

    private AppDto dto(Long id) {
        return new AppDto(id, "app_" + id, null, null, null,
                null, null, null, null, List.of(), 0L);
    }

    private static final class StubTrafficPolicySupport extends ResourceTrafficPolicySupport {

        private int batchCalls;
        private int singleCalls;
        private String singleResourceType;
        private Long singleResourceId;
        private List<Long> batchIds = new ArrayList<>();

        private StubTrafficPolicySupport() {
            super(null, null, null, null);
        }

        @Override
        public Map<Long, TrafficPolicies> loadBatch(String resourceType, List<Long> resourceIds) {
            batchCalls++;
            batchIds = resourceIds;
            return Map.of();
        }

        @Override
        public TrafficPolicies load(String resourceType, Long resourceId) {
            singleCalls++;
            singleResourceType = resourceType;
            singleResourceId = resourceId;
            return null;
        }
    }
}
