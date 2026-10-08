package org.chobit.knot.gateway.controller;

import org.chobit.knot.gateway.GlobalExceptionHandler;
import org.chobit.knot.gateway.converter.OptionConverter;
import org.chobit.knot.gateway.entity.OptionRow;
import org.chobit.knot.gateway.mapper.OptionsMapper;
import org.chobit.knot.gateway.rw.RwProperties;
import org.chobit.knot.gateway.service.OptionsService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 阶段一/四 options 端点的 controller 级契约单测。
 *
 * <p>用 MockMvc standaloneSetup（不启 Spring 上下文、不过拦截器）驱动真实 controller →
 * 真实 {@link OptionsService} → mock 的 {@link OptionsMapper}（接口，避开 JDK25 +
 * Mockito inline 对具体类的限制）。11 个端点各跑 7 场景：</p>
 * <ol>
 *   <li>无关键字：默认 enabledOnly=true、pageNum=1、pageSize=20、missingValues 空；</li>
 *   <li>关键字：keyword 原样传到 mapper（非空）；</li>
 *   <li>单值回显：已选项不在检索页也能由 byValues 回显，missingValues 空；</li>
 *   <li>多值回显：其中一个不存在 → 进 missingValues；</li>
 *   <li>停用项回显：byValues 返回的 disabled=1 序列化为 true（前端渲染禁用态）；</li>
 *   <li>全部缺失：检索页与回显皆空 → list 空、missingValues 全量返回；</li>
 *   <li>边界：pageSize&gt;50 钳制到 50、values&gt;100 拒绝（BusinessException → 200 + success=false）。</li>
 * </ol>
 *
 * <p>另有两项跨端点断言：敏感资源（路由消费者 / 供应商账户）响应不含 secretKey 字段；
 * 所有端点响应字段集合固定为 list/total/pageNum/pageSize/missingValues。</p>
 */
class OptionsEndpointContractTest {

    private static final String CT = "application/json";

    private OptionsMapper mapper;
    private OptionsService service;

    @BeforeEach
    void setUp() {
        mapper = mock(OptionsMapper.class);
        service = new OptionsService(mapper, new OptionConverter());
    }

    /** mapper 打桩器：检索页返回 search、回显返回 echo，并把检索调用参数记进 calls。 */
    private interface Stubber {
        void stub(OptionsMapper m, List<OptionRow<?>> search,
                  List<OptionRow<?>> echo, List<Object[]> calls);
    }

    /**
     * 端点描述符：路径 + controller 工厂 + 打桩器 + 该资源样例 value + 是否有启用态过滤。
     *
     * <p>{@code sample} 类型随资源的 value 语义：id 型资源为 {@link Long}（用户/部门/应用/
     * 路由消费者/角色），code 型为 {@link String}（供应商账户/统一模型/供应商模型/模型池/
     * 计费规则/供应商信息）。泛型化后这一约定由编译器与运行期共同守护。</p>
     */
    private record Endpoint(String name, String path, Function<OptionsService, Object> factory,
                            Stubber stubber, Object sample, boolean hasEnabledOnly) {
        @Override
        public String toString() {
            return name;
        }
    }

