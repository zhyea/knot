package org.chobit.knot.gateway.controller;

import org.chobit.knot.gateway.constants.enums.EnumOptionItem;
import org.chobit.knot.gateway.service.support.EnumOptionRegistry;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * 代码枚举字典：全站下拉选项的统一数据源。
 *
 * <p>登录后各页面都需要它，且无业务敏感性，因此在 {@code WebMvcConfig} 白名单放行
 * （认证即可访问，不走接口级授权）。</p>
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
     *
     * <p>数组结构而非 {@code code -> label} 的 map：JSON object key 必然是字符串，用 map 会让
     * 数字 code 在传输层退化成字符串 "1"，与字符串 code 无法区分。</p>
     *
     * <p>Enums maintained in {@code ks_enum_configs} are not included here, use
     * {@code /api/system/enums/items/{category}} for those.</p>
     */
    @GetMapping
    public Map<String, List<EnumOptionItem>> enums() {
        return enumOptionRegistry.enumMap();
    }
}
