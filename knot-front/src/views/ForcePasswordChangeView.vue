<template>
  <div class="password-change-container">
    <el-card class="password-change-card">
      <template #header>
        <div class="password-change-header">
          <h2 class="password-change-title">{{ t("forcePasswordChange.title") }}</h2>
          <p class="password-change-subtitle">
            {{ t("forcePasswordChange.description", { username: displayName }) }}
          </p>
        </div>
      </template>
      <el-form ref="formRef" :model="form" :rules="rules" @submit.prevent="handleSubmit">
        <el-form-item prop="newPassword">
          <el-input
            v-model="form.newPassword"
            type="password"
            show-password
            :placeholder="t('forcePasswordChange.newPassword')"
          />
        </el-form-item>
        <el-form-item prop="confirmPassword">
          <el-input
            v-model="form.confirmPassword"
            type="password"
            show-password
            :placeholder="t('forcePasswordChange.confirmPassword')"
            @keyup.enter="handleSubmit"
          />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" :loading="loading" style="width: 100%" @click="handleSubmit">
            {{ t("forcePasswordChange.submit") }}
          </el-button>
        </el-form-item>
        <el-form-item>
          <el-button style="width: 100%" @click="handleBackToLogin">
            {{ t("forcePasswordChange.backToLogin") }}
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
import { useAuth } from "../composables/useAuth";
import { useLocale } from "../composables/useLocale";

const router = useRouter();
const { forcePasswordChangeState, needsPasswordChange, submitForcedPasswordChange, clearAllAuthState } = useAuth();
const { t } = useLocale();

const formRef = ref(null);
const loading = ref(false);
const form = reactive({
  newPassword: "",
  confirmPassword: ""
});

const displayName = computed(() => {
  return forcePasswordChangeState.value?.realName || forcePasswordChangeState.value?.username || "-";
});

const rules = computed(() => ({
  newPassword: [
    { required: true, message: t("forcePasswordChange.newPasswordRequired"), trigger: "blur" },
    { min: 8, message: t("forcePasswordChange.passwordLength"), trigger: "blur" }
  ],
  confirmPassword: [
    { required: true, message: t("forcePasswordChange.confirmPasswordRequired"), trigger: "blur" },
    {
      validator: (_rule, value, callback) => {
        if (value !== form.newPassword) {
          callback(new Error(t("forcePasswordChange.passwordMismatch")));
          return;
        }
        callback();
      },
      trigger: "blur"
    }
  ]
}));

if (!needsPasswordChange.value) {
  router.replace("/login");
}

async function handleSubmit() {
  const valid = await formRef.value?.validate().catch(() => false);
  if (!valid) {
    return;
  }
  loading.value = true;
  try {
    await submitForcedPasswordChange(form.newPassword);
    ElMessage.success(t("forcePasswordChange.success"));
    router.push("/login");
  } catch (error) {
    ElMessage.error(error.message || t("forcePasswordChange.failed"));
  } finally {
    loading.value = false;
  }
}

function handleBackToLogin() {
  clearAllAuthState();
  router.push("/login");
}
</script>

<style scoped>
.password-change-container {
  display: flex;
  justify-content: center;
  align-items: center;
  min-height: 100vh;
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
}

.password-change-card {
  width: 420px;
}

.password-change-header {
  text-align: center;
}

.password-change-title {
  margin: 0 0 8px;
  font-size: 24px;
  font-weight: 500;
  color: #303133;
}

.password-change-subtitle {
  margin: 0;
  font-size: 14px;
  line-height: 1.6;
  color: #606266;
}
</style>
