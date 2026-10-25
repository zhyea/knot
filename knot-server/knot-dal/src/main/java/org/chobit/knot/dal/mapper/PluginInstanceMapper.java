package org.chobit.knot.dal.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.chobit.knot.dal.entity.PluginInstanceEntity;

import java.util.List;

@Mapper
public interface PluginInstanceMapper {

    List<PluginInstanceEntity> list(@Param("keyword") String keyword,
                                    @Param("status") Integer status,
                                    @Param("pluginType") String pluginType);

    PluginInstanceEntity getById(Long id);

    /**
     * 启动期 reconcile 的幂等写入口：以 {@code entrypoint} 为稳定键 upsert。
     * 只覆盖 descriptor 投影列，{@code status} / {@code is_deleted} 由运维持有、不被覆盖。
     */
    int upsert(PluginInstanceEntity entity);

    int updateStatus(@Param("id") Long id, @Param("status") Integer status);

    int updateDeleted(@Param("id") Long id, @Param("isDeleted") Integer isDeleted);

    List<PluginInstanceEntity> listActiveBindings(@Param("extensionPoint") String extensionPoint,
                                                   @Param("stageCode") String stageCode);

    List<PluginInstanceEntity> listAll();

    int setOrphaned(@Param("id") Long id, @Param("orphaned") Integer orphaned);
}
