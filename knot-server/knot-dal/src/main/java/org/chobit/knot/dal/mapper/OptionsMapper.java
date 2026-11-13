package org.chobit.knot.dal.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.chobit.knot.dal.entity.OptionRow;

import java.util.List;

/**
 * 下拉候选（options）投影查询。每资源两个方法：{@code listXxxOptions} 关键字检索页
 * （{@code PageHelper} 追加 limit/count，SQL 内不写 limit；参数含 enabledOnly / includeDeleted / 资源过滤字段）；
 * {@code listXxxOptionsByValues} 按已选 value 精确回显（不分页、不过滤 enabledOnly，计算 disabled 并进 missingValues）。
 * 返回 {@code List<OptionRow<V>>}（列 value / label / disabled / meta，顶层无 {@code code}）；禁返回凭据 / secretKey / 完整 configJson。
 */
@Mapper
public interface OptionsMapper {

    // ---------- 用户（value=username；路由规则/应用/消费者均按 username 绑定用户） ----------
    List<OptionRow<String>> listUserUsernameOptions(@Param("keyword") String keyword,
                                                    @Param("enabledOnly") boolean enabledOnly,
                                                    @Param("includeDeleted") boolean includeDeleted);

    List<OptionRow<String>> listUserUsernameOptionsByValues(@Param("values") List<String> values);

    // ---------- 部门（value=id） ----------
    List<OptionRow<Long>> listDepartmentOptions(@Param("keyword") String keyword,
                                                @Param("enabledOnly") boolean enabledOnly,
                                                @Param("includeDeleted") boolean includeDeleted);

    List<OptionRow<Long>> listDepartmentOptionsByValues(@Param("values") List<String> values);

    // ---------- 应用（value=id） ----------
    List<OptionRow<Long>> listAppOptions(@Param("keyword") String keyword,
                                         @Param("enabledOnly") boolean enabledOnly,
                                         @Param("includeDeleted") boolean includeDeleted);

    List<OptionRow<Long>> listAppOptionsByValues(@Param("values") List<String> values);

    // ---------- 供应商账户（value=code；meta 承载 baseUrl） ----------
    List<OptionRow<String>> listProviderAccountOptions(@Param("keyword") String keyword,
                                                       @Param("enabledOnly") boolean enabledOnly);

    List<OptionRow<String>> listProviderAccountOptionsByValues(@Param("values") List<String> values);

    // ---------- 统一模型（value=modelCode；meta 承载 modelType/modelFamily/status） ----------
    List<OptionRow<String>> listLogicalModelOptions(@Param("keyword") String keyword,
                                                     @Param("enabledOnly") boolean enabledOnly,
                                                     @Param("includeDeleted") boolean includeDeleted,
                                                     @Param("modelFamilyCode") String modelFamilyCode);

    List<OptionRow<String>> listLogicalModelOptionsByValues(@Param("values") List<String> values);

    // ---------- 供应商模型（value=modelCode；meta 承载供应商/统一模型派生字段） ----------
    List<OptionRow<String>> listModelOptions(@Param("keyword") String keyword,
                                              @Param("enabledOnly") boolean enabledOnly,
                                              @Param("logicalModelCode") String logicalModelCode,
                                              @Param("modelFamilyCode") String modelFamilyCode,
                                              @Param("providerAccountCode") String providerAccountCode,
                                              @Param("preferredLogicalModelCode") String preferredLogicalModelCode);

    List<OptionRow<String>> listModelOptionsByValues(@Param("values") List<String> values);

    // ---------- 模型池（value=poolCode） ----------
    List<OptionRow<String>> listModelPoolOptions(@Param("keyword") String keyword,
                                                  @Param("enabledOnly") boolean enabledOnly,
                                                  @Param("includeDeleted") boolean includeDeleted,
                                                  @Param("logicalModelCode") String logicalModelCode,
                                                  @Param("preferredLogicalModelCode") String preferredLogicalModelCode);

    List<OptionRow<String>> listModelPoolOptionsByValues(@Param("values") List<String> values);

    // ---------- 路由消费者（value=consumer_code；永不返回 secret_key） ----------
    List<OptionRow<String>> listRoutingConsumerOptions(@Param("keyword") String keyword,
                                                       @Param("enabledOnly") boolean enabledOnly);

    List<OptionRow<String>> listRoutingConsumerOptionsByValues(@Param("values") List<String> values);

    // ---------- 计费规则（value=code） ----------
    List<OptionRow<String>> listBillingRuleOptions(@Param("keyword") String keyword,
                                                    @Param("enabledOnly") boolean enabledOnly,
                                                    @Param("modelFamilyCode") String modelFamilyCode);

    List<OptionRow<String>> listBillingRuleOptionsByValues(@Param("values") List<String> values);

    // ---------- 角色（value=id；ks_roles 无 status/is_deleted，disabled 恒 0） ----------
    List<OptionRow<Long>> listRoleOptions(@Param("keyword") String keyword);

    List<OptionRow<Long>> listRoleOptionsByValues(@Param("values") List<String> values);

    // ---------- 供应商信息（value=code；kb_providers 无 status/is_deleted，disabled 恒 0） ----------
    List<OptionRow<String>> listProviderProfileOptions(@Param("keyword") String keyword);

    List<OptionRow<String>> listProviderProfileOptionsByValues(@Param("values") List<String> values);
}
