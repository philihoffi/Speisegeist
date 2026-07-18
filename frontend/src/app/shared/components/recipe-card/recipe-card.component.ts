import { Component, Input } from '@angular/core';
import { CommonModule } from '@angular/common';
import { Router } from '@angular/router';
import { MatCardModule } from '@angular/material/card';
import { MatChipsModule } from '@angular/material/chips';
import { MatIconModule } from '@angular/material/icon';
import { Recipe } from '../../../core/models/recipe.model';

@Component({
  selector: 'app-recipe-card',
  standalone: true,
  imports: [CommonModule, MatCardModule, MatChipsModule, MatIconModule],
  templateUrl: './recipe-card.component.html',
  styleUrl: './recipe-card.component.scss'
})
export class RecipeCardComponent {
  @Input({ required: true }) recipe!: Recipe;

  constructor(private router: Router) {}

  totalTime(): number {
    return (this.recipe.preparationTimeMinutes || 0) + (this.recipe.cookTimeMinutes || 0);
  }

  open(): void {
    this.router.navigate(['/recipes', this.recipe.id]);
  }
}
