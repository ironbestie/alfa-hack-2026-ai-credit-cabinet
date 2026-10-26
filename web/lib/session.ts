"use client";

import { useSyncExternalStore } from "react";

export type Role = "client" | "manager";

const INN_KEY = "credit-cabinet:inn";
const ROLE_KEY = "credit-cabinet:role";
const listeners = new Set<() => void>();

function readStorage(key: string) {
  try {
    return window.localStorage.getItem(key);
  } catch {
    return null;
  }
}

function writeStorage(key: string, value: string | null) {
  try {
    if (value === null) window.localStorage.removeItem(key);
    else window.localStorage.setItem(key, value);
  } catch {}
  listeners.forEach((notify) => notify());
}

function subscribe(notify: () => void) {
  listeners.add(notify);
  return () => listeners.delete(notify);
}

export function getRole(): Role | null {
  if (typeof window === "undefined") return null;
  const role = readStorage(ROLE_KEY);
  return role === "client" || role === "manager" ? role : null;
}

export function setRole(role: Role | null) {
  writeStorage(ROLE_KEY, role);
}

export function useRole() {
  return useSyncExternalStore(subscribe, getRole, () => null);
}

function readInn() {
  return readStorage(INN_KEY) ?? process.env.NEXT_PUBLIC_DEMO_INN ?? null;
}

export function saveClientInn(inn: string) {
  writeStorage(INN_KEY, inn);
}

export function useClientInn() {
  return useSyncExternalStore(subscribe, readInn, () => null);
}
