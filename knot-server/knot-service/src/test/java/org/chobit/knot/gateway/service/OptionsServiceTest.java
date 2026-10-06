package org.chobit.knot.gateway.service;

import org.chobit.knot.gateway.error.BusinessException;
import org.chobit.knot.gateway.mapper.OptionsMapper;
import org.chobit.knot.gateway.model.BillingRuleOptionQuery;
import org.chobit.knot.gateway.model.ModelPoolOptionQuery;
import org.chobit.knot.gateway.model.RoutingConsumerOptionQuery;
import org.chobit.knot.gateway.model.UserOptionQuery;
import org.chobit.knot.gateway.vo.common.OptionItem;
import org.chobit.knot.gateway.vo.common.OptionPage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 阶段一下拉候选（options）核心契约单测。
 *
 * <p>用 mock 的 {@link OptionsMapper}（接口，避开 JDK25 + Mockito inline 对具体类的限制）驱动
 * {@link OptionsService#assemble} 的共享逻辑：分页钳制、values 回显合并、missingValues、disabled 标记、
 * values 上限拒绝。覆盖 7 场景：</p>
 * <ol>
 *   <li>默认启用过滤（enabledOnly=true、includeDeleted=false、首页 20）；</li>
 *   <li>pageSize 服务端上限 50 钳制（入参 999 → 回显 50）；</li>
 *   <li>missingValues：请求 values 中不存在的项进 missingValues；</li>
 *   <li>回显合并 + 去重 + disabled 标记（已选项不在首页也能回显，停用项标 disabled）；</li>
 *   <li>values 超过 100 直接拒绝（BusinessException，mapper 不被调用）；</li>
 *   <li>code 型资源 value 原样透传（不转数字）；</li>
 *   <li>全部 values 均缺失时 list 为空、missingValues 全量返回；</li>
 * </ol>
 */
class OptionsServiceTest {

    private OptionsMapper mapper;
    private OptionsService service;

    @BeforeEach
    void setUp() {
        mapper = mock(OptionsMapper.class);
        service = new OptionsService(mapper);
    }

    private static Map<String, Object> row(Object value, String label, String code, int disabled) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("value", value);
        m.put("label", label);
        m.put("code", code);
        m.put("disabled", disabled);
        return m;
    }

    private UserOptionQuery userQuery(Integer pageNum, Integer pageSize, String keyword,
                                      List<String> values, Boolean enabledOnly, Boolean includeDeleted) {
        return new UserOptionQuery(pageNum, pageSize, keyword, values, enabledOnly, includeDeleted);
    }

    // 场景 1：默认启用过滤 + 首页默认 20
    @Test
    void defaultsToEnabledOnlyFirstPageSize20() {
        when(mapper.listUserOptions(isNull(), anyBoolean(), anyBoolean())).thenReturn(List.of(row(1L, "Alice", "alice", 0)));
        OptionPage<OptionItem> page = service.listUserOptions(null);

        verify(mapper).listUserOptions(isNull(), eq(true), eq(false));
        assertEquals(1, page.pageNum());
        assertEquals(20, page.pageSize());
        assertEquals(1, page.list().size());
        assertEquals(1L, page.list().get(0).value());
    }

    // 场景 2：pageSize 超过上限被钳制到 50
    @Test
    void clampsPageSizeTo50() {
        when(mapper.listUserOptions(any(), anyBoolean(), anyBoolean())).thenReturn(List.of());
        OptionPage<OptionItem> page = service.listUserOptions(userQuery(1, 999, null, null, null, null));
        assertEquals(50, page.pageSize());
    }

    // 场景 3：请求 values 中不存在的项进 missingValues
    @Test
    void reportsMissingValues() {
        when(mapper.listUserOptions(any(), anyBoolean(), anyBoolean())).thenReturn(List.of(row(1L, "Alice", "alice", 0)));
        when(mapper.listUserOptionsByValues(anyList())).thenReturn(List.of(row(1L, "Alice", "alice", 0)));

        OptionPage<OptionItem> page = service.listUserOptions(userQuery(1, 20, null, List.of("1", "999"), null, null));

        assertEquals(List.of("999"), page.missingValues());
        assertEquals(1, page.list().size());
    }

    // 场景 4：回显合并 + 去重 + disabled 标记（停用的已选项也能回显）
    @Test
    void mergesEchoItemsDeduplicatesAndMarksDisabled() {
        when(mapper.listUserOptions(any(), anyBoolean(), anyBoolean())).thenReturn(List.of(row(1L, "Alice", "alice", 0)));
        when(mapper.listUserOptionsByValues(anyList())).thenReturn(List.of(
                row(1L, "Alice", "alice", 0),
                row(2L, "Bob", "bob", 1)));

        OptionPage<OptionItem> page = service.listUserOptions(userQuery(1, 20, null, List.of("1", "2"), null, null));

        // 1 只出现一次（首页 + 回显去重），2 追加进来
        assertEquals(2, page.list().size());
        assertEquals(1L, page.list().get(0).value());
        assertEquals(2L, page.list().get(1).value());
        assertEquals(Boolean.TRUE, page.list().get(1).disabled());
        assertTrue(page.missingValues().isEmpty());
    }

    // 场景 5：values 超过 100 拒绝，且不触碰 mapper
    @Test
    void rejectsMoreThan100Values() {
        List<String> tooMany = new ArrayList<>();
        for (int i = 0; i < 101; i++) {
            tooMany.add(String.valueOf(i));
        }
        assertThrows(BusinessException.class,
                () -> service.listUserOptions(userQuery(1, 20, null, tooMany, null, null)));
        verify(mapper, never()).listUserOptionsByValues(anyList());
    }

    // 场景 6：code 型资源 value 原样透传（模型池 poolCode 不被数字化）
    @Test
    void codeValuedResourceKeepsStringValue() {
        when(mapper.listModelPoolOptions(any(), anyBoolean(), anyBoolean(), any())).thenReturn(List.of());
        when(mapper.listModelPoolOptionsByValues(anyList())).thenReturn(List.of(row("chat-economy-pool", "经济池", "chat-economy-pool", 0)));

        ModelPoolOptionQuery q = new ModelPoolOptionQuery(1, 20, null, List.of("chat-economy-pool"), null, null, null);
        OptionPage<OptionItem> page = service.listModelPoolOptions(q);

        assertEquals("chat-economy-pool", page.list().get(0).value());
        assertTrue(page.missingValues().isEmpty());
    }

    // 场景 7：全部 values 均缺失时 list 为空、missingValues 全量返回；敏感资源不带 meta
    @Test
    void allValuesMissingReturnsEmptyList() {
        when(mapper.listRoutingConsumerOptions(any(), anyBoolean())).thenReturn(List.of());
        when(mapper.listRoutingConsumerOptionsByValues(anyList())).thenReturn(List.of());

        RoutingConsumerOptionQuery q = new RoutingConsumerOptionQuery(1, 20, null, List.of("7", "8"), null, null);
        OptionPage<OptionItem> page = service.listRoutingConsumerOptions(q);

        assertTrue(page.list().isEmpty());
        assertEquals(List.of("7", "8"), page.missingValues());
    }

    // 补充：路由消费者/供应商账户 options 的 OptionItem 永远只有 value/label/code/disabled，meta 为 null（无凭据）
    @Test
    void sensitiveOptionsCarryNoMeta() {
        when(mapper.listRoutingConsumerOptions(any(), anyBoolean())).thenReturn(List.of(row(3L, "consumer-a", "ca", 0)));
        OptionPage<OptionItem> page = service.listRoutingConsumerOptions(null);
        OptionItem item = page.list().get(0);
        assertNull(item.meta());
        assertEquals(3L, item.value());
        assertEquals("ca", item.code());
    }

    // 补充：计费规则 value=code 回显
    @Test
    void billingRuleValueIsCode() {
        when(mapper.listBillingRuleOptions(any(), anyBoolean(), any())).thenReturn(List.of());
        when(mapper.listBillingRuleOptionsByValues(anyList())).thenReturn(List.of(row("rule-a", "rule-a", "rule-a", 0)));
        BillingRuleOptionQuery q = new BillingRuleOptionQuery(1, 20, null, List.of("rule-a"), null, null, null);
        OptionPage<OptionItem> page = service.listBillingRuleOptions(q);
        assertEquals("rule-a", page.list().get(0).value());
    }
}
