package com.knot.gateway.converter;

import com.knot.gateway.controller.BillingController;
import com.knot.gateway.entity.BillingDetailEntity;
import com.knot.gateway.entity.BillingRuleEntity;
import com.knot.gateway.service.BillingService;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.util.List;

@Mapper(componentModel = "spring", uses = CommonMappings.class)
public interface BillingConverter {

    // ==================== Entity ↔ DTO ====================

    BillingService.BillingRuleDto toRuleDto(BillingRuleEntity entity);

    @Mapping(source = "totalTokens", target = "tokenUsage")
    @Mapping(source = "costAmount", target = "cost")
    BillingService.BillingDetailDto toDetailDto(BillingDetailEntity entity);

    List<BillingService.BillingDetailDto> toDetailDtoList(List<BillingDetailEntity> entities);

    // ==================== DTO ↔ VO ====================

    BillingController.BillingRule toRuleVO(BillingService.BillingRuleDto dto);

    BillingController.BillingSummary toSummaryVO(BillingService.BillingSummaryDto dto);

    BillingController.BillingDetail toDetailVO(BillingService.BillingDetailDto dto);

    BillingController.ReconciliationResult toReconciliationVO(BillingService.ReconciliationResultDto dto);

    BillingService.BillingRuleDto toRuleDto(BillingController.BillingRule vo);

    List<BillingController.BillingRule> toRuleVOList(List<BillingService.BillingRuleDto> dtos);

    List<BillingController.BillingDetail> toDetailVOList(List<BillingService.BillingDetailDto> dtos);
}
