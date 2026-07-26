import { TestBed } from '@angular/core/testing';
import { HttpClient, provideHttpClient, withInterceptorsFromDi, HTTP_INTERCEPTORS } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { Router } from '@angular/router';
import { AuthInterceptor } from './auth.interceptor';
import { AuthService } from '../services/auth.service';
import { vi } from 'vitest';

describe('AuthInterceptor', () => {
  let http: HttpTestingController;
  let httpClient: HttpClient;
  let authService: { getToken: ReturnType<typeof vi.fn>; logout: ReturnType<typeof vi.fn> };
  let router: { navigate: ReturnType<typeof vi.fn> };

  beforeEach(() => {
    authService = { getToken: vi.fn(), logout: vi.fn() };
    router = { navigate: vi.fn() };

    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(withInterceptorsFromDi()),
        provideHttpClientTesting(),
        { provide: AuthService, useValue: authService },
        { provide: Router, useValue: router },
        { provide: HTTP_INTERCEPTORS, useClass: AuthInterceptor, multi: true },
      ],
    });

    http = TestBed.inject(HttpTestingController);
    httpClient = TestBed.inject(HttpClient);
  });

  afterEach(() => http.verify());

  it('attaches Bearer token when token exists', () => {
    authService.getToken.mockReturnValue('my-jwt-token');

    httpClient.get('/api/recipes').subscribe();

    const req = http.expectOne('/api/recipes');
    expect(req.request.headers.get('Authorization')).toBe('Bearer my-jwt-token');
    req.flush([]);
  });

  it('does not attach Authorization header when no token', () => {
    authService.getToken.mockReturnValue(null);

    httpClient.get('/api/recipes').subscribe();

    const req = http.expectOne('/api/recipes');
    expect(req.request.headers.has('Authorization')).toBe(false);
    req.flush([]);
  });

  it('logs out and redirects on 401 outside auth endpoints', () => {
    authService.getToken.mockReturnValue(null);

    httpClient.get('/api/recipes').subscribe({ error: () => {} });

    http.expectOne('/api/recipes').flush({}, { status: 401, statusText: 'Unauthorized' });

    expect(authService.logout).toHaveBeenCalled();
    expect(router.navigate).toHaveBeenCalledWith(['/auth/login']);
  });

  it('does not logout on 401 for auth endpoints', () => {
    authService.getToken.mockReturnValue(null);

    httpClient.post('/api/auth/login', {}).subscribe({ error: () => {} });

    http.expectOne('/api/auth/login').flush({}, { status: 401, statusText: 'Unauthorized' });

    expect(authService.logout).not.toHaveBeenCalled();
    expect(router.navigate).not.toHaveBeenCalled();
  });
});
