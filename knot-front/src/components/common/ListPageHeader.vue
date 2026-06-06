<template>
  <div ref="rootRef" class="list-page-header">
    <div class="list-page-header__main">
      <div v-if="hasFilters" class="list-page-header__filters">
        <div
          class="list-page-header__filters-viewport"
          :style="{ maxHeight: `${currentMaxHeight}px` }"
        >
          <div ref="contentRef" class="list-page-header__filters-content">
            <slot name="filters" />
          </div>
        </div>
      </div>

      <div class="list-page-header__actions">
        <slot name="actions" />
      </div>
    </div>

    <div v-if="hasFilters && collapsible" class="list-page-header__toggle">
      <el-button link type="primary" @click="expanded = !expanded">
        {{ expanded ? "收起筛选" : "展开筛选" }}
      </el-button>
    </div>
  </div>
</template>

<script setup>
import { computed, nextTick, onBeforeUnmount, onMounted, ref, useSlots, watch } from "vue";

const props = defineProps({
  collapsedHeight: { type: Number, default: 48 }
});

const slots = useSlots();
const hasFilters = computed(() => Boolean(slots.filters));
const rootRef = ref(null);
const contentRef = ref(null);
const contentHeight = ref(0);
const collapsible = ref(false);
const expanded = ref(false);

let resizeObserver;

const currentMaxHeight = computed(() => {
  if (!hasFilters.value) {
    return 0;
  }
  if (!collapsible.value || expanded.value) {
    return Math.max(contentHeight.value, props.collapsedHeight);
  }
  return props.collapsedHeight;
});

function updateState() {
  const el = contentRef.value;
  if (!el) {
    contentHeight.value = 0;
    collapsible.value = false;
    expanded.value = false;
    return;
  }
  const nextHeight = Math.ceil(el.scrollHeight);
  contentHeight.value = nextHeight;
  const nextCollapsible = nextHeight > props.collapsedHeight + 4;
  collapsible.value = nextCollapsible;
  if (!nextCollapsible) {
    expanded.value = false;
  }
}

onMounted(() => {
  nextTick(updateState);
  if (typeof ResizeObserver !== "undefined") {
    resizeObserver = new ResizeObserver(() => updateState());
    if (contentRef.value) {
      resizeObserver.observe(contentRef.value);
    }
  }
});

onBeforeUnmount(() => {
  resizeObserver?.disconnect();
});

watch(hasFilters, () => nextTick(updateState));

defineExpose({
  getRootEl: () => rootRef.value,
  getHeight: () => rootRef.value?.offsetHeight || 0
});
</script>

<style scoped>
.list-page-header {
  margin-bottom: 16px;
}

.list-page-header__main {
  display: flex;
  align-items: flex-start;
  gap: 16px;
}

.list-page-header__filters {
  flex: 1 1 auto;
  min-width: 0;
}

.list-page-header__filters-viewport {
  overflow: hidden;
  transition: max-height 0.2s ease;
}

.list-page-header__filters-content {
  display: flex;
  flex-wrap: wrap;
  align-items: flex-start;
  gap: 12px 16px;
}

.list-page-header__actions {
  display: flex;
  align-items: center;
  justify-content: flex-end;
  flex: 0 0 auto;
  flex-wrap: wrap;
  gap: 10px;
  margin-left: auto;
}

.list-page-header__toggle {
  display: flex;
  justify-content: flex-end;
  margin-top: 6px;
}

.list-page-header :deep(.list-filter-item) {
  display: flex;
  align-items: center;
  gap: 8px;
  min-height: 32px;
}

.list-page-header :deep(.list-filter-item--grow) {
  flex: 1 1 280px;
  min-width: 220px;
}

.list-page-header :deep(.list-filter-label) {
  flex: 0 0 auto;
  color: #606266;
  font-size: 13px;
  line-height: 20px;
  white-space: nowrap;
}

.list-page-header :deep(.list-filter-control) {
  width: 180px;
}

.list-page-header :deep(.list-filter-control--wide) {
  width: 240px;
}

.list-page-header :deep(.list-filter-actions) {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 8px;
  margin-left: auto;
}

@media (max-width: 768px) {
  .list-page-header__main {
    flex-direction: column;
    gap: 10px;
  }

  .list-page-header__actions {
    width: 100%;
    margin-left: 0;
    justify-content: flex-start;
  }

  .list-page-header :deep(.list-filter-item),
  .list-page-header :deep(.list-filter-item--grow) {
    width: 100%;
    min-width: 0;
  }

  .list-page-header :deep(.list-filter-control),
  .list-page-header :deep(.list-filter-control--wide) {
    width: 100%;
  }

  .list-page-header :deep(.list-filter-actions) {
    margin-left: 0;
  }
}
</style>
