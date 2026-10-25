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
 * 启动期插件 reconcile：把 IOC 中的 {@link PluginHandler} 元数据物化进 {@code kb_plugin_instances}，
 * 并校正孤儿标记。
 *
 * <p><b>权威边界</b>（2026-10-25 重构）：这张表里只有两类东西，边界必须清晰——</p>
 * <ul>
 *   <li><b>代码权威</b>（{@link PluginDescriptor} 的投影）：{@code instance_code} / {@code instance_name} /
 *       {@code capability_code} / {@code extension_point} / {@code stage_code} / {@code order_no} /
 *       {@code timeout_ms} / {@code version} / {@code plugin_type}。每次启动<b>全覆盖写</b>，
 *       代码改了重启即跟随。</li>
 *   <li><b>运维权威</b>：{@code status}（启停）、{@code is_deleted}（逻辑删除）。
 *       <b>永不覆盖</b>——用户停用过的插件不能被重启重新打开。</li>
 *   <li>{@code orphaned} 是派生标记，每次启动重算。</li>
 * </ul>
 *
 * <p><b>为什么用 upsert 而不是「先查再决定插不插」</b>：旧实现只在「DB 里找不到该 entrypoint」
 * 时插入，从不更新派生列。于是插件实现类改包名/改名后，旧行永远匹配不上新类名 → 全部判成
 * 「代码新增」→ 重复插入 + 旧行全标孤儿。{@code data.sql} 那边的种子也补不了，因为它是
 * {@code INSERT IGNORE} + 显式主键，既有库主键冲突即静默跳过。
 * 改成以 {@code entrypoint} 为唯一键的幂等 upsert 后，「DB 里存着需要人工迁移的 FQCN」
 * 这个风险类型彻底不存在——派生值每次启动重写一遍。</p>
 *
 * <p><b>为什么在写库前做校验</b>：{@code code} 为空 / 重复 / 超列宽这三类错误原先只能等插库时
 * 由 MySQL 报出来（判例：派生码 65 字符超过 {@code VARCHAR(64)}，上下文直接起不来）。
 * 提前到启动第二秒指名报错，报错信息带 entrypoint，排查不用再翻 SQL 日志。</p>
 *
 * <p>仅处理 SYNC handler（其 {@link PluginDescriptor} 提供 capability/extension/stage 用于分发匹配）；
 * dispatcher 仅按 SYNC 分发，异步 sink 能力已移除。</p>
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
     * 以 entrypoint（实现类全限定名）为键收集 descriptor，并完成三项写库前的校验。
     *
     * <p>用 {@link LinkedHashMap} 而非 {@link HashSet}：遍历顺序与 IOC 注入顺序一致，
     * 出问题时日志里看到的是稳定顺序，不是哈希顺序。</p>
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
     *
     * <p>同一 entrypoint 出现多行只打一条告警——历史上的重复行会各自被标记，
     * 但告警没必要重复刷屏。</p>
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
