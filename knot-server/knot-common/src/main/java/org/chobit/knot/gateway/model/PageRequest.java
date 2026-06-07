package org.chobit.knot.gateway.model;

/**
 * 分页请求参数。
 */
public record PageRequest(
        int pageNum,
        int pageSize
) {
    public PageRequest {
        if (pageNum <= 0) {
            pageNum = 1;
        }
        if (pageSize <= 0) {
            pageSize = 20;
        }
        if (pageSize > 500) {
            pageSize = 500;
        }
    }

    /**
     * Creates a page request with fallback defaults.
     */
    public static PageRequest of(Integer pageNum, Integer pageSize) {
        return new PageRequest(
                pageNum != null ? pageNum : 1,
                pageSize != null ? pageSize : 20
        );
    }

    /**
     * Builds a page request from an object containing pageNum and pageSize fields.
     */
    public static PageRequest from(Object body) {
        if (body instanceof PageRequest pr) {
            return pr;
        }
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
