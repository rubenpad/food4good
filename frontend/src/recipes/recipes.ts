import { useMutation, useQuery, useQueryCache } from "@pinia/colada";
import { getJson, sendJson } from "../api/http";

export const INGREDIENTS = [
  "cheese",
  "chicken",
  "ketchup",
  "lemon",
  "lettuce",
  "meat",
  "onion",
  "potato",
  "rice",
  "tomato",
];

export interface IngredientQuantity {
  ingredient: string;
  quantity: number;
}

export interface RecipeDetails {
  name: string;
  preparationTimeSeconds: number;
  ingredients: IngredientQuantity[];
}

export interface Recipe extends RecipeDetails {
  id: string;
}

const RECIPES_KEY = ["recipes"];

export function useRecipes() {
  return useQuery({
    key: RECIPES_KEY,
    query: () => getJson<Recipe[]>("/recipes"),
  });
}

/** Creates a Recipe, or edits the one with the given id. */
export function useSaveRecipe() {
  const queryCache = useQueryCache();
  return useMutation({
    mutation: ({ id, details }: { id?: string; details: RecipeDetails }) =>
      id
        ? sendJson<Recipe>("PUT", `/recipes/${id}`, details)
        : sendJson<Recipe>("POST", "/recipes", details),
    onSettled: () => queryCache.invalidateQueries({ key: RECIPES_KEY }),
  });
}

export function formatDuration(seconds: number): string {
  const minutes = Math.floor(seconds / 60);
  const rest = seconds % 60;
  if (minutes === 0) return `${rest} s`;
  return rest === 0 ? `${minutes} min` : `${minutes} min ${rest} s`;
}