    static List<Endpoint> endpoints() {
        return List.of(
                new Endpoint("用户", "/api/users/options",
                        svc -> new UserController(null, null, svc),
                        (m, s, e, c) -> {
                            when(m.listUserOptions(any(), anyBoolean(), anyBoolean()))
                                    .thenAnswer(inv -> capture(c, inv.getArguments(), s));
                            when(m.listUserOptionsByValues(anyList())).thenAnswer(inv -> e);
                        }, 1L, true),
                new Endpoint("部门", "/api/system/departments/options",
                        svc -> new DepartmentController(null, null, svc),
                        (m, s, e, c) -> {
                            when(m.listDepartmentOptions(any(), anyBoolean(), anyBoolean()))
                                    .thenAnswer(inv -> capture(c, inv.getArguments(), s));
                            when(m.listDepartmentOptionsByValues(anyList())).thenAnswer(inv -> e);
                        }, 2L, true),
                new Endpoint("应用", "/api/apps/options",
                        svc -> new AppController(null, null, svc),
                        (m, s, e, c) -> {
                            when(m.listAppOptions(any(), anyBoolean(), anyBoolean()))
                                    .thenAnswer(inv -> capture(c, inv.getArguments(), s));
                            when(m.listAppOptionsByValues(anyList())).thenAnswer(inv -> e);
                        }, 3L, true),
                new Endpoint("供应商账户", "/api/provider-accounts/options",
                        svc -> new ProviderController(null, null, svc),
                        (m, s, e, c) -> {
                            when(m.listProviderAccountOptions(any(), anyBoolean()))
                                    .thenAnswer(inv -> capture(c, inv.getArguments(), s));
                            when(m.listProviderAccountOptionsByValues(anyList())).thenAnswer(inv -> e);
                        }, "acc-1", true),
                new Endpoint("统一模型", "/api/logical-models/options",
                        svc -> new LogicalModelController(null, null, svc),
                        (m, s, e, c) -> {
                            when(m.listLogicalModelOptions(any(), anyBoolean(), anyBoolean(), any()))
                                    .thenAnswer(inv -> capture(c, inv.getArguments(), s));
                            when(m.listLogicalModelOptionsByValues(anyList())).thenAnswer(inv -> e);
                        }, "text.default", true),
                new Endpoint("供应商模型", "/api/models/options",
                        svc -> new ModelController(null, null, svc),
                        (m, s, e, c) -> {
                            when(m.listModelOptions(any(), anyBoolean(), any(), any(), any()))
                                    .thenAnswer(inv -> capture(c, inv.getArguments(), s));
                            when(m.listModelOptionsByValues(anyList())).thenAnswer(inv -> e);
                        }, "gpt-4o", true),
                new Endpoint("模型池", "/api/model-pools/options",
                        svc -> new ModelPoolController(null, null, svc),
                        (m, s, e, c) -> {
                            when(m.listModelPoolOptions(any(), anyBoolean(), anyBoolean(), any()))
                                    .thenAnswer(inv -> capture(c, inv.getArguments(), s));
                            when(m.listModelPoolOptionsByValues(anyList())).thenAnswer(inv -> e);
                        }, "chat-economy-pool", true),
                new Endpoint("路由消费者", "/api/routing-consumers/options",
                        svc -> new RoutingConsumerController(null, svc),
                        (m, s, e, c) -> {
                            when(m.listRoutingConsumerOptions(any(), anyBoolean()))
                                    .thenAnswer(inv -> capture(c, inv.getArguments(), s));
                            when(m.listRoutingConsumerOptionsByValues(anyList())).thenAnswer(inv -> e);
                        }, 5L, true),
                new Endpoint("计费规则", "/api/billing/options",
                        svc -> new BillingController(null, null, svc),
                        (m, s, e, c) -> {
                            when(m.listBillingRuleOptions(any(), anyBoolean(), any()))
                                    .thenAnswer(inv -> capture(c, inv.getArguments(), s));
                            when(m.listBillingRuleOptionsByValues(anyList())).thenAnswer(inv -> e);
                        }, "rule-a", true),
                new Endpoint("角色", "/api/system/authorizations/roles/options",
                        svc -> new AuthorizationRoleController(null, svc),
                        (m, s, e, c) -> {
                            when(m.listRoleOptions(any()))
                                    .thenAnswer(inv -> capture(c, inv.getArguments(), s));
                            when(m.listRoleOptionsByValues(anyList())).thenAnswer(inv -> e);
                        }, 7L, false),
                new Endpoint("供应商信息", "/api/provider-profiles/options",
                        svc -> new ProviderProfileController(null, svc),
                        (m, s, e, c) -> {
                            when(m.listProviderProfileOptions(any()))
                                    .thenAnswer(inv -> capture(c, inv.getArguments(), s));
                            when(m.listProviderProfileOptionsByValues(anyList())).thenAnswer(inv -> e);
                        }, "aliyun", false)
        );
    }

