package org.chobit.knot.gateway.controller;

import org.chobit.knot.gateway.config.GatewayRuntimeProperties;
import org.chobit.knot.gateway.dto.outage.GatewayOutageAck;
import org.chobit.knot.gateway.dto.outage.GatewayOutageEjectRequest;
import org.chobit.knot.gateway.dto.outage.GatewayOutageItem;
import org.chobit.knot.gateway.dto.outage.GatewayOutageReleaseRequest;
import org.chobit.knot.gateway.service.support.GatewayOutageRunner;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;

import java.util.List;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * admin 侧网关摘除管理面控制器：JDK 25 下 Mockito inline 无法 mock 具体类，故用桩子类承接
 * {@link GatewayOutageRunner}（仅验证委托关系、参数透传与 {@code @Valid} 校验，不含 AOP 切面）。
 */
class GatewayOutageControllerTest {

    /** 桩子类：记录调用参数并回灌预置响应，避免 JDK 25 下 mock 具体类的 Byte Buddy 限制。 */
    static class StubGatewayOutageRunner extends GatewayOutageRunner {
        List<GatewayOutageItem> itemsToReturn = List.of();
        GatewayOutageAck releaseAck = new GatewayOutageAck("r", "m", "release", false, 0L, "");
        GatewayOutageAck ejectAck = new GatewayOutageAck("r", "m", "eject", true, 1L, "MANUAL");
        String lastListBaseUrl;
        String lastListToken;
        String lastListRule;
        String lastListModel;
        String lastReleaseBaseUrl;
        String lastReleaseToken;
        GatewayOutageReleaseRequest lastReleaseReq;
        String lastEjectBaseUrl;
        String lastEjectToken;
        GatewayOutageEjectRequest lastEjectReq;

        @Override
        public List<GatewayOutageItem> listOutages(String gatewayBaseUrl, String adminToken,
                                                  String ruleCode, String modelCode) {
            this.lastListBaseUrl = gatewayBaseUrl;
            this.lastListToken = adminToken;
            this.lastListRule = ruleCode;
            this.lastListModel = modelCode;
            return itemsToReturn;
        }

        @Override
        public GatewayOutageAck release(String gatewayBaseUrl, String adminToken,
                                       GatewayOutageReleaseRequest request) {
            this.lastReleaseBaseUrl = gatewayBaseUrl;
            this.lastReleaseToken = adminToken;
            this.lastReleaseReq = request;
            return releaseAck;
        }

        @Override
        public GatewayOutageAck eject(String gatewayBaseUrl, String adminToken,
                                     GatewayOutageEjectRequest request) {
            this.lastEjectBaseUrl = gatewayBaseUrl;
            this.lastEjectToken = adminToken;
            this.lastEjectReq = request;
            return ejectAck;
        }
    }

    private StubGatewayOutageRunner runner;
    private GatewayRuntimeProperties properties;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        runner = new StubGatewayOutageRunner();
        properties = new GatewayRuntimeProperties();
        properties.setBaseUrl("http://gw:9090");
        properties.setAdminToken("tok");
        mockMvc = MockMvcBuilders.standaloneSetup(new GatewayOutageController(runner, properties))
                .setValidator(new LocalValidatorFactoryBean() {{
                    afterPropertiesSet();
                }})
                .build();
    }

    @Test
    @DisplayName("list 委托 runner 并透传 baseUrl / token / 过滤参数")
    void listDelegates() throws Exception {
        GatewayOutageItem item = new GatewayOutageItem("r1", "m1", 0, 0, 0L, 1L, 0L, 60000L, "MANUAL");
        runner.itemsToReturn = List.of(item);

        mockMvc.perform(get("/api/gateway/outages").param("ruleCode", "r1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].ruleCode").value("r1"))
                .andExpect(jsonPath("$[0].reason").value("MANUAL"));

        org.junit.jupiter.api.Assertions.assertEquals("http://gw:9090", runner.lastListBaseUrl);
        org.junit.jupiter.api.Assertions.assertEquals("tok", runner.lastListToken);
        org.junit.jupiter.api.Assertions.assertEquals("r1", runner.lastListRule);
        org.junit.jupiter.api.Assertions.assertNull(runner.lastListModel);
    }

    @Test
    @DisplayName("release 委托 runner 并返回 ack")
    void releaseDelegates() throws Exception {
        mockMvc.perform(post("/api/gateway/outages/release")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"ruleCode\":\"r1\",\"modelCode\":\"m1\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.action").value("release"))
                .andExpect(jsonPath("$.ejected").value(false));

        org.junit.jupiter.api.Assertions.assertEquals("http://gw:9090", runner.lastReleaseBaseUrl);
        org.junit.jupiter.api.Assertions.assertEquals("tok", runner.lastReleaseToken);
        org.junit.jupiter.api.Assertions.assertEquals(new GatewayOutageReleaseRequest("r1", "m1"),
                runner.lastReleaseReq);
    }

    @Test
    @DisplayName("eject 委托 runner 并返回 ack")
    void ejectDelegates() throws Exception {
        mockMvc.perform(post("/api/gateway/outages/eject")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"ruleCode\":\"r1\",\"modelCode\":\"m1\",\"cooldownSec\":120}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.action").value("eject"))
                .andExpect(jsonPath("$.ejected").value(true));

        org.junit.jupiter.api.Assertions.assertEquals("http://gw:9090", runner.lastEjectBaseUrl);
        org.junit.jupiter.api.Assertions.assertEquals("tok", runner.lastEjectToken);
        org.junit.jupiter.api.Assertions.assertNotNull(runner.lastEjectReq);
        org.junit.jupiter.api.Assertions.assertEquals("r1", runner.lastEjectReq.ruleCode());
        org.junit.jupiter.api.Assertions.assertEquals("m1", runner.lastEjectReq.modelCode());
        org.junit.jupiter.api.Assertions.assertEquals(120, runner.lastEjectReq.cooldownSec());
    }

    @Test
    @DisplayName("release 缺 modelCode 触发 @Valid 校验 400")
    void releaseRejectsBlank() throws Exception {
        mockMvc.perform(post("/api/gateway/outages/release")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"ruleCode\":\"r1\",\"modelCode\":\"\"}"))
                .andExpect(status().isBadRequest());
    }
}
