<template>
  <el-container class="layout">
    <el-aside width="220px" class="aside">
      <div class="logo">Knot AI Gateway</div>
      <el-menu :default-active="activePath" router>
        <el-menu-item index="/">总览</el-menu-item>
        <el-menu-item index="/system">系统管理</el-menu-item>
        <el-menu-item index="/providers">供应商管理</el-menu-item>
        <el-menu-item index="/models">模型管理</el-menu-item>
        <el-menu-item index="/apps">应用管理</el-menu-item>
        <el-menu-item index="/routing">路由规则</el-menu-item>
        <el-menu-item index="/billing">计费成本</el-menu-item>
        <el-menu-item index="/security">安全监控</el-menu-item>
        <el-menu-item index="/plugins">插件管理</el-menu-item>
        <el-menu-item index="/notifications">通知管理</el-menu-item>
        <el-menu-item index="/release">灰度发布</el-menu-item>
      </el-menu>
    </el-aside>
    <el-container>
      <el-header class="header">AI 网关管理后台</el-header>
      <el-main class="main">
        <router-view />
      </el-main>
    </el-container>
  </el-container>
</template>

<script setup>
import { computed } from "vue";
import { useRoute } from "vue-router";

const route = useRoute();
const activePath = computed(() => route.path);
</script>

<style scoped>
.layout {
  min-height: 100vh;
}

.aside {
  background: #fff;
  border-right: 1px solid #ebeef5;
}

.logo {
  height: 56px;
  line-height: 56px;
  text-align: center;
  font-weight: 600;
  border-bottom: 1px solid #ebeef5;
}

.header {
  background: #fff;
  border-bottom: 1px solid #ebeef5;
  line-height: 60px;
  font-weight: 600;
}

.main {
  background: #f5f7fa;
}
</style>
