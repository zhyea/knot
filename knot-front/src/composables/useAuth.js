import { ref, computed } from "vue";
import { login as apiLogin, logout as apiLogout } from "../api/auth";
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

const token = ref(getStorageItem(TOKEN_KEY));
const user = ref(getStorageJson(USER_KEY));

export function useAuth() {
  const isLoggedIn = computed(() => !!token.value);

  function setToken(newToken) {
    token.value = newToken;
    setStorageItem(TOKEN_KEY, newToken);
  }

  function setUser(newUser) {
    user.value = newUser;
    setStorageJson(USER_KEY, newUser);
  }

  function clearAuth() {
    token.value = null;
    user.value = null;
    removeStorageItem(TOKEN_KEY);
    removeStorageItem(USER_KEY);
    clearIdleActivity();
  }

  const isAdmin = computed(() => (user.value?.roles || []).includes("ADMIN"));

  async function login(username, password) {
    const response = await apiLogin({ username, password });
    setToken(response.token);
    setUser({
      userId: response.userId,
      username: response.username,
      realName: response.realName,
      roles: response.roles || []
    });
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
    isLoggedIn,
    isAdmin,
    login,
    logout
  };
}
