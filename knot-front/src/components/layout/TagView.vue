<template>
  <div class="tag-view" @click="hideContextMenu">
    <div class="tag-view__side">
      <el-button
        class="tag-view__nav"
        :icon="ArrowLeft"
        text
        @click="scrollByOffset(-240)"
      />
    </div>

    <div ref="scrollWrapRef" class="tag-view__scroll">
      <div class="tag-view__list">
        <button
          v-for="tag in visitedTags"
          :key="tag.key"
          type="button"
          class="tag-view__item"
          :class="{
            'tag-view__item--active': tag.key === activeTagKey,
            'tag-view__item--affix': tag.affix
          }"
          @click="openTag(tag)"
          @contextmenu.prevent="openContextMenu($event, tag)"
        >
          <span class="tag-view__title">{{ tag.title }}</span>
          <el-icon
            v-if="!tag.affix"
            class="tag-view__close"
            @click.stop="handleCloseTag(tag)"
          >
            <Close />
          </el-icon>
        </button>
      </div>
    </div>

    <div class="tag-view__side tag-view__side--right">
      <el-button
        class="tag-view__nav"
        :icon="ArrowRight"
        text
        @click="scrollByOffset(240)"
      />
      <el-button
        class="tag-view__nav"
        :icon="RefreshRight"
        text
        @click="refreshCurrentTag"
      />
      <el-button
        class="tag-view__nav"
        :icon="Close"
        text
        :disabled="activeTag?.affix"
        @click="closeActiveTag"
      />
    </div>

    <teleport to="body">
      <div
        v-if="contextMenu.visible"
        class="tag-view__menu"
        :style="{
          left: `${contextMenu.left}px`,
          top: `${contextMenu.top}px`
        }"
      >
        <button type="button" class="tag-view__menu-item" @click="refreshContextTag">
          刷新当前
        </button>
        <button
          type="button"
          class="tag-view__menu-item"
          :disabled="contextMenu.tag?.affix"
          @click="closeContextTag"
        >
          关闭当前
        </button>
        <button type="button" class="tag-view__menu-item" @click="closeOtherTags">
          关闭其他
        </button>
        <button type="button" class="tag-view__menu-item" @click="closeAllTags">
          关闭全部
        </button>
      </div>
    </teleport>
  </div>
</template>

<script setup>
import { computed, nextTick, onBeforeUnmount, onMounted, reactive, ref, watch } from "vue";
import { useRoute, useRouter } from "vue-router";
import { ArrowLeft, ArrowRight, Close, RefreshRight } from "@element-plus/icons-vue";
import { useTagView } from "@/composables/useTagView";

const route = useRoute();
const router = useRouter();
const scrollWrapRef = ref(null);
const contextMenu = reactive({
  visible: false,
  left: 0,
  top: 0,
  tag: null
});

const {
  visitedTags,
  ensureRouteTag,
  removeTag,
  removeOtherTags,
  removeAllTags,
  refreshTag,
  buildTagKey
} = useTagView();

const activeTagKey = computed(() => buildTagKey(route));
const activeTag = computed(() => visitedTags.value.find((item) => item.key === activeTagKey.value) || null);

watch(
  () => route.fullPath,
  async () => {
    ensureRouteTag(route, router);
    await nextTick();
    scrollActiveIntoView();
  },
  { immediate: true }
);

onMounted(() => {
  document.addEventListener("click", hideContextMenu);
  document.addEventListener("scroll", hideContextMenu, true);
});

onBeforeUnmount(() => {
  document.removeEventListener("click", hideContextMenu);
  document.removeEventListener("scroll", hideContextMenu, true);
});

function openTag(tag) {
  if (!tag) {
    return;
  }
  router.push(tag.fullPath || tag.path);
}

function navigateToFallback(tag) {
  if (tag) {
    router.push(tag.fullPath || tag.path);
  }
}

function handleCloseTag(tag) {
  const result = removeTag(tag, activeTagKey.value);
  navigateToFallback(result.fallbackTag);
}

function closeActiveTag() {
  handleCloseTag(activeTag.value);
}

async function refreshCurrentTag() {
  if (!activeTag.value) {
    return;
  }
  await refreshTag(activeTag.value);
  hideContextMenu();
}

