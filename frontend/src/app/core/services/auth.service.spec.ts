import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting, HttpTestingController } from '@angular/common/http/testing';
import { AuthService } from './auth.service';

describe('AuthService', () => {
  let service: AuthService;
  let httpMock: HttpTestingController;

  beforeEach(() => {
    localStorage.clear();
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        AuthService,
      ],
    });
    service = TestBed.inject(AuthService);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    httpMock.verify();
    localStorage.clear();
  });

  it('startet unauthentifiziert wenn kein Token vorhanden', () => {
    let isAuth = false;
    service.isAuthenticated$.subscribe(v => (isAuth = v));
    expect(isAuth).toBe(false);
  });

  it('login speichert Token und setzt isAuthenticated auf true', () => {
    let isAuth = false;
    service.isAuthenticated$.subscribe(v => (isAuth = v));

    service.login('user@test.de', 'secret').subscribe();

    const req = httpMock.expectOne('/api/auth/login');
    expect(req.request.method).toBe('POST');
    req.flush({ token: 'tok123', email: 'user@test.de', role: 'USER' });

    expect(isAuth).toBe(true);
    expect(service.getToken()).toBe('tok123');
  });

  it('register sendet korrekte URL und speichert Token', () => {
    service.register('new@test.de', 'pw').subscribe();

    const req = httpMock.expectOne('/api/auth/register');
    expect(req.request.method).toBe('POST');
    req.flush({ token: 'reg-tok', email: 'new@test.de', role: 'USER' });

    expect(service.getToken()).toBe('reg-tok');
  });

  it('logout entfernt Token und setzt isAuthenticated auf false', () => {
    localStorage.setItem('auth_token', 'existing-tok');
    let isAuth = true;
    service.isAuthenticated$.subscribe(v => (isAuth = v));

    service.logout();

    expect(service.getToken()).toBeNull();
    expect(isAuth).toBe(false);
  });

  it('isAdmin gibt true zurück wenn Rolle ADMIN', () => {
    service.login('admin@test.de', 'pw').subscribe();
    httpMock.expectOne('/api/auth/login')
      .flush({ token: 'admin-tok', email: 'admin@test.de', role: 'ADMIN' });

    expect(service.isAdmin()).toBe(true);
  });

  it('isAdmin gibt false zurück wenn Rolle USER', () => {
    service.login('user@test.de', 'pw').subscribe();
    httpMock.expectOne('/api/auth/login')
      .flush({ token: 'user-tok', email: 'user@test.de', role: 'USER' });

    expect(service.isAdmin()).toBe(false);
  });
});