    private static List<OptionRow<?>> capture(List<Object[]> calls, Object[] args,
                List<OptionRow<?>> rows) {
        calls.add(args);
        return rows;
    }

    /**
     * 打桩行。value 的实际类型必须与端点的 value 语义一致（id 型=Long / code 型=String）——
     * 泛型化后这一条由编译器守护：id 型端点误传 String 桩会直接 ClassCastException。
     */
    private static <V> OptionRow<V> row(V value, String label, String code, int disabled) {
        OptionRow<V> r = new OptionRow<>();
        r.setValue(value);
        r.setLabel(label);
        r.setCode(code);
        r.setDisabled(disabled);
        return r;
    }

    private MockMvc mvcFor(Endpoint ep) {
        return MockMvcBuilders.standaloneSetup(ep.factory().apply(service))
                .setControllerAdvice(new GlobalExceptionHandler(new RwProperties()))
                .build();
    }

    private static String body(String... pairs) {
        return "{" + String.join(",", pairs) + "}";
    }

    private static String valuesBody(List<String> values) {
        return body("\"values\":[" + values.stream()
                .map(v -> "\"" + v + "\"")
                .collect(Collectors.joining(",")) + "]");
    }

    // ---------------- 场景 1：无关键字 → 默认 enabledOnly=true / pageNum=1 / pageSize=20 ----------------
    @ParameterizedTest(name = "S1 无关键字默认契约 - {0}")
    @MethodSource("endpoints")
    void scenario1Defaults(Endpoint ep) throws Exception {
        List<Object[]> calls = new ArrayList<>();
        ep.stubber().stub(mapper, List.of(row(ep.sample(), "Alice", "alice", 0)), List.of(), calls);

        mvcFor(ep).perform(post(ep.path()).contentType(CT).content("{}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.pageNum").value(1))
                .andExpect(jsonPath("$.pageSize").value(20))
                .andExpect(jsonPath("$.missingValues").isEmpty())
                .andExpect(jsonPath("$.list.length()").value(1))
                .andExpect(jsonPath("$.list[0].label").value("Alice"));

        assertEquals(1, calls.size());
        if (ep.hasEnabledOnly()) {
            assertEquals(Boolean.TRUE, calls.get(0)[1], "enabledOnly 应默认 true（约束第 0 节第 11 条）");
        }
    }

    // ---------------- 场景 2：关键字原样下传 ----------------
    @ParameterizedTest(name = "S2 关键字下传 - {0}")
    @MethodSource("endpoints")
    void scenario2Keyword(Endpoint ep) throws Exception {
        List<Object[]> calls = new ArrayList<>();
        ep.stubber().stub(mapper, List.of(row(ep.sample(), "Alice", "alice", 0)), List.of(), calls);

        mvcFor(ep).perform(post(ep.path()).contentType(CT).content(body("\"keyword\":\"ali\"")))
                .andExpect(status().isOk());

        assertEquals(1, calls.size());
        assertEquals("ali", calls.get(0)[0], "keyword 应原样传到 mapper");
    }

