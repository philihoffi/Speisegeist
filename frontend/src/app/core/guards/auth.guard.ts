import { Injectable } from '@angular/core';
import { Router } from '@angular/router';
import { AuthService } from '../services/auth.service';

/**
 * Route guard that allows navigation only for authenticated users,
 * redirecting unauthenticated users to the login page.
 */
@Injectable({
  providedIn: 'root'
})
export class AuthGuard {
  constructor(private authService: AuthService, private router: Router) { }

  canActivate(): boolean {
    if (this.authService.hasToken()) {
      return true;
    }
    this.router.navigate(['/auth/login']);
    return false;
  }
}
