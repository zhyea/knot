import { ref, computed } from 'vue';
import { login as apiLogin, logout as apiLogout } from '../api/auth';

const TOKEN_KEY = 'knot_token';
const USER_KEY = 'knot_user';

const token = ref(localStorage.getItem(TOKEN_KEY));
const user = ref(JSON.parse(localStorage.getItem(USER_KEY) || 'null'));

export function useAuth() {
  const isLoggedIn = computed(() => !!token.value);

  function setToken(newToken) {
    token.value = newToken;
    localStorage.setItem(TOKEN_KEY, newToken);
  }

  function setUser(newUser) {
    user.value = newUser;
    localStorage.setItem(USER_KEY, JSON.stringify(newUser));
  }

  function clearAuth() {
    token.value = null;
    user.value = null;
    localStorage.removeItem(TOKEN_KEY);
    localStorage.removeItem(USER_KEY);
  }

  async function login(username, password) {
    const response = await apiLogin({ username, password });
    setToken(response.token);
    setUser({
      userId: response.userId,
      username: response.username,
      realName: response.realName,
    });
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
    login,
    logout,
  };
}
