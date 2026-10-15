package org.chobit.knot.gateway.outage.admin;

import org.chobit.knot.gateway.outage.CaffeineTargetOutageStore;
import org.chobit.knot.gateway.outage.OutageKeys;
import org.chobit.knot.gateway.outage.TargetOutageStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 网关侧摘除管理面端点的契约：列表 / 手动摘除 / 手动恢复 / 令牌与 loopback 准入。
 *
 * <p>用真实 {@link CaffeineTargetOutageStore}：管理面看到的内容必须与存储真实状态一致，
 * 假存储会把「list 只回非健康条目」这类口径掩盖掉。</p>
 */
class OutageAdminControllerTest {

    private static final String TOKEN_HEADER = "X-Knot-Admin-Token";

    private TargetOutageStore store;
    private OutageKeys keys;
    private OutageAdminProperties properties;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        store = new CaffeineTargetOutageStore(1_000L, 60_000L);
        keys = new OutageKeys("knot:outage");
        properties = new OutageAdminProperties();
        mockMvc = MockMvcBuilders
                .standaloneSetup(new OutageAdminController(store, keys, properties))
                .build();
    }

    @Test
    @DisplayName("无令牌时 loopback 放行；列表只回非健康条目")
    void listReturnsUnhealthyOnlyForLoopback() throws Exception {
        // 计数未达阈值（默认 5）：不算非健康，列表必须为空
        store.recordAnomaly(keys.of("rule-a", "gpt-4o"), org.chobit.knot.gateway.model.EjectionPolicy.DEFAULT,
                org.chobit.knot.gateway.outage.AnomalyKind.FAILURE, System.currentTimeMillis());

        mockMvc.perform(get("/_admin/outages").with(req -> {
                    req.setRemoteAddr("127.0.0.1");
                    return req;
                }))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));

        // 手动摘除后出现一条
        mockMvc.perform(post("/_admin/outages/eject")
                        .with(req -> {
                            req.setRemoteAddr("127.0.0.1");
                            return req;
                        })
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"ruleCode\":\"rule-a\",\"modelCode\":\"gpt-4o\",\"cooldownSec\":120}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ruleCode").value("rule-a"))
                .andExpect(jsonPath("$.modelCode").value("gpt-4o"))
                .andExpect(jsonPath("$.action").value("eject"))
                .andExpect(jsonPath("$.ejected").value(true))
                .andExpect(jsonPath("$.reason").value("MANUAL"));

        mockMvc.perform(get("/_admin/outages").with(req -> {
                    req.setRemoteAddr("127.0.0.1");
                    return req;
                }))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].ruleCode").value("rule-a"))
                .andExpect(jsonPath("$[0].modelCode").value("gpt-4o"))
                .andExpect(jsonPath("$[0].reason").value("MANUAL"))
                .andExpect(jsonPath("$[0].failures").value(0));
    }

    @Test
    @DisplayName("列表按 ruleCode / modelCode 过滤")
    void listFiltersByRuleAndModel() throws Exception {
        eject("rule-a", "gpt-4o");
        eject("rule-b", "claude");
        eject("rule-a", "claude");

        mockMvc.perform(get("/_admin/outages").param("ruleCode", "rule-a").with(loopback()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2));

        mockMvc.perform(get("/_admin/outages").param("ruleCode", "rule-a").param("modelCode", "claude")
                        .with(loopback()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].modelCode").value("claude"));

        mockMvc.perform(get("/_admin/outages").param("modelCode", "no-such").with(loopback()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    @DisplayName("手动恢复后条目消失")
    void releaseClearsEntry() throws Exception {
        eject("rule-a", "gpt-4o");

        mockMvc.perform(post("/_admin/outages/release")
                        .with(loopback())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"ruleCode\":\"rule-a\",\"modelCode\":\"gpt-4o\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.action").value("release"))
                .andExpect(jsonPath("$.ejected").value(false));

        mockMvc.perform(get("/_admin/outages").with(loopback()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    @DisplayName("配了令牌：缺头/错头 403，对头放行")
    void tokenGuardsAccess() throws Exception {
        properties.setToken("s3cret");

        mockMvc.perform(get("/_admin/outages").with(loopback()))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/_admin/outages").header(TOKEN_HEADER, "wrong").with(loopback()))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/_admin/outages").header(TOKEN_HEADER, "s3cret").with(remote("10.0.0.5")))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("未配令牌：非 loopback 一律 403")
    void nonLoopbackDeniedWithoutToken() throws Exception {
        mockMvc.perform(get("/_admin/outages").with(remote("10.0.0.5")))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/_admin/outages").with(remote("0:0:0:0:0:0:0:1")))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("手动摘除参数缺失 400")
    void ejectRejectsBlankFields() throws Exception {
        mockMvc.perform(post("/_admin/outages/eject")
                        .with(loopback())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"ruleCode\":\"\",\"modelCode\":\"gpt-4o\"}"))
                .andExpect(status().isBadRequest());

        mockMvc.perform(post("/_admin/outages/eject")
                        .with(loopback())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"ruleCode\":\"rule-a\",\"modelCode\":null}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("手动摘除默认冷却 60s，回执带剩余冷却")
    void ejectUsesDefaultCooldown() throws Exception {
        MvcResult result = mockMvc.perform(post("/_admin/outages/eject")
                        .with(loopback())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"ruleCode\":\"rule-a\",\"modelCode\":\"gpt-4o\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ejected").value(true))
                .andReturn();

        String body = result.getResponse().getContentAsString();
        long ejectedUntil = Long.parseLong(
                body.replaceAll(".*\"ejectedUntil\":(\\d+).*", "$1"));
        long remaining = ejectedUntil - System.currentTimeMillis();
        org.junit.jupiter.api.Assertions.assertTrue(remaining > 50_000L && remaining <= 60_000L,
                "默认冷却应约 60s，实际剩余 " + remaining + "ms");
    }

    private void eject(String ruleCode, String modelCode) throws Exception {
        mockMvc.perform(post("/_admin/outages/eject")
                        .with(loopback())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"ruleCode\":\"" + ruleCode + "\",\"modelCode\":\"" + modelCode
                                + "\",\"cooldownSec\":300}"))
                .andExpect(status().isOk());
    }

    private static RequestPostProcessor loopback() {
        return req -> {
            req.setRemoteAddr("127.0.0.1");
            return req;
        };
    }

    private static RequestPostProcessor remote(String addr) {
        return req -> {
            req.setRemoteAddr(addr);
            return req;
        };
    }
}
