"use client";

import { useSyncExternalStore } from "react";

const INN_KEY = "credit-cabinet:inn";
const listeners = new Set<() => void>();

function read() {
  try {
    return window.localStorage.getItem(INN_KEY) ?? process.env.NEXT_PUBLIC_DEMO_INN ?? null;
  } catch {
    return process.env.NEXT_PUBLIC_DEMO_INN ?? null;
  }
}

export function saveClientInn(inn: string) {
  try {
    window.localStorage.setItem(INN_KEY, inn);
  } catch {}
  listeners.forEach((notify) => notify());
}

function subscribe(notify: () => void) {
  listeners.add(notify);
  return () => listeners.delete(notify);
}

export function useClientInn() {
  return useSyncExternalStore(subscribe, read, () => null);
}
