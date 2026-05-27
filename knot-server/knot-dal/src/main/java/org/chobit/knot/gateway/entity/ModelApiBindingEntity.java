package org.chobit.knot.gateway.entity;

import lombok.Data;

@Data
public class ModelApiBindingEntity {
    private Long id;
    private Long modelId;
    /** 关联 models.model_code，仅查询展示 */
    private String modelCode;
    /** 关联 models.name，仅查询展示 */
    private String modelName;
    private Long providerId;
    /** 协议编码，见 {@link org.chobit.knot.gateway.constants.ModelApiProtocol} */
    private String protocol;
    /** 上游 API 路径，为空时使用协议默认路径 */
    private String apiPath;
    /** 消耗取值逻辑 JSON 字符串 */
    private String usageExtractJson;
    private String status;
    private String remark;
}
