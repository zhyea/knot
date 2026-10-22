<template>
  <TreeSelect
    v-bind="$attrs"
    :model-value="modelValue"
    :options="treeOptions"
    :loading="loading"
    :disabled="disabled"
    :placeholder="placeholder"
    filterable
    clearable
    leaf-only
    @update:model-value="onUpdate"
    @change="onChange"
  />
</template>

<script setup lang="ts">
import {computed, onMounted, ref, watch, type PropType} from "vue";
import TreeSelect from "../common/TreeSelect.vue";
import {listProviderAccountOptions} from "@/api/options";
import type {OptionItem, ProviderAccountMeta, TreeNodeOption} from "@/types";

defineOptions({inheritAttrs: false});

/**
 * 供应商账户两级树下拉：父行=供应商（只展开/折叠），子行=账户（选中值恒为账户 code）。
 *
 * <p>取代旧 {@code ProviderAccountSelect}（RemoteEntitySelect 平铺口径，label 靠
 * "供应商 / 账户码"斜杠拼接人眼分组）。旧组件随本组件接入后删除。</p>
 *
 * <p>数据策略：账户是配置型数据（个位数供应商 × 少量账户），onMounted 翻页聚合全量
 * （单页上限 50 = 后端 OptionQuery.MAX_PAGE_SIZE）后交给 TreeSelect 本地过滤；
 * 已选账户经 values 单查回显——enabledOnly=false 才能带回禁用账户（旧组件同路径），
 * 合并进树后禁用账户可见但禁选，不存在"已选项从树上消失"的退化。</p>
 */
const props = defineProps({
  modelValue: {type: [String, Number] as PropType<string | number | null>, default: null},
  placeholder: {type: String, default: "请选择供应商账户"},
  disabled: {type: Boolean, default: false},
  /** 仅列启用账户，默认 true（与旧 ProviderAccountSelect 的 extraParams 一致） */
  enabledOnly: {type: Boolean, default: true}
});

const emit = defineEmits(["update:modelValue", "change"]);

const ACCOUNT_PAGE_SIZE = 50;

const loading = ref(false);
const enabledItems = ref<OptionItem<ProviderAccountMeta>[]>([]);
/** 已选账户回显项（含禁用账户；全量列表 enabledOnly 会过滤掉它们） */
const echoItems = ref<OptionItem<ProviderAccountMeta>[]>([]);
/** 回显请求序号：modelValue 连续变化时丢弃过期响应，防止旧值覆盖新值 */
let echoSeq = 0;

/** 回显项优先（带禁用态），全量项补全；按 value 去重。 */
const mergedItems = computed(() => {
  const map = new Map<string, OptionItem<ProviderAccountMeta>>();
  for (const item of echoItems.value) {
    map.set(String(item.value), item);
  }
  for (const item of enabledItems.value) {
    map.set(String(item.value), item);
  }
  return Array.from(map.values());
});

const treeOptions = computed<TreeNodeOption<ProviderAccountMeta>[]>(() => {
  const groups = new Map<string, TreeNodeOption<ProviderAccountMeta>>();
  for (const item of mergedItems.value) {
    const providerCode = item.meta?.providerCode || "unknown";
    const providerName = item.meta?.providerName || providerCode;
    let parent = groups.get(providerCode);
    if (!parent) {
      parent = {value: providerCode, label: providerName, children: []};
      groups.set(providerCode, parent);
    }
    if (!parent.children) {
      parent.children = [];
    }
    parent.children.push({
      value: item.value,
      // 子行只展示账户编码（供应商信息由父行承载；robin 2026-10-22 拍板）
      label: String(item.value),
      disabled: item.disabled === true,
      meta: item.meta ?? null
    });
  }
  return Array.from(groups.values());
});

async function loadEnabled(): Promise<void> {
  loading.value = true;
  try {
    const items: OptionItem<ProviderAccountMeta>[] = [];
    let pageNum = 1;
    let total = Number.POSITIVE_INFINITY;
    while (items.length < total) {
      const page = await listProviderAccountOptions({
        pageNum,
        pageSize: ACCOUNT_PAGE_SIZE,
        enabledOnly: props.enabledOnly
      });
      total = page.total;
      items.push(...(page.list || []));
      if (!page.list || page.list.length === 0) {
        break;
      }
      pageNum += 1;
    }
    enabledItems.value = items;
  } finally {
    loading.value = false;
  }
}

/** 已选账户 values 单查回显；enabledOnly=false 才能拿到禁用账户（byValues SQL 带禁用标记）。 */
async function loadEcho(value: string | number): Promise<void> {
  const seq = ++echoSeq;
  const page = await listProviderAccountOptions({values: [String(value)], enabledOnly: false});
  if (seq !== echoSeq) {
    return;
  }
  echoItems.value = page.list || [];
}

watch(
  () => props.modelValue,
  (value) => {
    if (value == null || value === "") {
      echoItems.value = [];
      return;
    }
    const covered = mergedItems.value.some((item) => String(item.value) === String(value));
    if (!covered) {
      loadEcho(value);
    }
  },
  {immediate: true}
);

onMounted(() => {
  loadEnabled();
});

function onUpdate(value: unknown): void {
  emit("update:modelValue", value);
}

/** change 签名与旧 ProviderAccountSelect 对齐：(account | null, value)——调用方只消费 account.meta.baseUrl。 */
function onChange(value: unknown, selected: unknown): void {
  emit("change", (selected as TreeNodeOption<ProviderAccountMeta> | null) ?? null, value);
}
</script>
