<template>
  <el-popover
    v-model:visible="dropdownVisible"
    trigger="click"
    placement="bottom-start"
    :hide-after="0"
    :show-arrow="false"
    popper-class="tree-select__popper"
    @hide="onDropdownHide"
  >
    <template #reference>
      <div ref="referenceRef" class="tree-select">
        <el-input
          v-bind="$attrs"
          :model-value="triggerDisplay"
          :placeholder="placeholder"
          :readonly="!filterable"
          :disabled="disabled"
          :size="size"
          :title="triggerTitle"
          @update:model-value="onTriggerInput"
          @keydown.enter.prevent="openDropdown"
          @keydown.esc="closeDropdown"
        >
          <template v-if="multiple && selectedTags.length > 0" #prefix>
            <span class="tree-select__tags">
              <el-tag
                v-for="tag in selectedTags"
                :key="String(tag.value)"
                size="small"
                type="info"
                disable-transitions
              >
                {{ tag.label }}
              </el-tag>
            </span>
          </template>
          <template #suffix>
            <el-icon v-if="clearable && hasValue && !disabled" class="tree-select__clear" @click.stop="clearValue">
              <CircleClose />
            </el-icon>
            <el-icon v-else class="tree-select__arrow" :class="{'is-reverse': dropdownVisible}">
              <ArrowDown />
            </el-icon>
          </template>
        </el-input>
      </div>
    </template>

    <div class="tree-select__panel" :style="panelStyle" v-loading="loading">
      <el-scrollbar max-height="288px">
        <div v-if="visibleParents.length === 0" class="tree-select__empty">暂无数据</div>
        <div v-for="row in visibleParents" :key="String(row.node.value)" class="tree-select__group">
          <!-- 父级行：leafOnly 且无关键字时只负责展开/折叠；否则可像叶子一样选中 -->
          <div
            class="tree-select__row tree-select__row--parent"
            :class="{
              'is-disabled': row.node.disabled === true,
              'is-selectable': isParentSelectable(row),
              'is-selected': isParentSelectable(row) && isSelected(row.node.value)
            }"
            @click="onParentClick(row)"
          >
            <el-icon class="tree-select__caret" :class="{'is-expanded': row.expanded}">
              <ArrowRight />
            </el-icon>
            <span class="tree-select__label">
              <template v-for="(part, index) in highlightParts(row.node.label)" :key="index">
                <span v-if="part.hit" class="tree-select__hit">{{ part.text }}</span>
                <template v-else>{{ part.text }}</template>
              </template>
            </span>
          </div>
          <!-- 不用 <transition> 包裹 v-show：异步 options 多次到达（回显+全量）会打断 enter 过渡，
               enter-from（max-height:0/opacity:0）类残留导致子级高度为 0 不可见（robin 2026-10-23 实测）。
               展开动画改由 caret 旋转承担，children 容器直切。 -->
          <div v-show="row.expanded" class="tree-select__children">
              <div
                v-for="child in row.children"
                :key="String(child.value)"
                class="tree-select__row tree-select__row--child"
                :class="{'is-disabled': child.disabled === true, 'is-selected': isSelected(child.value)}"
                @click="pickNode(child)"
              >
                <span
                  v-if="multiple"
                  class="tree-select__checkbox"
                  :class="{'is-checked': isSelected(child.value)}"
                >
                  <el-icon v-if="isSelected(child.value)"><Check /></el-icon>
                </span>
                <span class="tree-select__label">
                  <template v-for="(part, index) in highlightParts(child.label)" :key="index">
                    <span v-if="part.hit" class="tree-select__hit">{{ part.text }}</span>
                    <template v-else>{{ part.text }}</template>
                  </template>
                </span>
              </div>
            </div>
          </div>
      </el-scrollbar>
    </div>
  </el-popover>
</template>

<script setup lang="ts">
import {computed, onBeforeUnmount, onMounted, ref, watch, type CSSProperties, type PropType} from "vue";
import {ArrowDown, ArrowRight, Check, CircleClose} from "@element-plus/icons-vue";
import type {TreeNodeOption} from "@/types";

defineOptions({inheritAttrs: false});

/** 单选绑定值；多选绑定值数组。与 EnumSelect 的 modelValue 口径一致。 */
type TreeSelectValue = string | number | (string | number)[] | null;

interface LabelPart {
  text: string;
  /** 是否为关键字命中片段（渲染高亮） */
  hit: boolean;
}

