package org.chobit.knot.gateway.redis;

/**
 * 共享状态存储（Redis）不可用。
 *
 * <p>由 {@link RedisCache} 统一包装底层异常（连接超时、只读副本拒绝、Lua 报错等）后抛出，
 * 使上层存储实现不必各自 try/catch 一堆 Spring Redis 异常类型。
 * {@code GatewayExceptionHandler} 捕获本异常并映射为 <b>503</b> + {@code storage_unavailable}。</p>
 *
 * <p><b>不静默降级</b>：本异常一定会上抛到 HTTP 层。频控计数与在途负载承载的是
 * 跨节点共享的全局口径，Redis 不可用时若按放行值处理会造成看不见的损失
 * （频控计 0 → 多节点实际限额悄悄涨到「配置值 × 节点数」，直接超发；
 * 在途记 0 → 所有模型被看成不拥塞，已过载的模型继续被加压）。
 * 宁可全拒 + ERROR 日志报警，也不要静默劣化。</p>
 *
 * <p>因此本异常<b>不带放行值</b>，也没有 fail-open 开关。</p>
 */
public class RedisUnavailableException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    private final String domain;
    private final String operation;
    private final String key;

    /**
     * Constructs a new instance.
     *
     * @param domain    存储域（traffic / load），仅用于日志定位
     * @param operation 触发故障的操作名
     * @param key       涉及的键，可为空（列举类操作）
     * @param cause     底层异常
     */
    public RedisUnavailableException(String domain, String operation, String key, Throwable cause) {
        super("redis unavailable: domain=" + domain + ", operation=" + operation
                + (key == null ? "" : ", key=" + key) + ", cause=" + cause, cause);
        this.domain = domain;
        this.operation = operation;
        this.key = key;
    }

    /**
     * Returns the requested value. Executes the public operation.
     */
    public String getDomain() {
        return domain;
    }

    /**
     * Returns the requested value. Executes the public operation.
     */
    public String getOperation() {
        return operation;
    }

    /**
     * Returns the requested value. Executes the public operation.
     */
    public String getKey() {
        return key;
    }
}