package org.chobit.knot.common.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

/**
 * 角色下拉候选查询：value 语义 = id，无资源专属过滤。
 * {@code ks_roles} 无 status / is_deleted 列，故 {@code enabledOnly} 与 {@code includeDeleted} 对本资源无效；
 * {@code disabled} 恒为 0，只有「值不存在」会进 missingValues。
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record RoleOptionQuery(
        Integer pageNum,
        Integer pageSize,
        String keyword,
        List<String> values,
        Boolean enabledOnly,
        Boolean includeDeleted
) {
    /** 归一化为基类（含分页上限钳制 / values 上限校验所需的默认值语义）。 */
    public OptionQuery toBase() {
        return new OptionQuery(pageNum, pageSize, keyword, values, enabledOnly, includeDeleted);
    }
}
