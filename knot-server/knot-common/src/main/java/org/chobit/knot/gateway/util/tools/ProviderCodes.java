package org.chobit.knot.gateway.util.tools;

/**
 * 供应商编码：基于 ShortCode 生成 8 位大写短码。
 */
public final class ProviderCodes {

    public static final int DEFAULT_LENGTH = 8;
    public static final int MAX_LENGTH = 32;

    public static String generate() {
        String raw = ShortCode.genUpper();
        if (raw.length() >= DEFAULT_LENGTH) {
            return raw.substring(raw.length() - DEFAULT_LENGTH);
        }
        return "0".repeat(DEFAULT_LENGTH - raw.length()) + raw;
    }

    private ProviderCodes() {
    }
}
