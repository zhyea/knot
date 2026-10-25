package org.chobit.knot.svc.service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import org.chobit.knot.gateway.constants.enums.EnabledStatusEnum;
import org.chobit.knot.svc.dto.provider.DiscountPolicyDto;
import org.chobit.knot.dal.entity.DiscountPolicyEntity;
import org.chobit.knot.dal.entity.ModelEntity;
import org.chobit.knot.gateway.error.BusinessException;
import org.chobit.knot.dal.mapper.DiscountPolicyMapper;
import org.chobit.knot.dal.mapper.ModelMapper;
import org.chobit.knot.svc.service.ModelService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;


/**
 * 供应商模型折扣策略（{@code kb_provider_discount_policies}）的存量行为固化。
 *
 * <p>本簇此前<b>零覆盖</b>（改进清单 §4.7 拆分第二批：评估后决定不拆出 {@code ModelService}，
 * 理由见清单，改为先补测试守住边界）。这里只固化当前行为，<b>不新增任何校验</b>——
 * 折扣仅供展示、不参与计费（robin 2026-10-25 二次强调），不要以「将来可能参与计费」
 * 为由给它补精度或取值范围约束。</p>
 */
class ModelDiscountPolicyTest {

    private static final String MODEL_CODE = "gpt-4o";
    private static final Long POLICY_ID = 31L;
    private static final LocalDateTime EFFECTIVE_FROM = LocalDateTime.of(2026, 10, 1, 0, 0);

    private final ModelMapper modelMapper = mock(ModelMapper.class);
    private final DiscountPolicyMapper discountPolicyMapper = mock(DiscountPolicyMapper.class);
    /** 折扣链路只碰这两个 mapper，其余依赖传 null 即可（与 ModelServiceTest 同法） */
    private final ModelService service = new ModelService(
            modelMapper, discountPolicyMapper, null, null, null, null, null, null, null, null);

    @Test
    void listRejectsBlankModelCode() {
        BusinessException ex = assertThrows(BusinessException.class, () -> service.listDiscountPolicies("  "));

        assertTrue(ex.getMessage().contains("模型业务码不能为空"));
        verify(discountPolicyMapper, never()).listByModelCode(any());
    }

    @Test
    void listRejectsUnknownModel() {
        BusinessException ex = assertThrows(BusinessException.class, () -> service.listDiscountPolicies(MODEL_CODE));

        assertTrue(ex.getMessage().contains("供应商模型不存在"));
        verify(discountPolicyMapper, never()).listByModelCode(any());
    }

    @Test
    void listMapsPolicyFields() {
        givenModelExists();
        DiscountPolicyEntity entity = policy(EnabledStatusEnum.ENABLED.code());
        when(discountPolicyMapper.listByModelCode(MODEL_CODE)).thenReturn(List.of(entity));

        List<DiscountPolicyDto> result = service.listDiscountPolicies(MODEL_CODE);

        assertEquals(1, result.size());
        DiscountPolicyDto dto = result.get(0);
        assertEquals(POLICY_ID, dto.id());
        assertEquals("PERCENTAGE", dto.discountType());
        assertEquals(0, new BigDecimal("0.9500").compareTo(dto.discountValue()));
        assertEquals(EnabledStatusEnum.ENABLED.code(), dto.status());
    }

    @Test
    void createBindsPathVariableCodeAndDefaultsStatusToEnabled() {
        givenModelExists();

        DiscountPolicyDto created = service.createDiscountPolicy(
                MODEL_CODE, new DiscountPolicyDto(null, "PERCENTAGE", new BigDecimal("0.85"), null));

        ArgumentCaptor<DiscountPolicyEntity> captor = ArgumentCaptor.forClass(DiscountPolicyEntity.class);
        verify(discountPolicyMapper).insert(captor.capture());
        DiscountPolicyEntity saved = captor.getValue();
        assertEquals(MODEL_CODE, saved.getModelCode(), "归属码取路径变量，不取请求体");
        assertEquals("PERCENTAGE", saved.getDiscountType());
        assertEquals(0, new BigDecimal("0.85").compareTo(saved.getDiscountValue()));
        assertEquals(EnabledStatusEnum.ENABLED.code(), saved.getStatus(), "未给状态时默认为启用");
        assertNotNull(saved.getEffectiveFrom(), "新建必须写入生效时间（列 NOT NULL）");
        assertNull(saved.getEffectiveTo(), "不设失效时间：停用由 status 表达");
        assertEquals(EnabledStatusEnum.ENABLED.code(), created.status());
    }

