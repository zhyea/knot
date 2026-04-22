package com.knot.gateway.mapper;

import com.knot.gateway.entity.BillingDetailEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

import java.math.BigDecimal;
import java.util.List;

@Mapper
public interface BillingStatsMapper {
    @Select("select count(1) from gateway_requests")
    Long countRequests();

    @Select("select coalesce(sum(total_tokens),0) from gateway_requests")
    Long sumTokens();

    @Select("select coalesce(sum(cost_amount),0) from gateway_requests")
    BigDecimal sumCost();

    @Select("select request_id, app_id, model_id, total_tokens, cost_amount from gateway_requests order by id desc limit 50")
    List<BillingDetailEntity> listDetails();

    @Select("select count(1) from billing_statements bs join providers p on bs.provider_id=p.id where p.code=#{providerCode}")
    Integer countStatementsByProviderCode(String providerCode);
}
