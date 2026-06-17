<template>
  <div class="login-container">
    <el-card class="login-card">
      <template #header>
        <div class="login-brand">
          <img src="/login-logo.png" alt="Knot AI Gateway" class="login-brand__logo" />
        </div>
      </template>
      <el-form :model="form" :rules="rules" ref="formRef" @submit.prevent="handleLogin">
        <el-form-item prop="username">
          <el-input v-model="form.username" :placeholder="t('login.username')" :prefix-icon="User" size="large" />
        </el-form-item>
        <el-form-item prop="password">
          <el-input
            v-model="form.password"
            type="password"
            :placeholder="t('login.password')"
            :prefix-icon="Lock"
            size="large"
            show-password
            @keyup.enter="handleLogin"
          />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" size="large" :loading="loading" @click="handleLogin" style="width: 100%">
            {{ t("login.submit") }}
          </el-button>
        </el-form-item>
      </el-form>
    </el-card>
  </div>
</template>

<script setup>
import { computed, reactive, ref } from "vue";
import { useRouter } from "vue-router";
import { ElMessage } from "element-plus";
import { Lock, User } from "@element-plus/icons-vue";
import { useAuth } from "../composables/useAuth";
import { useLocale } from "../composables/useLocale";
import { loadThemePreference } from "../composables/useTheme";

const router = useRouter();
const { login } = useAuth();
const { loadLocalePreference, t } = useLocale();

const formRef = ref(null);
const loading = ref(false);

const form = reactive({
  username: "",
  password: ""
});

const rules = computed(() => ({
  username: [{ required: true, message: t("login.usernameRequired"), trigger: "blur" }],
  password: [{ required: true, message: t("login.passwordRequired"), trigger: "blur" }]
}));

async function handleLogin() {
  const valid = await formRef.value.validate().catch(() => false);
  if (!valid) {
    return;
  }

  loading.value = true;
  try {
    const response = await login(form.username, form.password);
    if (response.forcePasswordChange) {
      ElMessage.warning(t("login.forcePasswordChangeRequired"));
      router.push("/force-password-change");
      return;
    }
    await Promise.all([loadThemePreference(), loadLocalePreference()]);
    ElMessage.success(t("login.success"));
    router.push("/");
  } catch (error) {
    ElMessage.error(error.message || t("login.failed"));
  } finally {
    loading.value = false;
  }
}
</script>

<style scoped>
.login-container {
  display: flex;
  justify-content: center;
  align-items: center;
  min-height: 100vh;
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
}

.login-card {
  width: 400px;
}

.login-brand {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 12px;
}

.login-brand__logo {
  display: block;
  width: 64px;
  height: 64px;
  object-fit: contain;
}
</style>
