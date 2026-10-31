import { computed } from "vue";

/**
 * 业务编码「改码锁」：与后端 `CodeBindingSupport` 同一口径 ——
 * 新建时永远可填；编辑时只要该编码被任意地方引用（boundCount > 0）就置灰。
 *
 * @param isEdit 是否编辑态（新建态不锁）
 * @param boundCount 后端下发的引用处数（可能为 null/undefined/字符串）
 */
export function useCodeBindingLock(isEdit: () => boolean, boundCount: () => unknown) {
  /** 引用处数归一：null / undefined / 非数字一律按 0 处理，避免「0 是合法值」被 truthy 判断吃掉 */
  const count = computed(() => {
    const value = Number(boundCount());
    return Number.isFinite(value) && value > 0 ? value : 0;
  });

  const codeLocked = computed(() => Boolean(isEdit()) && count.value > 0);

  const codeLockHint = computed(() =>
    codeLocked.value ? `已被 ${count.value} 处引用，需先解除绑定才能修改编码` : ""
  );

  return { count, codeLocked, codeLockHint };
}
