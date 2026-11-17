package org.chobit.knot.svc.service;

import com.github.pagehelper.Page;
import org.chobit.knot.common.model.PageRequest;
import org.chobit.knot.common.model.PageResult;
import org.chobit.knot.dal.entity.CodeCountRow;
import org.chobit.knot.dal.entity.ProviderProfileEntity;
import org.chobit.knot.dal.mapper.ProviderProfileMapper;
import org.chobit.knot.svc.vo.provider.ProviderProfileItem;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 供应商档案列表的分页契约：{@code total} 是全量条数，不是当前页条数。
 *
 * <p>PageHelper 的 {@code PageSerializable} 只在入参 {@code list instanceof Page} 时才取
 * {@code Page.getTotal()}，否则退化成 {@code list.size()}——所以转换必须发生在
 * {@code PageInfo} 之后，不能先把 {@code Page} 转成普通 {@code List}。</p>
 */
class ProviderProfileServicePageTest {

    private static final int TOTAL = 25;
    private static final int PAGE_SIZE = 10;

    private final ProviderProfileMapper mapper = mock(ProviderProfileMapper.class);

    private ProviderProfileService service() {
        return new ProviderProfileService(mapper);
    }

    @Test
    void firstPageReportsWholeTotal() {
        givenPage(1, PAGE_SIZE);

        PageResult<ProviderProfileItem> result = service().list(null, null, new PageRequest(1, PAGE_SIZE));

        assertEquals(TOTAL, result.total(), "第 1 页 total 必须是全量条数");
        assertEquals(3, result.pages(), "25 条 / 每页 10 → 3 页");
        assertEquals(PAGE_SIZE, result.list().size());
    }

    @Test
    void lastPageKeepsWholeTotal() {
        givenPage(3, 5);

        PageResult<ProviderProfileItem> result = service().list(null, null, new PageRequest(3, PAGE_SIZE));

        assertEquals(TOTAL, result.total(), "末页 total 仍是全量条数，不是本页 5 条");
        assertEquals(5, result.list().size());
    }

    /**
     * 整页只做一次分组计数：逐行 count 必须彻底消失。
     */
    @Test
    void countsBoundAccountsInOneBatchQuery() {
        givenPage(1, PAGE_SIZE);
        when(mapper.countAccountsByProviderCodes(anyList())).thenReturn(List.of(count("p0", 2L)));

        PageResult<ProviderProfileItem> result = service().list(null, null, new PageRequest(1, PAGE_SIZE));

        assertEquals(2L, result.list().get(0).boundCount(), "绑定数取自分组计数");
        assertEquals(0L, result.list().get(1).boundCount(), "结果里没有的编码按 0 兜底");
        verify(mapper, times(1)).countAccountsByProviderCodes(anyList());
        verify(mapper, never()).countAccountsByProviderCode(anyString());
    }

    private void givenPage(int pageNum, int rowCount) {
        Page<ProviderProfileEntity> page = new Page<>(pageNum, PAGE_SIZE);
        page.setTotal(TOTAL);
        for (int i = 0; i < rowCount; i++) {
            ProviderProfileEntity entity = new ProviderProfileEntity();
            entity.setId((long) i + 1);
            entity.setCode("p" + i);
            entity.setName("供应商" + i);
            page.add(entity);
        }
        when(mapper.list(null, null)).thenReturn(page);
    }

    private CodeCountRow count(String code, long total) {
        CodeCountRow row = new CodeCountRow();
        row.setCode(code);
        row.setTotal(total);
        return row;
    }
}
