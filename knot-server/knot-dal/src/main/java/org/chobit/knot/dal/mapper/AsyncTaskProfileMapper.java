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
     * 按编码读取<b>启用</b>档案（运行时唯一入口）。
     *
     * <p>⚠ 停用档案也要能被管理面读到（否则无法重新启用），所以这里和
     * {@link #getByProfileCode} 是两个方法，而不是给后者加一个 status 条件。</p>
     */
    AsyncTaskProfileEntity getEnabledByProfileCode(@Param("profileCode") String profileCode);

    List<AsyncTaskProfileEntity> listEnabled();

    /**
     * 管理面检索（条件为 null 表示不限）。
     *
     * <p>⚠ {@code status} 判空用 {@code != null}，不能写 {@code status != ''}：
     * 前者是本表唯一正确的写法，后者在整型列上会被 MyBatis 的 OGNL 当成与 0 比较。</p>
     */
    List<AsyncTaskProfileEntity> search(@Param("keyword") String keyword,
                                        @Param("protocol") String protocol,
                                        @Param("status") Integer status);

    int insert(AsyncTaskProfileEntity entity);

    int update(AsyncTaskProfileEntity entity);

    int deleteByProfileCode(@Param("profileCode") String profileCode);
}
