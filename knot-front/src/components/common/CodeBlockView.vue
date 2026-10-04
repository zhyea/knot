<template>
  <div class="code-block-view" :class="[`code-block-view--${theme}`]">
    <div v-if="copyable || $slots.actions" class="code-block-view__actions">
      <slot name="actions" />
      <el-button
        v-if="copyable"
        class="code-block-view__copy"
        size="small"
        text
        type="primary"
        @click="onCopy"
      >复制</el-button>
    </div>
    <el-scrollbar class="code-block-view__scrollbar" :style="scrollStyle">
      <div class="code-block-view__body">
        <slot />
      </div>
    </el-scrollbar>
  </div>
</template>

<script setup lang="ts">
import {computed} from "vue";
import {useCopyToClipboard} from "@/composables/useCopyToClipboard";

const props = withDefaults(
  defineProps<{
    /** 待复制的原始文本（非高亮 HTML），为空时行为由 emptyMessage 决定 */
    copyText?: string;
    copyable?: boolean;
    /** dark：深色代码面板（如 shell/json 终端）；light：浅色面板 */
    theme?: "dark" | "light";
    minHeight?: string;
    maxHeight?: string;
    /** 固定高度；仅设 max-height 时 el-scrollbar 无法稳定计算滚动区域 */
    height?: string;
    /** 复制内容为空时的提示；留空则静默（用于原 ShellCodeBlock 行为） */
    emptyMessage?: string;
  }>(),
  {
    copyText: "",
    copyable: true,
    theme: "light",
    minHeight: "",
    maxHeight: "",
    height: "",
    emptyMessage: ""
  }
);

const {copy} = useCopyToClipboard();

const scrollStyle = computed(() => {
  const style: Record<string, string> = {};
  if (props.minHeight) {
    style.minHeight = props.minHeight;
  }
  if (props.maxHeight) {
    style.maxHeight = props.maxHeight;
  }
  if (props.height) {
    style.height = props.height;
  }
  return style;
});

async function onCopy() {
  await copy(props.copyText, props.emptyMessage || undefined);
}
</script>

<style scoped>
.code-block-view {
  position: relative;
  width: 100%;
  display: flex;
  flex-direction: column;
  min-height: 0;
}

.code-block-view__actions {
  position: absolute;
  top: 8px;
  right: 8px;
  z-index: 3;
  display: flex;
  align-items: center;
  gap: 4px;
  padding: 2px 4px;
  border-radius: 6px;
  background: rgb(255 255 255 / 88%);
  backdrop-filter: blur(4px);
}

.code-block-view--dark .code-block-view__actions {
  background: rgb(30 30 30 / 88%);
}

.code-block-view__copy {
  min-height: 24px;
  padding: 4px 8px;
}

.code-block-view__scrollbar {
  width: 100%;
}

.code-block-view__body {
  position: relative;
  min-height: 100%;
}
</style>
