package org.chobit.knot.dal.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.chobit.knot.dal.entity.ConfigRevisionRow;

import java.util.List;

/**
 * 配置表变更指纹读取：一次查询返回全部配置表各一行。
 *
 * <p>只读，不写库。SQL 中的表名与网关侧 {@code ConfigCacheDomain} 的 table 清单一一对应，
 * 由契约测试守门——漏一张表会让该域永远探测不到变更，且没有任何运行时症状。</p>
 */
@Mapper
public interface ConfigRevisionMapper {

    List<ConfigRevisionRow> listRevisions();
}
