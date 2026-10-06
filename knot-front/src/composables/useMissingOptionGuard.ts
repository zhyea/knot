import {computed, inject, provide, ref, type InjectionKey} from "vue";

/**
 * 下拉缺失值守卫：收集一棵组件树内所有 RemoteEntitySelect 上报的 missingValues。
 *
 * <p>后端 {@code missingValues} 表示「已选值不存在或当前用户无权限」。按 options 契约第 10 条，
 * 前端必须统一展示并**阻止非法提交**，不得静默伪造 {@code #id}。让 15 个下拉各自暴露 ref 再逐个
 * 校验太啰嗦，这里用 provide/inject：表单（抽屉）声明一次守卫，子孙 RemoteEntitySelect 自动上报。</p>
 *
 * <p>用法（抽屉侧）：</p>
 * <pre>
 * const missing = useMissingOptionGuard();
 * // submit 开头：
 * if (missing.hasMissing.value) { ElMessage.warning(missing.hint.value); return; }
 * </pre>
 *
 * <p>组件侧由 {@link useMissingOptionReporter} 注入；未声明守卫时上报为 no-op，
 * 因此组件仍可独立使用（如纯筛选下拉）。</p>
 */
interface MissingOptionSink {
  report: (key: string, values: string[]) => void;
}

const MISSING_OPTION_SINK: InjectionKey<MissingOptionSink> = Symbol("knot:missingOptionSink");

/**
 * 上报键形如 {@code 请选择模型#12}（label + '#' + 组件实例 uid），这里剥掉 uid 后缀还原可读名。
 * uid 后缀只用于保证键唯一，不该出现在给用户看的提示里。
 */
function readableName(key: string): string {
  const at = key.lastIndexOf("#");
  return at > 0 ? key.slice(0, at) : key;
}

export function useMissingOptionGuard() {
  const reported = ref<Map<string, string[]>>(new Map());

  provide<MissingOptionSink>(MISSING_OPTION_SINK, {
    report(key: string, values: string[]) {
      if (values.length > 0) {
        reported.value.set(key, values);
      } else {
        reported.value.delete(key);
      }
    }
  });

  /** 全部缺失项，形如「供应商: ghost-provider」。 */
  const items = computed(() => Array.from(reported.value.entries())
      .flatMap(([key, values]) => values.map((v) => `${readableName(key)}: ${v}`)));

  const count = computed(() => items.value.length);
  const hasMissing = computed(() => count.value > 0);
  const hint = computed(() => items.value.length === 0
      ? ""
      : `存在无效的已选项（${items.value.join("、")}），请重新选择后再保存`);

  return {hasMissing, count, items, hint};
}

/** RemoteEntitySelect 侧：可选注入；未声明守卫时返回 null，上报为 no-op。 */
export function useMissingOptionReporter(): MissingOptionSink | null {
  return inject(MISSING_OPTION_SINK, null);
}
