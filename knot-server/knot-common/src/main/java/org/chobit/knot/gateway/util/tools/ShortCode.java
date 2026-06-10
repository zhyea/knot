package org.chobit.knot.gateway.util.tools;

import java.text.DecimalFormat;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 短码生成工具，参考
 * <a href="https://github.com/zhyea/zhy-commons/blob/main/common-utils/src/main/java/org/chobit/commons/tools/ShortCode.java">
 *     zhy-commons ShortCode
 * </a>
 */
public final class ShortCode {

    private static final AtomicInteger SEQ = new AtomicInteger(1);
    private static final DecimalFormat FORMAT = new DecimalFormat("00");
    private static final int MAX_PAD_SIZE = 100;

    /**
     * Generates an uppercase short code.
     */
    public static synchronized String genUpper() {
        long value = longValue();
        return Base36.encode(value);
    }

    private static long longValue() {
        StringBuilder builder = new StringBuilder(System.nanoTime() / 1000 + "");
        if (SEQ.incrementAndGet() % 10 == 0) {
            SEQ.incrementAndGet();
        }
        builder.append(FORMAT.format(SEQ.get()));
        if ((MAX_PAD_SIZE - 1) == SEQ.get()) {
            SEQ.set(1);
        }
        return Long.parseLong(builder.reverse().toString());
    }

    private ShortCode() {
    }
}
