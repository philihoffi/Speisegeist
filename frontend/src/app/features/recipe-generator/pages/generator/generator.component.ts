import { Component } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { Router } from '@angular/router';
import { MatCardModule } from '@angular/material/card';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatChipsModule } from '@angular/material/chips';
import { MatSelectModule } from '@angular/material/select';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { HeaderComponent } from '../../../../shared/components/header/header.component';
import { ErrorBannerComponent } from '../../../../shared/components/error-banner/error-banner.component';
import { RecipeService } from '../../../../core/services/recipe.service';

/**
 * Recipe generator page: collects ingredients and optional preferences, then
 * requests an AI-generated recipe and navigates to its detail page.
 */
@Component({
  selector: 'app-generator',
  standalone: true,
  imports: [
    CommonModule, FormsModule, HeaderComponent, ErrorBannerComponent,
    MatCardModule, MatFormFieldModule, MatInputModule, MatButtonModule,
    MatIconModule, MatChipsModule, MatSelectModule, MatProgressSpinnerModule
  ],
  templateUrl: './generator.component.html',
  styleUrl: './generator.component.scss'
})
export class GeneratorComponent {
  ingredientInput = '';
  ingredients: string[] = [];
  cuisine = '';
  cookTime: number | null = null;
  servings = 2;

  constructor(public recipeService: RecipeService, private router: Router) {}

  /** Adds the trimmed ingredient input to the ingredient list, ignoring duplicates. */
  addIngredient(): void {
    const value = this.ingredientInput.trim();
    if (value && !this.ingredients.includes(value)) {
      this.ingredients.push(value);
    }
    this.ingredientInput = '';
  }

  /** Removes the ingredient at the given index. */
  removeIngredient(index: number): void {
    this.ingredients.splice(index, 1);
  }

  /** Builds the preferences and triggers recipe generation. */
  generate(): void {
    const preferences = {
      cuisine: this.cuisine || undefined,
      cookTime: this.cookTime || undefined,
      servings: this.servings || undefined
    };

    this.recipeService.generateRecipe(this.ingredients, preferences).subscribe({
      next: (recipe) => this.router.navigate(['/recipes', recipe.id]),
      error: () => { /* error state handled by RecipeService.error$ */ }
    });
  }
}
