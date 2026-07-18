import { Component, OnInit } from '@angular/core';
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
import { ApiService } from '../../../../core/services/api.service';
import { RecipeService } from '../../../../core/services/recipe.service';
import { Recipe } from '../../../../core/models/recipe.model';

@Component({
  selector: 'app-detail',
  standalone: true,
  imports: [
    CommonModule, FormsModule, HeaderComponent,
    MatCardModule, MatButtonModule, MatIconModule, MatChipsModule,
    MatFormFieldModule, MatInputModule, MatListModule, MatProgressSpinnerModule
  ],
  templateUrl: './detail.component.html',
  styleUrl: './detail.component.scss'
})
export class DetailComponent implements OnInit {
  recipe: Recipe | null = null;
  displayServings = 2;

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
        this.recipe = recipe;
        this.displayServings = recipe.servings || 2;
      },
      error: () => this.router.navigate(['/recipes/library'])
    });
  }

  scaledQuantity(quantity: number): number {
    if (!this.recipe?.servings) return quantity;
    return quantity * (this.displayServings / this.recipe.servings);
  }

  rate(stars: number): void {
    if (!this.recipe) return;
    this.api.rateRecipe(this.recipe.id, stars).subscribe(updated => {
      this.recipe = updated;
    });
  }

  deleteRecipe(): void {
    if (!this.recipe) return;
    if (!confirm(`Rezept "${this.recipe.name}" wirklich löschen?`)) return;
    this.recipeService.deleteRecipe(this.recipe.id).subscribe(() => {
      this.router.navigate(['/recipes/library']);
    });
  }
}
