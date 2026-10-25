package org.chobit.knot.gateway.dal.mapper;

import org.chobit.knot.gateway.dal.entity.ProviderAccountEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface ProviderAccountMapper {

    List<ProviderAccountEntity> list(@Param("keyword") String keyword,
                                     @Param("status") Integer status);

    ProviderAccountEntity getById(Long id);

    /** 按账户业务码查询（ kb_provider_accounts.code 唯一） */
    ProviderAccountEntity getByCode(String code);

    Long countByCode(@Param("code") String code,
                     @Param("excludeId") Long excludeId);

    /**
     * 按账户业务码统计绑定的供应商模型数（改码守卫用）。
     *
     * <p>kb_models 逻辑删除后可恢复，被删行仍持有 provider_account_code 引用，
     * 故此处不按 is_deleted 过滤。</p>
     */
    Long countModelsByAccountCode(@Param("accountCode") String accountCode);

    int insert(ProviderAccountEntity entity);

    int update(ProviderAccountEntity entity);

    int updateStatus(@Param("id") Long id,
                     @Param("status") Integer status);
}
