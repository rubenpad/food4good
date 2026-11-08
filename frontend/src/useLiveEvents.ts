import { onScopeDispose } from "vue";
import { useQueryCache } from "@pinia/colada";
import type { Kitchen } from "./kitchen/kitchen";
import type { Order, OrderPage } from "./orders/orders";
import {
  PURCHASE_PAGE_SIZE,
  type IngredientStock,
  type Purchase,
  type PurchasePage,
} from "./storage/storage";

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

  /** New Purchases only ever reach the first page; older pages never change. */
  function addPurchases(added: Purchase[]) {
    const key = ["purchases", "first"];
    const page = queryCache.getQueryData(key) as PurchasePage | undefined;
    if (!page) return;
    const shown = new Set(page.items.map((purchase) => purchase.id));
    const merged = [
      ...added.filter((purchase) => !shown.has(purchase.id)).reverse(),
      ...page.items,
    ];
    if (merged.length === page.items.length) return;
    const items = merged.slice(0, PURCHASE_PAGE_SIZE);
    const nextCursor =
      merged.length > PURCHASE_PAGE_SIZE ? String(items.at(-1)!.id) : page.nextCursor;
    queryCache.setQueryData(key, { items, nextCursor });
  }

  function connect() {
    source = new EventSource("/api/events");
    source.onopen = () => void queryCache.invalidateQueries();
    source.addEventListener("orders", (event) => patchOrders(JSON.parse(event.data) as Order[]));
    source.addEventListener("storage", (event) =>
      queryCache.setQueryData(["storage"], JSON.parse(event.data) as IngredientStock[]),
    );
    source.addEventListener("kitchen", (event) =>
      queryCache.setQueryData(["kitchen"], JSON.parse(event.data) as Kitchen),
    );
    source.addEventListener("purchases", (event) =>
      addPurchases(JSON.parse(event.data) as Purchase[]),
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
