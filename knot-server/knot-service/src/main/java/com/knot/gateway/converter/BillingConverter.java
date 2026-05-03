package com.knot.gateway.converter;

import com.knot.gateway.dto.billing.BillingDetailDto;
import com.knot.gateway.vo.billing.*;
import com.knot.gateway.dto.billing.BillingRuleDto;
import com.knot.gateway.dto.billing.ReconciliationResultDto;
import com.knot.gateway.entity.BillingDetailEntity;
import com.knot.gateway.entity.BillingRuleEntity;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.util.List;

@Mapper(componentModel = "spring", uses = CommonMappings.class)
public interface BillingConverter {

    // ==================== Entity ↔ DTO ====================

    BillingRuleDto toRuleDto(BillingRuleEntity entity);

    @Mapping(source = "totalTokens", target = "tokenUsage")
    @Mapping(source = "costAmount", target = "cost")
    BillingDetailDto toDetailDto(BillingDetailEntity entity);

    List<BillingDetailDto> toDetailDtoList(List<BillingDetailEntity> entities);

    // ==================== DTO ↔ VO ====================

    BillingRule toRuleVO(BillingRuleDto dto);

    BillingSummary toSummaryVO(com.knot.gateway.dto.billing.BillingSummaryDto dto);

    BillingDetail toDetailVO(BillingDetailDto dto);

    ReconciliationResult toReconciliationVO(ReconciliationResultDto dto);

    BillingRuleDto toRuleDto(BillingRule vo);

    List<BillingRule> toRuleVOList(List<BillingRuleDto> dtos);

    List<BillingDetail> toDetailVOList(List<BillingDetailDto> dtos);
}