    @Test
    void createKeepsExplicitStatus() {
        givenModelExists();

        service.createDiscountPolicy(
                MODEL_CODE, new DiscountPolicyDto(null, "FIXED", new BigDecimal("1.5"),
                        EnabledStatusEnum.DISABLED.code()));

        ArgumentCaptor<DiscountPolicyEntity> captor = ArgumentCaptor.forClass(DiscountPolicyEntity.class);
        verify(discountPolicyMapper).insert(captor.capture());
        assertEquals(EnabledStatusEnum.DISABLED.code(), captor.getValue().getStatus());
    }

    @Test
    void updateRejectsPolicyOwnedByAnotherModel() {
        givenModelExists();
        DiscountPolicyEntity other = policy(EnabledStatusEnum.ENABLED.code());
        other.setModelCode("claude-3");
        when(discountPolicyMapper.getById(POLICY_ID)).thenReturn(other);

        BusinessException ex = assertThrows(BusinessException.class, () -> service.updateDiscountPolicy(
                MODEL_CODE, POLICY_ID, new DiscountPolicyDto(POLICY_ID, "PERCENTAGE", new BigDecimal("0.9"), 1)));

        assertTrue(ex.getMessage().contains("折扣策略不存在"), "跨模型改归属一律当作不存在");
        verify(discountPolicyMapper, never()).update(any());
    }

    @Test
    void updateRejectsUnknownPolicy() {
        givenModelExists();
        when(discountPolicyMapper.getById(POLICY_ID)).thenReturn(null);

        BusinessException ex = assertThrows(BusinessException.class, () -> service.updateDiscountPolicy(
                MODEL_CODE, POLICY_ID, new DiscountPolicyDto(POLICY_ID, "PERCENTAGE", new BigDecimal("0.9"), 1)));

        assertTrue(ex.getMessage().contains("折扣策略不存在"));
        verify(discountPolicyMapper, never()).update(any());
    }

    @Test
    void updateWritesProvidedFields() {
        givenModelExists();
        when(discountPolicyMapper.getById(POLICY_ID)).thenReturn(policy(EnabledStatusEnum.ENABLED.code()));

        service.updateDiscountPolicy(MODEL_CODE, POLICY_ID,
                new DiscountPolicyDto(POLICY_ID, "FIXED", new BigDecimal("2.5"), EnabledStatusEnum.DISABLED.code()));

        ArgumentCaptor<DiscountPolicyEntity> captor = ArgumentCaptor.forClass(DiscountPolicyEntity.class);
        verify(discountPolicyMapper).update(captor.capture());
        DiscountPolicyEntity saved = captor.getValue();
        assertEquals(POLICY_ID, saved.getId());
        assertEquals("FIXED", saved.getDiscountType());
        assertEquals(0, new BigDecimal("2.5").compareTo(saved.getDiscountValue()));
        assertEquals(EnabledStatusEnum.DISABLED.code(), saved.getStatus());
        assertEquals(EFFECTIVE_FROM, saved.getEffectiveFrom(), "更新不动生效时间");
    }

    @Test
    void auditSnapshotIsNullWhenPolicyMissing() {
        when(discountPolicyMapper.getById(POLICY_ID)).thenReturn(null);

        assertNull(service.discountPolicyAuditSnapshot(POLICY_ID), "快照不得改变业务结果：查不到返回 null 而非抛");
    }

    @Test
    void auditSnapshotCapturesPolicyFields() {
        when(discountPolicyMapper.getById(POLICY_ID)).thenReturn(policy(EnabledStatusEnum.ENABLED.code()));

        Map<String, Object> snapshot = service.discountPolicyAuditSnapshot(POLICY_ID);

        assertNotNull(snapshot);
        assertEquals("PERCENTAGE", snapshot.get("discountType"));
        assertEquals(MODEL_CODE, snapshot.get("modelCode"));
    }

    private void givenModelExists() {
        ModelEntity model = new ModelEntity();
        model.setModelCode(MODEL_CODE);
        when(modelMapper.getByCode(MODEL_CODE)).thenReturn(model);
    }

    private DiscountPolicyEntity policy(Integer status) {
        DiscountPolicyEntity entity = new DiscountPolicyEntity();
        entity.setId(POLICY_ID);
        entity.setModelCode(MODEL_CODE);
        entity.setDiscountType("PERCENTAGE");
        entity.setDiscountValue(new BigDecimal("0.9500"));
        entity.setEffectiveFrom(EFFECTIVE_FROM);
        entity.setStatus(status);
        return entity;
    }
}
