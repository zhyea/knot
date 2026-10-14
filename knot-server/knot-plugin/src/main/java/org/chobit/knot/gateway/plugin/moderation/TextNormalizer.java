package org.chobit.knot.gateway.plugin.moderation;

import java.text.Normalizer;
import java.util.Locale;

/**
 * 文本归一化：判定一律在归一化副本上进行，原文保留用于展示 / 后续脱敏替换。
 *
 * <p>本期为 STANDARD 强度（设计文档 §3.2）：NFKC + 去格式字符（零宽/BOM/软连字符）+ 小写折叠。
 * 繁简转换、leetspeak、谐音等高级变体留 M4。</p>
 */
public final class TextNormalizer {

    private TextNormalizer() {
    }

    /**
     * 归一化文本；null 原样返回，空串原样返回。
     */
    public static String normalize(String text) {
        if (text == null || text.isEmpty()) {
            return text;
        }
        String nfkc = Normalizer.normalize(text, Normalizer.Form.NFKC);
        StringBuilder builder = new StringBuilder(nfkc.length());
        for (int i = 0; i < nfkc.length(); i++) {
            char c = nfkc.charAt(i);
            // Cf（Format）类字符：零宽空格、零宽连接符、软连字符、BOM 等，常被用于绕过检测
            if (Character.getType(c) == Character.FORMAT) {
                continue;
            }
            builder.append(c);
        }
        return builder.toString().toLowerCase(Locale.ROOT);
    }
}
