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

export interface RecipeIngredient {
  name: string;
  quantity?: number;
  unit?: string;
  warengruppe?: string;
  notes?: string;
}

export interface CookingStep {
  stepNumber: number;
  instruction: string;
  durationMinutes?: number;
}

export interface SearchFilters {
  search?: string;
  tag?: string;
  page?: number;
  size?: number;
}

export interface PageResponse<T> {
  content: T[];
  totalElements: number;
  totalPages: number;
  currentPage: number;
}

export interface RecipeGenerationRequest {
  ingredients: string[];
  preferences?: {
    cuisine?: string;
    mealType?: string;
    cookTime?: number;
    servings?: number;
  };
}
