package org.chobit.knot.gateway.service;

import org.chobit.knot.gateway.entity.ModerationWordEntity;
import org.chobit.knot.gateway.mapper.ModerationWordMapper;
import org.chobit.knot.gateway.plugin.moderation.ModerationTerm;
import org.chobit.knot.gateway.plugin.moderation.ModerationWordSource;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;

/**
 * 敏感词库来源的 DB 实现：契约 {@link ModerationWordSource} 在 knot-plugin，实现在此——
 * 与 {@code PluginService implements PluginBindingProvider} 同构，让插件模块不必依赖持久层。
 *
 * <p>只做「查全量启用词条 + 映射成中性 {@link ModerationTerm}」：缓存与快照编译由
 * knot-plugin 的 {@code ModerationSnapshotCache} 负责，换实现无需重做缓存策略。</p>
 */
@Component
public class DbModerationWordSource implements ModerationWordSource {

    private final ModerationWordMapper wordMapper;

    /**
     * Constructs a new instance.
     */
    public DbModerationWordSource(ModerationWordMapper wordMapper) {
        this.wordMapper = wordMapper;
    }

    @Override
    public List<ModerationTerm> loadTerms() {
        List<ModerationWordEntity> rows = wordMapper.listEnabled();
        if (rows == null || rows.isEmpty()) {
            return List.of();
        }
        List<ModerationTerm> terms = new ArrayList<>(rows.size());
        for (ModerationWordEntity row : rows) {
            terms.add(new ModerationTerm(
                    row.getWord(),
                    row.getNormalizedWord(),
                    row.getCategoryCode(),
                    row.getSeverity(),
                    updatedAtMillis(row.getUpdatedAt())));
        }
        return terms;
    }

    @Override
    public long currentVersion() {
        // 与 ModerationSnapshot.of() 用同一套时区换算，否则版本号永远对不上、缓存会每次请求都重载
        Long millis = updatedAtMillis(wordMapper.maxUpdatedAt());
        return millis == null ? 0L : millis;
    }

    private static Long updatedAtMillis(LocalDateTime value) {
        return value == null ? null : value.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
    }
}
