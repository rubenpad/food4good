import { onScopeDispose } from "vue";
import { useQueryCache } from "@pinia/colada";
import type { Order, OrderPage } from "./orders/orders";
import type { IngredientStock } from "./storage/storage";

const RECONNECT_MS = 2_000;

/**
 * Keeps the query cache current from the one SSE stream. Changed rows are patched in place. When an
 * Order joins or leaves a page, that page is refetched instead, because its "older" cursor would
 * otherwise skip Orders. After any (re)connect everything is refetched to cover what was missed.
 */
export function useLiveEvents() {
  const queryCache = useQueryCache();
  let source: EventSource | null = null;
  let retry: ReturnType<typeof setTimeout> | undefined;

  function patchOrders(changed: Order[]) {
    const byId = new Map(changed.map((order) => [order.id, order]));
    for (const entry of queryCache.getEntries({ key: ["orders"] })) {
      const page = entry.state.value.data as OrderPage | undefined;
      if (!page) continue;
      const [, status, cursor] = entry.key;
      const fits = (order: Order) => status === "all" || order.status === status;
      const shown = new Set(page.items.map((order) => order.id));
      const items = page.items.map((order) => byId.get(order.id) ?? order);
      // A new Order only reaches the first page; with a filter, any page may gain an older Order.
      const canGainOrders = status !== "all" || cursor === "first";
      const membershipChanged =
        items.some((order) => !fits(order)) ||
        (canGainOrders && changed.some((order) => fits(order) && !shown.has(order.id)));
      queryCache.setQueryData(entry.key, { ...page, items });
      if (membershipChanged) {
        void queryCache.invalidateQueries({ key: entry.key, exact: true });
      }
    }
  }

  function connect() {
    source = new EventSource("/api/events");
    source.onopen = () => void queryCache.invalidateQueries();
    source.addEventListener("orders", (event) => patchOrders(JSON.parse(event.data) as Order[]));
    source.addEventListener("storage", (event) =>
      queryCache.setQueryData(["storage"], JSON.parse(event.data) as IngredientStock[]),
    );
    // The browser retries a dropped stream by itself, but gives up on an error response.
    source.onerror = () => {
      if (source?.readyState === EventSource.CLOSED) {
        retry = setTimeout(connect, RECONNECT_MS);
      }
    };
  }

  connect();
  onScopeDispose(() => {
    clearTimeout(retry);
    source?.close();
  });
}
