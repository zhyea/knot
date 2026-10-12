package org.chobit.knot.gateway.load;

/**
 * 在途并发计量键的拼装入口。
 *
 * <p>键格式：{@code <prefix>:MODEL:<modelCode>}，例如 {@code knot:load:MODEL:gpt-4o}。</p>
 *
 * <p>键用的是模型<b>业务码</b>（{@code kb_models.model_code}）而不是主键 id：
 * 对齐「跨模块绑定存 code 不存 id」口径，且重建库后键空间不变。</p>
 */
public final class ModelLoadKeys {

    private final String prefix;

    /**
     * Constructs a new instance.
     *
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
