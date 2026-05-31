package org.chobit.knot.gateway.constants.enums;

import lombok.Getter;

@Getter
public enum UserStatusEnum {

    ENABLED(1),
    DISABLED(0);

    private final int code;

    UserStatusEnum(int code) {
        this.code = code;
    }
}
