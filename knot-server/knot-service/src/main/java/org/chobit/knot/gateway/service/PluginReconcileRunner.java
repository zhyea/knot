package org.chobit.knot.gateway.service;

import org.chobit.knot.gateway.entity.PluginInstanceEntity;
import org.chobit.knot.gateway.mapper.PluginInstanceMapper;
import org.chobit.knot.gateway.plugin.PluginDescriptor;
import org.chobit.knot.gateway.plugin.PluginHandler;
import org.chobit.knot.gateway.plugin.PluginTypeEnum;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 启动期插件 reconcile：以 Spring IOC 中的 {@link PluginHandler} 为权威来源，
 * 校正 {@code kb_plugin_instances} 表（单表收口后，插件定义由代码驱动，DB 仅记录启用/禁用与孤儿标记）。
 *
 * <ul>
 *   <li>代码存在的插件：清除 orphaned 标记，保留用户设的 status / is_deleted / 人类可读名称。</li>
 *   <li>代码新增的插件（此前无行）：插入，默认启用（status=1）。</li>
 *   <li>代码已移除的插件（残留死行）：标记 orphaned=1，供前端浅红展示 + 逻辑删除按钮。</li>
 * </ul>
 *
 * 仅处理 SYNC handler（其 {@link PluginDescriptor} 提供 capability/extension/stage 用于分发匹配）；
 * dispatcher 仅按 SYNC 分发，异步 sink 能力已移除。
 */
@Component
public class PluginReconcileRunner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(PluginReconcileRunner.class);

    private final PluginInstanceMapper mapper;
    private final List<PluginHandler<?>> handlers;

    public PluginReconcileRunner(PluginInstanceMapper mapper,
                                 List<PluginHandler<?>> handlers) {
        this.mapper = mapper;
        this.handlers = handlers;
    }

    @Override
    public void run(ApplicationArguments args) {
        Set<String> iocEntrypoints = new HashSet<>();
        Map<String, PluginInstanceEntity> existing = mapper.listAll().stream()
                .collect(Collectors.toMap(PluginInstanceEntity::getEntrypoint, e -> e, (a, b) -> a));

        for (PluginHandler<?> h : handlers) {
            reconcileOne(h.getClass().getName(), h.descriptor(), existing, iocEntrypoints);
        }

        for (PluginInstanceEntity e : existing.values()) {
            if (!iocEntrypoints.contains(e.getEntrypoint())) {
                mapper.setOrphaned(e.getId(), 1);
                log.warn("plugin instance orphaned (code removed): instanceCode={}, entrypoint={}",
                        e.getCode(), e.getEntrypoint());
            }
        }
        log.info("plugin reconcile done: ioc={}, existing={}", iocEntrypoints.size(), existing.size());
    }

    private void reconcileOne(String entrypoint, PluginDescriptor d,
                              Map<String, PluginInstanceEntity> existing, Set<String> iocEntrypoints) {
        iocEntrypoints.add(entrypoint);
        PluginInstanceEntity cur = existing.get(entrypoint);
        if (cur != null) {
            mapper.setOrphaned(cur.getId(), 0);
            return;
        }
        PluginInstanceEntity e = new PluginInstanceEntity();
        e.setPackageCode("builtin-" + firstSegment(d.capabilityCode()));
        e.setCode(deriveInstanceCode(d));
        e.setName(PluginTypeEnum.SYNC.code() + ":" + d.capabilityCode() + ":" + d.stageCode().code());
        e.setPackageName(d.capabilityCode());
        e.setEntrypoint(entrypoint);
        e.setSourceType("BUILTIN");
        e.setVersion("1.0.0");
        e.setCapabilityCode(d.capabilityCode());
        e.setCapabilityName(d.capabilityCode());
        e.setExtensionPoint(d.extensionPoint().code());
        e.setStageCode(d.stageCode().code());
        e.setOrderNo(100);
        e.setStatus(1);
        e.setTimeoutMs(3000);
        e.setPluginType(PluginTypeEnum.SYNC.code());
        mapper.insert(e);
        log.info("plugin instance auto-created: instanceCode={}, entrypoint={}", e.getCode(), entrypoint);
    }

    private static String firstSegment(String capabilityCode) {
        int idx = capabilityCode.indexOf('-');
        return idx > 0 ? capabilityCode.substring(0, idx) : capabilityCode;
    }

    private static String deriveInstanceCode(PluginDescriptor d) {
        return (d.capabilityCode() + "-" + d.extensionPoint().code() + "-" + d.stageCode().code()).toLowerCase();
    }
}
