package org.chobit.knot.dal.entity;

import lombok.Data;
import org.chobit.knot.common.constants.enums.ModelApiProtocolEnum;

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
    /**
     * 异步任务档案编码（{@code kb_async_task_profiles.profile_code}）；为空表示同步链路。
     *
     * <p>⭐ 异步与否是「绑定」的属性，不是协议的属性：同为 {@code VIDEO_GENERATIONS}，
     * 可灵 / MiniMax 是异步（提交 + 轮询），其它模型可能同步。判异步一律看本列有没有值。</p>
     */
    private String asyncProfileCode;
    /** 启用状态（EnabledStatusEnum）：1-启用 0-停用 */
    private Integer status;
    private String remark;
}
