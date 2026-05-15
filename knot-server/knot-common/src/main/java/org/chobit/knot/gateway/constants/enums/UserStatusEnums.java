package org.chobit.knot.gateway.constants.enums;

import lombok.Getter;

@Getter
public enum UserStatusEnums {


    ENABLED(1),
    DISABLED(0);

    UserStatusEnums(int value) {
        this.code = value;
    }

    private final int code;

}
