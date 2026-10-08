package org.chobit.knot.gateway.service;

import org.chobit.knot.gateway.converter.OptionConverter;
import org.chobit.knot.gateway.entity.OptionRow;
import org.chobit.knot.gateway.error.BusinessException;
import org.chobit.knot.gateway.mapper.OptionsMapper;
import org.chobit.knot.gateway.model.BillingRuleOptionQuery;
import org.chobit.knot.gateway.model.ModelPoolOptionQuery;
import org.chobit.knot.gateway.model.RoutingConsumerOptionQuery;
import org.chobit.knot.gateway.model.UserOptionQuery;
import org.chobit.knot.gateway.vo.common.OptionItem;
import org.chobit.knot.gateway.vo.common.OptionPage;
import org.chobit.knot.gateway.vo.common.meta.ModelMeta;
import org.chobit.knot.gateway.vo.common.meta.ProviderAccountMeta;
import org.chobit.knot.gateway.vo.common.meta.RoutingConsumerOptionMeta;
import org.chobit.knot.gateway.vo.common.meta.UserOptionMeta;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
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
        service = new OptionsService(mapper, new OptionConverter());
    }

    /** id 型行（value=Long），供用户 / 部门 / 应用 / 路由消费者 / 角色类资源打桩。meta 为 json_object 文本，可为 null。 */
    private static OptionRow<Long> idRow(Long value, String label, String meta, int disabled) {
        OptionRow<Long> row = new OptionRow<>();
        row.setValue(value);
        row.setLabel(label);
        row.setMeta(meta);
        row.setDisabled(disabled);
        return row;
    }

    /** code 型行（value=String），供模型 / 模型池 / 计费规则 / 供应商账户类资源打桩。meta 同上。 */
    private static OptionRow<String> codeRow(String value, String label, String meta, int disabled) {
        OptionRow<String> row = new OptionRow<>();
        row.setValue(value);
        row.setLabel(label);
        row.setMeta(meta);
        row.setDisabled(disabled);
        return row;
    }

    private UserOptionQuery userQuery(Integer pageNum, Integer pageSize, String keyword,
                                      List<String> values, Boolean enabledOnly, Boolean includeDeleted) {
        return new UserOptionQuery(pageNum, pageSize, keyword, values, enabledOnly, includeDeleted);
    }

    // 场景 1：默认启用过滤 + 首页默认 20
    @Test
    void defaultsToEnabledOnlyFirstPageSize20() {
        when(mapper.listUserOptions(isNull(), anyBoolean(), anyBoolean())).thenReturn(List.of(idRow(1L, "Alice", null, 0)));
        OptionPage<OptionItem<UserOptionMeta>> page = service.listUserOptions(null);

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
        OptionPage<OptionItem<UserOptionMeta>> page = service.listUserOptions(userQuery(1, 999, null, null, null, null));
        assertEquals(50, page.pageSize());
    }

    // 场景 3：请求 values 中不存在的项进 missingValues
    @Test
    void reportsMissingValues() {
        when(mapper.listUserOptions(any(), anyBoolean(), anyBoolean())).thenReturn(List.of(idRow(1L, "Alice", null, 0)));
        when(mapper.listUserOptionsByValues(anyList())).thenReturn(List.of(idRow(1L, "Alice", null, 0)));

        OptionPage<OptionItem<UserOptionMeta>> page = service.listUserOptions(userQuery(1, 20, null, List.of("1", "999"), null, null));

        assertEquals(List.of("999"), page.missingValues());
        assertEquals(1, page.list().size());
    }

    // 场景 4：回显合并 + 去重 + disabled 标记（停用的已选项也能回显）
    @Test
    void mergesEchoItemsDeduplicatesAndMarksDisabled() {
        when(mapper.listUserOptions(any(), anyBoolean(), anyBoolean())).thenReturn(List.of(idRow(1L, "Alice", null, 0)));
        when(mapper.listUserOptionsByValues(anyList())).thenReturn(List.of(
                idRow(1L, "Alice", null, 0),
                idRow(2L, "Bob", null, 1)));

        OptionPage<OptionItem<UserOptionMeta>> page = service.listUserOptions(userQuery(1, 20, null, List.of("1", "2"), null, null));

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
        when(mapper.listModelPoolOptionsByValues(anyList())).thenReturn(List.of(codeRow("chat-economy-pool", "经济池", null, 0)));

        ModelPoolOptionQuery q = new ModelPoolOptionQuery(1, 20, null, List.of("chat-economy-pool"), null, null, null);
        OptionPage<OptionItem<Void>> page = service.listModelPoolOptions(q);

        assertEquals("chat-economy-pool", page.list().get(0).value());
        // 无附加信息的资源（模型池）meta 恒为 null
        assertNull(page.list().get(0).meta());
        assertTrue(page.missingValues().isEmpty());
    }

    // 场景 7：全部 values 均缺失时 list 为空、missingValues 全量返回；敏感资源不带 meta
    @Test
    void allValuesMissingReturnsEmptyList() {
        when(mapper.listRoutingConsumerOptions(any(), anyBoolean())).thenReturn(List.of());
        when(mapper.listRoutingConsumerOptionsByValues(anyList())).thenReturn(List.of());

        RoutingConsumerOptionQuery q = new RoutingConsumerOptionQuery(1, 20, null, List.of("7", "8"), null, null);
        OptionPage<OptionItem<RoutingConsumerOptionMeta>> page = service.listRoutingConsumerOptions(q);

        assertTrue(page.list().isEmpty());
        assertEquals(List.of("7", "8"), page.missingValues());
    }

    // 补充：路由消费者的业务码走 meta（顶层 code 已移除），meta 只含 consumerCode，不含 secretKey / 凭据
    @Test
    void consumerOptionCarriesConsumerCodeMeta() {
        when(mapper.listRoutingConsumerOptions(any(), anyBoolean()))
                .thenReturn(List.of(idRow(3L, "consumer-a", "{\"consumerCode\":\"ca\"}", 0)));
        OptionPage<OptionItem<RoutingConsumerOptionMeta>> page = service.listRoutingConsumerOptions(null);
        OptionItem<RoutingConsumerOptionMeta> item = page.list().get(0);

        assertEquals(3L, item.value());
        assertNotNull(item.meta());
        assertEquals("ca", item.meta().consumerCode());
    }

    // 补充：计费规则 value=code 回显
    @Test
    void billingRuleValueIsCode() {
        when(mapper.listBillingRuleOptions(any(), anyBoolean(), any())).thenReturn(List.of());
        when(mapper.listBillingRuleOptionsByValues(anyList())).thenReturn(List.of(codeRow("rule-a", "rule-a", null, 0)));
        BillingRuleOptionQuery q = new BillingRuleOptionQuery(1, 20, null, List.of("rule-a"), null, null, null);
        OptionPage<OptionItem<Void>> page = service.listBillingRuleOptions(q);
        assertEquals("rule-a", page.list().get(0).value());
    }

    // 补充：供应商账户 meta 由 json_object 文本反序列化为强类型 ProviderAccountMeta record
    @Test
    void providerAccountMetaDeserializesToRecord() {
        OptionConverter converter = new OptionConverter();
        OptionRow<String> row = codeRow("acc-1", "Aliyun / acc-1", null, 0);
        row.setMeta("{\"baseUrl\":\"https://gis-api.example.com\"}");

        OptionItem<ProviderAccountMeta> item = converter.toProviderAccountItem(row);

        assertEquals("acc-1", item.value());
        assertNotNull(item.meta());
        assertEquals("https://gis-api.example.com", item.meta().baseUrl());
    }

    // 补充：供应商模型 meta 反序列化为强类型 ModelMeta（多字段 + status 整数）
    @Test
    void modelMetaDeserializesToRecord() {
        OptionConverter converter = new OptionConverter();
        OptionRow<String> row = codeRow("gpt-4o", "GPT-4o", null, 0);
        row.setMeta("{\"providerName\":\"OpenAI\",\"providerAccountCode\":\"openai-1\","
                + "\"modelName\":\"GPT-4o\",\"name\":\"GPT-4o\",\"modelType\":\"chat\","
                + "\"logicalModelCode\":\"text.default\",\"status\":1}");

        OptionItem<ModelMeta> item = converter.toModelItem(row);
        ModelMeta meta = item.meta();
        assertNotNull(meta);
        assertEquals("OpenAI", meta.providerName());
        assertEquals("chat", meta.modelType());
        assertEquals("text.default", meta.logicalModelCode());
        assertEquals(1, meta.status());
    }

    // 补充：id 型资源（用户）的业务码走 meta —— username 不再占顶层 code
    @Test
    void userOptionCarriesUsernameMeta() {
        OptionConverter converter = new OptionConverter();
        OptionRow<Long> row = idRow(5L, "Alice", "{\"username\":\"alice\"}", 0);

        OptionItem<UserOptionMeta> item = converter.toUserItem(row);

        assertEquals(5L, item.value());
        assertNotNull(item.meta());
        assertEquals("alice", item.meta().username());
    }
}
