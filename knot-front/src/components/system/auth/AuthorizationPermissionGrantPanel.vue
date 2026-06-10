<template>
  <section class="auth-panel auth-panel--fill">
    <div class="auth-panel__header">
      <div>
        <div class="auth-panel__title">角色授权</div>
        <div class="auth-panel__subtitle" v-if="role">
          当前角色：{{ role.name }}（{{ role.code }}）
        </div>
      </div>
      <el-button type="primary" :disabled="!role" :loading="saving" @click="emit('save')">保存授权</el-button>
    </div>

    <el-empty v-if="!role" description="请选择左侧角色"/>
    <template v-else>
      <div class="auth-panel__toolbar">
        <el-input
          :model-value="keyword"
          clearable
          placeholder="按权限名称、模块、菜单筛选"
          @update:model-value="(value) => emit('keyword-change', value || '')"
        />
      </div>
      <div class="auth-panel__content">
        <div class="permission-group" v-for="group in groups" :key="group.moduleId">
          <div class="permission-group__title">{{ group.moduleName }}</div>
          <div v-if="group.unassignedPermissions.length > 0" class="permission-group__items">
            <el-checkbox
              v-for="permission in group.unassignedPermissions"
              :key="permission.id"
              :model-value="selectedIds.includes(permission.id)"
              @change="(checked) => emit('toggle', permission.id, checked)"
            >
              <span class="permission-item__name">{{ permission.permissionName }}</span>
            </el-checkbox>
          </div>
          <div class="permission-group__tree">
            <AuthorizationPermissionMenuNode
              v-for="menu in group.menus"
              :key="menu.id"
              :node="menu"
              :selected-ids="selectedIds"
              @toggle="(permissionId, checked) => emit('toggle', permissionId, checked)"
            />
          </div>
        </div>
        <el-empty v-if="groups.length === 0" description="暂无可授权权限"/>
      </div>
    </template>
  </section>
</template>

<script setup>
import AuthorizationPermissionMenuNode from "./AuthorizationPermissionMenuNode.vue";

const props = defineProps({
  role: {type: Object, default: null},
  groups: {type: Array, default: () => []},
  selectedIds: {type: Array, default: () => []},
  keyword: {type: String, default: ""},
  saving: {type: Boolean, default: false}
});

const emit = defineEmits(["save", "toggle", "keyword-change"]);
</script>

<style scoped>
.auth-panel {
  display: flex;
  flex-direction: column;
  gap: 18px;
  min-width: 0;
}

.auth-panel--fill {
  height: 100%;
}

.auth-panel > :deep(.el-empty) {
  flex: 1;
}

.auth-panel__header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;
  padding-bottom: 16px;
  border-bottom: 1px solid #ebeef5;
}

.auth-panel__title {
  font-size: 14px;
  color: #303133;
  font-weight: 600;
}

.auth-panel__subtitle {
  margin-top: 4px;
  color: #909399;
  font-size: 12px;
}

.auth-panel__toolbar {
  width: 420px;
  max-width: 100%;
}

.auth-panel__content {
  flex: 1;
  min-height: 0;
}

.permission-group {
  padding: 16px 0;
  border-top: 1px solid #ebeef5;
}

.permission-group:first-child {
  border-top: none;
  padding-top: 0;
}

.permission-group__title {
  margin-bottom: 14px;
  font-size: 13px;
  color: #606266;
  font-weight: 600;
}

.permission-group__tree {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.permission-group__items {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(280px, 1fr));
  gap: 12px 20px;
  margin-bottom: 16px;
}

.permission-item__name {
  margin-right: 6px;
}
</style>
