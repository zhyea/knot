package org.chobit.knot.gateway.vo.model;

import java.util.List;

/**
 * Model type option maintained by {@link org.chobit.knot.gateway.constants.enums.ModelTypeEnum}.
 */
public record ModelTypeItem(String code,
                            String displayName,
                            int sortOrder,
                            List<String> supportedProtocols) {
}
