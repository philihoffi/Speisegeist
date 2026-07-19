import { Injectable } from '@angular/core';
import { BehaviorSubject, Observable, throwError } from 'rxjs';
import { tap, finalize, catchError } from 'rxjs/operators';
import { Recipe, RecipeGenerationRequest, SearchFilters, PageResponse } from '../models/recipe.model';
import { ApiService } from './api.service';

/**
 * Stateful facade over {@link ApiService} for recipes. Exposes recipe lists,
 * the selected recipe, loading and error state as observables, and keeps the
 * in-memory list in sync with create/update/delete operations.
 */
@Injectable({
  providedIn: 'root'
})
export class RecipeService {
  private recipeListSubject = new BehaviorSubject<Recipe[]>([]);
  private selectedRecipeSubject = new BehaviorSubject<Recipe | null>(null);
  private loadingSubject = new BehaviorSubject<boolean>(false);
  private errorSubject = new BehaviorSubject<string | null>(null);
  private pageSubject = new BehaviorSubject<PageResponse<Recipe> | null>(null);

  public recipes$ = this.recipeListSubject.asObservable();
  public selectedRecipe$ = this.selectedRecipeSubject.asObservable();
  public loading$ = this.loadingSubject.asObservable();
  public error$ = this.errorSubject.asObservable();
  public page$ = this.pageSubject.asObservable();

  constructor(private api: ApiService) { }

  /** Loads recipes according to the given filters and publishes the result. */
  loadRecipes(filters: SearchFilters = {}): void {
    this.loadingSubject.next(true);
    this.errorSubject.next(null);

    this.api.searchRecipes(filters)
      .pipe(
        finalize(() => this.loadingSubject.next(false))
      )
      .subscribe({
        next: (response: PageResponse<Recipe>) => {
          this.recipeListSubject.next(response.content);
          this.pageSubject.next(response);
        },
        error: (err) => {
          this.errorSubject.next(err.error?.message
            || 'Rezepte konnten nicht geladen werden. Läuft das Backend?');
        }
      });
  }

  /** Marks the given recipe as the currently selected one. */
  selectRecipe(recipe: Recipe): void {
    this.selectedRecipeSubject.next(recipe);
  }

  /** Generates a recipe and prepends it to the in-memory list. */
  generateRecipe(ingredients: string[], preferences?: any): Observable<Recipe> {
    this.loadingSubject.next(true);
    this.errorSubject.next(null);

    const request: RecipeGenerationRequest = { ingredients, preferences };
    return this.api.generateRecipe(request)
      .pipe(
        tap(recipe => {
          this.recipeListSubject.next([recipe, ...this.recipeListSubject.value]);
          this.selectedRecipeSubject.next(recipe);
        }),
        catchError(err => {
          this.errorSubject.next(err.error?.message
            || 'Rezept konnte nicht generiert werden. Bitte erneut versuchen.');
          return throwError(() => err);
        }),
        finalize(() => this.loadingSubject.next(false))
      );
  }

  /** Deletes a recipe and removes it from the in-memory list. */
  deleteRecipe(id: string): Observable<void> {
    return this.api.deleteRecipe(id)
      .pipe(
        tap(() => {
          const updated = this.recipeListSubject.value.filter(r => r.id !== id);
          this.recipeListSubject.next(updated);
        })
      );
  }

  /** Updates a recipe and reflects the change in the in-memory list. */
  updateRecipe(id: string, updates: Partial<Recipe>): Observable<Recipe> {
    return this.api.updateRecipe(id, updates)
      .pipe(
        tap(updated => {
          const recipes = this.recipeListSubject.value.map(r => r.id === id ? updated : r);
          this.recipeListSubject.next(recipes);
        })
      );
  }

  /** Clears the current error state. */
  clearError(): void {
    this.errorSubject.next(null);
  }
}
