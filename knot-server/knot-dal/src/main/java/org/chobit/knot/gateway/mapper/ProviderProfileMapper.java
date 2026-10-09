package org.chobit.knot.gateway.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.chobit.knot.gateway.entity.ProviderProfileEntity;

import java.util.List;

@Mapper
public interface ProviderProfileMapper {

    List<ProviderProfileEntity> list(@Param("keyword") String keyword,
                                     @Param("tag") String tag);

    ProviderProfileEntity getById(Long id);

    ProviderProfileEntity getByCode(String code);

    Long countByCode(@Param("code") String code,
                     @Param("excludeId") Long excludeId);

    /**
     * 统计该供应商档案下挂的供应商账户数（kb_provider_accounts.provider_code）。
     *
     * <p>绑定一律走 code 不落 id（跨模块绑定约定），故入参是档案 code 而非主键 id。</p>
     */
    Long countAccountsByProviderCode(@Param("providerCode") String providerCode);

    /**
     * 统计该供应商档案下所有账户持有的凭据数。
     */
    Long countCredentialsByProviderCode(@Param("providerCode") String providerCode);

    /**
     * 统计该供应商档案下所有账户的折扣策略数。
     */
    Long countDiscountPoliciesByProviderCode(@Param("providerCode") String providerCode);

    /**
     * 统计该供应商档案下所有账户挂载的模型数（不含逻辑删除）。
     */
    Long countModelsByProviderCode(@Param("providerCode") String providerCode);

    /**
     * 统计该供应商档案下所有账户的供应商模型映射数。
     */
    Long countMappingsByProviderCode(@Param("providerCode") String providerCode);

    int insert(ProviderProfileEntity entity);

    int update(ProviderProfileEntity entity);

    int deleteById(Long id);
}
