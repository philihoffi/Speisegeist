import { TestBed } from '@angular/core/testing';
import { Router } from '@angular/router';
import { AdminGuard } from './admin.guard';
import { AuthService } from '../services/auth.service';
import { vi } from 'vitest';

describe('AdminGuard', () => {
  let guard: AdminGuard;
  let authService: { isAdmin: ReturnType<typeof vi.fn> };
  let router: { navigate: ReturnType<typeof vi.fn> };

  beforeEach(() => {
    authService = { isAdmin: vi.fn() };
    router = { navigate: vi.fn() };

    TestBed.configureTestingModule({
      providers: [
        AdminGuard,
        { provide: AuthService, useValue: authService },
        { provide: Router, useValue: router },
      ],
    });
    guard = TestBed.inject(AdminGuard);
  });

  it('allows navigation for admin users', () => {
    authService.isAdmin.mockReturnValue(true);

    expect(guard.canActivate()).toBe(true);
    expect(router.navigate).not.toHaveBeenCalled();
  });

  it('blocks navigation and redirects to / for non-admin users', () => {
    authService.isAdmin.mockReturnValue(false);

    expect(guard.canActivate()).toBe(false);
    expect(router.navigate).toHaveBeenCalledWith(['/']);
  });
});
