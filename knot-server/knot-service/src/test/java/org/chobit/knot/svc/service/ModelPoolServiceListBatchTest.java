package org.chobit.knot.svc.service;

import com.github.pagehelper.Page;
import org.chobit.knot.common.model.PageRequest;
import org.chobit.knot.dal.entity.CodeCountRow;
import org.chobit.knot.dal.entity.ModelPoolEntity;
import org.chobit.knot.dal.entity.ModelPoolItemEntity;
import org.chobit.knot.dal.mapper.ModelPoolMapper;
import org.chobit.knot.svc.converter.ModelPoolConverter;
import org.chobit.knot.svc.dto.model.ModelPoolDto;
import org.chobit.knot.svc.dto.model.ModelPoolItemDto;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 模型池列表：条目与路由目标数各一次批量查询，逐池 3 条查询必须消失。
 * 绑定数口径不变：池内条目数（在手，用 size）+ 路由规则目标数。
 */
class ModelPoolServiceListBatchTest {

    private final ModelPoolMapper poolMapper = mock(ModelPoolMapper.class);
    private final ModelPoolConverter converter = mock(ModelPoolConverter.class);

    @Test
    void groupsItemsAndTargetsInTwoBatchQueries() {
        Page<ModelPoolEntity> page = new Page<>(1, 20);
        page.setTotal(2);
        page.add(pool(1L, "pool_a"));
        page.add(pool(2L, "pool_b"));
        when(poolMapper.list(any(), any(), any(), any())).thenReturn(page);
        when(converter.toDto(any(ModelPoolEntity.class))).thenAnswer(invocation -> dto(invocation.getArgument(0)));
        when(converter.toItemDto(any(ModelPoolItemEntity.class))).thenReturn(item());
        when(poolMapper.listItemsByPoolCodes(anyList()))
                .thenReturn(List.of(poolItem("pool_a"), poolItem("pool_a"), poolItem("pool_b")));
        when(poolMapper.countRoutingTargetsByPoolCodes(anyList())).thenReturn(List.of(count("pool_a", 1L)));

        List<ModelPoolDto> rows = new ModelPoolService(poolMapper, null, null, converter)
                .list(new PageRequest(1, 20), null, null, null, null)
                .list();

        assertEquals(3L, rows.get(0).boundCount(), "pool_a = 2 条条目 + 1 处路由目标");
        assertEquals(1L, rows.get(1).boundCount(), "pool_b = 1 条条目 + 0，未命中按 0 兜底");
        verify(poolMapper, times(1)).listItemsByPoolCodes(anyList());
        verify(poolMapper, never()).listItemsByPoolCode(anyString());
        verify(poolMapper, times(1)).countRoutingTargetsByPoolCodes(anyList());
        verify(poolMapper, never()).countRoutingTargetsByPoolCode(anyString());
    }

    private ModelPoolEntity pool(Long id, String code) {
        ModelPoolEntity entity = new ModelPoolEntity();
        entity.setId(id);
        entity.setPoolCode(code);
        entity.setName(code);
        return entity;
    }

    private ModelPoolDto dto(ModelPoolEntity entity) {
        return new ModelPoolDto(entity.getId(), entity.getPoolCode(), entity.getName(),
                null, null, null, null, null, true, false, null, List.of(), 0L);
    }

    private ModelPoolItemEntity poolItem(String poolCode) {
        ModelPoolItemEntity entity = new ModelPoolItemEntity();
        entity.setPoolCode(poolCode);
        return entity;
    }

    private ModelPoolItemDto item() {
        return new ModelPoolItemDto(1L, "model_code", "模型", "CHAT", null, null, 100, 100, true);
    }

    private CodeCountRow count(String code, long total) {
        CodeCountRow row = new CodeCountRow();
        row.setCode(code);
        row.setTotal(total);
        return row;
    }
}
