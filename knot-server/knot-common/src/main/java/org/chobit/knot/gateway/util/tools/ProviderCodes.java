package org.chobit.knot.gateway.util.tools;

/**
 * Executes the public operation. Executes the public operation.
 */
/**
 * 渚涘簲鍟嗙紪鐮侊細鍩轰簬 ShortCode 鐢熸垚 8 浣嶅ぇ鍐欑煭鐮併€?
 */
public final class ProviderCodes {

    public static final int DEFAULT_LENGTH = 8;
    public static final int MAX_LENGTH = 32;

    /**
     * Executes the public operation.
     */
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
