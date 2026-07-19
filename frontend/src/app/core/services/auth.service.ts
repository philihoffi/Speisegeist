import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable, BehaviorSubject } from 'rxjs';
import { tap } from 'rxjs/operators';
import { AuthResponse, LoginRequest, RegisterRequest, User } from '../models/auth.model';

/**
 * Handles authentication: login, registration, token storage, and exposing the
 * current authentication state to the UI as observables.
 */
@Injectable({
  providedIn: 'root'
})
export class AuthService {
  private readonly TOKEN_KEY = 'auth_token';
  private readonly apiUrl = '/api/auth';

  private currentUserSubject = new BehaviorSubject<User | null>(null);
  public currentUser$ = this.currentUserSubject.asObservable();

  private isAuthenticatedSubject = new BehaviorSubject<boolean>(this.hasToken());
  public isAuthenticated$ = this.isAuthenticatedSubject.asObservable();

  constructor(private http: HttpClient) {
    this.loadUserFromToken();
  }

  login(email: string, password: string): Observable<AuthResponse> {
    const req: LoginRequest = { email, password };
    return this.http.post<AuthResponse>(`${this.apiUrl}/login`, req)
      .pipe(
        tap(response => this.handleAuthResponse(response))
      );
  }

  register(email: string, password: string): Observable<AuthResponse> {
    const req: RegisterRequest = { email, password };
    return this.http.post<AuthResponse>(`${this.apiUrl}/register`, req)
      .pipe(
        tap(response => this.handleAuthResponse(response))
      );
  }

  logout(): void {
    localStorage.removeItem(this.TOKEN_KEY);
    this.currentUserSubject.next(null);
    this.isAuthenticatedSubject.next(false);
  }

  getToken(): string | null {
    return localStorage.getItem(this.TOKEN_KEY);
  }

  hasToken(): boolean {
    return !!this.getToken();
  }

  private handleAuthResponse(response: AuthResponse): void {
    localStorage.setItem(this.TOKEN_KEY, response.token);
    this.currentUserSubject.next({ email: response.email });
    this.isAuthenticatedSubject.next(true);
  }

  private loadUserFromToken(): void {
    const token = this.getToken();
    if (!token) {
      return;
    }
    const claims = this.decodeToken(token);
    // Expired token: clean up state so the UI does not falsely show "logged in".
    if (claims?.exp && claims.exp * 1000 < Date.now()) {
      this.logout();
      return;
    }
    this.isAuthenticatedSubject.next(true);
    if (claims?.sub) {
      this.currentUserSubject.next({ email: claims.sub });
    }
  }

  private decodeToken(token: string): { sub?: string; exp?: number } | null {
    try {
      const payload = token.split('.')[1];
      if (!payload) {
        return null;
      }
      const json = atob(payload.replace(/-/g, '+').replace(/_/g, '/'));
      return JSON.parse(json);
    } catch {
      return null;
    }
  }
}
