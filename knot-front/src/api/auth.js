import { post } from './http';

export function login(data) {
  return post('/api/auth/login', data);
}

export function logout() {
  return post('/api/auth/logout');
}
