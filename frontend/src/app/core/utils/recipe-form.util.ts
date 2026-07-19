import { IngredientDraft, RecipeIngredient, StepDraft, CookingStep } from '../models/recipe.model';

export function emptyIngredientDraft(): IngredientDraft {
  return { name: '', quantity: null, unit: '', warengruppe: '', notes: '' };
}

export function emptyStepDraft(): StepDraft {
  return { instruction: '', durationMinutes: null };
}

export function toIngredientDrafts(ingredients: RecipeIngredient[]): IngredientDraft[] {
  return ingredients.map(i => ({
    name: i.name,
    quantity: i.quantity ?? null,
    unit: i.unit || '',
    warengruppe: i.warengruppe || '',
    notes: i.notes || ''
  }));
}

export function toStepDrafts(steps: CookingStep[]): StepDraft[] {
  return steps.map(s => ({ instruction: s.instruction, durationMinutes: s.durationMinutes ?? null }));
}

export function buildIngredientsPayload(rows: IngredientDraft[]) {
  return rows
    .filter(i => i.name.trim().length > 0)
    .map(i => ({
      name: i.name.trim(),
      quantity: i.quantity ?? undefined,
      unit: i.unit.trim() || undefined,
      warengruppe: i.warengruppe.trim() || undefined,
      notes: i.notes.trim() || undefined
    }));
}

export function buildStepsPayload(rows: StepDraft[]) {
  return rows
    .filter(s => s.instruction.trim().length > 0)
    .map((s, index) => ({
      stepNumber: index + 1,
      instruction: s.instruction.trim(),
      durationMinutes: s.durationMinutes ?? undefined
    }));
}
