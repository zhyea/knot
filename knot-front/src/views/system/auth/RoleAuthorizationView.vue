<template>
  <div class="list-page-shell">
    <FilterBar @query="state.handleRoleQuery" @reset="state.resetRoleQuery">
      <KeywordInput
        v-model="state.roleKeyword.value"
        placeholder="按角色编码、名称筛选"
        @query="state.handleRoleQuery"
      />
    </FilterBar>

    <section class="list-page-block list-page-block--content">
      <AuthorizationRolePanel
        :rows="state.rows.value"
        :loading="state.loading.value"
        :total="state.total.value"
        :page-num="state.pageNum.value"
        :page-size="state.pageSize.value"
        :selected-role-id="state.selectedRole.value?.id || null"
        @create="state.openRoleCreate"
        @action="state.handleRoleAction"
        @select="state.handleRoleSelect"
        @page-change="state.handleRolePageChange"
        @size-change="state.handleRoleSizeChange"
      />
    </section>
  </div>
</template>

<script setup>
import FilterBar from "../../../components/common/FilterBar.vue";
import KeywordInput from "../../../components/common/KeywordInput.vue";
import AuthorizationRolePanel from "../../../components/system/auth/AuthorizationRolePanel.vue";

defineProps({
  state: {
    type: Object,
    required: true
  }
});
</script>
