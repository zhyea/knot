<template>
  <PageSection>
    <RoleAuthorizationView :state="state" />

    <AuthorizationRoleFormDialog
      v-model="state.roleDialogVisible.value"
      :role="state.editingRole.value"
      @saved="state.onRoleSaved"
    />

    <AuthorizationRoleGrantDrawer
      v-model="state.roleGrantDrawerVisible.value"
      :role="state.selectedRole.value"
      :groups="state.permissionGroups.value"
      :selected-ids="state.grantedPermissionIds.value"
      :keyword="state.permissionKeyword.value"
      :saving="state.grantSaving.value"
      @save="state.saveRolePermissionBindings"
      @toggle="state.togglePermission"
      @keyword-change="state.handlePermissionKeywordChange"
    />
  </PageSection>
</template>

<script setup>
import { onMounted } from "vue";
import PageSection from "../../components/common/PageSection.vue";
import AuthorizationRoleGrantDrawer from "../../components/system/auth/AuthorizationRoleGrantDrawer.vue";
import AuthorizationRoleFormDialog from "../../components/system/auth/AuthorizationRoleFormDialog.vue";
import { useAuthorizationManagement } from "../../composables/useAuthorizationManagement";
import RoleAuthorizationView from "./auth/RoleAuthorizationView.vue";

const state = useAuthorizationManagement();

onMounted(() => {
  state.initializeRoleSection();
});
</script>
