package org.chobit.knot.svc.service.support;

import org.chobit.knot.common.error.BusinessException;
import org.chobit.knot.common.error.ErrorCode;

/**
 * 必填文本校验：去首尾空白后仍为空即拒绝，否则返回归一化后的文本。
 */
public final class RequiredTextSupport {

    private RequiredTextSupport() {
    }

    /**
     * 必填文本归一：{@code null} 视作空串，去首尾空白；仍为空则抛 {@code VALIDATION_ERROR}。
     * 用 {@code trim()} 而非 {@code isBlank()} 判空，{@code trim()} 只处理 {@code <= ' '}。
     *
     * @param value   待校验文本
     * @param message 报错文案；由调用方给出具体字段名，本类不猜字段
     */
    public static String require(String value, String message) {
        String text = value == null ? "" : value.trim();
        if (text.isEmpty()) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, message);
        }
        return text;
    }
}
