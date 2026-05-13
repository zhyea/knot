<template>
  <el-container class="layout">
    <el-aside width="220px" class="aside">
      <div class="logo">Knot AI Gateway</div>
      <el-menu
        :default-active="activePath"
        :default-openeds="openeds"
        router
      >
        <el-menu-item index="/">
          <el-icon><Odometer /></el-icon>
          <span>总览</span>
        </el-menu-item>

        <el-sub-menu index="/system">
          <template #title>
            <el-icon><Setting /></el-icon>
            <span>系统管理</span>
          </template>
          <el-menu-item index="/system/users">用户管理</el-menu-item>
          <el-menu-item index="/system/roles">角色权限</el-menu-item>
          <el-menu-item index="/system/logs">操作日志</el-menu-item>
          <el-menu-item index="/system/nodes">网关节点</el-menu-item>
          <el-menu-item index="/system/enums">枚举管理</el-menu-item>
          <el-menu-item index="/system/backup">备份还原</el-menu-item>
        </el-sub-menu>

        <el-menu-item index="/providers">
          <el-icon><Connection /></el-icon>
          <span>供应商管理</span>
        </el-menu-item>

        <el-menu-item index="/models">
          <el-icon><Cpu /></el-icon>
          <span>模型管理</span>
        </el-menu-item>

        <el-menu-item index="/apps">
          <el-icon><Monitor /></el-icon>
          <span>应用管理</span>
        </el-menu-item>

        <el-sub-menu index="/routing">
          <template #title>
            <el-icon><Guide /></el-icon>
            <span>路由规则</span>
          </template>
          <el-menu-item index="/routing/rules">规则列表</el-menu-item>
          <el-menu-item index="/routing/logs">切换日志</el-menu-item>
        </el-sub-menu>

        <el-sub-menu index="/billing">
          <template #title>
            <el-icon><Money /></el-icon>
            <span>计费成本</span>
          </template>
          <el-menu-item index="/billing/rules">计费规则</el-menu-item>
          <el-menu-item index="/billing/statistics">成本统计</el-menu-item>
          <el-menu-item index="/billing/reconciliation">对账管理</el-menu-item>
        </el-sub-menu>

        <el-sub-menu index="/security">
          <template #title>
            <el-icon><Lock /></el-icon>
            <span>安全监控</span>
          </template>
          <el-menu-item index="/security/policy">安全策略</el-menu-item>
          <el-menu-item index="/security/alerts">告警管理</el-menu-item>
          <el-menu-item index="/security/cache">缓存管理</el-menu-item>
        </el-sub-menu>

        <el-menu-item index="/plugins">
          <el-icon><Operation /></el-icon>
          <span>插件管理</span>
        </el-menu-item>

        <el-menu-item index="/release">
          <el-icon><SetUp /></el-icon>
          <span>灰度发布</span>
        </el-menu-item>

        <el-sub-menu index="/notifications">
          <template #title>
            <el-icon><Bell /></el-icon>
            <span>通知管理</span>
          </template>
          <el-menu-item index="/notifications/templates">通知模板</el-menu-item>
          <el-menu-item index="/notifications/send">通知发送</el-menu-item>
          <el-menu-item index="/notifications/policy">通知策略</el-menu-item>
        </el-sub-menu>
      </el-menu>
    </el-aside>
    <el-container>
      <el-header class="header">
        <span>AI 网关管理后台</span>
        <div class="header-right">
          <div class="theme-switcher">
            <span class="theme-label">主题</span>
            <span
              v-for="t in THEMES"
              :key="t.key"
              :class="['theme-dot', 'theme-dot--' + t.key, { active: current === t.key }]"
              :title="t.label"
              @click="setTheme(t.key)"
            />
          </div>
          <el-dropdown @command="handleCommand">
            <span class="user-info">
              <el-icon><User /></el-icon>
              <span>{{ user?.realName || user?.username || '用户' }}</span>
            </span>
            <template #dropdown>
              <el-dropdown-menu>
                <el-dropdown-item command="logout">退出登录</el-dropdown-item>
              </el-dropdown-menu>
            </template>
          </el-dropdown>
        </div>
      </el-header>
      <el-main class="main">
        <router-view />
      </el-main>
    </el-container>
  </el-container>
</template>

<script setup>
import { computed } from "vue";
import { useRoute, useRouter } from "vue-router";
import { useTheme, THEMES } from "../composables/useTheme";
import { useAuth } from "../composables/useAuth";
import {
  Odometer,
  Setting,
  Connection,
  Cpu,
  Monitor,
  Guide,
  Money,
  Lock,
  Operation,
  SetUp,
  Bell,
  User
} from "@element-plus/icons-vue";

const route = useRoute();
const router = useRouter();
const { current, setTheme } = useTheme();
const { user, logout } = useAuth();
const activePath = computed(() => route.path);

// 自动展开当前路径所属的子菜单
const openeds = computed(() => {
  const match = route.path.match(/^\/([^/]+)/);
  return match ? [`/${match[1]}`] : [];
});

async function handleCommand(command) {
  if (command === 'logout') {
    await logout();
    router.push('/login');
  }
}
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
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.header-right {
  display: flex;
  align-items: center;
  gap: 20px;
}

.user-info {
  display: flex;
  align-items: center;
  gap: 6px;
  cursor: pointer;
  font-weight: 400;
  font-size: 14px;
}

.theme-label {
  font-size: 13px;
  font-weight: 400;
  color: #909399;
  margin-right: 4px;
}

.main {
  background: #f5f7fa;
}
</style>
