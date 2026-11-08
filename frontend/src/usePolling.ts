import { onScopeDispose } from "vue";

/** Refreshes on an interval until the component goes away. Kitchen capacity still uses this until its live updates arrive (#9). */
export function usePolling(refresh: () => unknown, intervalMs = 2_000) {
  const timer = setInterval(refresh, intervalMs);
  onScopeDispose(() => clearInterval(timer));
}
