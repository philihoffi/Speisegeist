import { Injectable } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';
import { Recipe, RecipeGenerationRequest, SearchFilters, PageResponse } from '../models/recipe.model';

@Injectable({
  providedIn: 'root'
})
export class ApiService {
  private readonly apiUrl = '/api';
  private readonly recipesUrl = `${this.apiUrl}/recipes`;

  constructor(private http: HttpClient) { }

  generateRecipe(request: RecipeGenerationRequest): Observable<Recipe> {
    return this.http.post<Recipe>(`${this.recipesUrl}/generate`, request);
  }

  searchRecipes(filters: SearchFilters): Observable<PageResponse<Recipe>> {
    let params = new HttpParams();
    if (filters.search) params = params.set('search', filters.search);
    if (filters.tag) params = params.set('tag', filters.tag);
    if (filters.page !== undefined) params = params.set('page', filters.page);
    if (filters.size !== undefined) params = params.set('size', filters.size);

    return this.http.get<PageResponse<Recipe>>(this.recipesUrl, { params });
  }

  getRecipe(id: string): Observable<Recipe> {
    return this.http.get<Recipe>(`${this.recipesUrl}/${id}`);
  }

  createRecipe(recipe: Partial<Recipe>): Observable<Recipe> {
    return this.http.post<Recipe>(this.recipesUrl, recipe);
  }

  updateRecipe(id: string, recipe: Partial<Recipe>): Observable<Recipe> {
    return this.http.put<Recipe>(`${this.recipesUrl}/${id}`, recipe);
  }

  deleteRecipe(id: string): Observable<void> {
    return this.http.delete<void>(`${this.recipesUrl}/${id}`);
  }

  rateRecipe(id: string, rating: number): Observable<Recipe> {
    return this.http.post<Recipe>(`${this.recipesUrl}/${id}/rating`, { rating });
  }
}
