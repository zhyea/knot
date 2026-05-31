package org.chobit.knot.gateway.model;

/**
 * 鍒嗛〉璇锋眰鍙傛暟
 */
public record PageRequest(
        int pageNum,
        int pageSize
) {
    /**
     * Executes the public operation. Executes the public operation.
     */
    public PageRequest {
        if (pageNum <= 0) pageNum = 1;
        if (pageSize <= 0) pageSize = 20;
        if (pageSize > 500) pageSize = 500;
    }

    /**
     * Creates a new instance.
     */
    public static PageRequest of(Integer pageNum, Integer pageSize) {
        return new PageRequest(
                pageNum != null ? pageNum : 1,
                pageSize != null ? pageSize : 20
        );
    }

    /**
     * Builds the target value from the source input. Executes the public operation.
     */
    /**
     * 浠庝换鎰忓寘鍚?pageNum/pageSize 瀛楁鐨勫璞℃瀯閫犲垎椤佃姹?
     */
    public static PageRequest from(Object body) {
        if (body instanceof PageRequest pr) return pr;
        // fallback: 閫氳繃鍙嶅皠璇诲彇 pageNum / pageSize
        try {
            var c = body.getClass();
            var pnField = c.getDeclaredField("pageNum");
            var psField = c.getDeclaredField("pageSize");
            pnField.setAccessible(true);
            psField.setAccessible(true);
            return PageRequest.of(
                    (Integer) pnField.get(body),
                    (Integer) psField.get(body)
            );
        } catch (Exception e) {
            return new PageRequest(1, 20);
        }
    }
}
