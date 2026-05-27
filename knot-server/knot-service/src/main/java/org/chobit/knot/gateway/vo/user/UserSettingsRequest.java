package org.chobit.knot.gateway.vo.user;

import java.util.Map;

public record UserSettingsRequest(
        Map<String, String> settings
) {
}
