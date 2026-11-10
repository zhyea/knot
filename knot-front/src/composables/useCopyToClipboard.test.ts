import {describe, expect, it, vi, beforeEach} from "vitest";

vi.mock("element-plus", () => ({
  ElMessage: {success: vi.fn(), error: vi.fn(), warning: vi.fn()}
}));

import {ElMessage} from "element-plus";
import {useCopyToClipboard} from "@/composables/useCopyToClipboard";

const writeText = vi.fn();

beforeEach(() => {
  vi.clearAllMocks();
  Object.defineProperty(navigator, "clipboard", {
    value: {writeText},
    configurable: true
  });
});

describe("useCopyToClipboard 复制反馈", () => {
  it("默认成功提示为「已复制」", async () => {
    writeText.mockResolvedValue(undefined);
    const {copy} = useCopyToClipboard();
    await expect(copy("hello")).resolves.toBe(true);
    expect(writeText).toHaveBeenCalledWith("hello");
    expect(ElMessage.success).toHaveBeenCalledWith("已复制");
  });

  it("可在 hook 层定制成功提示（API Key 场景）", async () => {
    writeText.mockResolvedValue(undefined);
    const {copy} = useCopyToClipboard({successMessage: "已复制 API Key"});
    await copy("sk-xxx");
    expect(ElMessage.success).toHaveBeenCalledWith("已复制 API Key");
  });

  it("空文本静默返回 false；带 emptyMessage 时给出警告", async () => {
    const {copy} = useCopyToClipboard();
    await expect(copy(null)).resolves.toBe(false);
    expect(writeText).not.toHaveBeenCalled();
    await expect(copy(undefined, "该区块暂无内容")).resolves.toBe(false);
    expect(ElMessage.warning).toHaveBeenCalledWith("该区块暂无内容");
  });

  it("写入失败提示「复制失败」并返回 false", async () => {
    writeText.mockRejectedValue(new Error("denied"));
    const {copy} = useCopyToClipboard();
    await expect(copy("hello")).resolves.toBe(false);
    expect(ElMessage.error).toHaveBeenCalledWith("复制失败");
  });
});
