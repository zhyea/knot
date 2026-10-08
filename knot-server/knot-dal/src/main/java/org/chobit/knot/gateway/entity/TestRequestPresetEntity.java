package org.chobit.knot.gateway.entity;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 路由调试预设请求用例。替代 {@code RoutingRuleService#defaultRequestBody} 的硬编码骨架，
 * 由用户在「预设请求」菜单中维护完整、具体的请求体 JSON（按协议归类，全局共享复用）。
 */
@Data
public class TestRequestPresetEntity {
    private Long id;
    private String code;
    private String name;
    private String protocolCode;
    private String logicalModelCode;
    /** 派生自 kb_logical_models，非本表列 */
    private String logicalModelName;
    private String requestBody;
    private String remark;
    /** 启用状态（EnabledStatusEnum）：1-启用 0-停用 */
    private Integer status;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
