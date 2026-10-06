package org.chobit.knot.gateway.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Map;

/**
 * 下拉候选（options）投影查询。
 *
 * <p>每资源两个方法：
 * <ul>
 *   <li>{@code listXxxOptions}：关键字检索页（由 PageHelper 追加 limit/count，SQL 内不写 limit）；
 *       参数含 enabledOnly / includeDeleted / 资源过滤字段。</li>
 *   <li>{@code listXxxOptionsByValues}：按已选 value 精确回显（不分页、不过滤 enabledOnly，
 *       仅保留「存在」条件并计算 disabled 标记，停用/已删项据此回显并进 missingValues）。</li>
 * </ul>
 *
 * <p>返回行统一四列别名：{@code value / label / code / disabled}（disabled 为 0/1）。
 * 约定见 options-refactor-constraints.md 第 0/1 节：禁返回 secretKey / 凭据 / 完整 configJson。</p>
 */
@Mapper
public interface OptionsMapper {

    // ---------- 用户（value=id） ----------
    List<Map<String, Object>> listUserOptions(@Param("keyword") String keyword,
                                              @Param("enabledOnly") boolean enabledOnly,
                                              @Param("includeDeleted") boolean includeDeleted);

    List<Map<String, Object>> listUserOptionsByValues(@Param("values") List<String> values);

    // ---------- 部门（value=id） ----------
    List<Map<String, Object>> listDepartmentOptions(@Param("keyword") String keyword,
                                                    @Param("enabledOnly") boolean enabledOnly,
                                                    @Param("includeDeleted") boolean includeDeleted);

    List<Map<String, Object>> listDepartmentOptionsByValues(@Param("values") List<String> values);

    // ---------- 应用（value=id） ----------
    List<Map<String, Object>> listAppOptions(@Param("keyword") String keyword,
                                            @Param("enabledOnly") boolean enabledOnly,
                                            @Param("includeDeleted") boolean includeDeleted);

    List<Map<String, Object>> listAppOptionsByValues(@Param("values") List<String> values);

    // ---------- 供应商账户（value=code） ----------
    List<Map<String, Object>> listProviderAccountOptions(@Param("keyword") String keyword,
                                                         @Param("enabledOnly") boolean enabledOnly);

    List<Map<String, Object>> listProviderAccountOptionsByValues(@Param("values") List<String> values);

    // ---------- 统一模型（value=modelCode） ----------
    List<Map<String, Object>> listLogicalModelOptions(@Param("keyword") String keyword,
                                                     @Param("enabledOnly") boolean enabledOnly,
                                                     @Param("includeDeleted") boolean includeDeleted,
                                                     @Param("modelFamilyCode") String modelFamilyCode);

    List<Map<String, Object>> listLogicalModelOptionsByValues(@Param("values") List<String> values);

    // ---------- 供应商模型（value=modelCode） ----------
    List<Map<String, Object>> listModelOptions(@Param("keyword") String keyword,
                                              @Param("enabledOnly") boolean enabledOnly,
                                              @Param("logicalModelCode") String logicalModelCode,
                                              @Param("modelFamilyCode") String modelFamilyCode,
                                              @Param("providerAccountCode") String providerAccountCode);

    List<Map<String, Object>> listModelOptionsByValues(@Param("values") List<String> values);

    // ---------- 模型池（value=poolCode） ----------
    List<Map<String, Object>> listModelPoolOptions(@Param("keyword") String keyword,
                                                  @Param("enabledOnly") boolean enabledOnly,
                                                  @Param("includeDeleted") boolean includeDeleted,
                                                  @Param("logicalModelCode") String logicalModelCode);

    List<Map<String, Object>> listModelPoolOptionsByValues(@Param("values") List<String> values);

    // ---------- 路由消费者（value=id；永不返回 secret_key） ----------
    List<Map<String, Object>> listRoutingConsumerOptions(@Param("keyword") String keyword,
                                                        @Param("enabledOnly") boolean enabledOnly);

    List<Map<String, Object>> listRoutingConsumerOptionsByValues(@Param("values") List<String> values);

    // ---------- 计费规则（value=code） ----------
    List<Map<String, Object>> listBillingRuleOptions(@Param("keyword") String keyword,
                                                    @Param("enabledOnly") boolean enabledOnly,
                                                    @Param("modelFamilyCode") String modelFamilyCode);

    List<Map<String, Object>> listBillingRuleOptionsByValues(@Param("values") List<String> values);

    // ---------- 角色（value=id；ks_roles 无 status/is_deleted，disabled 恒 0） ----------
    List<Map<String, Object>> listRoleOptions(@Param("keyword") String keyword);

    List<Map<String, Object>> listRoleOptionsByValues(@Param("values") List<String> values);

    // ---------- 供应商信息（value=code；kb_providers 无 status/is_deleted，disabled 恒 0） ----------
    List<Map<String, Object>> listProviderProfileOptions(@Param("keyword") String keyword);

    List<Map<String, Object>> listProviderProfileOptionsByValues(@Param("values") List<String> values);
}
