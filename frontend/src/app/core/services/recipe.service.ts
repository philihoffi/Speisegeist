import { Injectable } from '@angular/core';
import { BehaviorSubject, Observable, throwError } from 'rxjs';
import { tap, finalize, catchError } from 'rxjs/operators';
import { Recipe, RecipeGenerationRequest, SearchFilters, PageResponse } from '../models/recipe.model';
import { ApiService } from './api.service';

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

  selectRecipe(recipe: Recipe): void {
    this.selectedRecipeSubject.next(recipe);
  }

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

  deleteRecipe(id: string): Observable<void> {
    return this.api.deleteRecipe(id)
      .pipe(
        tap(() => {
          const updated = this.recipeListSubject.value.filter(r => r.id !== id);
          this.recipeListSubject.next(updated);
        })
      );
  }

  updateRecipe(id: string, updates: Partial<Recipe>): Observable<Recipe> {
    return this.api.updateRecipe(id, updates)
      .pipe(
        tap(updated => {
          const recipes = this.recipeListSubject.value.map(r => r.id === id ? updated : r);
          this.recipeListSubject.next(recipes);
        })
      );
  }

  clearError(): void {
    this.errorSubject.next(null);
  }
}