/** 过滤/展开后的父级行视图：children 已按关键字收窄，expanded 区分过滤态与受控展开态。 */
interface VisibleParentRow {
  node: TreeNodeOption;
  children: TreeNodeOption[];
  /** 父级自身 label 命中关键字（过滤模式下允许直接选中父级） */
  matchSelf: boolean;
  expanded: boolean;
}

const props = defineProps({
  modelValue: {type: [String, Number, Array] as PropType<TreeSelectValue>, default: null},
  /** 两级树数据：父级 children 为 null/缺省即叶子 */
  options: {type: Array as PropType<TreeNodeOption[]>, required: true},
  multiple: {type: Boolean, default: false},
  filterable: {type: Boolean, default: false},
  /** 仅叶子可选（父级行只展开/折叠）。false 时父级也可直接选中 */
  leafOnly: {type: Boolean, default: true},
  /** 默认展开全部父级（异步到达的 options 也会补展开）；用户可手动折叠 */
  defaultExpandAll: {type: Boolean, default: true},
  clearable: {type: Boolean, default: false},
  disabled: {type: Boolean, default: false},
  loading: {type: Boolean, default: false},
  placeholder: {type: String, default: "请选择"},
  size: {type: String as PropType<"large" | "default" | "small">, default: undefined},
  /** 面板固定宽度；缺省时最小与触发器同宽、内容更宽时自适应撑开（上限 480px） */
  panelWidth: {type: [String, Number] as PropType<string | number>, default: undefined}
});

const emit = defineEmits<{
  (e: "update:modelValue", value: TreeSelectValue): void;
  (e: "change", value: TreeSelectValue, selected: TreeNodeOption | TreeNodeOption[] | null): void;
}>();

const dropdownVisible = ref(false);
const keyword = ref("");
const expandedKeys = ref<Set<string>>(new Set());
/**
 * 用户手动折叠过的父级：默认展开补集时跳过这些 key。
 *
 * <p>否则父组件异步刷新 options（如账户树回显项合并后 treeOptions 重算）会把用户刚折叠的组又强行展开。</p>
 */
const manuallyCollapsed = ref<Set<string>>(new Set());
const referenceRef = ref<HTMLElement | null>(null);
const measuredWidth = ref<number | undefined>(undefined);

/**
 * 面板宽度策略：显式传 panelWidth 则固定；否则最小与触发器同宽、内容更宽时在 480px 内
 * 自适应撑开——固定死绑触发器宽度会把 label 压成 ellipsis 省略号。
 *
 * <p>面板 teleported 到 body，组件根元素的 CSS 变量传不进去，故走内联 style（而非 popper-style）。</p>
 */
const panelStyle = computed<CSSProperties>(() => {
  if (props.panelWidth) {
    return {width: typeof props.panelWidth === "number" ? `${props.panelWidth}px` : String(props.panelWidth)};
  }
  return {
    minWidth: measuredWidth.value ? `${measuredWidth.value}px` : undefined,
    maxWidth: "480px"
  };
});
const keywordTrim = computed(() => (props.filterable ? keyword.value.trim() : ""));

/**
 * 默认展开：options 常由父组件异步加载（如账户树 onMounted 翻页聚合），故不能只在挂载时
 * 展开一次——改为侦听 options 引用变化，把新出现的父级补进展开集（用户已折叠的组除外）。
 */
watch(
  () => props.options,
  (options) => {
    if (!props.defaultExpandAll) {
      return;
    }
    const keys = new Set(expandedKeys.value);
    for (const parent of options || []) {
      const key = String(parent.value);
      if (manuallyCollapsed.value.has(key)) {
        continue;
      }
      keys.add(key);
    }
    expandedKeys.value = keys;
  },
  {immediate: true}
);

function currentValues(): (string | number)[] {
  const mv = props.modelValue;
  if (Array.isArray(mv)) {
    return mv.filter((v) => v != null && v !== "");
  }
  if (mv != null && mv !== "") {
    return [mv];
  }
  return [];
}

const hasValue = computed(() => currentValues().length > 0);

function findNodeByValue(value: string | number): TreeNodeOption | null {
  const key = String(value);
  for (const parent of props.options || []) {
    if (String(parent.value) === key) {
      return parent;
    }
    for (const child of parent.children || []) {
      if (String(child.value) === key) {
        return child;
      }
    }
  }
  return null;
}

function isSelected(value: string | number): boolean {
  const key = String(value);
  if (props.multiple) {
    return currentValues().some((v) => String(v) === key);
  }
  return hasValue.value && String(props.modelValue) === key;
}

/** 单选回显 label；找不到（已删除/无权限）降级为裸 value 并挂 title 提示。 */
const selectedLabel = computed(() => {
  const mv = props.modelValue;
  if (mv == null || mv === "" || Array.isArray(mv)) {
    return "";
  }
  return findNodeByValue(mv)?.label ?? String(mv);
});

