package org.chobit.knot.svc.service.support;

import org.chobit.knot.dal.mapper.ConfigRevisionMapper;
import org.chobit.knot.svc.model.ConfigTableRevision;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 配置表变更指纹读取：把 DAL 投影行转成 svc 的 record，网关侧不接触 {@code dal.entity}。
 *
 * <p>只读、无缓存、无事务。表清单由 {@code ConfigRevisionMapper.xml} 与网关侧
 * {@code ConfigCacheDomain} 两处声明，靠契约测试保持一致。</p>
 */
@Component
public class ConfigRevisionQuerySupport {

    private final ConfigRevisionMapper configRevisionMapper;

    public ConfigRevisionQuerySupport(ConfigRevisionMapper configRevisionMapper) {
        this.configRevisionMapper = configRevisionMapper;
    }

    /**
     * 读取全部配置表当前指纹。
     *
     * <p>返回值非空但可能少于声明的表数——调用方必须把「表缺席」当作异常而不是「无变化」，
     * 否则漏表会退化成永远探测不到变更。</p>
     */
    public List<ConfigTableRevision> snapshot() {
        return configRevisionMapper.listRevisions().stream()
                .map(row -> new ConfigTableRevision(
                        row.getTableName(),
                        row.getRowCount() == null ? 0L : row.getRowCount(),
                        row.getRevision() == null ? "" : row.getRevision()))
                .toList();
    }
}
