package org.chobit.knot.gateway.constants.enums;

public enum ProviderTypeEnum {
    OPENAI("OPENAI"),
    ANTHROPIC("ANTHROPIC"),
    DEEPSEEK("DEEPSEEK"),
    OPENROUTER("OPENROUTER"),
    QWEN("QWEN");

    private final String code;

    ProviderTypeEnum(String code) {
        this.code = code;
    }

    /**
     * Executes the public operation. Executes the public operation.
     */
    public String code() {
        return code;
    }
}
