package org.chobit.knot.svc.vo.gateway;

/**
 * 网关配置快照：模型 API 绑定（协议、路径、适配器与用量提取器）。
 * 字段逐字平移 {@code ModelApiBindingEntity}，不裁剪；新增实体字段时 {@code GatewayConfigConverter} 必须同步。
 */
public record ModelApiBindingConfigVo(
        Long id,
        Long modelId,
        String modelCode,
        String providerAccountCode,
        String protocol,
        String apiPath,
        String requestAdapter,
        String usageExtractor,
        String streamUsageExtractor,
        String asyncProfileCode,
        Integer status,
        String remark
) {
}
