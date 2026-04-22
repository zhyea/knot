<template>
  <div class="dashboard">
    <el-row :gutter="16">
      <el-col :xs="24" :md="10">
        <HealthStatusCard title="运行状态" :health="health" @refresh="loadHealth" />
      </el-col>
      <el-col :xs="24" :md="14">
        <el-card shadow="never">
          <template #header>
            <span class="card-title">功能模块</span>
          </template>
          <el-skeleton v-if="loading" :rows="4" animated />
          <div v-else class="modules">
            <div v-for="m in modules" :key="m.code" class="mod-item">
              <div class="mod-head">
                <span class="mod-name">{{ m.name }}</span>
                <el-tag size="small" type="info">{{ m.code }}</el-tag>
              </div>
              <div class="caps">
                <el-tag v-for="c in m.capabilities" :key="c" class="cap" size="small" effect="plain">{{ c }}</el-tag>
              </div>
            </div>
          </div>
        </el-card>
      </el-col>
    </el-row>
  </div>
</template>

<script setup>
import { onMounted, ref } from "vue";
import HealthStatusCard from "../components/business/HealthStatusCard.vue";
import { useHealthStatus } from "../composables/useHealthStatus";
import { listModuleCatalog } from "../api/modules";

const { health, loadHealth } = useHealthStatus();
const modules = ref([]);
const loading = ref(true);

onMounted(async () => {
  loadHealth();
  try {
    modules.value = await listModuleCatalog();
  } catch {
    modules.value = [];
  } finally {
    loading.value = false;
  }
});
</script>

<style scoped>
.dashboard {
  max-width: 1200px;
}

.card-title {
  font-weight: 600;
}

.modules {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.mod-item {
  padding: 12px;
  border: 1px solid #ebeef5;
  border-radius: 8px;
  background: #fafafa;
}

.mod-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 8px;
}

.mod-name {
  font-weight: 600;
  color: #303133;
}

.caps {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
}
</style>
