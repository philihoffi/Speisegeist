import { Injectable } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';
import { Recipe, RecipeGenerationRequest, SearchFilters, PageResponse } from '../models/recipe.model';

/**
 * Thin HTTP wrapper around the recipe REST API. All endpoints are relative to
 * the configured API base path.
 */
@Injectable({
  providedIn: 'root'
})
export class ApiService {
  private readonly apiUrl = '/api';
  private readonly recipesUrl = `${this.apiUrl}/recipes`;

  constructor(private http: HttpClient) { }

  /** Generates a recipe from the given ingredients and preferences. */
  generateRecipe(request: RecipeGenerationRequest): Observable<Recipe> {
    return this.http.post<Recipe>(`${this.recipesUrl}/generate`, request);
  }

  /** Searches the current user's recipes, returning a page of results. */
  searchRecipes(filters: SearchFilters): Observable<PageResponse<Recipe>> {
    let params = new HttpParams();
    if (filters.search) params = params.set('search', filters.search);
    if (filters.tag) params = params.set('tag', filters.tag);
    if (filters.page !== undefined) params = params.set('page', filters.page);
    if (filters.size !== undefined) params = params.set('size', filters.size);

    return this.http.get<PageResponse<Recipe>>(this.recipesUrl, { params });
  }

  /** Loads a single recipe by id. */
  getRecipe(id: string): Observable<Recipe> {
    return this.http.get<Recipe>(`${this.recipesUrl}/${id}`);
  }

  /** Loads a recipe's generated image as a binary blob. */
  getRecipeImage(id: string): Observable<Blob> {
    return this.http.get(`${this.recipesUrl}/${id}/image`, { responseType: 'blob' });
  }

  /** Creates a new recipe. */
  createRecipe(recipe: Partial<Recipe>): Observable<Recipe> {
    return this.http.post<Recipe>(this.recipesUrl, recipe);
  }

  /** Updates an existing recipe. */
  updateRecipe(id: string, recipe: Partial<Recipe>): Observable<Recipe> {
    return this.http.put<Recipe>(`${this.recipesUrl}/${id}`, recipe);
  }

  /** Deletes a recipe by id. */
  deleteRecipe(id: string): Observable<void> {
    return this.http.delete<void>(`${this.recipesUrl}/${id}`);
  }

  /** Stores a star rating for a recipe. */
  rateRecipe(id: string, rating: number): Observable<Recipe> {
    return this.http.post<Recipe>(`${this.recipesUrl}/${id}/rating`, { rating });
  }
}
