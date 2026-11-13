package org.chobit.knot.gateway.redis;

import lombok.Getter;

/**
 * 共享状态存储（Redis）不可用：由 {@link RedisCache} 统一包装底层异常（连接超时、只读副本拒绝、Lua 报错等）后抛出。
 * {@code GatewayExceptionHandler} 捕获后映射为 503 + {@code storage_unavailable}；本异常不带放行值，也没有 fail-open 开关。
 */
@Getter
public class RedisUnavailableException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    private final String domain;
    private final String operation;
    private final String key;

    /**
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
}