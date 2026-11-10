package org.chobit.knot.svc.service;

import org.chobit.knot.common.constants.enums.EnabledStatusEnum;
import org.chobit.knot.common.model.QuotaPolicy;
import org.chobit.knot.dal.entity.ModelEntity;
import org.chobit.knot.dal.mapper.AsyncTaskProfileMapper;
import org.chobit.knot.dal.mapper.BillingRuleMapper;
import org.chobit.knot.dal.mapper.DiscountPolicyMapper;
import org.chobit.knot.dal.mapper.LogicalModelMapper;
import org.chobit.knot.dal.mapper.ModelApiBindingMapper;
import org.chobit.knot.dal.mapper.ModelMapper;
import org.chobit.knot.dal.mapper.ProviderAccountMapper;
import org.chobit.knot.svc.converter.ModelConverter;
import org.chobit.knot.svc.dto.model.ModelDto;
import org.chobit.knot.svc.model.TrafficPolicies;
import org.chobit.knot.svc.service.support.ModelApiBindingSupport;
import org.chobit.knot.svc.service.support.ResourceTrafficPolicySupport;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 供应商模型列表「创建时间 / 更新时间」的回归单测。
 *
 * <p>时间列由库侧维护（{@code created_at} 默认值 / {@code updated_at} ON UPDATE），
 * 后端只做透传。风险点在 service 侧两处**按位置新建 {@link ModelDto}**：
 * {@code enrich(dto, traffic, bindings)} 叠加频控与 API 绑定、{@code withBoundCount} 补绑定数。
 * record 重建时漏透传一个字段既不报错也不影响其它断言，表现为列表时间列静默变成占位符；
 * 本测试钉死这条链路两跳之后时间仍在。</p>
 */
class ModelTimeColumnsTest {

    private static final Long MODEL_ID = 7L;
    private static final LocalDateTime CREATED_AT = LocalDateTime.of(2026, 11, 1, 9, 30, 15);
    private static final LocalDateTime UPDATED_AT = LocalDateTime.of(2026, 11, 9, 18, 5, 42);

    private final ModelMapper modelMapper = mock(ModelMapper.class);
    private final DiscountPolicyMapper discountPolicyMapper = mock(DiscountPolicyMapper.class);
    private final ModelApiBindingMapper bindingMapper = mock(ModelApiBindingMapper.class);
    private final AsyncTaskProfileMapper asyncTaskProfileMapper = mock(AsyncTaskProfileMapper.class);
    private final ModelConverter modelConverter = mock(ModelConverter.class);

    // 具体类在本机 JVM 上无法被 Mockito 内联插桩，用轻量测试子类替换（只覆写 enrich 用到的两个方法）
    private final ResourceTrafficPolicySupport trafficPolicySupport = new ResourceTrafficPolicySupport(null, null, null, null) {
        @Override
        public TrafficPolicies load(String resourceType, Long resourceId) {
            return null;
        }

        @Override
        public long countBindings(String resourceType, String resourceCode) {
            return 0L;
        }
    };

    private final ModelService service = new ModelService(
            modelMapper, discountPolicyMapper, new ModelApiBindingSupport(bindingMapper, asyncTaskProfileMapper),
            mock(LogicalModelMapper.class), mock(BillingRuleMapper.class), mock(ProviderAccountMapper.class),
            modelConverter, trafficPolicySupport, null, null);

    @Test
    void getByIdKeepsCreatedAndUpdatedTimeAcrossDtoRebuilds() {
        ModelEntity entity = new ModelEntity();
        entity.setId(MODEL_ID);
        entity.setModelCode("gpt-4o");
        entity.setUpstreamModel("gpt-4o");
        entity.setProviderAccountCode("acct-1");
        entity.setStatus(EnabledStatusEnum.ENABLED.code());
        entity.setCreatedAt(CREATED_AT);
        entity.setUpdatedAt(UPDATED_AT);
        when(modelMapper.getById(MODEL_ID)).thenReturn(entity);

        when(modelConverter.toDto(any(ModelEntity.class))).thenAnswer(invocation -> {
            ModelEntity source = invocation.getArgument(0);
            return new ModelDto(source.getId(), source.getModelCode(), source.getUpstreamModel(),
                    source.getProviderAccountCode(), null, null, null, "1.0.0", "/ep-1",
                    null, true, "logical-gpt", null, null, List.<QuotaPolicy>of(),
                    List.of(), false, null,
                    source.getCreatedAt(), source.getUpdatedAt());
        });
        when(bindingMapper.listByModelId(MODEL_ID)).thenReturn(List.of());

        ModelDto dto = service.getById(MODEL_ID);

        assertNotNull(dto.createdAt(), "enrich / withBoundCount 重建 DTO 时漏传了创建时间");
        assertNotNull(dto.updatedAt(), "enrich / withBoundCount 重建 DTO 时漏传了更新时间");
        assertEquals(CREATED_AT, dto.createdAt());
        assertEquals(UPDATED_AT, dto.updatedAt());
    }
}
