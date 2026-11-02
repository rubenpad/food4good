import { useQuery } from "@pinia/colada";
import { getJson } from "../api/http";

export interface IngredientQuantity {
  ingredient: string;
  quantity: number;
}

export interface Recipe {
  name: string;
  preparationTimeSeconds: number;
  ingredients: IngredientQuantity[];
}

export function useRecipes() {
  return useQuery({
    key: ["recipes"],
    query: () => getJson<Recipe[]>("/recipes"),
    // Recipes are fixed configuration; they only change when the backend restarts.
    staleTime: Infinity,
  });
}

export function formatDuration(seconds: number): string {
  const minutes = Math.floor(seconds / 60);
  const rest = seconds % 60;
  if (minutes === 0) return `${rest} s`;
  return rest === 0 ? `${minutes} min` : `${minutes} min ${rest} s`;
}
