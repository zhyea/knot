package org.chobit.knot.gateway.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.chobit.knot.gateway.entity.ModerationWordEntity;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 敏感词条读写：网关运行时只按「未逻辑删除 + 启用」加载全量词条，编译成一次自动机；
 * 管理面走完整 CRUD + 逻辑删除四必配。
 */
@Mapper
public interface ModerationWordMapper {

    /**
     * 参与匹配的全部词条；词库为空时返回空列表（不是 null）。
     */
    List<ModerationWordEntity> listEnabled();

    /**
     * 已启用词条的最大更新时间：词库版本号来源，供缓存失效判定。
     *
     * <p>返回 {@link LocalDateTime} 而非数据库侧 epoch 毫秒：避免 MySQL session 时区
     * 与 JVM 默认时区不一致导致版本号永远对不上（那样缓存会每次请求都重载）。</p>
     */
    LocalDateTime maxUpdatedAt();

    /**
     * 管理列表分页查询。
     *
     * @param includeDeleted true 时包含已逻辑删除的行（浅红底 + 恢复按钮）
     */
    List<ModerationWordEntity> list(@Param("keyword") String keyword,
                                    @Param("categoryCode") String categoryCode,
                                    @Param("includeDeleted") Boolean includeDeleted);

    /**
     * 导出用全量查询：不含已逻辑删除行，停用词一并导出。
     */
    List<ModerationWordEntity> listForExport();

    ModerationWordEntity getById(Long id);

    ModerationWordEntity getByIdIncludingDeleted(Long id);

    int insert(ModerationWordEntity entity);

    int update(ModerationWordEntity entity);

    int updateStatus(@Param("id") Long id, @Param("status") Integer status);

    int logicalDelete(@Param("id") Long id);

    int restore(@Param("id") Long id);

    /**
     * 编码唯一性按物理行判定：uk_word_category 不区分 is_deleted，
     * 逻辑删除后同 (word, category_code) 无法新建，只能恢复。
     */
    Long countByWordAndCategory(@Param("word") String word,
                                @Param("categoryCode") String categoryCode,
                                @Param("excludeId") Long excludeId);
}
