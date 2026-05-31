package org.chobit.knot.gateway.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

/**
 * 閫氱敤 POST 鍒嗛〉鏌ヨ璇锋眰浣?
 * <p>
 * 鍓嶇 postQuery 缁熶竴鍙戦€佹缁撴瀯锛屽悗绔帶鍒跺櫒鐢?@RequestBody 鎺ユ敹銆?
 * 鍚勬ā鍧楀闇€棰濆绛涢€夊弬鏁帮紝鍙洿鎺ュ湪璇锋眰浣撲腑鎼哄甫锛孋ontroller 鑷璇诲彇銆?
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record PageQuery(
        Integer pageNum,
        Integer pageSize,
        String category,
        String keyword,
        List<String> modelTypes,
        Long providerId,
        Long logicalModelId
) {
    /**
     * Converts the source value to the target representation. Executes the public operation.
     */
    public PageRequest toPageRequest() {
        return PageRequest.of(pageNum, pageSize);
    }
}