const triggerTitle = computed(() => {
  if (props.multiple || keywordTrim.value || !hasValue.value) {
    return undefined;
  }
  return findNodeByValue(props.modelValue as string | number) ? undefined : "选项不存在或已被删除";
});

/** 多选 tags：同样对缺失值降级展示裸 value，不静默吞掉。 */
const selectedTags = computed(() =>
  currentValues().map((value) => ({
    value,
    label: findNodeByValue(value)?.label ?? String(value)
  }))
);

/**
 * 触发器文本：
 * <ul>
 *   <li>多选：tags 在 prefix slot 渲染，输入框本体只承载关键字；</li>
 *   <li>单选 + filterable 且下拉已开：显示关键字（与 el-select filterable 行为一致）；</li>
 *   <li>其余：显示已选 label。</li>
 * </ul>
 */
const triggerDisplay = computed(() => {
  if (props.filterable && dropdownVisible.value) {
    return keyword.value;
  }
  if (props.multiple) {
    return "";
  }
  return selectedLabel.value;
});

function matchText(label: string, kw: string): boolean {
  return label.toLowerCase().includes(kw.toLowerCase());
}

/** 按关键字切分 label 供模板高亮——不用 v-html，避免后端文本直插 innerHTML 的 XSS 面。 */
function highlightParts(label: string): LabelPart[] {
  const kw = keywordTrim.value;
  if (!kw) {
    return [{text: label, hit: false}];
  }
  const lowerLabel = label.toLowerCase();
  const lowerKw = kw.toLowerCase();
  const parts: LabelPart[] = [];
  let cursor = 0;
  let idx = lowerLabel.indexOf(lowerKw, cursor);
  while (idx !== -1) {
    if (idx > cursor) {
      parts.push({text: label.slice(cursor, idx), hit: false});
    }
    parts.push({text: label.slice(idx, idx + kw.length), hit: true});
    cursor = idx + kw.length;
    idx = lowerLabel.indexOf(lowerKw, cursor);
  }
  if (cursor < label.length) {
    parts.push({text: label.slice(cursor), hit: false});
  }
  return parts;
}

const visibleParents = computed<VisibleParentRow[]>(() => {
  const kw = keywordTrim.value;
  const rows: VisibleParentRow[] = [];
  for (const parent of props.options || []) {
    const children = parent.children || [];
    const matchSelf = !!kw && matchText(parent.label, kw);
    // keyword 命中父级自身时，其子级全量展示（不过滤）——否则「按供应商名搜」会得到
    // 一个展开后空组的父行（账户 label 不含供应商名）；父未命中时才按 keyword 收窄子级。
    const matchedChildren = kw && !matchSelf ? children.filter((child) => matchText(child.label, kw)) : children;
    // 有关键字时，父子均未命中的父级整组隐藏
    if (kw && !matchSelf && matchedChildren.length === 0) {
      continue;
    }
    // 有关键字时命中路径全展开；无关键字时走受控展开态
    const expanded = kw ? true : expandedKeys.value.has(String(parent.value));
    rows.push({node: parent, children: kw ? matchedChildren : children, matchSelf, expanded});
  }
  return rows;
});

/**
 * 父级行是否可选中：leafOnly=false 时任意节点可选；
 * 过滤模式下用户已直接搜到该父级，也放开（否则「搜得到却选不中」）。
 */
function isParentSelectable(row: VisibleParentRow): boolean {
  if (row.node.disabled === true) {
    return false;
  }
  return props.leafOnly !== true || (!!keywordTrim.value && row.matchSelf);
}

function toggleExpand(parent: TreeNodeOption): void {
  const key = String(parent.value);
  const next = new Set(expandedKeys.value);
  const collapsed = new Set(manuallyCollapsed.value);
  if (next.has(key)) {
    next.delete(key);
    collapsed.add(key);
  } else {
    next.add(key);
    collapsed.delete(key);
  }
  expandedKeys.value = next;
  manuallyCollapsed.value = collapsed;
}

function onParentClick(row: VisibleParentRow): void {
  if (row.node.disabled === true) {
    return;
  }
  if (isParentSelectable(row)) {
    pickNode(row.node);
    return;
  }
  toggleExpand(row.node);
}

