import { Component, Input } from '@angular/core';
import { CommonModule } from '@angular/common';
import { Router } from '@angular/router';
import { MatCardModule } from '@angular/material/card';
import { MatChipsModule } from '@angular/material/chips';
import { MatIconModule } from '@angular/material/icon';
import { Recipe } from '../../../core/models/recipe.model';
import { RecipeImageComponent } from '../recipe-image/recipe-image.component';

/**
 * Card preview of a recipe shown in lists; navigates to the detail page on click.
 */
@Component({
  selector: 'app-recipe-card',
  standalone: true,
  imports: [CommonModule, MatCardModule, MatChipsModule, MatIconModule, RecipeImageComponent],
  templateUrl: './recipe-card.component.html',
  styleUrl: './recipe-card.component.scss'
})
export class RecipeCardComponent {
  @Input({ required: true }) recipe!: Recipe;

  constructor(private router: Router) {}

  /** Returns the sum of preparation and cooking time in minutes. */
  totalTime(): number {
    return (this.recipe.preparationTimeMinutes || 0) + (this.recipe.cookTimeMinutes || 0);
  }

  /** Navigates to the recipe detail page. */
  open(): void {
    this.router.navigate(['/recipes', this.recipe.id]);
  }
}
