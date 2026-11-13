package org.chobit.knot.svc.service;

import org.chobit.knot.dal.mapper.AppMapper;
import org.chobit.knot.dal.mapper.BillingRuleMapper;
import org.chobit.knot.dal.mapper.ModelApiBindingMapper;
import org.chobit.knot.dal.mapper.ModelMapper;
import org.chobit.knot.dal.mapper.ModelPoolMapper;
import org.chobit.knot.dal.mapper.ProviderAccountMapper;
import org.chobit.knot.dal.mapper.ProviderCredentialMapper;
import org.chobit.knot.dal.mapper.RoutingConsumerMapper;
import org.chobit.knot.dal.mapper.RoutingRuleMapper;
import org.chobit.knot.dal.mapper.RoutingRuleTargetMapper;
import org.chobit.knot.svc.converter.GatewayConfigConverter;
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
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 网关配置读取：把 10 张配置表的裸读集中到 svc，只吐 {@code ConfigVo}，不含缓存与事务。
 * 只承担「按主键 / 业务码把一行配置读出来并投影」；本类只有读方法，配置写入仍走各自的 admin 服务。
 * 不要在这里加 {@code @Cacheable}，缓存（TTL、预热、容量）留在 gateway。
 */
@Service
public class GatewayConfigQueryService {

    private final AppMapper appMapper;
    private final RoutingConsumerMapper routingConsumerMapper;
    private final RoutingRuleMapper routingRuleMapper;
    private final RoutingRuleTargetMapper routingRuleTargetMapper;
    private final ModelMapper modelMapper;
    private final ModelPoolMapper modelPoolMapper;
    private final ProviderAccountMapper providerAccountMapper;
    private final ProviderCredentialMapper providerCredentialMapper;
    private final ModelApiBindingMapper modelApiBindingMapper;
    private final BillingRuleMapper billingRuleMapper;
    private final GatewayConfigConverter converter;

    public GatewayConfigQueryService(AppMapper appMapper,
                                     RoutingConsumerMapper routingConsumerMapper,
                                     RoutingRuleMapper routingRuleMapper,
                                     RoutingRuleTargetMapper routingRuleTargetMapper,
                                     ModelMapper modelMapper,
                                     ModelPoolMapper modelPoolMapper,
                                     ProviderAccountMapper providerAccountMapper,
                                     ProviderCredentialMapper providerCredentialMapper,
                                     ModelApiBindingMapper modelApiBindingMapper,
                                     BillingRuleMapper billingRuleMapper,
                                     GatewayConfigConverter converter) {
        this.appMapper = appMapper;
        this.routingConsumerMapper = routingConsumerMapper;
        this.routingRuleMapper = routingRuleMapper;
        this.routingRuleTargetMapper = routingRuleTargetMapper;
        this.modelMapper = modelMapper;
        this.modelPoolMapper = modelPoolMapper;
        this.providerAccountMapper = providerAccountMapper;
        this.providerCredentialMapper = providerCredentialMapper;
        this.modelApiBindingMapper = modelApiBindingMapper;
        this.billingRuleMapper = billingRuleMapper;
        this.converter = converter;
    }

    public AppConfigVo getAppById(Long id) {
        return converter.toAppVo(appMapper.getById(id));
    }

    public RoutingConsumerConfigVo getConsumerBySecretKey(String secretKey) {
        return converter.toConsumerVo(routingConsumerMapper.getBySecretKey(secretKey));
    }

    public RoutingRuleConfigVo getEnabledRuleByConsumerCodeAndRuleCode(String consumerCode, String ruleCode) {
        return converter.toRuleVo(routingRuleMapper.getEnabledByConsumerCodeAndRuleCode(consumerCode, ruleCode));
    }

    public List<RoutingRuleTargetConfigVo> listTargetsByRuleCode(String ruleCode) {
        return converter.toTargetVoList(routingRuleTargetMapper.listByRuleCode(ruleCode));
    }

    public ModelConfigVo getModelById(Long id) {
        return converter.toModelVo(modelMapper.getById(id));
    }

    public ModelConfigVo getModelByCode(String modelCode) {
        return converter.toModelVo(modelMapper.getByCode(modelCode));
    }

    public ModelPoolConfigVo getModelPoolById(Long id) {
        return converter.toModelPoolVo(modelPoolMapper.getById(id));
    }

    public ModelPoolConfigVo getModelPoolByCode(String poolCode) {
        return converter.toModelPoolVo(modelPoolMapper.getByCode(poolCode));
    }

    public List<ModelPoolItemConfigVo> listModelPoolItemsByPoolCode(String poolCode) {
        return converter.toModelPoolItemVoList(modelPoolMapper.listItemsByPoolCode(poolCode));
    }

    public ProviderAccountConfigVo getProviderById(Long id) {
        return converter.toProviderAccountVo(providerAccountMapper.getById(id));
    }

    public ProviderAccountConfigVo getProviderByCode(String code) {
        return converter.toProviderAccountVo(providerAccountMapper.getByCode(code));
    }

    public ProviderCredentialConfigVo getActiveCredentialByProviderId(Long providerId) {
        return converter.toCredentialVo(providerCredentialMapper.getActiveByProviderId(providerId));
    }

    public List<ModelApiBindingConfigVo> listApiBindingsByModelId(Long modelId) {
        return converter.toApiBindingVoList(modelApiBindingMapper.listByModelId(modelId));
    }

    /**
     * 计费规则解析：先按模型显式绑定的规则业务码，未命中或未绑定时按模型族回退。
     * 「当前时间」在这里取，与 {@code effective_from/effective_to} 的库时钟对齐。
     *
     * @param ruleCode        模型显式绑定的计费规则业务码，可为空
     * @param modelFamilyCode 模型族 code（派生自绑定统一模型的 model_family），可为空
     */
    public BillingRuleConfigVo getActiveBillingRule(String ruleCode, String modelFamilyCode) {
        LocalDateTime now = LocalDateTime.now();
        String code = normalize(ruleCode);
        if (code != null) {
            BillingRuleConfigVo byCode = converter.toBillingRuleVo(
                    billingRuleMapper.getActiveByRuleCode(code, now));
            if (byCode != null) {
                return byCode;
            }
        }
        return converter.toBillingRuleVo(
                billingRuleMapper.getActiveByModelFamily(normalize(modelFamilyCode), now));
    }

    private static String normalize(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
