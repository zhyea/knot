import {describe, expect, it, vi} from "vitest";

vi.mock("@/composables/useEnumOptions", () => ({
  useEnumOptions: () => ({
    labelOf: (enumName: string, code: unknown) => `${enumName}:${String(code)}`
  })
}));

import {useAsyncTaskPresentation} from "@/composables/useAsyncTaskPresentation";
import {AsyncTaskStatus} from "@/constants/status";

describe("useAsyncTaskPresentation 状态展示口径", () => {
  it("终态与在途态各自映射到固定标签色", () => {
    const {statusTagType} = useAsyncTaskPresentation();
    expect(statusTagType(AsyncTaskStatus.SUCCEEDED)).toBe("success");
    expect(statusTagType(AsyncTaskStatus.POLLING)).toBe("warning");
    expect(statusTagType(AsyncTaskStatus.FAILED)).toBe("danger");
  });

  it("PENDING 是数字 0，必须按合法值落到 info 而不是被 truthy 吞掉", () => {
    const {statusTagType} = useAsyncTaskPresentation();
    expect(statusTagType(AsyncTaskStatus.PENDING)).toBe("info");
    expect(statusTagType(null)).toBe("info");
  });

  it("label 走代码枚举，不在前端自建词表", () => {
    const {statusLabel} = useAsyncTaskPresentation();
    expect(statusLabel(AsyncTaskStatus.FAILED)).toBe("AsyncTaskStatusEnum:3");
  });
});
