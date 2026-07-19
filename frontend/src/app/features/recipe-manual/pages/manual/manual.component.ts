import { Component, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { Router } from '@angular/router';
import { MatCardModule } from '@angular/material/card';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatChipsModule } from '@angular/material/chips';
import { HeaderComponent } from '../../../../shared/components/header/header.component';
import { ErrorBannerComponent } from '../../../../shared/components/error-banner/error-banner.component';
import { ApiService } from '../../../../core/services/api.service';
import { IngredientDraft, Recipe, StepDraft } from '../../../../core/models/recipe.model';
import { buildIngredientsPayload, buildStepsPayload, emptyIngredientDraft, emptyStepDraft } from '../../../../core/utils/recipe-form.util';

@Component({
  selector: 'app-manual-recipe',
  standalone: true,
  imports: [
    CommonModule, FormsModule, HeaderComponent, ErrorBannerComponent,
    MatCardModule, MatFormFieldModule, MatInputModule, MatButtonModule,
    MatIconModule, MatChipsModule
  ],
  templateUrl: './manual.component.html',
  styleUrl: './manual.component.scss'
})
export class ManualRecipeComponent {
  name = '';
  description = '';
  preparationTimeMinutes: number | null = null;
  cookTimeMinutes: number | null = null;
  servings = 2;
  estimatedKcal: number | null = null;

  tagInput = '';
  tags: string[] = [];

  ingredients: IngredientDraft[] = [emptyIngredientDraft()];
  steps: StepDraft[] = [emptyStepDraft()];

  // Zoneless: Zustand, der im HTTP-Subscribe-Callback gesetzt wird, muss ein Signal sein
  saving = signal(false);
  error = signal<string | null>(null);

  constructor(private api: ApiService, private router: Router) {}

  addIngredientRow(): void {
    this.ingredients.push(emptyIngredientDraft());
  }

  removeIngredientRow(index: number): void {
    this.ingredients.splice(index, 1);
  }

  addStepRow(): void {
    this.steps.push(emptyStepDraft());
  }

  removeStepRow(index: number): void {
    this.steps.splice(index, 1);
  }

  addTag(): void {
    const value = this.tagInput.trim();
    if (value && !this.tags.includes(value)) {
      this.tags.push(value);
    }
    this.tagInput = '';
  }

  removeTag(index: number): void {
    this.tags.splice(index, 1);
  }

  get canSubmit(): boolean {
    return this.name.trim().length > 0
      && this.ingredients.some(i => i.name.trim().length > 0)
      && this.steps.some(s => s.instruction.trim().length > 0);
  }

  submit(): void {
    if (!this.canSubmit || this.saving()) return;
    this.saving.set(true);
    this.error.set(null);

    const payload: Partial<Recipe> = {
      name: this.name.trim(),
      description: this.description.trim() || undefined,
      ingredients: buildIngredientsPayload(this.ingredients),
      steps: buildStepsPayload(this.steps),
      preparationTimeMinutes: this.preparationTimeMinutes ?? undefined,
      cookTimeMinutes: this.cookTimeMinutes ?? undefined,
      servings: this.servings || 1,
      estimatedKcal: this.estimatedKcal ?? undefined,
      tags: this.tags
    };

    this.api.createRecipe(payload).subscribe({
      next: (recipe) => this.router.navigate(['/recipes', recipe.id]),
      error: (err) => {
        this.saving.set(false);
        this.error.set(err.error?.message || 'Rezept konnte nicht gespeichert werden.');
      }
    });
  }
}
