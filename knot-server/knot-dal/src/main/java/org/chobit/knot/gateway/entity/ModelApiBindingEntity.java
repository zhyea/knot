package org.chobit.knot.gateway.entity;

import lombok.Data;
import org.chobit.knot.gateway.constants.enums.ModelApiProtocolEnum;

@Data
public class ModelApiBindingEntity {
    private Long id;
    /** 由 join kb_models 派生（m.id），不落库 */
    private Long modelId;
    /**
     * 落库列：关联 kb_models.model_code（业务码，非主键 id）
     */
    private String modelCode;
    private String providerAccountCode;
    /**
     * 协议编码，见 {@link ModelApiProtocolEnum}
     */
    private String protocol;
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
    /** 启用状态（EnabledStatusEnum）：1-启用 0-停用 */
    private Integer status;
    private String remark;
}
