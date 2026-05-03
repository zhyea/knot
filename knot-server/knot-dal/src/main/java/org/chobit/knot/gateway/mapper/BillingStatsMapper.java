package org.chobit.knot.gateway.mapper;

import org.chobit.knot.gateway.entity.BillingDetailEntity;
import org.apache.ibatis.annotations.Mapper;

import java.math.BigDecimal;
import java.util.List;

@Mapper
public interface BillingStatsMapper {

    Long countRequests();

    Long sumTokens();

    BigDecimal sumCost();

    List<BillingDetailEntity> listDetails();

    Integer countStatementsByProviderCode(String providerCode);
}
