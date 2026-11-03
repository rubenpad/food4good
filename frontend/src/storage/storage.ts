import { useQuery } from "@pinia/colada";
import type { Ref } from "vue";
import { getJson } from "../api/http";

export interface IngredientStock {
  ingredient: string;
  onHand: number;
  reserved: number;
  available: number;
}

export interface Purchase {
  id: number;
  ingredient: string;
  quantity: number;
  purchasedAt: string;
}

export interface PurchasePage {
  items: Purchase[];
  nextCursor: string | null;
}

const PURCHASE_PAGE_SIZE = 10;

export function usePurchasePage(cursor: Ref<string | null>) {
  return useQuery({
    key: () => ["purchases", cursor.value ?? "first"],
    query: () => {
      const params = new URLSearchParams({ limit: String(PURCHASE_PAGE_SIZE) });
      if (cursor.value) params.set("cursor", cursor.value);
      return getJson<PurchasePage>(`/purchases?${params}`);
    },
    placeholderData: (previous: PurchasePage | undefined) => previous,
  });
}

export function useStock() {
  return useQuery({
    key: ["storage"],
    query: () => getJson<IngredientStock[]>("/storage"),
  });
}
