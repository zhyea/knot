import { ref, computed } from "vue";
import { login as apiLogin, logout as apiLogout } from "../api/auth";
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

const token = ref(getStorageItem(TOKEN_KEY));
const user = ref(getStorageJson(USER_KEY));
const authorizations = ref(getStorageJson(AUTHZ_KEY, { permissions: [], modules: [] }));

export function useAuth() {
  const isLoggedIn = computed(() => !!token.value);
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

  function clearAuth() {
    token.value = null;
    user.value = null;
    authorizations.value = { permissions: [], modules: [] };
    removeStorageItem(TOKEN_KEY);
    removeStorageItem(USER_KEY);
    removeStorageItem(AUTHZ_KEY);
    clearIdleActivity();
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
    const response = await apiLogin({ username, password });
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

  async function logout() {
    try {
      await apiLogout();
    } finally {
      clearAuth();
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
    loadAuthorizations,
    login,
    logout
  };
}
