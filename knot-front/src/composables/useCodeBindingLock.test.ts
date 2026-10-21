import { describe, it, expect } from "vitest";
import { ref } from "vue";
import { useCodeBindingLock } from "@/composables/useCodeBindingLock";

// 改码锁与后端 CodeBindingSupport 同口径：未绑定（0）可改，绑定后置灰。
// 「0 是合法值」是前端 truthy 的头号风险点，这里逐条钉死归一行为。
describe("useCodeBindingLock 改码锁", () => {
  it("新建态一律可改，即便计数异常", () => {
    const { codeLocked, codeLockHint } = useCodeBindingLock(() => false, () => 5);
    expect(codeLocked.value).toBe(false);
    expect(codeLockHint.value).toBe("");
  });

  it("编辑态 + 引用数 0 → 可改（0 不能被当作 truthy 之外的假值误判）", () => {
    const { codeLocked, count } = useCodeBindingLock(() => true, () => 0);
    expect(count.value).toBe(0);
    expect(codeLocked.value).toBe(false);
  });

  it("编辑态 + 引用数 > 0 → 锁定并给出去重提示", () => {
    const { codeLocked, codeLockHint } = useCodeBindingLock(() => true, () => 3);
    expect(codeLocked.value).toBe(true);
    expect(codeLockHint.value).toContain("3");
    expect(codeLockHint.value).toContain("解除绑定");
  });

  it("引用数为 null / undefined / 非数字时按 0 处理", () => {
    for (const raw of [null, undefined, "", "abc", NaN]) {
      const { count, codeLocked } = useCodeBindingLock(() => true, () => raw);
      expect(count.value).toBe(0);
      expect(codeLocked.value).toBe(false);
    }
  });

  it("引用数为数字字符串时照常识别（后端 JSON 可能下发字符串）", () => {
    const { count, codeLocked } = useCodeBindingLock(() => true, () => "2");
    expect(count.value).toBe(2);
    expect(codeLocked.value).toBe(true);
  });

  it("负数按 0 处理，不产生「负引用也算绑定」的怪状态", () => {
    const { count, codeLocked } = useCodeBindingLock(() => true, () => -1);
    expect(count.value).toBe(0);
    expect(codeLocked.value).toBe(false);
  });

  it("计数与编辑态是响应式的：详情异步回填后锁自动生效", () => {
    const isEdit = ref(true);
    const bound = ref<unknown>(0);
    const { codeLocked } = useCodeBindingLock(() => isEdit.value, () => bound.value);
    expect(codeLocked.value).toBe(false);
    bound.value = 4;
    expect(codeLocked.value).toBe(true);
  });
});
