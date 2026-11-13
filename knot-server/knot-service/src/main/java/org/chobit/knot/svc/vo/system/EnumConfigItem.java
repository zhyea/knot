package org.chobit.knot.svc.vo.system;

/**
 * 枚举项载体（系统管理 → 枚举管理）。
 * {@code categoryId} 是落库外键，{@code category} 是分类编码（列表接口按它过滤、编辑表单按它回显），前端只认 {@code category}。
 * {@code isSystem} 由分类带下来，决定「能不能改 / 删」，判据在 svc 的 create / update / delete，不靠前端置灰。
 * {@code boundCount} 是派生列（不落库）：枚举项编码被计费规则 model_family、统一模型 model_family、
 * 折扣策略 discount_type、敏感词条 category_code 引用的处数；大于 0 时禁止改码。
 */
public record EnumConfigItem(
        Long id,
        /** 分类 ID（落库） */
        Long categoryId,
        /** 分类编码（JOIN / 接口入参，非表字段） */
        String category,
        String itemCode,
        String itemLabel,
        Integer sortOrder,
        Boolean isSystem,
        Boolean isEnabled,
        Boolean isDeleted,
        String remark,
        /** 派生：该编码被多少处引用（&gt;0 时禁止改码） */
        Long boundCount
) {
}
