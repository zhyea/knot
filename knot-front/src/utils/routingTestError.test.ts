import {describe, expect, it} from "vitest";
import {normalizeErrorResult, normalizeNetworkErrorMessage, serializeBody} from "./routingTestError";

/**
 * 路由测试抽屉错误归一（改进清单 3.6 第一步）。
 *
 * <p>钉住三条口径：① 统一响应包装与业务对象的负载位置不同，不能混着取；
 * ② fetch 的英文 `TypeError` 必须翻译成可操作提示；③ 网络失败也要有 Body，
 * 否则调试面板看起来像「空响应」而不是「没连上」。</p>
 */
describe("normalizeErrorResult", () => {
  it("统一响应包装：负载在 data 内，错误文案取外层 message", () => {
    const result = normalizeErrorResult(
      {
        message: "fallback",
        response: {
          status: 400,
          data: {success: false, message: "规则未启用", data: {modelCode: "gpt-4o", protocol: "MESSAGES"}}
        }
      },
      "CHAT_COMPLETIONS"
    );

    expect(result.status).toBe("ERROR");
    expect(result.httpStatus).toBe(400);
    expect(result.modelCode).toBe("gpt-4o");
    expect(result.protocol).toBe("MESSAGES");
    expect(result.errorMessage).toBe("规则未启用");
    expect(result.responseBody).toContain("gpt-4o");
  });

  it("包装内缺字段时回落到当前协议，而不是沿用包装外的字段", () => {
    const result = normalizeErrorResult(
      {response: {status: 500, data: {success: false, data: {}}}},
      "EMBEDDINGS"
    );

    expect(result.modelCode).toBeNull();
    expect(result.protocol).toBe("EMBEDDINGS");
    expect(result.errorMessage).toBe("请求失败");
  });

  it("业务对象：error 为对象时取其中的 message", () => {
    const result = normalizeErrorResult(
      {response: {status: 402, data: {modelCode: "claude", protocol: "MESSAGES", error: {message: "余额不足"}}}},
      null
    );

    expect(result.httpStatus).toBe(402);
    expect(result.modelCode).toBe("claude");
    expect(result.protocol).toBe("MESSAGES");
    expect(result.errorMessage).toBe("余额不足");
    expect(result.responseBody).toContain("claude");
  });

  it("业务对象：error 为字符串时直接当文案", () => {
    const result = normalizeErrorResult({response: {status: 502, data: {error: "upstream boom"}}}, "RESPONSES");

    expect(result.errorMessage).toBe("upstream boom");
    expect(result.protocol).toBe("RESPONSES");
  });

  it("fetch 网络失败：没有 response，也要给出中文提示与 Body", () => {
    const result = normalizeErrorResult({message: "Failed to fetch"}, "CHAT_COMPLETIONS");

    expect(result.status).toBe("ERROR");
    expect(result.httpStatus).toBeNull();
    expect(result.modelCode).toBeNull();
    expect(result.protocol).toBe("CHAT_COMPLETIONS");
    expect(result.errorMessage).toBe("无法连接 gateway 服务，请确认 gateway 已启动且地址可访问");
    expect(result.responseBody).toBe(result.errorMessage);
  });

  it("无当前协议时 protocol 为 null（不写成空串，避免面板显示空白协议名）", () => {
    expect(normalizeErrorResult({message: "boom"}, null).protocol).toBeNull();
  });
});

describe("normalizeNetworkErrorMessage", () => {
  it.each([
    ["", "无法连接 gateway 服务，请确认 gateway 已启动且地址可访问"],
    ["   ", "无法连接 gateway 服务，请确认 gateway 已启动且地址可访问"],
    ["Failed to fetch", "无法连接 gateway 服务，请确认 gateway 已启动且地址可访问"],
    ["NetworkError when attempting to fetch resource.", "无法连接 gateway 服务，请确认 gateway 已启动且地址可访问"],
    ["Load failed", "无法连接 gateway 服务，请确认 gateway 已启动且地址可访问"]
  ])("%s → 可操作提示", (raw, expected) => {
    expect(normalizeNetworkErrorMessage(raw)).toBe(expected);
  });

  it("其余错误保留原文并加前缀", () => {
    expect(normalizeNetworkErrorMessage("timeout of 30000ms exceeded")).toBe("网络错误：timeout of 30000ms exceeded");
  });
});

describe("serializeBody", () => {
  it("空值给空串，不是 \"null\"", () => {
    expect(serializeBody(null)).toBe("");
    expect(serializeBody(undefined)).toBe("");
    expect(serializeBody("")).toBe("");
  });

  it("字符串原样透出（SSE 原文不能被二次 JSON 化）", () => {
    expect(serializeBody("data: {}\n\n")).toBe("data: {}\n\n");
  });

  it("对象序列化为 JSON", () => {
    expect(serializeBody({a: 1})).toBe('{"a":1}');
  });
});
