package org.chobit.knot.gateway.converter;

import org.chobit.knot.gateway.dto.billing.BillingDetailDto;
import org.chobit.knot.gateway.dto.billing.BillingRuleDto;
import org.chobit.knot.gateway.dto.billing.BillingSummaryDto;
import org.chobit.knot.gateway.dto.billing.ReconciliationResultDto;
import org.chobit.knot.gateway.entity.BillingDetailEntity;
import org.chobit.knot.gateway.entity.BillingRuleEntity;
import org.chobit.knot.gateway.vo.billing.BillingDetail;
import org.chobit.knot.gateway.vo.billing.BillingRule;
import org.chobit.knot.gateway.vo.billing.BillingSummary;
import org.chobit.knot.gateway.vo.billing.ReconciliationResult;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.util.List;

@Mapper(componentModel = "spring", uses = CommonMappings.class)
public interface BillingConverter {

    // ==================== Entity ↔ DTO ====================

    @org.mapstruct.Mapping(source = "status", target = "enabled", qualifiedByName = "billingStatusToEnabled")
    BillingRuleDto toRuleDto(BillingRuleEntity entity);

    @org.mapstruct.Mapping(source = "enabled", target = "status", qualifiedByName = "billingEnabledToStatus")
    BillingRuleEntity toRuleEntity(BillingRuleDto dto);

    @Mapping(source = "totalTokens", target = "tokenUsage")
    @Mapping(source = "costAmount", target = "cost")
    BillingDetailDto toDetailDto(BillingDetailEntity entity);

    List<BillingDetailDto> toDetailDtoList(List<BillingDetailEntity> entities);

    // ==================== DTO ↔ VO ====================

    BillingRule toRuleVO(BillingRuleDto dto);

    BillingSummary toSummaryVO(BillingSummaryDto dto);

    BillingDetail toDetailVO(BillingDetailDto dto);

    ReconciliationResult toReconciliationVO(ReconciliationResultDto dto);

    BillingRuleDto toRuleDto(BillingRule vo);

    List<BillingRule> toRuleVOList(List<BillingRuleDto> dtos);

    List<BillingDetail> toDetailVOList(List<BillingDetailDto> dtos);
}
