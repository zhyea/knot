package org.chobit.knot.admin.controller;

import org.chobit.knot.common.constants.enums.EnumOptionItem;
import org.chobit.knot.svc.service.support.EnumOptionRegistry;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * 代码枚举字典：全站下拉选项的统一数据源。
 * 在 {@code WebMvcConfig} 白名单放行：认证即可访问，不走接口级授权。
 */
@RestController
@RequestMapping("/api/enum-options")
public class EnumOptionsController {

    private final EnumOptionRegistry enumOptionRegistry;

    public EnumOptionsController(EnumOptionRegistry enumOptionRegistry) {
        this.enumOptionRegistry = enumOptionRegistry;
    }

    /**
     * Returns the code based enums as an array structure: {@code enumKey -> [{code, label}, ...]}.
     * 用数组结构而非 {@code code -> label} 的 map，避免数字 code 在传输层退化成字符串。
     * {@code ks_enum_configs} 维护的枚举不在此返回，走 {@code /api/system/enums/items/{category}}。
     */
    @GetMapping
    public Map<String, List<EnumOptionItem>> enums() {
        return enumOptionRegistry.enumMap();
    }
}
