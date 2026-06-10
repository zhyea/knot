<template>
  <div class="row-actions">
    <template v-for="action in visibleActions" :key="action.key || action.label">
      <el-popconfirm
        v-if="action.confirm"
        :title="confirmTitle(action)"
        @confirm="emit('action', action.key)"
      >
        <template #reference>
          <span>
            <el-tooltip :content="action.label" placement="top">
              <el-button
                link
                :type="action.type || 'primary'"
                :icon="action.icon"
                :disabled="action.disabled"
                class="row-action-button"
              />
            </el-tooltip>
          </span>
        </template>
      </el-popconfirm>

      <el-tooltip v-else :content="action.label" placement="top">
        <el-button
          link
          :type="action.type || 'primary'"
          :icon="action.icon"
          :disabled="action.disabled"
          class="row-action-button"
          @click="emit('action', action.key)"
        />
      </el-tooltip>
    </template>
  </div>
</template>

<script setup>
import { computed } from "vue";

const props = defineProps({
  actions: { type: Array, default: () => [] }
});

const emit = defineEmits(["action"]);

const visibleActions = computed(() =>
  props.actions.filter((action) => action && action.hidden !== true)
);

function confirmTitle(action) {
  return typeof action.confirm === "string" ? action.confirm : `确认${action.label}？`;
}
</script>

<style scoped>
.row-actions {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
  min-width: 0;
}

.row-action-button {
  margin-left: 0 !important;
  padding: 0;
  font-size: 16px;
}
</style>
