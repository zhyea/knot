package org.chobit.knot.gateway.constants.enums;

/**
 * Contract for enums already migrated out of {@code ks_enum_configs} and exposed to the front end
 * through {@code GET /api/common/enums} as {@code code -> label} options.
 */
public interface EnumOption {

    /**
     * Returns the option code persisted in business tables.
     */
    String code();

    /**
     * Returns the display label shown by the front end.
     */
    String label();
}
