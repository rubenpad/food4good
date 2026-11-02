import { useQuery } from "@pinia/colada";
import { getJson } from "../api/http";

export interface IngredientStock {
  ingredient: string;
  onHand: number;
  reserved: number;
  available: number;
}

export function useStock() {
  return useQuery({
    key: ["storage"],
    query: () => getJson<IngredientStock[]>("/storage"),
  });
}
