package org.chobit.knot.gateway.controller;

import org.chobit.knot.gateway.constants.enums.EnumOptionItem;
import org.chobit.knot.gateway.service.EnumOptionRegistry;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
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
     * Returns the code based enums as an array structure: {@code enumKey -> [{code, label}, ...]}.
     *
     * <p>数组结构而非 {@code code -> label} 的 map：JSON object key 必然是字符串，用 map 会让
     * 数字 code 在传输层退化成字符串 "1"，与字符串 code 无法区分。</p>
     *
     * <p>Enums maintained in {@code ks_enum_configs} are not included here, use
     * {@code /api/system/enums/items/{category}} for those.</p>
     */
    @GetMapping("/enums")
    public Map<String, List<EnumOptionItem>> enums() {
        return enumOptionRegistry.enumMap();
    }
}
