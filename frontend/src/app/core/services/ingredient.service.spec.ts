import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { IngredientService } from './ingredient.service';
import { Ingredient } from '../models/ingredient.model';
import { PageResponse } from '../models/recipe.model';

const mockIngredient = (name: string): Ingredient => ({
  id: crypto.randomUUID(),
  name,
  normalizedName: name.toLowerCase(),
  createdAt: new Date().toISOString(),
});

const mockPage = (content: Ingredient[]): PageResponse<Ingredient> => ({
  content,
  totalElements: content.length,
  totalPages: 1,
  currentPage: 0,
});

describe('IngredientService', () => {
  let service: IngredientService;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    service = TestBed.inject(IngredientService);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('loadIngredients sets loading$ true during request', () => {
    const loadingStates: boolean[] = [];
    service.loading$.subscribe(v => loadingStates.push(v));

    service.loadIngredients();
    expect(loadingStates).toContain(true);

    http.expectOne('/api/ingredients').flush(mockPage([]));
  });

  it('loadIngredients publishes ingredients after success', () => {
    const tofu = mockIngredient('Tofu');
    let result: Ingredient[] = [];
    service.ingredients$.subscribe(v => (result = v));

    service.loadIngredients();
    http.expectOne('/api/ingredients').flush(mockPage([tofu]));

    expect(result).toHaveLength(1);
    expect(result[0].name).toBe('Tofu');
  });

  it('loadIngredients sets error$ on failure', () => {
    let error: string | null = null;
    service.error$.subscribe(v => (error = v));

    service.loadIngredients();
    http.expectOne('/api/ingredients').flush({ message: 'Server error' }, { status: 500, statusText: 'Error' });

    expect(error).not.toBeNull();
  });

  it('createIngredient appends and sorts ingredients alphabetically', () => {
    const spinach = mockIngredient('Spinat');
    const apple = mockIngredient('Apfel');
    let result: Ingredient[] = [];
    service.ingredients$.subscribe(v => (result = v));

    service.createIngredient({ name: 'Apfel' }).subscribe();
    http.expectOne({ method: 'POST', url: '/api/ingredients' }).flush(apple);

    service.createIngredient({ name: 'Spinat' }).subscribe();
    http.expectOne({ method: 'POST', url: '/api/ingredients' }).flush(spinach);

    expect(result[0].name).toBe('Apfel');
    expect(result[1].name).toBe('Spinat');
  });

  it('deleteIngredient removes ingredient from list', () => {
    const tofu = mockIngredient('Tofu');
    let result: Ingredient[] = [];
    service.ingredients$.subscribe(v => (result = v));

    service.loadIngredients();
    http.expectOne('/api/ingredients').flush(mockPage([tofu]));

    service.deleteIngredient(tofu.id).subscribe();
    http.expectOne({ method: 'DELETE', url: `/api/ingredients/${tofu.id}` }).flush(null);

    expect(result).toHaveLength(0);
  });

  it('catalogChanged$ emits after create and delete', () => {
    const tofu = mockIngredient('Tofu');
    let emitCount = 0;
    service.catalogChanged$.subscribe(() => emitCount++);

    service.createIngredient({ name: 'Tofu' }).subscribe();
    http.expectOne({ method: 'POST', url: '/api/ingredients' }).flush(tofu);

    service.loadIngredients();
    http.expectOne('/api/ingredients').flush(mockPage([tofu]));

    service.deleteIngredient(tofu.id).subscribe();
    http.expectOne({ method: 'DELETE', url: `/api/ingredients/${tofu.id}` }).flush(null);

    expect(emitCount).toBe(2);
  });

  it('clearError resets error$ to null', () => {
    let error: string | null = 'initial';
    service.error$.subscribe(v => (error = v));

    service.loadIngredients();
    http.expectOne('/api/ingredients').flush({ message: 'fail' }, { status: 500, statusText: 'Error' });

    service.clearError();
    expect(error).toBeNull();
  });
});
