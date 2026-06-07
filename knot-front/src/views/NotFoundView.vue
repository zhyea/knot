<template>
  <div class="not-found">
    <h1>404</h1>
    <p>{{ t("notFound.description") }}</p>
    <el-button type="primary" @click="$router.push('/')">{{ t("notFound.backHome") }}</el-button>
  </div>
</template>

<script setup>
import { useLocale } from "../composables/useLocale";

const { t } = useLocale();
</script>

<style scoped>
.not-found {
  text-align: center;
  padding: 80px 0;
}

.not-found h1 {
  font-size: 72px;
  color: #909399;
  margin: 0 0 12px;
}

.not-found p {
  font-size: 16px;
  color: #606266;
  margin: 0 0 24px;
}
</style>
