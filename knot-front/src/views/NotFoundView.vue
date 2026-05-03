<template>
  <div class="not-found">
    <h1>404</h1>
    <p>页面不存在</p>
    <el-button type="primary" @click="$router.push('/')">返回首页</el-button>
  </div>
</template>

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
