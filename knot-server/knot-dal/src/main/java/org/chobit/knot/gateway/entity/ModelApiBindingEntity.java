package org.chobit.knot.gateway.entity;

import lombok.Data;
import org.chobit.knot.gateway.constants.enums.ModelApiProtocolEnum;

@Data
public class ModelApiBindingEntity {
    private Long id;
    private Long modelId;
    /**
     * 关联 models.model_code，仅查询展示
     */
    private String modelCode;
    /**
     * 关联 models.name，仅查询展示
     */
    private String modelName;
    private Long providerId;
    /**
     * 协议编码，见 {@link ModelApiProtocolEnum}
     */
    private String protocol;
    private String baseUrl;
    /**
     * 上游 API 路径，为空时使用协议默认路径
     */
    private String apiPath;
    private String requestAdapter;
    /**
     * 消耗取值逻辑 JSON 字符串
     */
    private String usageExtractor;
    private String streamUsageExtractor;
    private String status;
    private String remark;
}
