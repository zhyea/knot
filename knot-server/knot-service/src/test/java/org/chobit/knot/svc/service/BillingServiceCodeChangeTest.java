package org.chobit.knot.svc.service;

import org.chobit.knot.common.error.BusinessException;
import org.chobit.knot.dal.entity.BillingRuleEntity;
import org.chobit.knot.dal.entity.BillingRuleVersionEntity;
import org.chobit.knot.dal.mapper.BillingRuleMapper;
import org.chobit.knot.svc.converter.BillingConverter;
import org.chobit.knot.svc.dto.billing.BillingRuleDto;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 计费规则改码守卫专项测试：版本行<b>不是</b>引用方。
 *
 * <p>守护 2026-10-26 拍板的关联方式：{@code kb_billing_rule_versions} 按 {@code rule_id}
 * （规则主键）归属主体，不按 {@code rule_code}。此前的按码关联让「每条规则恒有版本行」
 * 直接等价于「编码恒被引用」——改码守卫永远拦下，规则编码实际不可改。</p>
 *
 * <p>三条断言对应三个真实故障模式：</p>
 * <ol>
 *   <li>只有版本行、无模型绑定 → 允许改码（旧实现恒 409）；</li>
 *   <li>有模型绑定（{@code kb_models.billing_rule_code} 存 code）→ 仍必须 409，改码会静默掉绑；</li>
 *   <li>版本行的读取/新建一律按 ruleId，改码时不得按新码查版本（旧实现查不到 → 另起 v1 + 孤儿行）。</li>
 * </ol>
 */
class BillingServiceCodeChangeTest {

    private final BillingRuleMapper billingRuleMapper = mock(BillingRuleMapper.class);
    private final BillingConverter billingConverter = mock(BillingConverter.class);
    private final BillingService service = new BillingService(billingRuleMapper, billingConverter);

    private static final long RULE_ID = 7L;

    private BillingRuleEntity existingRule(String code) {
        BillingRuleEntity entity = new BillingRuleEntity();
        entity.setId(RULE_ID);
        entity.setCode(code);
        entity.setStatus(1);
        entity.setIsDeleted(0);
        return entity;
    }

    private BillingRuleDto request(String code) {
        return new BillingRuleDto(null, code, null, null, null, null, "TOKEN", "FIXED",
                "USD", "1K_TOKENS", "{}", true, null, null, null, 0L);
    }

    private BillingRuleVersionEntity version(long id, long ruleId) {
        BillingRuleVersionEntity version = new BillingRuleVersionEntity();
        version.setId(id);
        version.setRuleId(ruleId);
        version.setVersionCode("v1");
        version.setStatus(1);
        return version;
    }

    @Test
    void codeChangeAllowedWhenOnlyVersionRowExists() {
        // 无模型绑定（countBoundModels = 0）：旧实现把版本行计入引用 → 恒 409；现应放行并原地覆盖版本
        when(billingRuleMapper.getById(RULE_ID)).thenReturn(existingRule("OLD_CODE"));
        when(billingRuleMapper.countByCode("NEW_CODE", RULE_ID)).thenReturn(0L);
        when(billingRuleMapper.countBoundModels("OLD_CODE")).thenReturn(0L);
        when(billingRuleMapper.getLatestVersion(RULE_ID)).thenReturn(version(11L, RULE_ID));

        service.updateRule(RULE_ID, request("NEW_CODE"));

        verify(billingRuleMapper).update(any(BillingRuleEntity.class));
        // 版本按主键定位：即使编码已变，读的仍是同一条规则下的版本行
        verify(billingRuleMapper).getLatestVersion(RULE_ID);
        verify(billingRuleMapper).updateVersionContent(any(BillingRuleVersionEntity.class));
        // 编码变了但版本行没变 → 不得另起新版本行（旧实现会走 createVersion）
        verify(billingRuleMapper, never()).insertVersion(any(BillingRuleVersionEntity.class));
    }

    @Test
    void codeChangeRejectedWhenBoundByProviderModels() {
        // 模型绑定存的是 code，改码会让存量模型静默掉绑 → 必须 409，且不落任何写操作
        when(billingRuleMapper.getById(RULE_ID)).thenReturn(existingRule("OLD_CODE"));
        when(billingRuleMapper.countByCode("NEW_CODE", RULE_ID)).thenReturn(0L);
        when(billingRuleMapper.countBoundModels("OLD_CODE")).thenReturn(3L);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.updateRule(RULE_ID, request("NEW_CODE")));

        assertTrue(ex.getMessage().contains("计费规则编码"));
        verify(billingRuleMapper, never()).update(any(BillingRuleEntity.class));
        verify(billingRuleMapper, never()).updateVersionContent(any(BillingRuleVersionEntity.class));
    }

    @Test
    void versionRowIsCreatedAgainstRuleIdNotCode() {
        // 规则尚无版本行：新建版本必须落 ruleId，且版本号序号也按 ruleId 统计
        when(billingRuleMapper.getById(RULE_ID)).thenReturn(existingRule("OLD_CODE"));
        when(billingRuleMapper.countByCode("NEW_CODE", RULE_ID)).thenReturn(0L);
        when(billingRuleMapper.countBoundModels("OLD_CODE")).thenReturn(0L);
        when(billingRuleMapper.getLatestVersion(RULE_ID)).thenReturn(null);
        when(billingRuleMapper.maxVersionSeq(RULE_ID)).thenReturn(0);

        service.updateRule(RULE_ID, request("NEW_CODE"));

        ArgumentCaptor<BillingRuleVersionEntity> captor = ArgumentCaptor.forClass(BillingRuleVersionEntity.class);
        verify(billingRuleMapper).insertVersion(captor.capture());
        assertEquals(RULE_ID, captor.getValue().getRuleId());
        assertEquals("v1", captor.getValue().getVersionCode());
        verify(billingRuleMapper).maxVersionSeq(RULE_ID);
    }

    @Test
    void unchangedCodeSkipsBindingCheck() {
        // 只改价格不动编码：不得因「自身引用」误伤（旧实现此处也会查版本行计数）
        when(billingRuleMapper.getById(RULE_ID)).thenReturn(existingRule("SAME_CODE"));
        when(billingRuleMapper.countByCode("SAME_CODE", RULE_ID)).thenReturn(0L);
        when(billingRuleMapper.getLatestVersion(RULE_ID)).thenReturn(version(11L, RULE_ID));

        service.updateRule(RULE_ID, request("SAME_CODE"));

        verify(billingRuleMapper, never()).countBoundModels(any());
        verify(billingRuleMapper, never()).insertVersion(any(BillingRuleVersionEntity.class));
        verify(billingRuleMapper, never()).updateStatus(anyLong(), any());
    }
}
