import { parseJson, stringifyJson } from "./format";

export function getStorageItem(key) {
  try {
    return localStorage.getItem(key);
  } catch {
    return null;
  }
}

export function setStorageItem(key, value) {
  try {
    localStorage.setItem(key, value);
  } catch {
    // ignore storage write failures
  }
}

export function removeStorageItem(key) {
  try {
    localStorage.removeItem(key);
  } catch {
    // ignore storage removal failures
  }
}

export function hasStorageItem(key) {
  return !!getStorageItem(key);
}

export function getStorageJson(key, fallback = null) {
  const raw = getStorageItem(key);
  if (!raw) {
    return fallback;
  }
  return parseJson(raw, fallback);
}

export function setStorageJson(key, value) {
  try {
    setStorageItem(key, stringifyJson(value));
  } catch {
    // ignore json serialization failures
  }
}
