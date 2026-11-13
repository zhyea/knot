package org.chobit.knot.dal.mapper;

import org.apache.ibatis.annotations.Param;
import org.chobit.knot.dal.entity.AsyncTaskProfileEntity;

import java.util.List;

/**
 * 异步任务查询口径档案读写（{@code kb_async_task_profiles}）。
 */
public interface AsyncTaskProfileMapper {

    AsyncTaskProfileEntity getByProfileCode(@Param("profileCode") String profileCode);

    /**
     * 按编码读取启用档案（运行时唯一入口）；含停用档案的读取走 {@link #getByProfileCode}。
     */
    AsyncTaskProfileEntity getEnabledByProfileCode(@Param("profileCode") String profileCode);

    List<AsyncTaskProfileEntity> listEnabled();

    /**
     * 管理面检索（条件为 {@code null} 表示不限）。{@code status} 判空必须用 {@code != null}：
     * 整型列上写 {@code status != ''} 会被 MyBatis 的 OGNL 当成与 0 比较。
     */
    List<AsyncTaskProfileEntity> search(@Param("keyword") String keyword,
                                        @Param("protocol") String protocol,
                                        @Param("status") Integer status);

    int insert(AsyncTaskProfileEntity entity);

    int update(AsyncTaskProfileEntity entity);

    int deleteByProfileCode(@Param("profileCode") String profileCode);
}
