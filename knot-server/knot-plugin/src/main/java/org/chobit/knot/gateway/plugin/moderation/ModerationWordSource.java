package org.chobit.knot.gateway.plugin.moderation;

import java.util.List;

/**
 * 词库来源（依赖倒置）：契约在 knot-plugin，实现在 knot-service——
 * 与 {@code PluginBindingProvider} / {@code PluginService} 同构，避免本模块反向依赖持久层。
 *
 * <p>只有两件事：加载全量启用词条、报当前版本号。缓存与快照编译由
 * {@link ModerationSnapshotCache} 负责，因此换实现（DB / 远程配置 / 文件）无需重做缓存策略。</p>
 */
public interface ModerationWordSource {

    /**
     * 全量启用词条；无词条时返回空列表（不返回 null）。
     */
    List<ModerationTerm> loadTerms();

    /**
     * 当前词库版本：与 {@link ModerationSnapshot#version()} 同一算法（已加载词条的
     * {@code MAX(updated_at)} 毫秒值），空库返回 0。
     *
     * <p>必须**轻量**：它在请求路径上按 {@link ModerationConstants#VERSION_CHECK_INTERVAL_MILLIS}
     * 节流调用，只用于判断缓存是否过期，实现方不要在这里做全表加载。</p>
     */
    long currentVersion();
}
