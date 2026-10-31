package org.chobit.knot.svc.service.support;

import org.chobit.knot.common.error.BusinessException;
import org.chobit.knot.common.error.ErrorCode;

/**
 * 必填文本校验：去首尾空白后仍为空即拒绝，否则返回归一化后的文本。
 *
 * <p>⚠ <b>为什么单独一个类</b>：{@code LogicalModelService} 与 {@code ModelService} 的私有
 * {@code requireText} 曾<b>逐字相同</b>，{@code ModelPoolService} 是同一语义却走了
 * {@code normalizeText} 的变体实现。三份并存的实际风险不是「多几行」，而是口径会静默漂移——
 * 同一个空值在 A 接口报错、在 B 接口被写成空串落库。</p>
 *
 * <p>⚠ <b>为什么不把 {@code OutageAdminController} 那份也并进来</b>：它抛的是
 * {@code ResponseStatusException}（gateway 端口层直接映射成 400），而这里抛
 * {@code BusinessException(VALIDATION_ERROR)} 走统一错误体。合并就得让 svc 依赖 Spring Web 的
 * HTTP 语义，等于把端口细节渗进服务层。<b>判据：同名同签名但异常类型不同，不算重复实现</b>——
 * 别只看方法名就判重。</p>
 */
public final class RequiredTextSupport {

    private RequiredTextSupport() {
    }

    /**
     * 必填文本归一：{@code null} 视作空串，去首尾空白；仍为空则抛 {@code VALIDATION_ERROR}。
     *
     * <p>⚠ 刻意用 {@code trim()} 而非 {@code isBlank()} 判空：后者对 em space（U+2003）这类
     * {@code Character.isWhitespace} 为真的字符也判空，而 {@code trim()} 只处理 {@code <= ' '}。
     * 这里要的是<b>与收口前逐字等价</b>，不能顺手把历史可入参从放行改成报错。</p>
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
