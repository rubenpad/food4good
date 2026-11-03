import { onScopeDispose } from "vue";

/** Refreshes on an interval until the component goes away. Purchases and Kitchen capacity still use this until their live updates arrive (#8, #9). */
export function usePolling(refresh: () => unknown, intervalMs = 2_000) {
  const timer = setInterval(refresh, intervalMs);
  onScopeDispose(() => clearInterval(timer));
}
