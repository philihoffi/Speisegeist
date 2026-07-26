import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ApiService } from './api.service';
import { Recipe } from '../models/recipe.model';

const mockRecipe = (over: Partial<Recipe> = {}): Recipe => ({
  id: 'r-1',
  name: 'Tofu-Pfanne',
  ingredients: [],
  steps: [],
  servings: 2,
  tags: [],
  sourceType: 'GENERATED',
  createdAt: '2026-01-01T00:00:00',
  updatedAt: '2026-01-01T00:00:00',
  ...over,
});

describe('ApiService', () => {
  let service: ApiService;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    service = TestBed.inject(ApiService);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  describe('generateRecipe', () => {
    it('POSTs the request to /api/recipes/generate', () => {
      let result: Recipe | undefined;
      service.generateRecipe({ ingredients: ['Tofu'] }).subscribe(r => (result = r));

      const req = http.expectOne('/api/recipes/generate');
      expect(req.request.method).toBe('POST');
      expect(req.request.body).toEqual({ ingredients: ['Tofu'] });
      req.flush(mockRecipe());

      expect(result?.name).toBe('Tofu-Pfanne');
    });

    it('forwards preferences unchanged', () => {
      service.generateRecipe({
        ingredients: ['Tofu'],
        preferences: { cuisine: 'Asiatisch', servings: 4, dietaryRestrictions: ['VEGAN'] },
      }).subscribe();

      const req = http.expectOne('/api/recipes/generate');
      expect(req.request.body.preferences.cuisine).toBe('Asiatisch');
      expect(req.request.body.preferences.dietaryRestrictions).toEqual(['VEGAN']);
      req.flush(mockRecipe());
    });
  });

  describe('searchRecipes', () => {
    it('sends no query params for empty filters', () => {
      service.searchRecipes({}).subscribe();

      const req = http.expectOne(r => r.url === '/api/recipes');
      expect(req.request.params.keys()).toEqual([]);
      req.flush({ content: [], totalElements: 0, totalPages: 0, currentPage: 0 });
    });

    it('maps every filter to a query param', () => {
      service.searchRecipes({ search: 'tofu', tag: 'vegan', page: 2, size: 20 }).subscribe();

      const req = http.expectOne(r => r.url === '/api/recipes');
      expect(req.request.params.get('search')).toBe('tofu');
      expect(req.request.params.get('tag')).toBe('vegan');
      expect(req.request.params.get('page')).toBe('2');
      expect(req.request.params.get('size')).toBe('20');
      req.flush({ content: [], totalElements: 0, totalPages: 0, currentPage: 2 });
    });

    it('includes page 0 but omits an empty search string', () => {
      service.searchRecipes({ search: '', page: 0 }).subscribe();

      const req = http.expectOne(r => r.url === '/api/recipes');
      expect(req.request.params.has('search')).toBe(false);
      expect(req.request.params.get('page')).toBe('0');
      req.flush({ content: [], totalElements: 0, totalPages: 0, currentPage: 0 });
    });
  });

  describe('getRecipe', () => {
    it('GETs /api/recipes/:id', () => {
      service.getRecipe('r-42').subscribe();

      const req = http.expectOne('/api/recipes/r-42');
      expect(req.request.method).toBe('GET');
      req.flush(mockRecipe({ id: 'r-42' }));
    });
  });

  describe('getRecipeImage', () => {
    it('requests the image as a blob', () => {
      service.getRecipeImage('r-1').subscribe();

      const req = http.expectOne('/api/recipes/r-1/image');
      expect(req.request.responseType).toBe('blob');
      req.flush(new Blob(['x']));
    });
  });

  describe('createRecipe', () => {
    it('POSTs to /api/recipes', () => {
      service.createRecipe({ name: 'Manuell' }).subscribe();

      const req = http.expectOne('/api/recipes');
      expect(req.request.method).toBe('POST');
      expect(req.request.body).toEqual({ name: 'Manuell' });
      req.flush(mockRecipe({ name: 'Manuell' }));
    });
  });

  describe('updateRecipe', () => {
    it('PUTs to /api/recipes/:id', () => {
      service.updateRecipe('r-1', { name: 'Neu' }).subscribe();

      const req = http.expectOne('/api/recipes/r-1');
      expect(req.request.method).toBe('PUT');
      expect(req.request.body).toEqual({ name: 'Neu' });
      req.flush(mockRecipe({ name: 'Neu' }));
    });
  });

  describe('deleteRecipe', () => {
    it('DELETEs /api/recipes/:id', () => {
      service.deleteRecipe('r-1').subscribe();

      const req = http.expectOne('/api/recipes/r-1');
      expect(req.request.method).toBe('DELETE');
      req.flush(null);
    });
  });

  describe('rateRecipe', () => {
    it('POSTs the rating to /api/recipes/:id/rating', () => {
      service.rateRecipe('r-1', 4.5).subscribe();

      const req = http.expectOne('/api/recipes/r-1/rating');
      expect(req.request.method).toBe('POST');
      expect(req.request.body).toEqual({ rating: 4.5 });
      req.flush(mockRecipe({ rating: 4.5 }));
    });
  });

  it('propagates HTTP errors to the caller', () => {
    let error: unknown;
    service.getRecipe('missing').subscribe({ error: e => (error = e) });

    http.expectOne('/api/recipes/missing')
      .flush({ message: 'nicht gefunden' }, { status: 404, statusText: 'Not Found' });

    expect(error).toBeDefined();
    expect((error as { status: number }).status).toBe(404);
  });
});
