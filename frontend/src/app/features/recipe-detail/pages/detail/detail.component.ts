import { Component, OnInit, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, Router } from '@angular/router';
import { MatCardModule } from '@angular/material/card';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatChipsModule } from '@angular/material/chips';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatListModule } from '@angular/material/list';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { HeaderComponent } from '../../../../shared/components/header/header.component';
import { LoadingSpinnerComponent } from '../../../../shared/components/loading-spinner/loading-spinner.component';
import { ErrorBannerComponent } from '../../../../shared/components/error-banner/error-banner.component';
import { RecipeImageComponent } from '../../../../shared/components/recipe-image/recipe-image.component';
import { ApiService } from '../../../../core/services/api.service';
import { RecipeService } from '../../../../core/services/recipe.service';
import { IngredientDraft, Recipe, StepDraft } from '../../../../core/models/recipe.model';
import {
  buildIngredientsPayload, buildStepsPayload,
  emptyIngredientDraft, emptyStepDraft,
  toIngredientDrafts, toStepDrafts
} from '../../../../core/utils/recipe-form.util';

/**
 * Recipe detail page: shows a recipe, supports inline editing, rating, and
 * deletion. The edit form is kept in plain fields; signals are used for state
 * that is updated from asynchronous callbacks.
 */
@Component({
  selector: 'app-detail',
  standalone: true,
  imports: [
    CommonModule, FormsModule, HeaderComponent, LoadingSpinnerComponent, ErrorBannerComponent, RecipeImageComponent,
    MatCardModule, MatButtonModule, MatIconModule, MatChipsModule,
    MatFormFieldModule, MatInputModule, MatListModule, MatProgressSpinnerModule
  ],
  templateUrl: './detail.component.html',
  styleUrl: './detail.component.scss'
})
export class DetailComponent implements OnInit {
  // Zoneless: state set inside subscribe callbacks must be a signal so the view
  // re-renders after the HTTP response.
  recipe = signal<Recipe | null>(null);
  editing = signal(false);
  saving = signal(false);
  editError = signal<string | null>(null);
  actionError = signal<string | null>(null);

  displayServings = 2;

  editName = '';
  editDescription = '';
  editPreparationTimeMinutes: number | null = null;
  editCookTimeMinutes: number | null = null;
  editServings = 2;
  editEstimatedKcal: number | null = null;
  editTagInput = '';
  editTags: string[] = [];
  editIngredients: IngredientDraft[] = [];
  editSteps: StepDraft[] = [];

  constructor(
    private route: ActivatedRoute,
    private router: Router,
    private api: ApiService,
    private recipeService: RecipeService
  ) {}

  /** Loads the recipe identified by the route's id parameter. */
  ngOnInit(): void {
    const id = this.route.snapshot.paramMap.get('id');
    if (!id) {
      this.router.navigate(['/recipes/library']);
      return;
    }
    this.api.getRecipe(id).subscribe({
      next: (recipe) => {
        this.recipe.set(recipe);
        this.displayServings = recipe.servings || 2;
      },
      error: () => this.router.navigate(['/recipes/library'])
    });
  }

  /** Scales a quantity from the recipe's servings to the currently displayed servings. */
  scaledQuantity(quantity: number): number {
    const servings = this.recipe()?.servings;
    if (!servings) return quantity;
    return quantity * (this.displayServings / servings);
  }

  /** Copies the recipe into the edit form fields and switches to edit mode. */
  startEdit(): void {
    const recipe = this.recipe();
    if (!recipe) return;
    this.editName = recipe.name;
    this.editDescription = recipe.description || '';
    this.editPreparationTimeMinutes = recipe.preparationTimeMinutes ?? null;
    this.editCookTimeMinutes = recipe.cookTimeMinutes ?? null;
    this.editServings = recipe.servings || 2;
    this.editEstimatedKcal = recipe.estimatedKcal ?? null;
    this.editTagInput = '';
    this.editTags = [...recipe.tags];
    this.editIngredients = recipe.ingredients.length ? toIngredientDrafts(recipe.ingredients) : [emptyIngredientDraft()];
    this.editSteps = recipe.steps.length ? toStepDrafts(recipe.steps) : [emptyStepDraft()];
    this.editError.set(null);
    this.editing.set(true);
  }

  /** Discards pending edits and leaves edit mode. */
  cancelEdit(): void {
    this.editing.set(false);
    this.editError.set(null);
  }

  /** Appends a blank ingredient row to the edit form. */
  addIngredientRow(): void {
    this.editIngredients.push(emptyIngredientDraft());
  }

  /** Removes the ingredient row at the given index. */
  removeIngredientRow(index: number): void {
    this.editIngredients.splice(index, 1);
  }

  /** Appends a blank step row to the edit form. */
  addStepRow(): void {
    this.editSteps.push(emptyStepDraft());
  }

  /** Removes the step row at the given index. */
  removeStepRow(index: number): void {
    this.editSteps.splice(index, 1);
  }

  /** Adds the trimmed tag input to the edit tag list. */
  addTag(): void {
    const value = this.editTagInput.trim();
    if (value && !this.editTags.includes(value)) {
      this.editTags.push(value);
    }
    this.editTagInput = '';
  }

  /** Removes the tag at the given index. */
  removeTag(index: number): void {
    this.editTags.splice(index, 1);
  }

  /** Whether the edit form has enough content to be saved. */
  get canSaveEdit(): boolean {
    return this.editName.trim().length > 0
      && this.editIngredients.some(i => i.name.trim().length > 0)
      && this.editSteps.some(s => s.instruction.trim().length > 0);
  }

  /** Persists the edited recipe and exits edit mode on success. */
  saveEdit(): void {
    const recipe = this.recipe();
    if (!recipe || !this.canSaveEdit) return;
    this.saving.set(true);
    this.editError.set(null);

    this.recipeService.updateRecipe(recipe.id, {
      name: this.editName.trim(),
      description: this.editDescription.trim() || undefined,
      ingredients: buildIngredientsPayload(this.editIngredients),
      steps: buildStepsPayload(this.editSteps),
      preparationTimeMinutes: this.editPreparationTimeMinutes ?? undefined,
      cookTimeMinutes: this.editCookTimeMinutes ?? undefined,
      servings: this.editServings || 1,
      estimatedKcal: this.editEstimatedKcal ?? undefined,
      tags: this.editTags
    }).subscribe({
      next: (updated) => {
        this.recipe.set(updated);
        this.displayServings = updated.servings || 2;
        this.editing.set(false);
        this.saving.set(false);
      },
      error: (err) => {
        this.saving.set(false);
        this.editError.set(err.error?.message || 'Änderungen konnten nicht gespeichert werden.');
      }
    });
  }

  /** Stores a star rating for the recipe. */
  rate(stars: number): void {
    const recipe = this.recipe();
    if (!recipe) return;
    this.actionError.set(null);
    this.api.rateRecipe(recipe.id, stars).subscribe({
      next: (updated) => this.recipe.set(updated),
      error: (err) => this.actionError.set(err.error?.message
        || 'Bewertung konnte nicht gespeichert werden.')
    });
  }

  /** Deletes the recipe after confirmation and returns to the library. */
  deleteRecipe(): void {
    const recipe = this.recipe();
    if (!recipe) return;
    if (!confirm(`Rezept "${recipe.name}" wirklich löschen?`)) return;
    this.actionError.set(null);
    this.recipeService.deleteRecipe(recipe.id).subscribe({
      next: () => this.router.navigate(['/recipes/library']),
      error: (err) => this.actionError.set(err.error?.message
        || 'Rezept konnte nicht gelöscht werden.')
    });
  }
}
