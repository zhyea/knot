package org.chobit.knot.svc.converter;

import org.chobit.knot.svc.dto.billing.BillingRuleDto;
import org.chobit.knot.dal.entity.BillingRuleEntity;
import org.chobit.knot.svc.vo.billing.BillingRule;
import org.chobit.knot.svc.vo.billing.BillingRuleListItem;
import org.mapstruct.Mapper;

import java.util.List;

@Mapper(componentModel = "spring", uses = SharedMappings.class)
public interface BillingConverter {

    @org.mapstruct.Mapping(source = "status", target = "enabled", qualifiedByName = "statusToEnabled")
    BillingRuleDto toRuleDto(BillingRuleEntity entity);

    @org.mapstruct.Mapping(source = "enabled", target = "status", qualifiedByName = "enabledToStatus")
    // versionStatus 是查询侧派生列（当前版本状态，来自 kb_billing_rule_versions.status），写路径不回写
    @org.mapstruct.Mapping(target = "versionStatus", ignore = true)
    BillingRuleEntity toRuleEntity(BillingRuleDto dto);

    BillingRule toRuleVO(BillingRuleDto dto);

    @org.mapstruct.Mapping(source = "status", target = "enabled", qualifiedByName = "statusToEnabled")
    BillingRuleListItem toRuleListItem(BillingRuleEntity entity);

    List<BillingRuleListItem> toRuleListItemList(List<BillingRuleEntity> entities);

    BillingRuleDto toRuleDto(BillingRule vo);

    List<BillingRule> toRuleVOList(List<BillingRuleDto> dtos);
}
