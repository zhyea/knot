package org.chobit.knot.gateway.util.tools;

/**
 * Base36 编码（大写随机字符），参考 zhy-commons。
 */
public final class Base36 {

    private static final char[] DIGITS_CHAR = "XYZDEFGHIJK567834ABCVWLMNOPQRSTU9012".toCharArray();
    private static final int BASE_LEN = DIGITS_CHAR.length;

    public static String encode(long number) {
        if (number <= 0) {
            throw new IllegalArgumentException("Number(Base36) must be positive: " + number);
        }
        StringBuilder builder = new StringBuilder();
        while (number != 0) {
            builder.append(DIGITS_CHAR[(int) (number % BASE_LEN)]);
            number /= BASE_LEN;
        }
        return builder.reverse().toString();
    }

    private Base36() {
    }
}
