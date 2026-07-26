import { Component, OnInit, OnDestroy, signal, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { MatDialogModule, MatDialogRef } from '@angular/material/dialog';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatCardModule } from '@angular/material/card';
import { MatListModule } from '@angular/material/list';
import { Router } from '@angular/router';
import { RecipeService } from '../../../../core/services/recipe.service';
import { Subject } from 'rxjs';
import { takeUntil } from 'rxjs/operators';

interface StreamEvent {
  type: 'ingredient' | 'step' | 'tip' | 'complete' | 'error' | 'progress';
  data: any;
}

interface IngredientData {
  name: string;
  quantity: number;
  unit: string;
}

interface StepData {
  order: number;
  instruction: string;
  duration: number;
}

interface CompleteData {
  id: string;
  name: string;
  description: string;
  servings: number;
  cookTime: number;
  preparationTime: number;
}

@Component({
  selector: 'app-streaming-recipe',
  standalone: true,
  imports: [
    CommonModule,
    MatDialogModule,
    MatProgressBarModule,
    MatButtonModule,
    MatIconModule,
    MatCardModule,
    MatListModule
  ],
  templateUrl: './streaming-recipe.component.html',
  styleUrl: './streaming-recipe.component.scss'
})
export class StreamingRecipeComponent implements OnInit, OnDestroy {
  isLoading = signal(true);
  progress = signal(0);
  recipeName = signal('Rezept wird generiert...');
  recipeDescription = signal<string | null>(null);
  ingredients = signal<IngredientData[]>([]);
  steps = signal<StepData[]>([]);
  errorMessage = signal<string | null>(null);
  currentRecipeId = signal<string | null>(null);

  private subscription: any = null;
  private lastIngredients: string[] = [];
  private lastPreferences: any = null;
  protected readonly destroy$ = new Subject<void>();

  constructor(
    private dialogRef: MatDialogRef<StreamingRecipeComponent>,
    private router: Router,
    private recipeService: RecipeService
  ) {}

  ngOnInit(): void {
    // Streaming wird vom Parent gestartet
  }

  ngOnDestroy(): void {
    this.close();
  }

  startStreaming(ingredients: string[], preferences: any): void {
    this.lastIngredients = ingredients;
    this.lastPreferences = preferences;

    if (this.subscription) {
      this.subscription.unsubscribe();
    }

    this.subscription = this.recipeService.generateRecipeStream(ingredients, preferences)
      .pipe(
        takeUntil(this.destroy$)
      )
      .subscribe({
        next: (event: StreamEvent) => {
          this.handleStreamEvent(event);
        },
        error: (err) => {
          this.errorMessage.set('Fehler bei der Rezept-Generierung: ' + (err.message || 'Unbekannter Fehler'));
          this.isLoading.set(false);
        }
      });
  }

  private handleStreamEvent(event: StreamEvent): void {
    switch (event.type) {
      case 'ingredient':
        const currentIng = this.ingredients();
        this.ingredients.set([...currentIng, event.data]);
        break;

      case 'step':
        const currentSteps = this.steps();
        this.steps.set([...currentSteps, event.data].sort((a, b) => a.order - b.order));
        break;

      case 'progress':
        this.progress.set(event.data.tokens || 0);
        break;

      case 'complete':
        const data = event.data as CompleteData;
        this.recipeName.set(data.name);
        this.recipeDescription.set(data.description || null);
        this.currentRecipeId.set(data.id);
        this.isLoading.set(false);
        break;

      case 'error':
        this.errorMessage.set(event.data.message);
        this.isLoading.set(false);
        break;
    }
  }

  saveRecipe(): void {
    if (this.currentRecipeId()) {
      this.dialogRef.close({ recipeId: this.currentRecipeId() });
      this.router.navigate(['/recipes', this.currentRecipeId()]);
    }
  }

  close(): void {
    if (this.subscription) {
      this.subscription.unsubscribe();
    }
    this.destroy$.next();
    this.destroy$.complete();
    this.dialogRef.close();
  }

  regenerate(): void {
    this.ingredients.set([]);
    this.steps.set([]);
    this.errorMessage.set(null);
    this.currentRecipeId.set(null);
    this.recipeName.set('Rezept wird generiert...');
    this.recipeDescription.set(null);
    this.isLoading.set(true);
    this.progress.set(0);
    this.startStreaming(this.lastIngredients, this.lastPreferences);
  }
}
