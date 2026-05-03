package org.chobit.knot.gateway.mapper;

import org.chobit.knot.gateway.entity.BillingRuleEntity;
import org.apache.ibatis.annotations.*;

import java.util.List;

@Mapper
public interface BillingRuleMapper {
    @Select("select id,code,name,billing_mode,unit,unit_price,status,effective_from from billing_rules order by id desc")
    List<BillingRuleEntity> list();

    @Insert("insert into billing_rules(code,name,billing_mode,unit,unit_price,status,effective_from) values(#{code},#{name},#{billingMode},#{unit},#{unitPrice},#{status},#{effectiveFrom})")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(BillingRuleEntity entity);
}
