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
 * 网关配置读取：把 10 张配置表的裸读集中到 svc，只吐 {@code ConfigVo}、不含缓存与事务。
 *
 * <p>⭐ <b>为什么需要它，以及为什么不能复用同域的 admin 服务</b>：
 * 网关要的是「配置快照」（语义与实体一一对应、字段一个不少），admin 要的是「展示模型」
 * （{@code status} 已翻成 {@code enabled}、派生列早就裁过）。同一张表两种读取口径，
 * 硬合会逼其中一方妥协。所以这里只承担一件事：<b>按主键/业务码把一行配置读出来并投影</b>。</p>
 *
 * <p>⚠ <b>这里没有缓存，缓存留在 gateway</b>：TTL、预热、容量都是「这个进程怎么跑」的决策，
 * 属于 gateway（{@code 10-backend-java.md §3.2}：只有一个 app 用的编排留在该 app）。
 * 本类被调用一次就查一次库，<b>不要在这里加 {@code @Cacheable}</b>——
 * 那会让网关的 Caffeine 与本层的缓存互相遮挡，调 TTL 时找不到真正的那个。</p>
 *
 * <p>⚠ <b>同一张表两个读入口是可接受的，判据是「谁能改」仍然是单点</b>：
 * 这些表的写入仍然只走各自的 admin 服务（{@code ModelService} / {@code ProviderService} …），
 * 本类<b>只有读方法、没有任何写方法</b>。若哪天有人往这里加 {@code save/update}，
 * 那才是「同一张表两个写入口」，应当拒绝。</p>
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

    /**
     * Constructs a new instance.
     */
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
     *
     * <p>「当前时间」在这里取而不是由调用方传入：它是<b>数据层的时间语义</b>
     * （{@code effective_from/effective_to} 与库里的时钟对齐），
     * 让编排层传时间会引入「网关机器时钟 vs 数据库时钟」的漂移。</p>
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