function pickNode(node: TreeNodeOption): void {
  if (node.disabled === true) {
    return;
  }
  if (props.multiple) {
    const key = String(node.value);
    const current = currentValues();
    const next = current.some((v) => String(v) === key)
      ? current.filter((v) => String(v) !== key)
      : [...current, node.value];
    emit("update:modelValue", next);
    emit("change", next, next.map((value) => findNodeByValue(value) ?? {value, label: String(value)}));
    return;
  }
  keyword.value = "";
  emit("update:modelValue", node.value);
  emit("change", node.value, node);
  closeDropdown();
}

function clearValue(): void {
  keyword.value = "";
  emit("update:modelValue", props.multiple ? [] : null);
  emit("change", props.multiple ? [] : null, props.multiple ? [] : null);
}

function onTriggerInput(value: string): void {
  if (props.filterable) {
    keyword.value = value;
  }
}

function openDropdown(): void {
  if (!props.disabled) {
    dropdownVisible.value = true;
  }
}

function closeDropdown(): void {
  dropdownVisible.value = false;
}

/** 关闭即清空关键字：下次打开是全量两级视图，不残留上次的过滤态。 */
function onDropdownHide(): void {
  keyword.value = "";
}

function measure(): void {
  measuredWidth.value = referenceRef.value?.offsetWidth ?? undefined;
}

onMounted(() => {
  measure();
  window.addEventListener("resize", measure);
});

onBeforeUnmount(() => {
  window.removeEventListener("resize", measure);
});
</script>

<style>
/*
 * 不写 scoped：popover 面板 teleported 到 body，scoped 的 data-v 选择器命中不到。
 * 全部类名以 .tree-select__ 前缀限定，避免全局污染。
 */
.tree-select {
  width: 100%;
}

.tree-select__tags {
  display: inline-flex;
  flex-wrap: wrap;
  gap: 4px;
  max-width: 100%;
}

.tree-select__clear {
  cursor: pointer;
  color: var(--el-text-color-placeholder, #a8abb2);
}

.tree-select__clear:hover {
  color: var(--el-text-color-secondary, #909399);
}

.tree-select__arrow {
  color: var(--el-text-color-placeholder, #a8abb2);
  transition: transform 0.2s;
}

.tree-select__arrow.is-reverse {
  transform: rotate(180deg);
}

.tree-select__popper .tree-select__panel {
  padding: 4px 0;
}

.tree-select__row {
  display: flex;
  align-items: center;
  gap: 6px;
  height: 28px;
  padding: 0 12px;
  font-size: 12px;
  color: var(--el-text-color-regular, #606266);
  cursor: pointer;
}

.tree-select__row:hover {
  background: var(--el-fill-color-light, #f5f7fa);
}

.tree-select__row--child {
  padding-left: 30px;
}

.tree-select__row.is-selected {
  color: var(--el-color-primary, #409eff);
  font-weight: 500;
}

.tree-select__row.is-disabled {
  color: var(--el-text-color-disabled, #c0c4cc);
  cursor: not-allowed;
}

.tree-select__row.is-disabled:hover {
  background: transparent;
}

.tree-select__row.is-disabled .tree-select__caret {
  cursor: not-allowed;
}

.tree-select__caret {
  flex: none;
  font-size: 12px;
  color: var(--el-text-color-placeholder, #a8abb2);
  transition: transform 0.18s;
}

.tree-select__caret.is-expanded {
  transform: rotate(90deg);
}

.tree-select__checkbox {
  flex: none;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 14px;
  height: 14px;
  border: 1px solid var(--el-border-color, #dcdfe6);
  border-radius: 2px;
  font-size: 12px;
  color: #ffffff;
}

.tree-select__checkbox.is-checked {
  background: var(--el-color-primary, #409eff);
  border-color: var(--el-color-primary, #409eff);
}

.tree-select__label {
  /* min-width:0 是 flex 子项 ellipsis 生效的前提；面板宽度自适应后正常不触发省略 */
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.tree-select__hit {
  color: var(--el-color-primary, #409eff);
  font-weight: 600;
}

.tree-select__children {
  overflow: hidden;
}

/*
 * 面板宽度自适应：EP scrollbar 的 wrap 是 overflow 容器，会切断内容宽度传播——
 * 内容超宽时只在 wrap 内部横向滚动，撑不开 popper（子节点文本被横向截断）。
 * 给 wrap 设 width:max-content 让内容宽反向撑开 popper；min-width:100% 防内容窄时塌缩。
 */
.tree-select__popper .el-scrollbar__wrap {
  width: max-content;
  min-width: 100%;
  overflow-x: hidden;
  overflow-y: auto;
}

.tree-select__empty {
  padding: 16px 0;
  text-align: center;
  font-size: 12px;
  color: var(--el-text-color-secondary, #909399);
}
</style>
