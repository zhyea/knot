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
 * <p>⭐ <b>为什么这一层必须存在</b>：网关的 11 个消费点（路由解析、上游代理、异步任务、流量控制）
 * 全部是「读几个字段做决策」。它们原来直接拿 dal 实体，于是 {@code gateway} 成了
 * {@code dal.entity} 的第二落地点——{@code LayeringGuardTest} 的实体棘轮从 1 个长到 10 个，
 * 中间没有任何测试会响。把投影收在这一个 Converter 里之后，
 * 「网关有没有碰实体」重新变成一条 grep 能出结果的事。</p>
 *
 * <p>⚠ <b>两侧字段逐字同名，因此没有任何 {@code @Mapping}</b>。不要在这里顺手改名或
 * 「友好化」（例如把 {@code status} 翻成 {@code enabled}）——那是 admin 那批展示载体的活，
 * 网关必须与实体同语义：判定口径写在消费点里，投影层只搬运。</p>
 *
 * <p>⚠ <b>加实体字段时必须同步扩 record</b>：MapStruct 对未映射的<b>目标</b>字段会告警
 * （{@code Unmapped target property}），但<b>源字段被漏掉不会报错</b>——
 * 表现是「新字段读出来恒为 null」，靠 {@code GatewayConfigConverterTest} 的逐字段断言钉住。</p>
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
