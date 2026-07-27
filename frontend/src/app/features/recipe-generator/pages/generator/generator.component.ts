import { Component, OnDestroy, OnInit, ViewChild, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { MatCardModule } from '@angular/material/card';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatChipsModule } from '@angular/material/chips';
import { MatSelectModule } from '@angular/material/select';
import { MatAutocomplete, MatAutocompleteModule, MatAutocompleteTrigger } from '@angular/material/autocomplete';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatDialogModule, MatDialog } from '@angular/material/dialog';
import { Subject, Subscription, of } from 'rxjs';
import { catchError, debounceTime, distinctUntilChanged, map, switchMap } from 'rxjs/operators';
import { HeaderComponent } from '../../../../shared/components/header/header.component';
import { ErrorBannerComponent } from '../../../../shared/components/error-banner/error-banner.component';
import { RecipeService } from '../../../../core/services/recipe.service';
import { IngredientService } from '../../../../core/services/ingredient.service';
import { StreamingRecipeComponent } from '../../components/streaming-recipe/streaming-recipe.component';

/**
 * Recipe generator page: collects ingredients and optional preferences, then
 * requests an AI-generated recipe and navigates to its detail page. The
 * ingredient input offers autocomplete suggestions from the global catalog.
 */
@Component({
  selector: 'app-generator',
  standalone: true,
  imports: [
    CommonModule, FormsModule, RouterLink, HeaderComponent, ErrorBannerComponent,
    MatCardModule, MatFormFieldModule, MatInputModule, MatButtonModule,
    MatIconModule, MatChipsModule, MatSelectModule, MatAutocompleteModule,
    MatProgressSpinnerModule, MatDialogModule
  ],
  templateUrl: './generator.component.html',
  styleUrl: './generator.component.scss'
})
export class GeneratorComponent implements OnInit, OnDestroy {
  @ViewChild('ingredientAuto') ingredientAutoRef!: MatAutocomplete;
  @ViewChild(MatAutocompleteTrigger) private autoTrigger!: MatAutocompleteTrigger;

  ingredientInput = '';
  ingredients: string[] = [];
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

  // Zoneless: filled inside the HTTP subscribe callback, hence a signal.
  suggestions = signal<string[]>([]);

  private suggestionInput$ = new Subject<string>();
  private subscriptions = new Subscription();

  constructor(
    public recipeService: RecipeService,
    private ingredientService: IngredientService,
    private router: Router,
    private dialog: MatDialog
  ) {}

  /** Wires the debounced catalog search feeding the autocomplete suggestions. */
  ngOnInit(): void {
    this.subscriptions.add(
      this.suggestionInput$.pipe(
        debounceTime(250),
        distinctUntilChanged(),
        switchMap(term => {
          if (!term.trim()) return of([] as string[]);
          return this.ingredientService.searchIngredients({ search: term.trim(), size: 8 }).pipe(
            map(page => page.content.map(i => i.name)),
            catchError(() => of([] as string[]))
          );
        })
      ).subscribe(names => {
        this.suggestions.set(names.filter(name => !this.ingredients.includes(name)));
      })
    );
  }

  /** Cleans up subscriptions when the component is destroyed. */
  ngOnDestroy(): void {
    this.subscriptions.unsubscribe();
  }

  /** Tracks the ingredient input and requests matching catalog suggestions. */
  onIngredientInputChange(value: string): void {
    this.ingredientInput = value;
    this.suggestionInput$.next(value);
  }

  /** Adds an autocomplete suggestion as ingredient and clears the input. */
  onSuggestionSelected(name: string): void {
    if (name && !this.ingredients.includes(name)) {
      this.ingredients.push(name);
    }
    this.ingredientInput = '';
    this.suggestions.set([]);
  }

  /** Adds ingredient on Enter, unless Angular Material has a keyboard-highlighted option to select. */
  onEnterIngredient(): void {
    if (this.ingredientAutoRef?.isOpen && this.autoTrigger?.activeOption) {
      return; // Let Angular Material select the highlighted option
    }
    this.addIngredient();
  }

  /** Adds the trimmed ingredient input to the ingredient list, ignoring duplicates. */
  addIngredient(): void {
    const value = this.ingredientInput.trim();
    if (value && !this.ingredients.includes(value)) {
      this.ingredients.push(value);
    }
    this.ingredientInput = '';
    this.suggestions.set([]);
  }

  /** Removes the ingredient at the given index. */
  removeIngredient(index: number): void {
    this.ingredients.splice(index, 1);
  }

  /** Builds the preferences and triggers recipe generation with streaming. */
  generate(): void {
    const selectedRestrictions = Array.from(this.dietaryRestrictions.entries())
      .filter(([_, checked]) => checked)
      .map(([key, _]) => key);

    const preferences = {
      cuisine: this.cuisine || undefined,
      cookTime: this.cookTime || undefined,
      servings: this.servings || undefined,
      dietaryRestrictions: selectedRestrictions.length > 0 ? selectedRestrictions : undefined
    };

    const dialogRef = this.dialog.open(StreamingRecipeComponent, {
      width: '90%',
      maxWidth: '600px',
      disableClose: false
    });

    // Start streaming in the dialog
    const componentInstance = dialogRef.componentInstance;
    componentInstance.startStreaming(this.ingredients, preferences);

    dialogRef.afterClosed().subscribe((result) => {
      if (result?.recipeId) {
        this.router.navigate(['/recipes', result.recipeId]);
      }
    });
  }

  toggleDietaryRestriction(key: string): void {
    const current = this.dietaryRestrictions.get(key);
    if (current !== undefined) {
      this.dietaryRestrictions.set(key, !current);
    }
  }
}
