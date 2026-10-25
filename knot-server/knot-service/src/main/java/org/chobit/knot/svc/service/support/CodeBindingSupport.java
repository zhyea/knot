package org.chobit.knot.svc.service.support;

import org.chobit.knot.gateway.error.BusinessException;
import org.chobit.knot.gateway.error.ErrorCode;

/**
 * 业务编码改码守卫：编码在没有任何绑定前可改，一旦被其它实体按业务码引用就锁死。
 *
 * <p>跨模块绑定一律存 code 不存 id（见项目约定），所以改码会让存量引用整体掉绑，
 * 且掉绑是静默的（查询 inner join 落空、运行时回退默认策略），不像删库那样立刻报错。
 * 因此统一在服务端拦：{@code 409 CONFLICT}，前端按同一份引用计数把输入框置灰。</p>
 *
 * <p><b>引用计数口径</b>（2026-10-22 robin 拍板）：只统计未被逻辑删除的引用行；
 * 但「逻辑删除后可恢复」的实体例外——被删的行仍持有编码引用，恢复后必须还能解析，
 * 因此这类表一律<b>不</b>带 {@code is_deleted = 0} 过滤。当前可恢复的引用表：
 * {@code kb_models}、{@code kb_model_pools}、{@code kb_logical_models}、{@code kb_billing_rules}；
 * {@code kb_apps} / {@code ks_users} / {@code ks_departments} 没有恢复入口，按 {@code is_deleted = 0} 过滤。</p>
 *
 * <p>改码不做级联 UPDATE（robin 拍板：直接拒绝，不静默改写引用行）。</p>
 */
public final class CodeBindingSupport {

    private CodeBindingSupport() {
    }

    /**
     * 编码归一：null 视作空串，其余去首尾空白。与后端各服务存码口径一致（不强制大小写）。
     */
    public static String normalize(String value) {
        return value == null ? "" : value.trim();
    }

    /**
     * 计数归一：null 视作 0，避免各调用点重复判空。
     */
    public static long countOf(Long value) {
        return value == null ? 0L : value;
    }

    /**
     * 是否已绑定：任一引用行存在即视为已绑定。
     */
    public static boolean isBound(Long boundCount) {
        return countOf(boundCount) > 0;
    }

    /**
     * 改码守卫：编码未变直接放行（避免"保存其它字段"被自身引用数误伤）；
     * 编码变了且存在引用则 409。
     *
     * @param oldCode    库中现有编码
     * @param newCode    本次提交编码（未归一）
     * @param boundCount 旧编码的引用计数
     * @param label      实体中文名，用于提示文案
     */
    public static void requireCodeChangeAllowed(String oldCode, String newCode, Long boundCount, String label) {
        if (isSameCode(oldCode, newCode)) {
            return;
        }
        long count = countOf(boundCount);
        if (count > 0) {
            throw new BusinessException(ErrorCode.CONFLICT,
                    label + "已被 " + count + " 处引用，请先解除绑定再修改编码");
        }
    }

    /**
     * 编码是否实质未变：按归一后的值比较（后端存码只去空白，不做大小写归一，这里同口径）。
     */
    public static boolean isSameCode(String oldCode, String newCode) {
        return normalize(oldCode).equals(normalize(newCode));
    }
}
