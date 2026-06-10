<template>
  <el-drawer
    :model-value="modelValue"
    title="角色授权"
    size="1024px"
    class="drawer-with-scrollbar"
    destroy-on-close
    @update:model-value="emit('update:modelValue', $event)"
  >
    <el-scrollbar class="grant-drawer-scrollbar">
      <div class="slot-body grant-drawer-body">
        <AuthorizationPermissionGrantPanel
          :role="role"
          :groups="groups"
          :selected-ids="selectedIds"
          :keyword="keyword"
          :saving="saving"
          @save="emit('save')"
          @toggle="(permissionId, checked) => emit('toggle', permissionId, checked)"
          @keyword-change="(value) => emit('keyword-change', value)"
        />
      </div>
    </el-scrollbar>
  </el-drawer>
</template>

<script setup>
import AuthorizationPermissionGrantPanel from "./AuthorizationPermissionGrantPanel.vue";

defineProps({
  modelValue: {type: Boolean, default: false},
  role: {type: Object, default: null},
  groups: {type: Array, default: () => []},
  selectedIds: {type: Array, default: () => []},
  keyword: {type: String, default: ""},
  saving: {type: Boolean, default: false}
});

const emit = defineEmits(["update:modelValue", "save", "toggle", "keyword-change"]);
</script>

<style scoped>
.grant-drawer-scrollbar {
  height: 100%;
}

.grant-drawer-body {
  min-height: 100%;
  display: flex;
}

.grant-drawer-body :deep(.auth-panel) {
  flex: 1;
}
</style>
