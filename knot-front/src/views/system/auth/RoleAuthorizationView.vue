<template>
  <div class="list-page-shell">
    <section class="list-page-block">
      <div class="list-page-filters">
        <div class="list-filter-item list-filter-item--grow">
          <span class="list-filter-label">关键字</span>
          <el-input
            v-model="state.roleKeyword.value"
            class="list-filter-control--wide"
            placeholder="按角色编码、名称筛选"
            clearable
            @keyup.enter="state.handleRoleQuery"
          />
        </div>
        <div class="list-filter-actions">
          <el-button type="primary" @click="state.handleRoleQuery">查询</el-button>
          <el-button @click="state.resetRoleQuery">重置</el-button>
        </div>
      </div>
    </section>

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
import AuthorizationRolePanel from "../../../components/system/auth/AuthorizationRolePanel.vue";

defineProps({
  state: {
    type: Object,
    required: true
  }
});
</script>
