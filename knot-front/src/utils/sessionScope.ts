/**
 * 401 是否应清理管理端登录态。
 *
 * <p>判据只看「这条通道是否承载管理端会话」，<b>不看请求头里有没有 `Authorization`</b>：
 * 网关调试通道带的是消费者 API Key，同样会返回 401，但与管理端会话无关。
 * 早先用 `!requestHeaders.Authorization` 反推，用户在调试抽屉删掉该表头（或写成小写）
 * 就会把消费者请求的 401 当成管理端过期，清掉 JWT 并强制跳登录。</p>
 *
 * @param status       HTTP 状态码
 * @param adminSession 当前通道是否走管理端认证
 * @param hasToken     本地是否持有管理端 token
 */
export function shouldClearAdminSessionOn401(
  status: number,
  adminSession: boolean,
  hasToken: boolean
): boolean {
  return status === 401 && adminSession && hasToken;
}
