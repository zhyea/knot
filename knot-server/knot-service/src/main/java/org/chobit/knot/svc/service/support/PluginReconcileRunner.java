package org.chobit.knot.svc.service.support;

import lombok.extern.slf4j.Slf4j;

import org.chobit.knot.dal.entity.PluginInstanceEntity;
import org.chobit.knot.dal.mapper.PluginInstanceMapper;
import org.chobit.knot.plugin.PluginDescriptor;
import org.chobit.knot.plugin.PluginHandler;
import org.chobit.knot.plugin.PluginTypeEnum;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 启动期插件 reconcile：把 IOC 中的 {@link PluginHandler} 元数据物化进 {@code kb_plugin_instances}，并校正孤儿标记。
 * 代码权威列（{@link PluginDescriptor} 投影：{@code instance_code} / {@code instance_name} / {@code capability_code} / {@code extension_point} / {@code stage_code} / {@code order_no} / {@code timeout_ms} / {@code version} / {@code plugin_type}）每次启动全覆盖写；
 * 运维权威列（{@code status} / {@code is_deleted}）永不覆盖，{@code orphaned} 每次启动重算；以 {@code entrypoint} 为唯一键幂等 upsert，写库前校验 {@code code} 为空 / 重复 / 超列宽。
 * 仅处理 SYNC handler（{@link PluginDescriptor} 提供 capability / extension / stage 用于分发匹配），dispatcher 仅按 SYNC 分发。
 */
@Slf4j
@Component
public class PluginReconcileRunner implements ApplicationRunner {

    /** 内置插件统一版本；JAR 插件已确认不支持，故无第二来源。 */
    private static final String BUILTIN_VERSION = "1.0.0";

    /** 内置插件统一超时；分发器尚未读取该列（超时治理属待办），此处只保证列有值。 */
    private static final int BUILTIN_TIMEOUT_MS = 3000;

    /** 与 {@code kb_plugin_instances.instance_code} 列宽一致，改列宽时两处同步。 */
    private static final int INSTANCE_CODE_MAX_LENGTH = 64;

    private final PluginInstanceMapper mapper;
    private final List<PluginHandler<?>> handlers;

    public PluginReconcileRunner(PluginInstanceMapper mapper,
                                 List<PluginHandler<?>> handlers) {
        this.mapper = mapper;
        this.handlers = handlers;
    }

    @Override
    public void run(ApplicationArguments args) {
        Map<String, PluginDescriptor> declared = declaredDescriptors();
        for (Map.Entry<String, PluginDescriptor> entry : declared.entrySet()) {
            mapper.upsert(toEntity(entry.getKey(), entry.getValue()));
        }
        int orphaned = markOrphans(declared.keySet());
        log.info("plugin reconcile done: ioc={}, orphaned={}", declared.size(), orphaned);
    }

    /**
     * 以 entrypoint（实现类全限定名）为键收集 descriptor，并完成三项写库前的校验；
     * 用 {@link LinkedHashMap} 而非 {@link HashSet}，遍历顺序与 IOC 注入顺序一致。
     */
    private Map<String, PluginDescriptor> declaredDescriptors() {
        Map<String, PluginDescriptor> declared = new LinkedHashMap<>();
        Map<String, String> entrypointByCode = new LinkedHashMap<>();
        for (PluginHandler<?> handler : handlers) {
            String entrypoint = handler.getClass().getName();
            PluginDescriptor descriptor = handler.descriptor();
            String code = descriptor.code();
            if (code == null || code.isBlank()) {
                throw new IllegalStateException("plugin descriptor code is blank: entrypoint=" + entrypoint);
            }
            if (code.length() > INSTANCE_CODE_MAX_LENGTH) {
                throw new IllegalStateException("plugin descriptor code is longer than "
                        + INSTANCE_CODE_MAX_LENGTH + " (kb_plugin_instances.instance_code): code="
                        + code + ", entrypoint=" + entrypoint);
            }
            String previous = entrypointByCode.putIfAbsent(code, entrypoint);
            if (previous != null) {
                throw new IllegalStateException("duplicated plugin descriptor code: code=" + code
                        + ", entrypoints=[" + previous + ", " + entrypoint + "]");
            }
            declared.put(entrypoint, descriptor);
        }
        return declared;
    }

    private static PluginInstanceEntity toEntity(String entrypoint, PluginDescriptor descriptor) {
        PluginInstanceEntity entity = new PluginInstanceEntity();
        entity.setEntrypoint(entrypoint);
        entity.setCode(descriptor.code());
        entity.setName(descriptor.name());
        entity.setVersion(BUILTIN_VERSION);
        entity.setCapabilityCode(descriptor.capabilityCode());
        entity.setExtensionPoint(descriptor.extensionPoint().code());
        entity.setStageCode(descriptor.stageCode().code());
        entity.setOrderNo(descriptor.orderNo());
        entity.setTimeoutMs(BUILTIN_TIMEOUT_MS);
        entity.setPluginType(PluginTypeEnum.SYNC.code());
        return entity;
    }

    /**
     * 把「DB 里存在、IOC 里没有」的存量行标 orphaned=1；返回本次标记的行数。
     * 同一 entrypoint 出现多行只打一条告警。
     */
    private int markOrphans(Set<String> declaredEntrypoints) {
        Set<String> warned = new HashSet<>();
        int marked = 0;
        for (PluginInstanceEntity row : mapper.listAll()) {
            if (declaredEntrypoints.contains(row.getEntrypoint())) {
                continue;
            }
            mapper.setOrphaned(row.getId(), 1);
            marked++;
            if (warned.add(row.getEntrypoint())) {
                log.warn("plugin instance orphaned (code removed): instanceCode={}, entrypoint={}",
                        row.getCode(), row.getEntrypoint());
            }
        }
        return marked;
    }
}
