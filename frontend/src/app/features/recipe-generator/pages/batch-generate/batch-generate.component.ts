import { Component, OnDestroy, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { MatCardModule } from '@angular/material/card';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { Subscription } from 'rxjs';
import { HeaderComponent } from '../../../../shared/components/header/header.component';
import { ErrorBannerComponent } from '../../../../shared/components/error-banner/error-banner.component';
import { RecipeCardComponent } from '../../../../shared/components/recipe-card/recipe-card.component';
import { RecipeService } from '../../../../core/services/recipe.service';
import { Recipe } from '../../../../core/models/recipe.model';

interface RecipeErrorEntry {
  index: number;
  message: string;
}

interface BatchStreamEvent {
  type: 'recipe' | 'recipe-error' | 'batch-progress' | 'batch-complete';
  data: any;
}

/**
 * Generates a batch of recipes from a free-text theme instead of an explicit ingredient
 * list. Recipes appear one by one as they're generated; a single failed recipe doesn't
 * stop the rest of the batch.
 */
@Component({
  selector: 'app-batch-generate',
  standalone: true,
  imports: [
    CommonModule, FormsModule, HeaderComponent, ErrorBannerComponent, RecipeCardComponent,
    MatCardModule, MatFormFieldModule, MatInputModule, MatButtonModule, MatIconModule,
    MatProgressSpinnerModule
  ],
  templateUrl: './batch-generate.component.html',
  styleUrl: './batch-generate.component.scss'
})
export class BatchGenerateComponent implements OnDestroy {
  theme = '';
  count = 10;
  cuisine = '';
  cookTime: number | null = null;
  servings = 2;
  dietaryRestrictions = new Map<string, boolean>([
    ['VEGAN', false],
    ['VEGETARISCH', false],
    ['PESCETARISCH', false],
    ['GLUTENFREI', false],
    ['LAKTOSEFREI', false],
    ['NUSSALLERGIKER', false],
    ['KETO', false],
    ['LOW_FODMAP', false]
  ]);

  readonly dietaryRestrictionLabels = new Map<string, string>([
    ['VEGAN', 'Vegan'],
    ['VEGETARISCH', 'Vegetarisch'],
    ['PESCETARISCH', 'Pescetarisch'],
    ['GLUTENFREI', 'Glutenfrei'],
    ['LAKTOSEFREI', 'Laktosefrei'],
    ['NUSSALLERGIKER', 'Nussallergiker'],
    ['KETO', 'Keto'],
    ['LOW_FODMAP', 'Low-FODMAP'],
  ]);

  isGenerating = signal(false);
  recipes = signal<Recipe[]>([]);
  recipeErrors = signal<RecipeErrorEntry[]>([]);
  progressIndex = signal(0);
  progressTotal = signal(0);
  errorMessage = signal<string | null>(null);

  private subscription: Subscription | null = null;

  constructor(private recipeService: RecipeService) {}

  ngOnDestroy(): void {
    this.subscription?.unsubscribe();
  }

  /** Toggles a dietary restriction checkbox. */
  toggleDietaryRestriction(key: string): void {
    const current = this.dietaryRestrictions.get(key);
    if (current !== undefined) {
      this.dietaryRestrictions.set(key, !current);
    }
  }

  /** Starts the batch generation and resets all progress state. */
  startBatch(): void {
    const theme = this.theme.trim();
    if (!theme || this.count < 1 || this.isGenerating()) {
      return;
    }

    this.recipes.set([]);
    this.recipeErrors.set([]);
    this.progressIndex.set(0);
    this.progressTotal.set(this.count);
    this.errorMessage.set(null);
    this.isGenerating.set(true);

    const selectedRestrictions = Array.from(this.dietaryRestrictions.entries())
      .filter(([, checked]) => checked)
      .map(([key]) => key);

    const preferences = {
      cuisine: this.cuisine || undefined,
      cookTime: this.cookTime || undefined,
      servings: this.servings || undefined,
      dietaryRestrictions: selectedRestrictions.length > 0 ? selectedRestrictions : undefined
    };

    this.subscription?.unsubscribe();
    this.subscription = this.recipeService.generateRecipeBatchStream(theme, this.count, preferences)
      .subscribe({
        next: (event: BatchStreamEvent) => this.handleEvent(event),
        error: (err) => {
          this.errorMessage.set('Fehler bei der Batch-Generierung: ' + (err.message || 'Unbekannter Fehler'));
          this.isGenerating.set(false);
        }
      });
  }

  private handleEvent(event: BatchStreamEvent): void {
    switch (event.type) {
      case 'batch-progress':
        this.progressIndex.set(event.data.index);
        this.progressTotal.set(event.data.total);
        break;

      case 'recipe':
        this.recipes.set([...this.recipes(), event.data]);
        break;

      case 'recipe-error':
        this.recipeErrors.set([...this.recipeErrors(), event.data]);
        break;

      case 'batch-complete':
        this.isGenerating.set(false);
        break;
    }
  }
}
