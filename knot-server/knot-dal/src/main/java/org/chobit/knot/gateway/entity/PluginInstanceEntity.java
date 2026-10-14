package org.chobit.knot.gateway.entity;

import lombok.Data;

@Data
public class PluginInstanceEntity {
    private Long id;
    private String code;
    private String name;
    private String packageCode;
    private String packageName;
    private String capabilityCode;
    private String capabilityName;
    private String extensionPoint;
    private String stageCode;
    private String version;
    /** 插件实现类全限定名（IOC 稳定键，启动期 reconcile 按此匹配） */
    private String entrypoint;
    /** 来源类型（BUILTIN/JAR，当前仅 BUILTIN） */
    private String sourceType;
    /** 同阶段执行顺序 */
    private Integer orderNo;
    /** 启用状态（EnabledStatusEnum）：1-启用 0-停用 */
    private Integer status;
    private Integer timeoutMs;
    /** 插件执行类型（PluginTypeEnum）：SYNC-同步插件 */
    private String pluginType;
    private String configJson;
    /** 逻辑删除（死行不展示）：1-已删除 0-未删除 */
    private Integer isDeleted;
    /** 代码已移除标记（reconcile 检出 IOC 无此插件）：1-孤儿 0-正常 */
    private Integer orphaned;
}
