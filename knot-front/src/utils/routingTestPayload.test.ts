import {describe, expect, it} from "vitest";
import {
  extractField,
  extractTextValue,
  findObjectField,
  findSiblingField,
  formatUpstreamEvent,
  highlightBlock,
  isAbortError,
  parseStreamJson,
  safeParseTemplate,
  stripModelField
} from "./routingTestPayload";

/**
 * 路由测试抽屉的负载解析原语（改进清单 3.6 第二步）。
 *
 * <p>这里钉的是「同一语义在多种协议形态下都能掏出来」——网关测试链路是透传调试，
 * 上游形态随供应商变化，逐个协议硬编码路径会在遇到新变体时静默丢内容。</p>
 */
describe("extractField", () => {
  it("chat 流式 delta 与缓冲 message 都能取到 content", () => {
    expect(extractField({choices: [{delta: {content: "你"}}]}, "content")).toBe("你");
    expect(extractField({choices: [{message: {content: "你好"}}]}, "content")).toBe("你好");
  });

  it("completions 的 choices[].text 由调用方按 text 键取", () => {
    expect(extractField({choices: [{text: "补全"}]}, "text")).toBe("补全");
  });

  it("Anthropic / Responses 的 content 块数组拼接", () => {
    expect(extractField({content: [{type: "text", text: "A"}, {type: "text", text: "B"}]}, "content")).toBe("AB");
  });

  it("reasoning_content 与 content 分开取，互不串台", () => {
    const payload = {choices: [{delta: {content: "正文", reasoning_content: "思考"}}]};
    expect(extractField(payload, "content")).toBe("正文");
    expect(extractField(payload, "reasoning_content")).toBe("思考");
  });

  it("多个同名节点按出现顺序拼接", () => {
    expect(extractField({a: {content: "1"}, b: {content: "2"}}, "content")).toBe("12");
  });

  it("非对象输入给空串（不抛）", () => {
    expect(extractField(null, "content")).toBe("");
    expect(extractField("text", "content")).toBe("");
  });
});

describe("extractTextValue", () => {
  it("字符串原样、块数组拼 text、其余空串", () => {
    expect(extractTextValue("raw")).toBe("raw");
    expect(extractTextValue(["a", {text: "b"}, {foo: 1}])).toBe("ab");
    expect(extractTextValue({text: "x"})).toBe("");
    expect(extractTextValue(42)).toBe("");
  });
});

describe("findObjectField / findSiblingField", () => {
  it("findObjectField 深挖到任意层级的字段", () => {
    expect(findObjectField({response: {knot_extend: {usage: 1}}}, "knot_extend")).toEqual({usage: 1});
  });

  it("findObjectField 接受 JSON 字符串，非法 JSON 返回 null", () => {
    expect(findObjectField('{"knot_extend":{"oss":1}}', "knot_extend")).toEqual({oss: 1});
    expect(findObjectField("{bad json", "knot_extend")).toBeNull();
  });

  it("findSiblingField 只在含 anchor 的那一层取兄弟字段", () => {
    const payload = {outer: {knot_extend: {x: 1}, usage: {input_tokens: 5}}};
    expect(findSiblingField(payload, "knot_extend", "usage")).toEqual({input_tokens: 5});
  });

  it("兄弟字段不在同一层时返回 null（不能跨层误取）", () => {
    const payload = {knot_extend: {x: 1}, other: {usage: {input_tokens: 5}}};
    expect(findSiblingField(payload, "knot_extend", "usage")).toBeNull();
  });
});

describe("parseStreamJson", () => {
  it("data: 前缀可有可无", () => {
    expect(parseStreamJson("data: {\"a\":1}").value).toEqual({a: 1});
    expect(parseStreamJson("{\"a\":1}").value).toEqual({a: 1});
  });

  it("非法 JSON 返回 error 而不是抛", () => {
    expect(parseStreamJson("data: {oops").error).toBeTruthy();
  });

  it("[DONE] 这类非 JSON 事件也走 error 分支", () => {
    expect(parseStreamJson("data: [DONE]").error).toBeTruthy();
  });
});

describe("stripModelField", () => {
  it("剔除 model，其余字段原样保留", () => {
    expect(stripModelField({model: "gpt-4o", temperature: 0.2})).toEqual({temperature: 0.2});
  });

  it("非对象一律给空对象，避免把字符串当请求体发出去", () => {
    expect(stripModelField(null)).toEqual({});
    expect(stripModelField("body")).toEqual({});
    expect(stripModelField(["a"])).toEqual({});
  });
});

describe("safeParseTemplate", () => {
  it("空文本给空对象且无错误", () => {
    expect(safeParseTemplate("")).toEqual({value: {}, error: null});
  });

  it("非法 JSON 保留错误，调用方据此提示而不是发空体", () => {
    const parsed = safeParseTemplate("{oops");
    expect(parsed.error).toBeTruthy();
    expect(parsed.value).toEqual({});
  });
});

describe("formatUpstreamEvent", () => {
  it("message 是默认事件名，不写 event 行", () => {
    expect(formatUpstreamEvent({event: "message", data: "{}"})).toBe("data: {}\n\n");
  });

  it("具名事件保留 event 行", () => {
    expect(formatUpstreamEvent({event: "meta", data: "{}"})).toBe("event: meta\ndata: {}\n\n");
  });

  it("无事件名只写 data", () => {
    expect(formatUpstreamEvent({data: "x"})).toBe("data: x\n\n");
  });
});

describe("highlightBlock", () => {
  it("空内容给占位，不渲染空块", () => {
    expect(highlightBlock("")).toContain("（空）");
  });

  it("JSON 文本被高亮成 span（内部已转义）", () => {
    const html = highlightBlock('{"a":"<b>"}');
    expect(html).toContain("json-key");
    expect(html).not.toContain("<b>");
  });
});

describe("isAbortError", () => {
  it("只认 AbortError", () => {
    expect(isAbortError({name: "AbortError"})).toBe(true);
    expect(isAbortError(new DOMException("x", "AbortError"))).toBe(true);
    expect(isAbortError({name: "TypeError"})).toBe(false);
    expect(isAbortError(null)).toBe(false);
  });
});
