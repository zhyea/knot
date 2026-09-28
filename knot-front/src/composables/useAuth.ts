import {computed, ref} from "vue";
import type {Dict, Row} from "@/types";
import {forcePasswordChange as apiForcePasswordChange, login as apiLogin, logout as apiLogout} from "@/api/auth";
import {getMyAuthorizations} from "@/api/authorizations";
import {clearIdleActivity, touchIdleActivity} from "./idleActivity";
import {
  getStorageItem,
  getStorageJson,
  removeStorageItem,
  setStorageItem,
  setStorageJson
} from "@/utils/storage";

const TOKEN_KEY = "knot_token";
const USER_KEY = "knot_user";
const AUTHZ_KEY = "knot_authorizations";
const FORCE_PASSWORD_CHANGE_KEY = "knot_force_password_change";

const token = ref<string | null>(getStorageItem(TOKEN_KEY));
const user = ref<Row | null>(getStorageJson<Row>(USER_KEY));
const authorizations = ref<Row>(getStorageJson<Row>(AUTHZ_KEY, { permissions: [], modules: [] }) as Row);
const forcePasswordChangeState = ref<Row>(
  (getStorageJson<Row>(FORCE_PASSWORD_CHANGE_KEY, { username: "", passwordChangeToken: "", realName: "" }) ||
    { username: "", passwordChangeToken: "", realName: "" }) as Row
);

// 登录流程和 MainLayout 挂载可能同时刷新授权信息；复用同一个请求，
// 避免重复请求在会话切换时互相覆盖状态。
let authorizationsRequest: Promise<Row | null> | null = null;
let authorizationsRequestToken: string | null = null;

export function useAuth() {
  const isLoggedIn = computed(() => !!token.value);
  const needsPasswordChange = computed(() => !!forcePasswordChangeState.value?.passwordChangeToken);
  const permissions = computed(() => authorizations.value?.permissions || []);
  const modules = computed(() => authorizations.value?.modules || []);

  function setToken(newToken: string): void {
    token.value = newToken;
    setStorageItem(TOKEN_KEY, newToken);
  }

  function setUser(newUser: Row): void {
    user.value = newUser;
    setStorageJson(USER_KEY, newUser);
  }

  function setAuthorizations(newValue: Row | null): void {
    const value = newValue || { permissions: [], modules: [] };
    authorizations.value = value;
    setStorageJson(AUTHZ_KEY, value);
  }

  function setForcePasswordChangeState(newValue: Row | null): void {
    const value = newValue || { username: "", passwordChangeToken: "", realName: "" };
    forcePasswordChangeState.value = value;
    if (value.passwordChangeToken) {
      setStorageJson(FORCE_PASSWORD_CHANGE_KEY, value);
      return;
    }
    removeStorageItem(FORCE_PASSWORD_CHANGE_KEY);
  }

  function clearAuth() {
    token.value = null;
    user.value = null;
    authorizations.value = { permissions: [], modules: [] };
    removeStorageItem(TOKEN_KEY);
    removeStorageItem(USER_KEY);
    removeStorageItem(AUTHZ_KEY);
    clearIdleActivity();
  }

  function clearForcePasswordChangeState() {
    setForcePasswordChangeState(null);
  }

  function clearAllAuthState() {
    clearAuth();
    clearForcePasswordChangeState();
  }

  const isAdmin = computed(() => (user.value?.roles || []).includes("ADMIN"));
  const hasPermission = (permissionCode: string) => permissions.value.includes(permissionCode);

  async function loadAuthorizations() {
    if (!token.value) {
      setAuthorizations({ permissions: [], modules: [] });
      return { permissions: [], modules: [] };
    }
    if (authorizationsRequest && authorizationsRequestToken === token.value) {
      return authorizationsRequest;
    }

    const requestToken = token.value;
    authorizationsRequestToken = requestToken;
    authorizationsRequest = getMyAuthorizations({ silentError: true })
      .then((response) => {
        // 请求返回期间可能已经退出或重新登录；旧响应不得覆盖新会话。
        if (token.value !== requestToken) {
          return null;
        }
        setAuthorizations(response);
        if (response) {
          setUser({
            ...((user.value || {}) as Dict),
            userId: response.userId,
            username: response.username,
            realName: response.realName,
            roles: response.roles || []
          });
        }
        return response;
      })
      .finally(() => {
        if (authorizationsRequestToken === requestToken) {
          authorizationsRequest = null;
          authorizationsRequestToken = null;
        }
      });
    return authorizationsRequest;
  }

  async function login(username: string, password: string): Promise<Row> {
    clearAllAuthState();
    const response = await apiLogin({ username, password });
    if (response.forcePasswordChange) {
      setForcePasswordChangeState({
        username: response.username,
        realName: response.realName,
        passwordChangeToken: response.passwordChangeToken
      });
      return response;
    }
    setToken(response.token);
    setUser({
      userId: response.userId,
      username: response.username,
      realName: response.realName,
      roles: response.roles || []
    });
    await loadAuthorizations();
    touchIdleActivity();
    return response;
  }

  async function submitForcedPasswordChange(newPassword: string): Promise<void> {
    const passwordChangeToken = forcePasswordChangeState.value?.passwordChangeToken;
    if (!passwordChangeToken) {
      throw new Error("改密会话已失效，请重新登录");
    }
    await apiForcePasswordChange({ passwordChangeToken, newPassword });
    clearAllAuthState();
  }

  async function logout() {
    try {
      if (token.value) {
        await apiLogout();
      }
    } finally {
      clearAllAuthState();
    }
  }

  return {
    token,
    user,
    authorizations,
    permissions,
    modules,
    isLoggedIn,
    isAdmin,
    hasPermission,
    needsPasswordChange,
    forcePasswordChangeState,
    loadAuthorizations,
    login,
    logout,
    submitForcedPasswordChange,
    clearAllAuthState
  };
}
