<template>
  <PageSection>
    <div class="auth-manage">
      <section class="list-page-block auth-manage__switcher">
        <el-segmented
          v-model="state.activeSection.value"
          :options="sectionOptions"
          block
        />
      </section>

      <RoleAuthorizationView
        v-if="state.activeSection.value === 'roles'"
        :state="state"
      />
      <AuthorizationResourceView
        v-else
        :state="state"
      />
    </div>

    <AuthorizationRoleFormDialog
      v-model="state.roleDialogVisible.value"
      :role="state.editingRole.value"
      @saved="state.onRoleSaved"
    />

    <AuthorizationEntityFormDialog
      v-model="state.resourceDialogVisible.value"
      :title="state.resourceDialogTitle.value"
      :form="state.resourceDialogForm.value"
      :fields="state.resourceDialogFields.value"
      :submitter="state.resourceDialogSubmitter.value"
      @saved="state.onResourceSaved"
    />
  </PageSection>
</template>

<script setup>
import { onMounted } from "vue";
import PageSection from "../../components/common/PageSection.vue";
import AuthorizationEntityFormDialog from "../../components/system/auth/AuthorizationEntityFormDialog.vue";
import AuthorizationRoleFormDialog from "../../components/system/auth/AuthorizationRoleFormDialog.vue";
import { useAuthorizationManagement } from "../../composables/useAuthorizationManagement";
import AuthorizationResourceView from "./auth/AuthorizationResourceView.vue";
import RoleAuthorizationView from "./auth/RoleAuthorizationView.vue";

const state = useAuthorizationManagement();

const sectionOptions = [
  { label: "角色授权", value: "roles" },
  { label: "授权资源", value: "resources" }
];

onMounted(() => {
  state.initializePage();
});
</script>

<style scoped>
.auth-manage {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.auth-manage__switcher {
  padding: 0;
}
</style>
