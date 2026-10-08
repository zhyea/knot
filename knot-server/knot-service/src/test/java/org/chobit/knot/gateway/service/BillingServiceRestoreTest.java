package org.chobit.knot.gateway.service;

import org.chobit.knot.gateway.converter.BillingConverter;
import org.chobit.knot.gateway.dto.billing.BillingRuleDto;
import org.chobit.knot.gateway.entity.BillingRuleEntity;
import org.chobit.knot.gateway.error.BusinessException;
import org.chobit.knot.gateway.mapper.BillingRuleMapper;
import org.chobit.knot.gateway.mapper.ModelMapper;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 计费规则删除/恢复语义（{@code is_deleted} + {@code status}）专项测试。
 *
 * <p>守护 §4.2 拍板的删除语义：删时强制停用（is_deleted=1 + status=0）、
 * 恢复只清 is_deleted（status 保持 0，不自动参与计费）、
 * code 唯一性按物理行判定（删除后同 code 只能恢复不能新建）。</p>
 */
class BillingServiceRestoreTest {

    private final BillingRuleMapper billingRuleMapper = mock(BillingRuleMapper.class);
    private final ModelMapper modelMapper = mock(ModelMapper.class);
    private final BillingConverter billingConverter = mock(BillingConverter.class);
    private final BillingService service =
            new BillingService(billingRuleMapper, modelMapper, billingConverter);

    private BillingRuleEntity rule(Long id, String code, Integer isDeleted) {
        BillingRuleEntity entity = new BillingRuleEntity();
        entity.setId(id);
        entity.setCode(code);
        entity.setStatus(0);
        entity.setIsDeleted(isDeleted);
        return entity;
    }

    private BillingRuleDto dto(Long id, String code, boolean enabled) {
        return new BillingRuleDto(id, code, null, null, null, null, "TOKEN", "FIXED",
                "USD", "1K_TOKENS", "{}", enabled, null, null, null, 0L);
    }

    // ==================== deleteRule ====================

    @Test
    void deleteRejectsMissingRule() {
        when(billingRuleMapper.getById(9L)).thenReturn(null);

        BusinessException ex = assertThrows(BusinessException.class, () -> service.deleteRule(9L));
        assertTrue(ex.getMessage().contains("not found"));
        verify(billingRuleMapper, never()).logicalDelete(anyLong());
    }

    @Test
    void deleteRejectsWhenBoundByModels() {
        when(billingRuleMapper.getById(1L)).thenReturn(rule(1L, "TOKEN_GPT4O", 0));
        when(billingRuleMapper.countBoundModels("TOKEN_GPT4O")).thenReturn(2L);

        BusinessException ex = assertThrows(BusinessException.class, () -> service.deleteRule(1L));
        assertTrue(ex.getMessage().contains("bound by provider models"));
        verify(billingRuleMapper, never()).logicalDelete(anyLong());
    }

    @Test
    void deleteGoesThroughLogicalDelete() {
        when(billingRuleMapper.getById(1L)).thenReturn(rule(1L, "TOKEN_GPT4O", 0));
        when(billingRuleMapper.countBoundModels("TOKEN_GPT4O")).thenReturn(0L);

        service.deleteRule(1L);

        // 生命周期删除必须是逻辑删（is_deleted=1 且 status=0 由 SQL 侧保证），不得出现物理删除
        verify(billingRuleMapper).logicalDelete(1L);
    }

    // ==================== restoreRule ====================

    @Test
    void restoreRejectsMissingRuleEvenIfDeleted() {
        when(billingRuleMapper.getByIdIncludingDeleted(9L)).thenReturn(null);

        BusinessException ex = assertThrows(BusinessException.class, () -> service.restoreRule(9L));
        assertTrue(ex.getMessage().contains("not found"));
        verify(billingRuleMapper, never()).restore(anyLong());
    }

    @Test
    void restoreRejectsRuleThatIsNotDeleted() {
        // getById（排除已删）查不到 + getByIdIncludingDeleted 查到 is_deleted=0 → 未删除，拒绝恢复
        when(billingRuleMapper.getByIdIncludingDeleted(1L)).thenReturn(rule(1L, "TOKEN_GPT4O", 0));

        BusinessException ex = assertThrows(BusinessException.class, () -> service.restoreRule(1L));
        assertTrue(ex.getMessage().contains("not deleted"));
        verify(billingRuleMapper, never()).restore(anyLong());
    }

    @Test
    void restoreFailsWhenNoRowAffected() {
        when(billingRuleMapper.getByIdIncludingDeleted(1L)).thenReturn(rule(1L, "TOKEN_GPT4O", 1));
        when(billingRuleMapper.restore(1L)).thenReturn(0);

        BusinessException ex = assertThrows(BusinessException.class, () -> service.restoreRule(1L));
        assertTrue(ex.getMessage().contains("restore failed"));
    }

    @Test
    void restoreKeepsDisabledAndDoesNotTouchVersionStatus() {
        // 恢复 = 只清 is_deleted；status 保持 0（不自动参与计费），版本状态不得被联动改动
        when(billingRuleMapper.getByIdIncludingDeleted(1L)).thenReturn(rule(1L, "TOKEN_GPT4O", 1));
        when(billingRuleMapper.restore(1L)).thenReturn(1);
        when(billingRuleMapper.getById(1L)).thenReturn(rule(1L, "TOKEN_GPT4O", 0));
        when(billingConverter.toRuleDto(rule(1L, "TOKEN_GPT4O", 0))).thenReturn(dto(1L, "TOKEN_GPT4O", false));

        BillingRuleDto restored = service.restoreRule(1L);

        assertEquals(false, restored.enabled());
        InOrder order = inOrder(billingRuleMapper);
        order.verify(billingRuleMapper).restore(1L);
        order.verify(billingRuleMapper).getById(1L);
        verify(billingRuleMapper, never()).updateVersionStatus(anyLong(), org.mockito.ArgumentMatchers.any());
        verify(billingRuleMapper, never()).updateStatus(anyLong(), org.mockito.ArgumentMatchers.any());
    }
}
