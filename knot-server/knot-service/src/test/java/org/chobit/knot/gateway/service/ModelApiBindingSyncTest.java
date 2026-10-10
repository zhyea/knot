package org.chobit.knot.gateway.service;

import org.chobit.knot.gateway.constants.enums.EnabledStatusEnum;
import org.chobit.knot.gateway.converter.ModelConverter;
import org.chobit.knot.gateway.dto.model.ModelApiBindingDto;
import org.chobit.knot.gateway.dto.model.ModelDto;
import org.chobit.knot.gateway.entity.BillingRuleEntity;
import org.chobit.knot.gateway.entity.LogicalModelEntity;
import org.chobit.knot.gateway.entity.ModelApiBindingEntity;
import org.chobit.knot.gateway.entity.ModelEntity;
import org.chobit.knot.gateway.entity.ProviderAccountEntity;
import org.chobit.knot.gateway.mapper.BillingRuleMapper;
import org.chobit.knot.gateway.mapper.LogicalModelMapper;
import org.chobit.knot.gateway.mapper.ModelApiBindingMapper;
import org.chobit.knot.gateway.mapper.ModelMapper;
import org.chobit.knot.gateway.mapper.ProviderAccountMapper;
import org.chobit.knot.gateway.model.QuotaPolicy;
import org.chobit.knot.gateway.model.RateLimitPolicy;
import org.chobit.knot.gateway.model.TrafficPolicies;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 「编辑供应商模型抽屉不做任何调整直接保存，也会产生操作日志、api-binding 记录也会变更」
 * 的回归单测。
 *
 * <p>根因：保存绑定原本「先按 model_id 整表删除、再全量插入」，未改动也会重排 binding 主键；
 * 而 {@code ModelService#modelAuditSnapshot} 带出 binding id，前后快照因此不等，
 * {@code OperationLogAspect} 的「无变化不记日志」判定失效。改为按 id diff 后主键稳定，
 * 无实质变化时不写库，日志判定自然恢复。</p>
 */
class ModelApiBindingSyncTest {

    private static final Long MODEL_ID = 100L;
    private static final Long BINDING_ID = 10L;

    private final ModelMapper modelMapper = mock(ModelMapper.class);
    private final ModelApiBindingMapper bindingMapper = mock(ModelApiBindingMapper.class);
    private final LogicalModelMapper logicalModelMapper = mock(LogicalModelMapper.class);
    private final BillingRuleMapper billingRuleMapper = mock(BillingRuleMapper.class);
    private final ProviderAccountMapper providerAccountMapper = mock(ProviderAccountMapper.class);
    private final ModelConverter modelConverter = mock(ModelConverter.class);
    // 具体类在本机 JVM 上无法被 Mockito 内联插桩，用轻量测试子类替换（只覆写 update 用到的两个方法）
    private static final ResourceTrafficPolicySupport trafficPolicySupport = new ResourceTrafficPolicySupport(null, null, null) {
        @Override
        public void save(String resourceType, Long resourceId, RateLimitPolicy rateLimit, QuotaPolicy quota) {
        }

        @Override
        public TrafficPolicies load(String resourceType, Long resourceId) {
            return null;
        }
    };

    private final ModelService service = new ModelService(
            modelMapper, null, bindingMapper, logicalModelMapper, billingRuleMapper, providerAccountMapper,
            modelConverter, trafficPolicySupport, null, null);

    @BeforeEach
    void setUp() {
        ModelEntity entity = new ModelEntity();
        entity.setId(MODEL_ID);
        entity.setModelCode("gpt-4o");
        entity.setUpstreamModel("gpt-4o");
        entity.setProviderAccountCode("acct-1");
        entity.setStatus(EnabledStatusEnum.ENABLED.code());
        entity.setBaseUrl("https://example.com");

        when(modelMapper.getById(MODEL_ID)).thenReturn(entity);
        when(modelMapper.countByModelCode(any(), eq(MODEL_ID))).thenReturn(0L);

        LogicalModelEntity logicalModel = new LogicalModelEntity();
        logicalModel.setModelCode("logical-gpt");
        logicalModel.setModelType("CHAT");
        logicalModel.setStatus(EnabledStatusEnum.ENABLED.code());
        when(logicalModelMapper.getByCode("logical-gpt")).thenReturn(logicalModel);

        BillingRuleEntity rule = new BillingRuleEntity();
        rule.setCode("billing-1");
        when(billingRuleMapper.getByCode("billing-1")).thenReturn(rule);

        ProviderAccountEntity account = new ProviderAccountEntity();
        account.setCode("acct-1");
        when(providerAccountMapper.getByCode("acct-1")).thenReturn(account);

        when(modelConverter.toEntity(any(ModelDto.class))).thenAnswer(invocation -> new ModelEntity());
        when(modelConverter.toDto(any(ModelEntity.class))).thenAnswer(invocation -> {
            ModelEntity source = invocation.getArgument(0);
            return new ModelDto(source.getId(), source.getModelCode(), source.getUpstreamModel(),
                    source.getProviderAccountCode(), null, null, null, "1.0.0", source.getBaseUrl(),
                    null, true, "logical-gpt", "billing-1", null, null, List.of(), false);
        });
        when(bindingMapper.listByModelId(MODEL_ID)).thenReturn(List.of());
    }

    @Test
    void noChangeSaveKeepsBindingRowUntouched() {
        when(bindingMapper.listByModelId(MODEL_ID)).thenReturn(List.of(existingBinding()));

        service.update(MODEL_ID, request(List.of(existingBindingDto())));

        verify(bindingMapper, never()).insert(any());
        verify(bindingMapper, never()).update(any());
        verify(bindingMapper, never()).deleteById(anyLong());
        verify(bindingMapper, never()).deleteByModelId(anyLong());
    }

    @Test
    void changedBindingUpdatesExistingRowInPlaceWithoutIdChurn() {
        when(bindingMapper.listByModelId(MODEL_ID)).thenReturn(List.of(existingBinding()));

        service.update(MODEL_ID, request(List.of(new ModelApiBindingDto(BINDING_ID, "CHAT_COMPLETIONS",
                "/v1/chat/completions", null, "DEFAULT", null, true, "人工备注"))));

        ArgumentCaptor<ModelApiBindingEntity> captor = ArgumentCaptor.forClass(ModelApiBindingEntity.class);
        verify(bindingMapper).update(captor.capture());
        assertEquals(BINDING_ID, captor.getValue().getId(), "更新走原地，主键不应变化");
        verify(bindingMapper, never()).insert(any());
        verify(bindingMapper, never()).deleteById(anyLong());
    }

    @Test
    void addedBindingIsInserted() {
        service.update(MODEL_ID, request(List.of(new ModelApiBindingDto(null, "CHAT_COMPLETIONS",
                null, null, "DEFAULT", null, true, null))));

        ArgumentCaptor<ModelApiBindingEntity> captor = ArgumentCaptor.forClass(ModelApiBindingEntity.class);
        verify(bindingMapper).insert(captor.capture());
        assertNull(captor.getValue().getId(), "新增行不携带前端 id");
    }

    @Test
    void removedBindingIsDeleted() {
        when(bindingMapper.listByModelId(MODEL_ID)).thenReturn(List.of(existingBinding()));

        service.update(MODEL_ID, request(List.of()));

        verify(bindingMapper).deleteById(BINDING_ID);
        verify(bindingMapper, never()).insert(any());
    }

    private ModelDto request(List<ModelApiBindingDto> bindings) {
        return new ModelDto(MODEL_ID, "gpt-4o", "gpt-4o", "acct-1", null, null, "CHAT",
                "1.0.0", "https://example.com", null, true, "logical-gpt", "billing-1",
                null, null, bindings, false);
    }

    private ModelApiBindingEntity existingBinding() {
        ModelApiBindingEntity entity = new ModelApiBindingEntity();
        entity.setId(BINDING_ID);
        entity.setModelId(MODEL_ID);
        entity.setProtocol("CHAT_COMPLETIONS");
        entity.setApiPath("/v1/chat/completions");
        entity.setUsageExtractor("DEFAULT");
        entity.setStatus(EnabledStatusEnum.ENABLED.code());
        return entity;
    }

    private ModelApiBindingDto existingBindingDto() {
        return new ModelApiBindingDto(BINDING_ID, "CHAT_COMPLETIONS", "/v1/chat/completions",
                null, "DEFAULT", null, true, null);
    }
}
