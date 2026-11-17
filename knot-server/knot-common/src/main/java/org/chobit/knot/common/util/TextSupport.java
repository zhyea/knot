package org.chobit.knot.common.util;

import org.chobit.knot.common.error.BusinessException;
import org.chobit.knot.common.error.ErrorCode;

/**
 * 文本归一：全项目唯一的「空白即缺失」判据。
 *
 * <p>空白按 Unicode 判（{@link String#isBlank()}），并只保留用 {@link String#strip()}
 * 去掉首尾空白后的内容——{@code trim()} 只处理 {@code <= ' '} 的字符，全角空格
 * （U+3000）清不掉，于是「看着是空的」值会被当成有值存进库。</p>
 *
 * <p>不换行空格（U+00A0）按 JDK 口径不算空白，会被当成正常内容——它是刻意保留的可视字符，
 * 不是排版空白。需要支持它时改这里，别在调用方加第二套判据。</p>
 *
 * <p>本类只处理「空 → null」与「必填校验」两件事。「空 → 空串」是另一种语义
 * （调用方要拼接或写进 NOT NULL 列），不要往这里加。</p>
 */
public final class TextSupport {

    private TextSupport() {
    }

    /**
     * 空白归一：{@code null} 与全空白都返回 {@code null}，否则返回去首尾空白后的文本。
     */
    public static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.strip();
    }

    /**
     * 必填文本归一：{@code null} 视作空串，去首尾空白；仍为空则抛 {@code VALIDATION_ERROR}。
     *
     * @param value   待校验文本
     * @param message 报错文案；由调用方给出具体字段名，本类不猜字段
     */
    public static String require(String value, String message) {
        String text = value == null ? "" : value.strip();
        if (text.isEmpty()) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, message);
        }
        return text;
    }
}
