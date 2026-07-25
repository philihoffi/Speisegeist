import { Injectable } from '@angular/core';
import { BehaviorSubject, Observable, throwError, Subject } from 'rxjs';
import { tap, finalize, catchError } from 'rxjs/operators';
import { Recipe, RecipeGenerationRequest, SearchFilters, PageResponse } from '../models/recipe.model';
import { ApiService } from './api.service';
import { AuthService } from './auth.service';

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

  constructor(private api: ApiService, private authService: AuthService) { }

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

  /** Generates a recipe with streaming output (NDJSON format). */
  generateRecipeStream(ingredients: string[], preferences?: any): Observable<any> {
    const subject = new Subject<any>();

    this.loadingSubject.next(true);
    this.errorSubject.next(null);

    const request: RecipeGenerationRequest = { ingredients, preferences };
    const token = this.authService.getToken();

    const headers: Record<string, string> = {
      'Content-Type': 'application/json'
    };

    if (token) {
      headers['Authorization'] = `Bearer ${token}`;
    }

    fetch('/api/recipes/generate-stream', {
      method: 'POST',
      headers: headers,
      body: JSON.stringify(request)
    })
      .then(response => {
        if (!response.ok) {
          throw new Error(`HTTP error! status: ${response.status}`);
        }
        return response.body?.getReader();
      })
      .then(reader => {
        if (!reader) throw new Error('No reader available');

        const decoder = new TextDecoder();
        const readChunk = () => {
          reader.read().then(({ done, value }) => {
            if (done) {
              subject.complete();
              this.loadingSubject.next(false);
              return;
            }

            const chunk = decoder.decode(value, { stream: true });
            chunk.split('\n').forEach(line => {
              if (line.trim()) {
                try {
                  const event = JSON.parse(line);
                  subject.next(event);
                } catch (e) {
                  console.error('Failed to parse NDJSON line', e);
                }
              }
            });

            readChunk();
          });
        };

        readChunk();
      })
      .catch(err => {
        this.errorSubject.next(err.message || 'Streaming-Fehler');
        this.loadingSubject.next(false);
        subject.error(err);
      });

    return subject.asObservable();
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
