import { TestBed } from '@angular/core/testing';
import { vi } from 'vitest';
import { of, throwError } from 'rxjs';
import { RecipeService } from './recipe.service';
import { ApiService } from './api.service';
import { AuthService } from './auth.service';
import { Recipe, PageResponse } from '../models/recipe.model';

const mockRecipe = (id = '1', name = 'Tofu-Pfanne'): Recipe =>
  ({ id, name } as unknown as Recipe);

const mockPage = (recipes: Recipe[]): PageResponse<Recipe> => ({
  content: recipes,
  totalElements: recipes.length,
  totalPages: 1,
  currentPage: 0,
});

describe('RecipeService', () => {
  let service: RecipeService;
  let apiMock: Partial<ApiService>;
  let authMock: Partial<AuthService>;

  beforeEach(() => {
    apiMock = {
      searchRecipes: vi.fn(),
      generateRecipe: vi.fn(),
      deleteRecipe: vi.fn(),
      updateRecipe: vi.fn(),
    };
    authMock = {
      getToken: vi.fn().mockReturnValue(null),
    };

    TestBed.configureTestingModule({
      providers: [
        RecipeService,
        { provide: ApiService, useValue: apiMock },
        { provide: AuthService, useValue: authMock },
      ],
    });
    service = TestBed.inject(RecipeService);
  });

  it('loadRecipes setzt loading während des Requests', () => {
    (apiMock.searchRecipes as ReturnType<typeof vi.fn>).mockReturnValue(of(mockPage([])));
    const loadingStates: boolean[] = [];
    service.loading$.subscribe(v => loadingStates.push(v));

    service.loadRecipes();

    expect(loadingStates).toContain(true);
    expect(loadingStates[loadingStates.length - 1]).toBe(false);
  });

  it('loadRecipes veröffentlicht Rezepte aus der API-Antwort', () => {
    const recipes = [mockRecipe('1'), mockRecipe('2')];
    (apiMock.searchRecipes as ReturnType<typeof vi.fn>).mockReturnValue(of(mockPage(recipes)));

    service.loadRecipes();

    let result: Recipe[] = [];
    service.recipes$.subscribe(v => (result = v));
    expect(result.length).toBe(2);
  });

  it('loadRecipes setzt Fehlermeldung bei API-Fehler', () => {
    (apiMock.searchRecipes as ReturnType<typeof vi.fn>).mockReturnValue(
      throwError(() => ({ error: { message: 'Serverfehler' } }))
    );

    service.loadRecipes();

    let error: string | null = null;
    service.error$.subscribe(v => (error = v));
    expect(error).toBe('Serverfehler');
  });

  it('generateRecipe hängt neues Rezept vorne an die Liste', () => {
    const existing = mockRecipe('1');
    (apiMock.searchRecipes as ReturnType<typeof vi.fn>).mockReturnValue(of(mockPage([existing])));
    service.loadRecipes();

    const generated = mockRecipe('2', 'Neues Rezept');
    (apiMock.generateRecipe as ReturnType<typeof vi.fn>).mockReturnValue(of(generated));
    service.generateRecipe(['Tofu']).subscribe();

    let result: Recipe[] = [];
    service.recipes$.subscribe(v => (result = v));
    expect(result[0].id).toBe('2');
    expect(result[1].id).toBe('1');
  });

  it('deleteRecipe entfernt Rezept aus der Liste', () => {
    (apiMock.searchRecipes as ReturnType<typeof vi.fn>).mockReturnValue(
      of(mockPage([mockRecipe('1'), mockRecipe('2')]))
    );
    service.loadRecipes();

    (apiMock.deleteRecipe as ReturnType<typeof vi.fn>).mockReturnValue(of(void 0));
    service.deleteRecipe('1').subscribe();

    let result: Recipe[] = [];
    service.recipes$.subscribe(v => (result = v));
    expect(result.length).toBe(1);
    expect(result[0].id).toBe('2');
  });

  it('clearError setzt error$ auf null zurück', () => {
    (apiMock.searchRecipes as ReturnType<typeof vi.fn>).mockReturnValue(
      throwError(() => ({}))
    );
    service.loadRecipes();

    service.clearError();

    let error: string | null = 'noch-was';
    service.error$.subscribe(v => (error = v));
    expect(error).toBeNull();
  });

  it('selectRecipe veröffentlicht ausgewähltes Rezept', () => {
    const recipe = mockRecipe('42');
    service.selectRecipe(recipe);

    let selected: Recipe | null = null;
    service.selectedRecipe$.subscribe(v => (selected = v));
    expect((selected as Recipe | null)?.id).toBe('42');
  });
});
