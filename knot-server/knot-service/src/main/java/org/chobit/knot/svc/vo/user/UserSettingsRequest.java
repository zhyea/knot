package org.chobit.knot.svc.vo.user;

import java.util.Map;

public record UserSettingsRequest(
        Map<String, String> settings
) {
}
