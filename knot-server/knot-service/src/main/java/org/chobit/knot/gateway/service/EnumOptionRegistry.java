package org.chobit.knot.gateway.service;

import org.chobit.knot.gateway.constants.enums.ModelApiProtocolEnum;
import org.chobit.knot.gateway.constants.enums.ModelTypeEnum;
import org.chobit.knot.gateway.constants.enums.EnumOption;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Collects the enum options that are maintained in code and exposed to the front end through
 * {@code GET /api/common/enums}.
 *
 * <p>Only enums already migrated out of {@code ks_enum_configs} belong here. Enums still kept in the
 * database stay editable by administrators and are served by {@code /api/system/enums}.</p>
 */
@Component
public class EnumOptionRegistry {

    /**
     * Front end key of an enum -> its {@code code -> label} map.
     */
    private final Map<String, Map<String, String>> enumMap;

    public EnumOptionRegistry() {
        Map<String, Map<String, String>> map = new LinkedHashMap<>();
        put(map, ModelTypeEnum.class.getSimpleName(), ModelTypeEnum.values());
        put(map, ModelApiProtocolEnum.class.getSimpleName(), ModelApiProtocolEnum.values());
        this.enumMap = Collections.unmodifiableMap(map);
    }

    /**
     * Returns all exposed enums as {@code enumKey -> (code -> label)}.
     */
    public Map<String, Map<String, String>> enumMap() {
        return enumMap;
    }

    private static void put(Map<String, Map<String, String>> target, String key, EnumOption[] values) {
        Map<String, String> options = new LinkedHashMap<>();
        for (EnumOption item : values) {
            options.put(item.code(), item.label());
        }
        target.put(key, Collections.unmodifiableMap(options));
    }
}
