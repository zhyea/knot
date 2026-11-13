package org.chobit.knot.gateway.load;

/**
 * 在途并发计量键的拼装入口。
 * 键格式 {@code <prefix>:MODEL:<modelCode>}（如 {@code knot:load:MODEL:gpt-4o}），用模型业务码 {@code kb_models.model_code}，不用主键 id。
 */
public final class ModelLoadKeys {

    private final String prefix;

    /**
     * @param prefix 键前缀，多环境共用同一 Redis 时用于隔离
     */
    public ModelLoadKeys(String prefix) {
        this.prefix = prefix;
    }

    /**
     * 单个供应商模型的在途键。
     */
    public String model(String modelCode) {
        return prefix + ":MODEL:" + modelCode;
    }
}
