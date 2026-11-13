package org.chobit.knot.svc.converter;

import org.chobit.knot.dal.entity.AppEntity;
import org.chobit.knot.dal.entity.BillingRuleEntity;
import org.chobit.knot.dal.entity.ModelApiBindingEntity;
import org.chobit.knot.dal.entity.ModelEntity;
import org.chobit.knot.dal.entity.ModelPoolEntity;
import org.chobit.knot.dal.entity.ModelPoolItemEntity;
import org.chobit.knot.dal.entity.ProviderAccountEntity;
import org.chobit.knot.dal.entity.ProviderCredentialEntity;
import org.chobit.knot.dal.entity.RoutingConsumerEntity;
import org.chobit.knot.dal.entity.RoutingRuleEntity;
import org.chobit.knot.dal.entity.RoutingRuleTargetEntity;
import org.chobit.knot.svc.vo.gateway.AppConfigVo;
import org.chobit.knot.svc.vo.gateway.BillingRuleConfigVo;
import org.chobit.knot.svc.vo.gateway.ModelApiBindingConfigVo;
import org.chobit.knot.svc.vo.gateway.ModelConfigVo;
import org.chobit.knot.svc.vo.gateway.ModelPoolConfigVo;
import org.chobit.knot.svc.vo.gateway.ModelPoolItemConfigVo;
import org.chobit.knot.svc.vo.gateway.ProviderAccountConfigVo;
import org.chobit.knot.svc.vo.gateway.ProviderCredentialConfigVo;
import org.chobit.knot.svc.vo.gateway.RoutingConsumerConfigVo;
import org.chobit.knot.svc.vo.gateway.RoutingRuleConfigVo;
import org.chobit.knot.svc.vo.gateway.RoutingRuleTargetConfigVo;
import org.mapstruct.Mapper;

import java.util.List;

/**
 * 网关配置快照：{@code dal.entity} → {@code svc.vo.gateway} 的纯投影。
 *
 * 两侧字段逐字同名，无 {@code @Mapping}；网关与实体保持同语义，判定口径写在消费点。
 * 加实体字段时必须同步扩 record，漏掉的源字段会静默读成 {@code null}。
 */
@Mapper(componentModel = "spring")
public interface GatewayConfigConverter {

    AppConfigVo toAppVo(AppEntity entity);

    RoutingConsumerConfigVo toConsumerVo(RoutingConsumerEntity entity);

    RoutingRuleConfigVo toRuleVo(RoutingRuleEntity entity);

    RoutingRuleTargetConfigVo toTargetVo(RoutingRuleTargetEntity entity);

    List<RoutingRuleTargetConfigVo> toTargetVoList(List<RoutingRuleTargetEntity> entities);

    ModelConfigVo toModelVo(ModelEntity entity);

    ModelPoolConfigVo toModelPoolVo(ModelPoolEntity entity);

    ModelPoolItemConfigVo toModelPoolItemVo(ModelPoolItemEntity entity);

    List<ModelPoolItemConfigVo> toModelPoolItemVoList(List<ModelPoolItemEntity> entities);

    ProviderAccountConfigVo toProviderAccountVo(ProviderAccountEntity entity);

    ProviderCredentialConfigVo toCredentialVo(ProviderCredentialEntity entity);

    ModelApiBindingConfigVo toApiBindingVo(ModelApiBindingEntity entity);

    List<ModelApiBindingConfigVo> toApiBindingVoList(List<ModelApiBindingEntity> entities);

    BillingRuleConfigVo toBillingRuleVo(BillingRuleEntity entity);
}
