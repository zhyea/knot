package org.chobit.knot.gateway.controller;

import org.chobit.knot.gateway.service.EnumOptionRegistry;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Common options shared by several front end modules.
 */
@RestController
@RequestMapping("/api/common")
public class CommonController {

    private final EnumOptionRegistry enumOptionRegistry;

    public CommonController(EnumOptionRegistry enumOptionRegistry) {
        this.enumOptionRegistry = enumOptionRegistry;
    }

    /**
     * Returns the code based enums as a map structure: {@code enumKey -> (code -> label)}.
     *
     * <p>Enums maintained in {@code ks_enum_configs} are not included here, use
     * {@code /api/system/enums/items/{category}} for those.</p>
     */
    @GetMapping("/enums")
    public Map<String, Map<String, String>> enums() {
        return enumOptionRegistry.enumMap();
    }
}
