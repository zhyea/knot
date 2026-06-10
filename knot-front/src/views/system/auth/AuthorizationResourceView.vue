<template>
  <div class="list-page-shell">
    <section class="list-page-block list-page-block--content">
      <el-tabs v-model="state.activeResourceTab.value">
        <el-tab-pane label="模块" name="modules">
          <AuthorizationResourcePanel
            title="模块管理"
            create-text="新建模块"
            :rows="state.modules.value"
            :loading="state.moduleLoading.value"
            :columns="state.moduleColumns"
            :filters="state.moduleFilters.value"
            :filter-values="state.moduleQuery"
            delete-name="模块"
            @create="state.openModuleCreate"
            @action="state.handleModuleAction"
            @filter-change="state.updateModuleFilter"
            @query="state.loadModules"
            @reset-filters="state.resetModuleFilters"
          >
            <template #statusSwitch="{ row }">
              <el-switch
                :model-value="row.status !== 'DISABLED'"
                :loading="state.moduleStatusUpdatingId.value === row.id"
                inline-prompt
                active-text="启用"
                inactive-text="禁用"
                @change="(value) => state.handleModuleStatusChange(row, value)"
              />
            </template>
          </AuthorizationResourcePanel>
        </el-tab-pane>

        <el-tab-pane label="菜单" name="menus">
          <AuthorizationResourcePanel
            title="菜单管理"
            create-text="新建菜单"
            :rows="state.menus.value"
            :loading="state.menuLoading.value"
            :columns="state.menuColumns"
            :filters="state.menuFilters.value"
            :filter-values="state.menuQuery"
            delete-name="菜单"
            @create="state.openMenuCreate"
            @action="state.handleMenuAction"
            @filter-change="state.updateMenuFilter"
            @query="state.loadMenus"
            @reset-filters="state.resetMenuFilters"
          >
            <template #parentLabel="{ row }">
              <span>{{ state.resolveMenuName(row.parentId) || "-" }}</span>
            </template>
            <template #statusSwitch="{ row }">
              <el-switch
                :model-value="row.status !== 'DISABLED'"
                :loading="state.menuStatusUpdatingId.value === row.id"
                inline-prompt
                active-text="启用"
                inactive-text="禁用"
                @change="(value) => state.handleMenuStatusChange(row, value)"
              />
            </template>
          </AuthorizationResourcePanel>
        </el-tab-pane>

        <el-tab-pane label="权限" name="permissions">
          <AuthorizationResourcePanel
            title="权限管理"
            create-text="新建权限"
            :rows="state.permissions.value"
            :loading="state.permissionLoading.value"
            :columns="state.permissionColumns"
            :filters="state.permissionFilters.value"
            :filter-values="state.permissionQuery"
            delete-name="权限"
            :before-delete="state.beforePermissionDelete"
            @create="state.openPermissionCreate"
            @action="state.handlePermissionAction"
            @filter-change="state.updatePermissionFilter"
            @query="state.loadPermissions"
            @reset-filters="state.resetPermissionFilters"
          >
            <template #statusSwitch="{ row }">
              <el-switch
                :model-value="row.status !== 'DISABLED'"
                :loading="state.permissionStatusUpdatingId.value === row.id"
                inline-prompt
                active-text="启用"
                inactive-text="禁用"
                @change="(value) => state.handlePermissionStatusChange(row, value)"
              />
            </template>
          </AuthorizationResourcePanel>
        </el-tab-pane>

        <el-tab-pane label="API 绑定" name="bindings">
          <AuthorizationResourcePanel
            title="API 权限绑定"
            create-text="新建绑定"
            :rows="state.apiBindings.value"
            :loading="state.apiBindingLoading.value"
            :columns="state.apiBindingColumns"
            :filters="state.apiBindingFilters.value"
            :filter-values="state.apiBindingQuery"
            delete-name="API 绑定"
            @create="state.openApiBindingCreate"
            @action="state.handleApiBindingAction"
            @filter-change="state.updateApiBindingFilter"
            @query="state.loadApiBindings"
            @reset-filters="state.resetApiBindingFilters"
          >
            <template #statusSwitch="{ row }">
              <el-switch
                :model-value="row.status !== 'DISABLED'"
                :loading="state.apiBindingStatusUpdatingId.value === row.id"
                inline-prompt
                active-text="启用"
                inactive-text="禁用"
                @change="(value) => state.handleApiBindingStatusChange(row, value)"
              />
            </template>
          </AuthorizationResourcePanel>
        </el-tab-pane>
      </el-tabs>
    </section>
  </div>
</template>

<script setup>
import AuthorizationResourcePanel from "../../../components/system/auth/AuthorizationResourcePanel.vue";

defineProps({
  state: {
    type: Object,
    required: true
  }
});
</script>

<style scoped>
.list-page-block :deep(.el-tabs__content) {
  padding-top: 16px;
}
</style>
