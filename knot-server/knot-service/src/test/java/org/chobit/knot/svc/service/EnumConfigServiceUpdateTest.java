package org.chobit.knot.svc.service;

import org.chobit.knot.dal.entity.EnumConfigEntity;
import org.chobit.knot.gateway.error.BusinessException;
import org.chobit.knot.gateway.error.ErrorCode;
import org.chobit.knot.dal.mapper.EnumCategoryMapper;
import org.chobit.knot.dal.mapper.EnumConfigMapper;
import org.chobit.knot.svc.service.EnumConfigService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 枚举项编码口径：未绑定可改、绑定后 409。
 *
 * <p>引用方一律按 {@code item_code} 绑定（模型族 → 计费规则/统一模型，折扣类型 → 折扣策略，
 * 敏感词分类 → 词条），改码等于让存量行掉绑，所以引用数 &gt; 0 必须拒绝。</p>
 */
class EnumConfigServiceUpdateTest {

    private final EnumConfigMapper enumConfigMapper = mock(EnumConfigMapper.class);
    private final EnumCategoryMapper enumCategoryMapper = mock(EnumCategoryMapper.class);
    private final EnumConfigService service = new EnumConfigService(enumConfigMapper, enumCategoryMapper);

    private EnumConfigEntity existing(String itemCode, Boolean isSystem) {
        EnumConfigEntity entity = new EnumConfigEntity();
        entity.setId(9L);
        entity.setCategoryId(28L);
        entity.setCategory("model_family");
        entity.setItemCode(itemCode);
        entity.setItemLabel("旧名称");
        entity.setSortOrder(1);
        entity.setIsSystem(isSystem);
        entity.setIsEnabled(true);
        return entity;
    }

    private EnumConfigEntity request(String itemCode) {
        EnumConfigEntity entity = new EnumConfigEntity();
        entity.setItemCode(itemCode);
        entity.setItemLabel("新名称");
        entity.setSortOrder(2);
        entity.setIsEnabled(false);
        entity.setRemark("r");
        return entity;
    }

    @Test
    void keepsCodeWhenRequestCodeBlank() {
        when(enumConfigMapper.getById(9L)).thenReturn(existing("claude", false));

        service.update(9L, request("  "));

        ArgumentCaptor<EnumConfigEntity> captor = ArgumentCaptor.forClass(EnumConfigEntity.class);
        verify(enumConfigMapper).update(captor.capture());
        assertEquals("claude", captor.getValue().getItemCode());
        verify(enumConfigMapper, never()).countItemCodeBindings(anyString());
    }

    @Test
    void keepsCodeWhenRequestCodeUnchanged() {
        when(enumConfigMapper.getById(9L)).thenReturn(existing("claude", false));

        service.update(9L, request(" claude "));

        ArgumentCaptor<EnumConfigEntity> captor = ArgumentCaptor.forClass(EnumConfigEntity.class);
        verify(enumConfigMapper).update(captor.capture());
        assertEquals("claude", captor.getValue().getItemCode());
        verify(enumConfigMapper, never()).countItemCodeBindings(anyString());
    }

    @Test
    void rejectsCodeChangeWhenReferenced() {
        when(enumConfigMapper.getById(9L)).thenReturn(existing("claude", false));
        when(enumConfigMapper.getByCategoryAndCode("model_family", "claude-4")).thenReturn(null);
        when(enumConfigMapper.countItemCodeBindings("claude")).thenReturn(3L);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.update(9L, request("claude-4")));

        assertEquals(ErrorCode.CONFLICT.code(), ex.getCode());
        verify(enumConfigMapper, never()).update(any());
    }

    @Test
    void allowsCodeChangeWhenNotReferenced() {
        when(enumConfigMapper.getById(9L)).thenReturn(existing("claude", false));
        when(enumConfigMapper.getByCategoryAndCode("model_family", "claude-4")).thenReturn(null);
        when(enumConfigMapper.countItemCodeBindings("claude")).thenReturn(0L);

        service.update(9L, request("claude-4"));

        ArgumentCaptor<EnumConfigEntity> captor = ArgumentCaptor.forClass(EnumConfigEntity.class);
        verify(enumConfigMapper).update(captor.capture());
        assertEquals("claude-4", captor.getValue().getItemCode());
        assertEquals("model_family", captor.getValue().getCategory());
    }

    @Test
    void rejectsCodeChangeWhenDuplicateInSameCategory() {
        EnumConfigEntity duplicated = existing("claude-4", false);
        duplicated.setId(11L);
        when(enumConfigMapper.getById(9L)).thenReturn(existing("claude", false));
        when(enumConfigMapper.getByCategoryAndCode("model_family", "claude-4")).thenReturn(duplicated);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.update(9L, request("claude-4")));

        assertEquals(ErrorCode.CONFLICT.code(), ex.getCode());
        verify(enumConfigMapper, never()).countItemCodeBindings(anyString());
        verify(enumConfigMapper, never()).update(any());
    }

    @Test
    void ignoresDuplicateThatIsItself() {
        when(enumConfigMapper.getById(9L)).thenReturn(existing("claude", false));
        when(enumConfigMapper.getByCategoryAndCode("model_family", "claude")).thenReturn(existing("claude", false));
        when(enumConfigMapper.countItemCodeBindings("claude")).thenReturn(0L);

        service.update(9L, request("claude"));

        verify(enumConfigMapper, never()).countItemCodeBindings(anyString());
    }

    @Test
    void rejectsSystemCategoryEdit() {
        when(enumConfigMapper.getById(9L)).thenReturn(existing("spam", true));

        BusinessException ex = assertThrows(BusinessException.class,
                () -> service.update(9L, request("spam-2")));

        assertEquals(ErrorCode.VALIDATION_ERROR.code(), ex.getCode());
        verify(enumConfigMapper, never()).getByCategoryAndCode(anyString(), anyString());
    }

    @Test
    void reloadsAfterUpdate() {
        when(enumConfigMapper.getById(9L)).thenReturn(existing("claude", false));
        when(enumConfigMapper.getByCategoryAndCode(anyString(), anyString())).thenReturn(null);
        when(enumConfigMapper.countItemCodeBindings(anyString())).thenReturn(0L);

        service.update(9L, request("claude-4"));

        verify(enumConfigMapper).update(any());
        verify(enumConfigMapper, org.mockito.Mockito.times(2)).getById(9L);
        verify(enumCategoryMapper, never()).selectIdByCategory(eq("model_family"));
    }
}
