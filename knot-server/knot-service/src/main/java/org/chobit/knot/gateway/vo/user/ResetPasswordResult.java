package org.chobit.knot.gateway.vo.user;

/**
 * 重置密码的结果：一次性随机口令只在本次响应中返回一次，不落库明文、不写日志。
 */
public record ResetPasswordResult(Long id, String username, String oneTimePassword) {
}
