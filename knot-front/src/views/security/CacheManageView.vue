<template>
  <PageSection title="缓存管理">
    <el-form :inline="true" @submit.prevent="runEvict">
      <el-form-item label="Cache Key"><el-input v-model="evict.cacheKey" placeholder="路由/策略键" clearable /></el-form-item>
      <el-form-item label="类型"><el-input v-model="evict.cacheType" placeholder="默认 GENERIC" clearable /></el-form-item>
      <el-form-item><el-button type="warning" :loading="evictLoading" @click="runEvict">淘汰</el-button></el-form-item>
    </el-form>
    <el-alert v-if="evictResult" type="success" :closable="false" show-icon>
      键 {{ evictResult.cacheKey }} → {{ evictResult.result }}
    </el-alert>
  </PageSection>
</template>

<script setup>
import { reactive, ref } from "vue";
import { ElMessage } from "element-plus";
import PageSection from "../../components/common/PageSection.vue";
import { evictCache } from "../../api/security";

const evict = reactive({ cacheKey: "", cacheType: "" });
const evictLoading = ref(false);
const evictResult = ref(null);

async function runEvict() {
  if (!evict.cacheKey?.trim()) {
    ElMessage.warning("请填写 cacheKey");
    return;
  }
  evictLoading.value = true;
  try {
    evictResult.value = await evictCache({
      cacheKey: evict.cacheKey.trim(),
      cacheType: evict.cacheType?.trim() || null
    });
    ElMessage.success("已提交淘汰");
  } finally {
    evictLoading.value = false;
  }
}
</script>
