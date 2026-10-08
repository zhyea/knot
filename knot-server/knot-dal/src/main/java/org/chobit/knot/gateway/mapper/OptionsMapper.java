package org.chobit.knot.gateway.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.chobit.knot.gateway.entity.OptionRow;

import java.util.List;

/**
 * 下拉候选（options）投影查询。
 *
 * <p>每资源两个方法：
 * <ul>
 *   <li>{@code listXxxOptions}：关键字检索页（由 PageHelper 追加 limit/count，SQL 内不写 limit）；
 *       参数含 enabledOnly / includeDeleted / 资源过滤字段。</li>
 *   <li>{@code listXxxOptionsByValues}：按已选value 精确回显（不分页、不过滤 enabledOnly，
 *       仅保留「存在」条件并计算 disabled 标记，停用/已删项据此回显并进 missingValues）。</li>
 * </ul>
 *
 * <p>返回 {@code List<OptionRow<V>>}：投影列名 value / label / code / disabled / meta 与该类字段
 * 一一对应，由 MyBatis 直接填充（见 {@code OptionsMapper.xml} 的 {@code resultType}）。
 * 泛型 {@code V} 标注 value 的实际类型——id 型资源 {@code Long}，code 型资源 {@code String}——
 * 仅作文档与编译期提示，反射填充行为与不带泛型一致。</p>
 *
 * <p>约定见 options-refactor-constraints.md 第 0/1 节：禁返回 secretKey / 凭据 / 完整 configJson。
 * baseUrl 不再单独占列，已并入供应商账户的 {@code meta}（非敏感的上游地址）。</p>
 */
@Mapper
public interface OptionsMapper {

    // ---------- 用户（value=id） ----------
    List<OptionRow<Long>> listUserOptions(@Param("keyword") String keyword,
                                          @Param("enabledOnly") boolean enabledOnly,
                                          @Param("includeDeleted") boolean includeDeleted);

    List<OptionRow<Long>> listUserOptionsByValues(@Param("values") List<String> values);

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
                                              @Param("providerAccountCode") String providerAccountCode);

    List<OptionRow<String>> listModelOptionsByValues(@Param("values") List<String> values);

    // ---------- 模型池（value=poolCode） ----------
    List<OptionRow<String>> listModelPoolOptions(@Param("keyword") String keyword,
                                                  @Param("enabledOnly") boolean enabledOnly,
                                                  @Param("includeDeleted") boolean includeDeleted,
                                                  @Param("logicalModelCode") String logicalModelCode);

    List<OptionRow<String>> listModelPoolOptionsByValues(@Param("values") List<String> values);

    // ---------- 路由消费者（value=id；永不返回 secret_key） ----------
    List<OptionRow<Long>> listRoutingConsumerOptions(@Param("keyword") String keyword,
                                                      @Param("enabledOnly") boolean enabledOnly);

    List<OptionRow<Long>> listRoutingConsumerOptionsByValues(@Param("values") List<String> values);

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