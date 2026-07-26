/** A catalog ingredient (global master data). */
export interface Ingredient {
  id: string;
  name: string;
  createdAt: string;
}

/** Payload for creating or updating a catalog ingredient. */
export interface IngredientRequest {
  name: string;
}

/** Query filters used when listing catalog ingredients. */
export interface IngredientFilters {
  search?: string;
  page?: number;
  size?: number;
}
