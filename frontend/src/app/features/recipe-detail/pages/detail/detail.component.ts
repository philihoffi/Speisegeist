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
import { ApiService } from '../../../../core/services/api.service';
import { RecipeService } from '../../../../core/services/recipe.service';
import { IngredientDraft, Recipe, StepDraft } from '../../../../core/models/recipe.model';
import {
  buildIngredientsPayload, buildStepsPayload,
  emptyIngredientDraft, emptyStepDraft,
  toIngredientDrafts, toStepDrafts
} from '../../../../core/utils/recipe-form.util';

@Component({
  selector: 'app-detail',
  standalone: true,
  imports: [
    CommonModule, FormsModule, HeaderComponent, LoadingSpinnerComponent, ErrorBannerComponent,
    MatCardModule, MatButtonModule, MatIconModule, MatChipsModule,
    MatFormFieldModule, MatInputModule, MatListModule, MatProgressSpinnerModule
  ],
  templateUrl: './detail.component.html',
  styleUrl: './detail.component.scss'
})
export class DetailComponent implements OnInit {
  // Zoneless: Zustand, der in Subscribe-Callbacks gesetzt wird, muss ein Signal sein,
  // sonst rendert die View nach dem HTTP-Response nicht neu.
  recipe = signal<Recipe | null>(null);
  editing = signal(false);
  saving = signal(false);
  editError = signal<string | null>(null);

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

  scaledQuantity(quantity: number): number {
    const servings = this.recipe()?.servings;
    if (!servings) return quantity;
    return quantity * (this.displayServings / servings);
  }

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

  cancelEdit(): void {
    this.editing.set(false);
    this.editError.set(null);
  }

  addIngredientRow(): void {
    this.editIngredients.push(emptyIngredientDraft());
  }

  removeIngredientRow(index: number): void {
    this.editIngredients.splice(index, 1);
  }

  addStepRow(): void {
    this.editSteps.push(emptyStepDraft());
  }

  removeStepRow(index: number): void {
    this.editSteps.splice(index, 1);
  }

  addTag(): void {
    const value = this.editTagInput.trim();
    if (value && !this.editTags.includes(value)) {
      this.editTags.push(value);
    }
    this.editTagInput = '';
  }

  removeTag(index: number): void {
    this.editTags.splice(index, 1);
  }

  get canSaveEdit(): boolean {
    return this.editName.trim().length > 0
      && this.editIngredients.some(i => i.name.trim().length > 0)
      && this.editSteps.some(s => s.instruction.trim().length > 0);
  }

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

  rate(stars: number): void {
    const recipe = this.recipe();
    if (!recipe) return;
    this.api.rateRecipe(recipe.id, stars).subscribe(updated => {
      this.recipe.set(updated);
    });
  }

  deleteRecipe(): void {
    const recipe = this.recipe();
    if (!recipe) return;
    if (!confirm(`Rezept "${recipe.name}" wirklich löschen?`)) return;
    this.recipeService.deleteRecipe(recipe.id).subscribe(() => {
      this.router.navigate(['/recipes/library']);
    });
  }
}
