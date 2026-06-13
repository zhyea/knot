package org.chobit.knot.gateway.util;

import java.math.BigDecimal;
import java.util.Map;

public final class MapNumberUtils {

    private MapNumberUtils() {
    }

    /**
     * Returns the first positive long value found by key order.
     */
    public static long firstLong(Map<String, Object> map, String... keys) {
        if (map == null || map.isEmpty() || keys == null || keys.length == 0) {
            return 0L;
        }
        for (String key : keys) {
            Long parsed = longValue(map.get(key));
            if (parsed != null && parsed > 0) {
                return parsed;
            }
        }
        return 0L;
    }

    /**
     * Returns the numeric value from a nested map entry.
     */
    @SuppressWarnings("unchecked")
    public static long nestedLong(Map<String, Object> map, String objectKey, String valueKey) {
        if (map == null || map.isEmpty()) {
            return 0L;
        }
        Object nested = map.get(objectKey);
        if (!(nested instanceof Map<?, ?> nestedMap)) {
            return 0L;
        }
        Long parsed = longValue(((Map<String, Object>) nestedMap).get(valueKey));
        return parsed == null ? 0L : parsed;
    }

    /**
     * Converts an arbitrary object to a long when possible.
     */
    public static Long longValue(Object value) {
        if (value instanceof Number number) {
            return number.longValue();
        }
        if (value instanceof String text) {
            String normalized = text.trim();
            if (normalized.isEmpty()) {
                return null;
            }
            try {
                return new BigDecimal(normalized).longValue();
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return null;
    }
}
