import { useMutation, useQuery, useQueryCache } from "@pinia/colada";
import type { Ref } from "vue";
import { getJson, sendJson } from "../api/http";

export type OrderStatus = "WAITING_FOR_INGREDIENTS" | "READY" | "PREPARING" | "DELIVERED";

export const ORDER_STATUSES: { value: OrderStatus; label: string; severity: string }[] = [
  { value: "WAITING_FOR_INGREDIENTS", label: "Waiting for ingredients", severity: "warn" },
  { value: "READY", label: "Ready", severity: "info" },
  { value: "PREPARING", label: "Preparing", severity: "contrast" },
  { value: "DELIVERED", label: "Delivered", severity: "success" },
];

export interface Order {
  id: string;
  recipeId: string;
  recipe: string;
  preparationTimeSeconds: number;
  status: OrderStatus;
  acceptedAt: string;
  readyAt: string | null;
  preparingAt: string | null;
  deliveredAt: string | null;
}

export interface OrderPage {
  items: Order[];
  nextCursor: string | null;
}

export interface PlacedOrders {
  requested: number;
  accepted: number;
  orderIds: string[];
  estimatedWaitSeconds: number;
}

const PAGE_SIZE = 20;

export function useOrderPage(status: Ref<OrderStatus | null>, cursor: Ref<string | null>) {
  return useQuery({
    key: () => ["orders", status.value ?? "all", cursor.value ?? "first"],
    query: () => {
      const params = new URLSearchParams({ limit: String(PAGE_SIZE) });
      if (status.value) params.set("status", status.value);
      if (cursor.value) params.set("cursor", cursor.value);
      return getJson<OrderPage>(`/orders?${params}`);
    },
    placeholderData: (previous: OrderPage | undefined) => previous,
  });
}

export function usePlaceOrders() {
  const queryCache = useQueryCache();
  return useMutation({
    mutation: (quantity: number | null) => sendJson<PlacedOrders>("POST", "/orders", { quantity }),
    onSettled: () => {
      queryCache.invalidateQueries({ key: ["orders"] });
      queryCache.invalidateQueries({ key: ["storage"] });
    },
  });
}