    // ---------------- 场景 3：单值回显（已选项不在检索页也能回显） ----------------
    @ParameterizedTest(name = "S3 单值回显 - {0}")
    @MethodSource("endpoints")
    void scenario3SingleValueEcho(Endpoint ep) throws Exception {
        List<Object[]> calls = new ArrayList<>();
        ep.stubber().stub(mapper, List.of(), List.of(row(ep.sample(), "Echo", "echo-code", 0)), calls);

        mvcFor(ep).perform(post(ep.path()).contentType(CT).content(valuesBody(List.of(String.valueOf(ep.sample())))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.list.length()").value(1))
                .andExpect(jsonPath("$.list[0].label").value("Echo"))
                .andExpect(jsonPath("$.missingValues").isEmpty());
    }

    // ---------------- 场景 4：多值回显 + missingValues ----------------
    @ParameterizedTest(name = "S4 多值回显含缺失 - {0}")
    @MethodSource("endpoints")
    void scenario4MultiValueWithMissing(Endpoint ep) throws Exception {
        List<Object[]> calls = new ArrayList<>();
        ep.stubber().stub(mapper, List.of(), List.of(row(ep.sample(), "Echo", "echo-code", 0)), calls);

        mvcFor(ep).perform(post(ep.path()).contentType(CT)
                        .content(valuesBody(List.of(String.valueOf(ep.sample()), "ghost"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.list.length()").value(1))
                .andExpect(jsonPath("$.missingValues.length()").value(1))
                .andExpect(jsonPath("$.missingValues[0]").value("ghost"));
    }

    // ---------------- 场景 5：停用项回显标 disabled ----------------
    @ParameterizedTest(name = "S5 停用项回显禁用 - {0}")
    @MethodSource("endpoints")
    void scenario5DisabledEcho(Endpoint ep) throws Exception {
        List<Object[]> calls = new ArrayList<>();
        ep.stubber().stub(mapper, List.of(), List.of(row(ep.sample(), "Echo", "echo-code", 1)), calls);

        mvcFor(ep).perform(post(ep.path()).contentType(CT).content(valuesBody(List.of(String.valueOf(ep.sample())))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.list[0].disabled").value(true))
                .andExpect(jsonPath("$.missingValues").isEmpty());
    }

    // ---------------- 场景 6：全部缺失（删除项 / 无权限项） ----------------
    @ParameterizedTest(name = "S6 全部缺失 - {0}")
    @MethodSource("endpoints")
    void scenario6AllMissing(Endpoint ep) throws Exception {
        List<Object[]> calls = new ArrayList<>();
        ep.stubber().stub(mapper, List.of(), List.of(), calls);

        mvcFor(ep).perform(post(ep.path()).contentType(CT)
                        .content(valuesBody(List.of("ghost-a", "ghost-b"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.list").isEmpty())
                .andExpect(jsonPath("$.missingValues.length()").value(2));
    }

    // ---------------- 场景 7：pageSize>50 钳制 + values>100 拒绝 ----------------
    @ParameterizedTest(name = "S7 边界钳制与拒绝 - {0}")
    @MethodSource("endpoints")
    void scenario7Boundaries(Endpoint ep) throws Exception {
        List<Object[]> calls = new ArrayList<>();
        ep.stubber().stub(mapper, List.of(), List.of(), calls);

        mvcFor(ep).perform(post(ep.path()).contentType(CT).content(body("\"pageSize\":999")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.pageSize").value(50));

        List<String> tooMany = new ArrayList<>();
        for (int i = 0; i < 101; i++) {
            tooMany.add(String.valueOf(i));
        }
        mvcFor(ep).perform(post(ep.path()).contentType(CT).content(valuesBody(tooMany)))
                // BusinessException 无 @ResponseStatus → HTTP 200 + success=false
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(false));
    }

    // ---------------- 跨端点：响应字段集合固定，且不含敏感字段 ----------------
    @ParameterizedTest(name = "敏感字段隔离 + 字段集合 - {0}")
    @MethodSource("endpoints")
    void responseShapeAndNoSecretLeak(Endpoint ep) throws Exception {
        List<Object[]> calls = new ArrayList<>();
        ep.stubber().stub(mapper, List.of(row(ep.sample(), "Alice", "alice", 0)), List.of(), calls);

        String json = mvcFor(ep).perform(post(ep.path()).contentType(CT).content("{}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        String lower = json.toLowerCase();
        for (String leak : List.of("secretkey", "secret_key", "encryptedconfig", "encrypted_config",
                "configjson", "passwordhash", "appsecret", "credential")) {
            org.junit.jupiter.api.Assertions.assertFalse(lower.contains(leak),
                    "options 响应不得包含敏感字段: " + leak + " → " + json);
        }
        for (String field : List.of("\"list\"", "\"total\"", "\"pageNum\"", "\"pageSize\"", "\"missingValues\"")) {
            org.junit.jupiter.api.Assertions.assertTrue(json.contains(field),
                    "options 响应缺少契约字段: " + field);
        }
    }
}
