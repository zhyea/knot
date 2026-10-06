/**
 * 受控草稿：本地 reactive 副本 + 双向同步（父→子、子→父），用「normalize 后的结构比较」阻断回环。
 *
 * <p>Section 类组件（重试策略 / 频控额度）用本地 draft 承接控件 v-model，同时要把结果回写给父级，
 * 双向 watch 天然成环，故必须有一个「是否真的变了」的守卫。</p>
 *
 * <p>⚠ 守卫**不能**手写字段清单：那是字段清单的第二份真相，与 interface / default / normalize 三处重复，
 * 加字段必漏且 TS 不报错（2026-10-06 `RetryPolicySection` 漏比 `mode`，导致深度模式切换不 emit、
 * 配置根本没提交）。这里改为两侧都过 normalize 后做结构比较——字段增减零维护，key 顺序由
 * normalize 的对象字面量固定，`JSON.stringify` 结果稳定。</p>
 *
 * <p>子→父 emit 的是 normalize 的输出（纯对象、数组已复制），不把 reactive 代理泄漏给父级。</p>
 */

import {reactive, watch} from "vue";

/**
 * @param source    读父级值的 getter（watch 源）
 * @param normalize 规范化：负责补默认、收敛越界、过滤非法项，同时充当结构比较的口径
 * @param emit      回写父级
 * @returns 本地草稿，可直接给模板 v-model
 */
export function useDraftModel<T extends object>(
  source: () => unknown,
  normalize: (raw: unknown) => T,
  emit: (value: T) => void
): T {
  const draft = reactive(normalize(source())) as T;
  const keyOf = (value: unknown): string => JSON.stringify(normalize(value));

  watch(source,
    value => {
      if (keyOf(value) !== keyOf(draft)) {
        Object.assign(draft, normalize(value));
      }
    },
    {immediate: true, deep: true});

  watch(draft,
    value => {
      const next = normalize(value);
      if (keyOf(source()) !== keyOf(next)) {
        emit(next);
      }
    },
    {deep: true});

  return draft;
}
