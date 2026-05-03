package org.chobit.knot.gateway.mapper;

import org.chobit.knot.gateway.entity.BillingDetailEntity;
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

    @Select("select gr.request_id, a.app_id as app_id, m.model_code as model_code, gr.total_tokens, gr.cost_amount " +
            "from gateway_requests gr " +
            "left join apps a on gr.app_id = a.id " +
            "left join models m on gr.model_id = m.id " +
            "order by gr.id desc")
    List<BillingDetailEntity> listDetails();

    @Select("select count(1) from billing_statements bs join providers p on bs.provider_id=p.id where p.code=#{providerCode}")
    Integer countStatementsByProviderCode(String providerCode);
}
