<template>
  <div class="permission-menu-node" :class="{ 'permission-menu-node--child': level > 0 }">
    <div class="permission-menu-node__header">
      <span class="permission-menu-node__title">{{ node.menuName }}</span>
    </div>

    <div v-if="node.permissions.length > 0" class="permission-menu-node__permissions">
      <el-checkbox
        v-for="permission in node.permissions"
        :key="permission.id"
        :model-value="selectedIds.includes(permission.id)"
        @change="(checked) => emit('toggle', permission.id, checked)"
      >
        <span class="permission-item__name">{{ permission.permissionName }}</span>
      </el-checkbox>
    </div>

    <div v-if="node.children.length > 0" class="permission-menu-node__children">
      <AuthorizationPermissionMenuNode
        v-for="child in node.children"
        :key="child.id"
        :node="child"
        :selected-ids="selectedIds"
        :level="level + 1"
        @toggle="(permissionId, checked) => emit('toggle', permissionId, checked)"
      />
    </div>
  </div>
</template>

<script setup>
defineProps({
  node: {type: Object, required: true},
  selectedIds: {type: Array, default: () => []},
  level: {type: Number, default: 0}
});

const emit = defineEmits(["toggle"]);
</script>

<style scoped>
.permission-menu-node {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.permission-menu-node--child {
  padding-left: 20px;
  border-left: 1px solid #ebeef5;
}

.permission-menu-node__header {
  display: flex;
  align-items: center;
  min-height: 28px;
}

.permission-menu-node__title {
  font-size: 13px;
  line-height: 20px;
  color: #303133;
  font-weight: 500;
}

.permission-menu-node__permissions {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(280px, 1fr));
  gap: 12px 20px;
}

.permission-menu-node__children {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.permission-item__name {
  margin-right: 6px;
}
</style>
