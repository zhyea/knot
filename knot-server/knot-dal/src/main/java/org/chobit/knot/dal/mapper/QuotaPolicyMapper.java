package org.chobit.knot.dal.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.chobit.knot.dal.entity.QuotaPolicyEntity;

import java.util.List;

@Mapper
public interface QuotaPolicyMapper {

    QuotaPolicyEntity getById(@Param("id") Long id);

    List<QuotaPolicyEntity> listByIds(@Param("ids") List<Long> ids);

    QuotaPolicyEntity getByCode(@Param("policyCode") String policyCode);

    List<QuotaPolicyEntity> listByCodes(@Param("codes") List<String> codes);

    /**
     * 按业务码前缀回收孤儿策略（写路径删孤儿时定位某资源名下的全部额度策略）。
     */
    List<QuotaPolicyEntity> listByCodePrefix(@Param("prefix") String prefix);

    int insert(QuotaPolicyEntity entity);

    int update(QuotaPolicyEntity entity);

    int deleteById(@Param("id") Long id);
}
