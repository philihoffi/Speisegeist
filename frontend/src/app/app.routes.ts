import { Routes } from '@angular/router';
import { AuthGuard } from './core/guards/auth.guard';

/**
 * Application routes. Public auth routes sit under {@code /auth}; all recipe
 * routes are guarded by {@link AuthGuard}. Unknown paths redirect to the dashboard.
 */
export const routes: Routes = [
  {
    path: 'auth',
    children: [
      {
        path: 'login',
        loadComponent: () => import('./features/auth/pages/login/login.component').then(m => m.LoginComponent)
      },
      {
        path: 'register',
        loadComponent: () => import('./features/auth/pages/register/register.component').then(m => m.RegisterComponent)
      }
    ]
  },
  {
    path: 'recipes',
    canActivate: [AuthGuard],
    children: [
      {
        path: 'generate',
        loadComponent: () => import('./features/recipe-generator/pages/generator/generator.component').then(m => m.GeneratorComponent)
      },
      {
        path: 'library',
        loadComponent: () => import('./features/recipe-library/pages/library/library.component').then(m => m.LibraryComponent)
      },
      {
        path: 'new',
        loadComponent: () => import('./features/recipe-manual/pages/manual/manual.component').then(m => m.ManualRecipeComponent)
      },
      {
        path: ':id',
        loadComponent: () => import('./features/recipe-detail/pages/detail/detail.component').then(m => m.DetailComponent)
      }
    ]
  },
  {
    path: 'ingredients',
    canActivate: [AuthGuard],
    loadComponent: () => import('./features/ingredient-management/pages/ingredients/ingredients.component').then(m => m.IngredientsComponent)
  },
  {
    path: '',
    loadComponent: () => import('./features/dashboard/pages/dashboard/dashboard.component').then(m => m.DashboardComponent),
    canActivate: [AuthGuard]
  },
  {
    path: '**',
    redirectTo: ''
  }
];
