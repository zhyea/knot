import { computed, ref } from "vue";
import { forcePasswordChange as apiForcePasswordChange, login as apiLogin, logout as apiLogout } from "../api/auth";
import { getMyAuthorizations } from "../api/authorizations";
import { clearIdleActivity, touchIdleActivity } from "./idleActivity";
import {
  getStorageItem,
  getStorageJson,
  removeStorageItem,
  setStorageItem,
  setStorageJson
} from "../utils/storage";

const TOKEN_KEY = "knot_token";
const USER_KEY = "knot_user";
const AUTHZ_KEY = "knot_authorizations";
const FORCE_PASSWORD_CHANGE_KEY = "knot_force_password_change";

const token = ref(getStorageItem(TOKEN_KEY));
const user = ref(getStorageJson(USER_KEY));
const authorizations = ref(getStorageJson(AUTHZ_KEY, { permissions: [], modules: [] }));
const forcePasswordChangeState = ref(
  getStorageJson(FORCE_PASSWORD_CHANGE_KEY, { username: "", passwordChangeToken: "", realName: "" })
);

export function useAuth() {
  const isLoggedIn = computed(() => !!token.value);
  const needsPasswordChange = computed(() => !!forcePasswordChangeState.value?.passwordChangeToken);
  const permissions = computed(() => authorizations.value?.permissions || []);
  const modules = computed(() => authorizations.value?.modules || []);

  function setToken(newToken) {
    token.value = newToken;
    setStorageItem(TOKEN_KEY, newToken);
  }

  function setUser(newUser) {
    user.value = newUser;
    setStorageJson(USER_KEY, newUser);
  }

  function setAuthorizations(newValue) {
    const value = newValue || { permissions: [], modules: [] };
    authorizations.value = value;
    setStorageJson(AUTHZ_KEY, value);
  }

  function setForcePasswordChangeState(newValue) {
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
  const hasPermission = (permissionCode) => permissions.value.includes(permissionCode);

  async function loadAuthorizations() {
    if (!token.value) {
      setAuthorizations({ permissions: [], modules: [] });
      return { permissions: [], modules: [] };
    }
    const response = await getMyAuthorizations({ silentError: true });
    setAuthorizations(response);
    if (response) {
      setUser({
        ...(user.value || {}),
        userId: response.userId,
        username: response.username,
        realName: response.realName,
        roles: response.roles || []
      });
    }
    return response;
  }

  async function login(username, password) {
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

  async function submitForcedPasswordChange(newPassword) {
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
