import { onScopeDispose } from "vue";

/** Refreshes on an interval until the component goes away. Live updates over SSE will replace this. */
export function usePolling(refresh: () => unknown, intervalMs = 2_000) {
  const timer = setInterval(refresh, intervalMs);
  onScopeDispose(() => clearInterval(timer));
}
