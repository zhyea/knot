package org.chobit.knot.gateway.converter;

import org.chobit.knot.gateway.dto.billing.BillingRuleDto;
import org.chobit.knot.gateway.dto.billing.ReconciliationResultDto;
import org.chobit.knot.gateway.entity.BillingRuleEntity;
import org.chobit.knot.gateway.vo.billing.BillingRule;
import org.chobit.knot.gateway.vo.billing.ReconciliationResult;
import org.mapstruct.Mapper;

import java.util.List;

@Mapper(componentModel = "spring", uses = CommonMappings.class)
public interface BillingConverter {

    @org.mapstruct.Mapping(source = "status", target = "enabled", qualifiedByName = "billingStatusToEnabled")
    BillingRuleDto toRuleDto(BillingRuleEntity entity);

    @org.mapstruct.Mapping(source = "enabled", target = "status", qualifiedByName = "billingEnabledToStatus")
    BillingRuleEntity toRuleEntity(BillingRuleDto dto);

    BillingRule toRuleVO(BillingRuleDto dto);

    ReconciliationResult toReconciliationVO(ReconciliationResultDto dto);

    BillingRuleDto toRuleDto(BillingRule vo);

    List<BillingRule> toRuleVOList(List<BillingRuleDto> dtos);
}
