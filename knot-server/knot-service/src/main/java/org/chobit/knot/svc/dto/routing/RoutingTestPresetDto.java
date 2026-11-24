package org.chobit.knot.svc.dto.routing;

public record RoutingTestPresetDto(Long id,
                                   String code,
                                   String name,
                                   String protocolCode,
                                   /** 模型族 code（ks_enum_configs.category=model_family 的 item_code），null 表示通用用例 */
                                   String modelFamily,
                                   String modelFamilyName,
                                   String requestBody,
                                   String remark,
                                   /** 启用状态（EnabledStatusEnum）：1-启用 0-停用 */
                                   Integer status) {
}
