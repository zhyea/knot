import { parseJson, stringifyJson } from "./format";

export function getStorageItem(key: string): string | null {
  try {
    return localStorage.getItem(key);
  } catch {
    return null;
  }
}

export function setStorageItem(key: string, value: string): void {
  try {
    localStorage.setItem(key, value);
  } catch {
    // ignore storage write failures
  }
}

export function removeStorageItem(key: string): void {
  try {
    localStorage.removeItem(key);
  } catch {
    // ignore storage removal failures
  }
}

export function hasStorageItem(key: string): boolean {
  return !!getStorageItem(key);
}

export function getStorageJson<T = unknown>(key: string, fallback: T | null = null): T | null {
  const raw = getStorageItem(key);
  if (!raw) {
    return fallback;
  }
  return parseJson(raw, fallback) as T | null;
}

export function setStorageJson(key: string, value: unknown): void {
  try {
    setStorageItem(key, stringifyJson(value));
  } catch {
    // ignore json serialization failures
  }
}
