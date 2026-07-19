import { Injectable } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { BehaviorSubject, Observable, throwError } from 'rxjs';
import { tap, finalize, catchError } from 'rxjs/operators';
import { Ingredient, IngredientFilters, IngredientRequest } from '../models/ingredient.model';
import { PageResponse } from '../models/recipe.model';

/**
 * Facade for the global ingredient catalog. Combines the thin HTTP layer with
 * the list/loading/error state used by the management page; the stateless
 * {@link #searchIngredients} call is shared with the generator's autocomplete.
 */
@Injectable({
  providedIn: 'root'
})
export class IngredientService {
  private readonly ingredientsUrl = '/api/ingredients';

  private ingredientListSubject = new BehaviorSubject<Ingredient[]>([]);
  private pageSubject = new BehaviorSubject<PageResponse<Ingredient> | null>(null);
  private loadingSubject = new BehaviorSubject<boolean>(false);
  private errorSubject = new BehaviorSubject<string | null>(null);

  public ingredients$ = this.ingredientListSubject.asObservable();
  public page$ = this.pageSubject.asObservable();
  public loading$ = this.loadingSubject.asObservable();
  public error$ = this.errorSubject.asObservable();

  constructor(private http: HttpClient) { }

  /** Loads an ingredient's AI-generated image as a binary blob. */
  getIngredientImage(id: string): Observable<Blob> {
    return this.http.get(`${this.ingredientsUrl}/${id}/image`, { responseType: 'blob' });
  }

  /** Searches catalog ingredients without touching the list state (autocomplete). */
  searchIngredients(filters: IngredientFilters): Observable<PageResponse<Ingredient>> {
    let params = new HttpParams();
    if (filters.search) params = params.set('search', filters.search);
    if (filters.warengruppe) params = params.set('warengruppe', filters.warengruppe);
    if (filters.page !== undefined) params = params.set('page', filters.page);
    if (filters.size !== undefined) params = params.set('size', filters.size);

    return this.http.get<PageResponse<Ingredient>>(this.ingredientsUrl, { params });
  }

  /** Loads all distinct warengruppen present in the catalog. */
  loadWarengruppen(): Observable<string[]> {
    return this.http.get<string[]>(`${this.ingredientsUrl}/warengruppen`);
  }

  /** Loads catalog ingredients according to the filters and publishes the result. */
  loadIngredients(filters: IngredientFilters = {}): void {
    this.loadingSubject.next(true);
    this.errorSubject.next(null);

    this.searchIngredients(filters)
      .pipe(
        finalize(() => this.loadingSubject.next(false))
      )
      .subscribe({
        next: (response) => {
          this.ingredientListSubject.next(response.content);
          this.pageSubject.next(response);
        },
        error: (err) => {
          this.errorSubject.next(err.error?.message
            || 'Zutaten konnten nicht geladen werden. Läuft das Backend?');
        }
      });
  }

  /** Creates a catalog ingredient and inserts it into the in-memory list. */
  createIngredient(request: IngredientRequest): Observable<Ingredient> {
    return this.http.post<Ingredient>(this.ingredientsUrl, request)
      .pipe(
        tap(created => {
          const updated = [...this.ingredientListSubject.value, created]
            .sort((a, b) => a.name.localeCompare(b.name, 'de'));
          this.ingredientListSubject.next(updated);
        }),
        catchError(err => this.publishError(err, 'Zutat konnte nicht angelegt werden.'))
      );
  }

  /** Updates a catalog ingredient and reflects the change in the in-memory list. */
  updateIngredient(id: string, request: IngredientRequest): Observable<Ingredient> {
    return this.http.put<Ingredient>(`${this.ingredientsUrl}/${id}`, request)
      .pipe(
        tap(updated => {
          const ingredients = this.ingredientListSubject.value
            .map(i => i.id === id ? updated : i)
            .sort((a, b) => a.name.localeCompare(b.name, 'de'));
          this.ingredientListSubject.next(ingredients);
        }),
        catchError(err => this.publishError(err, 'Zutat konnte nicht gespeichert werden.'))
      );
  }

  /** Deletes a catalog ingredient and removes it from the in-memory list. */
  deleteIngredient(id: string): Observable<void> {
    return this.http.delete<void>(`${this.ingredientsUrl}/${id}`)
      .pipe(
        tap(() => {
          const updated = this.ingredientListSubject.value.filter(i => i.id !== id);
          this.ingredientListSubject.next(updated);
        }),
        catchError(err => this.publishError(err, 'Zutat konnte nicht gelöscht werden.'))
      );
  }

  /** Clears the current error state. */
  clearError(): void {
    this.errorSubject.next(null);
  }

  /** Publishes the server error message (or the fallback) and rethrows. */
  private publishError(err: any, fallback: string): Observable<never> {
    this.errorSubject.next(err.error?.message || fallback);
    return throwError(() => err);
  }
}
