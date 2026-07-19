/** A user-owned recipe. */
export interface Recipe {
  id: string;
  userId: string;
  name: string;
  description?: string;
  ingredients: RecipeIngredient[];
  steps: CookingStep[];
  preparationTimeMinutes?: number;
  cookTimeMinutes?: number;
  servings: number;
  estimatedKcal?: number;
  rating?: number;
  tags: string[];
  sourceType: 'GENERATED' | 'MANUAL';
  sourceModel?: string;
  sourceVersion?: string;
  generatedAt?: string;
  createdAt: string;
  updatedAt: string;
}

/** An ingredient used within a recipe. */
export interface RecipeIngredient {
  name: string;
  quantity?: number;
  unit?: string;
  warengruppe?: string;
  notes?: string;
}

/** A single cooking step. */
export interface CookingStep {
  stepNumber: number;
  instruction: string;
  durationMinutes?: number;
}

/** Query filters used when searching recipes. */
export interface SearchFilters {
  search?: string;
  tag?: string;
  page?: number;
  size?: number;
}

/** Generic paged response envelope. */
export interface PageResponse<T> {
  content: T[];
  totalElements: number;
  totalPages: number;
  currentPage: number;
}

/** Editable draft of an ingredient in a form. */
export interface IngredientDraft {
  name: string;
  quantity: number | null;
  unit: string;
  warengruppe: string;
  notes: string;
}

/** Editable draft of a cooking step in a form. */
export interface StepDraft {
  instruction: string;
  durationMinutes: number | null;
}

/** Request payload to generate a recipe from ingredients and preferences. */
export interface RecipeGenerationRequest {
  ingredients: string[];
  preferences?: {
    cuisine?: string;
    mealType?: string;
    cookTime?: number;
    servings?: number;
  };
}
