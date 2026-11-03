import { useQuery } from "@pinia/colada";
import { getJson } from "../api/http";

export interface Kitchen {
  cooks: number;
  busyCooks: number;
  ordersWaitingForIngredients: number;
  ordersReady: number;
  ordersPreparing: number;
  maxWaitSeconds: number;
  backlogSeconds: number;
  estimatedCapacity: number;
}

export function useKitchen() {
  return useQuery({
    key: ["kitchen"],
    query: () => getJson<Kitchen>("/kitchen"),
  });
}
