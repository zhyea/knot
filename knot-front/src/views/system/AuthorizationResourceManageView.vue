<template>
  <PageSection>
    <AuthorizationResourceView :state="state" />

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
import { useAuthorizationManagement } from "../../composables/useAuthorizationManagement";
import AuthorizationResourceView from "./auth/AuthorizationResourceView.vue";

const state = useAuthorizationManagement();
state.activeResourceTab.value = "modules";

onMounted(() => {
  state.initializeResourceSection();
});
</script>
