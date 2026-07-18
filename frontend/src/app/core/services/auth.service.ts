import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable, BehaviorSubject } from 'rxjs';
import { tap } from 'rxjs/operators';
import { AuthResponse, LoginRequest, RegisterRequest, User } from '../models/auth.model';

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
    if (this.hasToken()) {
      const token = this.getToken();
      // In production, decode JWT to get user info
      // For now, just mark as authenticated
      this.isAuthenticatedSubject.next(true);
    }
  }
}
