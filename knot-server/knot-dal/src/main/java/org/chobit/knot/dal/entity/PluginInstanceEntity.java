package org.chobit.knot.dal.entity;

import lombok.Data;

/**
 * 插件实例（{@code kb_plugin_instances}）。
 * 承载两类字段：代码事实的物化（每次启动由 descriptor 全覆盖写）、运维开关
 * （{@code status} / {@code isDeleted}，由运维持有，reconcile 不覆盖）。
 */
@Data
public class PluginInstanceEntity {
    private Long id;
    /** 实例业务码，由 {@code PluginDescriptor.code} 声明 */
    private String code;
    /** 实例名称，由 {@code PluginDescriptor.name} 声明 */
    private String name;
    /** 能力族码，多个插件可共享 */
    private String capabilityCode;
    private String extensionPoint;
    private String stageCode;
    private String version;
    /** 插件实现类全限定名（IOC 稳定键，reconcile 以此为唯一匹配键） */
    private String entrypoint;
    /** 同扩展点+阶段的执行顺序 */
    private Integer orderNo;
    /** 启用状态（EnabledStatusEnum）：1-启用 0-停用；运维权威，reconcile 不覆盖 */
    private Integer status;
    private Integer timeoutMs;
    /** 插件执行类型（PluginTypeEnum）：SYNC-同步插件 */
    private String pluginType;
    /** 逻辑删除：运维权威，reconcile 不覆盖 */
    private Integer isDeleted;
    /** 代码已移除标记（reconcile 每次启动重算）：1-孤儿 0-正常 */
    private Integer orphaned;
}
