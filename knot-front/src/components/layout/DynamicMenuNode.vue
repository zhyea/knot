<template>
  <el-menu-item v-if="!hasChildren" :index="menu.routePath">
    <span>{{ menu.menuName }}</span>
  </el-menu-item>
  <el-sub-menu v-else :index="menu.routePath || menu.menuCode">
    <template #title>
      <span>{{ menu.menuName }}</span>
    </template>
    <DynamicMenuNode
      v-for="child in menu.children"
      :key="child.menuCode"
      :menu="child"
    />
  </el-sub-menu>
</template>

<script setup>
import { computed } from "vue";

const props = defineProps({
  menu: {
    type: Object,
    required: true
  }
});

const hasChildren = computed(() => Array.isArray(props.menu.children) && props.menu.children.length > 0);
</script>
