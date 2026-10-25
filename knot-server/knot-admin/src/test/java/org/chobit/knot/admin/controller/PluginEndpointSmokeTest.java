package org.chobit.knot.admin.controller;

import java.util.ArrayList;
import java.util.List;
import org.chobit.knot.admin.GlobalExceptionHandler;
import org.chobit.knot.gateway.converter.PluginConverterImpl;
import org.chobit.knot.dal.entity.PluginInstanceEntity;
import org.chobit.knot.dal.mapper.PluginInstanceMapper;
import org.chobit.knot.admin.rw.RwProperties;
import org.chobit.knot.svc.service.PluginService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;



/**
 * 插件管理单表收口（A+B）端点级契约冒烟：不启 Spring 上下文、不加鉴权拦截器，
 * 直接用真实 {@link PluginController} → 真实 {@link PluginService} → mock 的
 * {@link PluginInstanceMapper} 驱动。验证三件事：
 *  1) list 返回 6 个 SYNC 插件、status=1；
 *  2) updateStatus 即时把 status 透传到 mapper 并在响应回显；
 *  3) DELETE 逻辑删除即时把 is_deleted 透传到 mapper（死行不展示）。
 * 真实 DB 往返已由 live 启动 + PluginReconcileRunner 在真实 MySQL 上证明（ioc=9/existing=6 全命中）。
 */
class PluginEndpointSmokeTest {

    private PluginInstanceMapper mapper;
    private MockMvc mockMvc;
    private final PluginInstanceEntity one = buildEntity(1L, "gateway-request-log", "网关请求日志插件", 1);

    @BeforeEach
    void setUp() {
        mapper = mock(PluginInstanceMapper.class);

        List<PluginInstanceEntity> six = new ArrayList<>();
        six.add(one);
        six.add(buildEntity(2L, "gateway-response-log", "网关响应日志插件", 1));
        six.add(buildEntity(3L, "gateway-error-log", "网关异常日志插件", 1));
        six.add(buildEntity(4L, "provider-request-log", "上游请求日志插件", 1));
        six.add(buildEntity(5L, "provider-response-log", "上游响应日志插件", 1));
        six.add(buildEntity(6L, "provider-error-log", "上游异常日志插件", 1));
        when(mapper.list(any(), any(), any())).thenReturn(six);

        // getById 返回可变实体，使 updateStatus/updateDeleted 的后续 getById 回显最新状态
        when(mapper.getById(anyLong())).thenReturn(one);
        Mockito.doAnswer(inv -> {
            one.setStatus(inv.getArgument(1));
            return null;
        }).when(mapper).updateStatus(anyLong(), anyInt());
        Mockito.doAnswer(inv -> {
            one.setIsDeleted(inv.getArgument(1));
            return null;
        }).when(mapper).updateDeleted(anyLong(), anyInt());

        PluginService service = new PluginService(mapper, new PluginConverterImpl());
        PluginController controller = new PluginController(service, new PluginConverterImpl());
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler(new RwProperties()))
                .build();
    }

    private static PluginInstanceEntity buildEntity(Long id, String code, String name, Integer status) {
        PluginInstanceEntity e = new PluginInstanceEntity();
        e.setId(id);
        e.setCode(code);
        e.setName(name);
        e.setPackageCode("builtin-gateway-audit");
        e.setPackageName("网关请求响应审计插件");
        e.setCapabilityCode("gateway-request-response-log");
        e.setCapabilityName("网关请求响应日志");
        e.setExtensionPoint("GATEWAY_EXCHANGE");
        e.setStageCode("GATEWAY_REQUEST");
        e.setVersion("1.0.0");
        e.setStatus(status);
        e.setTimeoutMs(3000);
        e.setPluginType("SYNC");
        e.setConfigJson(null);
        e.setEntrypoint("org.chobit.knot.plugin.builtin.GatewayRequestLoggingPlugin");
        e.setSourceType("BUILTIN");
        e.setOrderNo(100);
        e.setIsDeleted(0);
        e.setOrphaned(0);
        return e;
    }

    @Test
    void list_returnsSixSyncEnabled() throws Exception {
        mockMvc.perform(post("/api/plugins/list").contentType("application/json").content("{}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(6))
                .andExpect(jsonPath("$.list.length()").value(6))
                .andExpect(jsonPath("$.list[0].status").value(1))
                .andExpect(jsonPath("$.list[0].pluginType").value("SYNC"))
                .andExpect(jsonPath("$.list[0].code").value("gateway-request-log"));
    }

    @Test
    void updateStatus_togglesAndEchoes() throws Exception {
        mockMvc.perform(put("/api/plugins/1/status").contentType("application/json").content("{\"status\":0}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.status").value(0));
        verify(mapper, times(1)).updateStatus(eq(1L), eq(0));

        mockMvc.perform(put("/api/plugins/1/status").contentType("application/json").content("{\"status\":1}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value(1));
        verify(mapper, times(1)).updateStatus(eq(1L), eq(1));
    }

    @Test
    void delete_logicalMarksDeleted() throws Exception {
        mockMvc.perform(delete("/api/plugins/1").contentType("application/json"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1));
        verify(mapper, atLeastOnce()).updateDeleted(eq(1L), eq(1));
    }

    @Test
    void unknownPlugin_returnsNotFoundBusiness() throws Exception {
        when(mapper.getById(999L)).thenReturn(null);
        mockMvc.perform(put("/api/plugins/999/status").contentType("application/json").content("{\"status\":0}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(false));
    }
}
