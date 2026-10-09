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

    <OperationLogDrawer
      v-model="state.logDrawerVisible.value"
      :title="state.logTitle.value"
      :load-logs="state.logLoadFn.value"
    />
  </PageSection>
</template>

<script setup lang="ts">
import {onMounted} from "vue";
import PageSection from "../../components/common/PageSection.vue";
import AuthorizationEntityFormDialog from "../../components/system/auth/AuthorizationEntityFormDialog.vue";
import OperationLogDrawer from "../../components/common/OperationLogDrawer.vue";
import {useAuthorizationManagement} from "@/composables/useAuthorizationManagement";
import AuthorizationResourceView from "./auth/AuthorizationResourceView.vue";

const state = useAuthorizationManagement();
state.activeResourceTab.value = "modules";

onMounted(() => {
  state.initializeResourceSection();
});
</script>
