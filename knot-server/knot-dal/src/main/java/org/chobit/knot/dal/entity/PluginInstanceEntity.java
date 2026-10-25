package org.chobit.knot.dal.entity;

import lombok.Data;

/**
 * 插件实例（{@code kb_plugin_instances}）。
 *
 * <p>本实体只承载「代码事实的物化 + 运维开关」两类字段，边界见
 * {@code PluginReconcileRunner} 的类注释：前者每次启动由 descriptor 全覆盖写，
 * 后者（{@code status} / {@code isDeleted}）由运维持有、reconcile 不覆盖。</p>
 *
 * <p>2026-10-25 删除了六个字段，均为「插件包」概念与冗余副本的残留：
 * {@code packageCode} / {@code packageName}（包分组，前后端零读取）、
 * {@code capabilityName}（能力码的中文副本，无人读取）、
 * {@code sourceType}（BUILTIN/JAR，JAR 已确认不支持、无人读取）、
 * {@code configJson}（预留列，从未写入也从未读取）。</p>
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