async function refreshContextTag() {
  if (!contextMenu.tag) {
    return;
  }
  if (contextMenu.tag.key !== activeTagKey.value) {
    await router.push(contextMenu.tag.fullPath || contextMenu.tag.path);
  }
  await refreshTag(contextMenu.tag);
  hideContextMenu();
}

function closeContextTag() {
  handleCloseTag(contextMenu.tag);
  hideContextMenu();
}

function closeOtherTags() {
  const targetTag = contextMenu.tag || activeTag.value;
  const fallbackTag = removeOtherTags(targetTag);
  if (fallbackTag && fallbackTag.key !== activeTagKey.value) {
    router.push(fallbackTag.fullPath || fallbackTag.path);
  }
  hideContextMenu();
}

function closeAllTags() {
  const fallbackTag = removeAllTags();
  if (fallbackTag) {
    router.push(fallbackTag.fullPath || fallbackTag.path);
  }
  hideContextMenu();
}

function scrollByOffset(offset) {
  scrollWrapRef.value?.scrollBy({ left: offset, behavior: "smooth" });
}

function scrollActiveIntoView() {
  const container = scrollWrapRef.value;
  const activeEl = container?.querySelector(".tag-view__item--active");
  if (!container || !activeEl) {
    return;
  }

  const itemLeft = activeEl.offsetLeft;
  const itemRight = itemLeft + activeEl.offsetWidth;
  const viewLeft = container.scrollLeft;
  const viewRight = viewLeft + container.clientWidth;

  if (itemLeft < viewLeft) {
    container.scrollTo({ left: itemLeft - 12, behavior: "smooth" });
  } else if (itemRight > viewRight) {
    container.scrollTo({ left: itemRight - container.clientWidth + 12, behavior: "smooth" });
  }
}

function openContextMenu(event, tag) {
  contextMenu.visible = true;
  contextMenu.left = event.clientX;
  contextMenu.top = event.clientY;
  contextMenu.tag = tag;
}

function hideContextMenu() {
  contextMenu.visible = false;
  contextMenu.tag = null;
}
</script>

<style scoped>
.tag-view {
  position: relative;
  display: flex;
  align-items: stretch;
  min-height: 44px;
  border-bottom: 1px solid var(--knot-border, #ebeef5);
  background: var(--knot-surface, #fff);
}

.tag-view__side {
  display: flex;
  align-items: center;
  flex: 0 0 auto;
  border-right: 1px solid var(--knot-border, #ebeef5);
}

.tag-view__side--right {
  margin-left: auto;
  border-left: 1px solid var(--knot-border, #ebeef5);
  border-right: 0;
}

.tag-view__nav {
  width: 44px;
  height: 44px;
  color: #606266;
}

.tag-view__scroll {
  flex: 1;
  min-width: 0;
  overflow-x: auto;
  overflow-y: hidden;
  padding: 6px 10px;
}

.tag-view__scroll::-webkit-scrollbar {
  display: none;
}

.tag-view__list {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  min-width: 100%;
}

.tag-view__item {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  height: 32px;
  padding: 0 12px;
  border: 1px solid var(--knot-border, #dcdfe6);
  background: #f5f7fa;
  color: #606266;
  cursor: pointer;
  white-space: nowrap;
}

.tag-view__item--active {
  border-color: var(--el-color-primary, #409eff);
  background: #ecf5ff;
  color: var(--el-color-primary, #409eff);
}

.tag-view__item--affix .tag-view__title {
  font-weight: 600;
}

.tag-view__title {
  line-height: 1;
}

.tag-view__close {
  font-size: 12px;
}

.tag-view__menu {
  position: fixed;
  z-index: 3000;
  display: flex;
  flex-direction: column;
  min-width: 128px;
  padding: 6px 0;
  border: 1px solid var(--knot-border, #dcdfe6);
  background: #fff;
  box-shadow: 0 8px 24px rgba(0, 0, 0, 0.12);
}

.tag-view__menu-item {
  height: 34px;
  padding: 0 14px;
  border: 0;
  background: transparent;
  text-align: left;
  color: #303133;
  cursor: pointer;
}

.tag-view__menu-item:hover {
  background: #f5f7fa;
}

.tag-view__menu-item:disabled {
  color: #c0c4cc;
  cursor: not-allowed;
}

.tag-view__menu-item:disabled:hover {
  background: transparent;
}
</style>
