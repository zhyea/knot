import {describe, expect, it} from "vitest";
import {shouldClearAdminSessionOn401} from "./sessionScope";

/**
 * 反向探针：网关调试通道（消费者 API Key）的 401 不得清管理端登录态。
 *
 * <p>这组用例守的是「认证边界」：只要判据重新变回「请求头里有没有 Authorization」，
 * 第一条与第三条就会红——它们分别对应「删掉 Authorization 行」和「写成小写」两种真实操作。</p>
 */
describe("shouldClearAdminSessionOn401", () => {
  it("消费者通道（调试抽屉删掉 Authorization）的 401 不清登录态", () => {
    expect(shouldClearAdminSessionOn401(401, false, true)).toBe(false);
  });

  it("管理端通道的 401 且有 token 时才清", () => {
    expect(shouldClearAdminSessionOn401(401, true, true)).toBe(true);
  });

  it("管理端通道没有 token（登录前的请求）不清", () => {
    expect(shouldClearAdminSessionOn401(401, true, false)).toBe(false);
  });

  it("非 401 一律不清", () => {
    expect(shouldClearAdminSessionOn401(403, true, true)).toBe(false);
    expect(shouldClearAdminSessionOn401(200, false, true)).toBe(false);
  });
});
