import { Component } from '@angular/core';
import { RouterModule, RouterLinkActive } from '@angular/router';
import { MatTabsModule } from '@angular/material/tabs';
import { MatIconModule } from '@angular/material/icon';

@Component({
  selector: 'app-admin-nav',
  standalone: true,
  imports: [RouterModule, RouterLinkActive, MatTabsModule, MatIconModule],
  template: `
    <nav class="admin-nav">
      <div class="admin-nav-inner">
        <a routerLink="/admin" routerLinkActive="active" [routerLinkActiveOptions]="{ exact: true }" class="admin-tab">
          <mat-icon>dashboard</mat-icon>
          <span>Übersicht</span>
        </a>
        <a routerLink="/admin/users" routerLinkActive="active" class="admin-tab">
          <mat-icon>manage_accounts</mat-icon>
          <span>Benutzer</span>
        </a>
      </div>
    </nav>
  `,
  styles: [`
    .admin-nav {
      border-bottom: 2px solid var(--sg-surface-alt, #ede8ff);
      margin-bottom: 28px;
    }

    .admin-nav-inner {
      display: flex;
      gap: 4px;
      padding: 0 28px;
      max-width: 1400px;
      margin: 0 auto;
    }

    .admin-tab {
      display: flex;
      align-items: center;
      gap: 6px;
      padding: 10px 16px;
      font-size: 0.9rem;
      font-weight: 500;
      color: var(--sg-ink-soft);
      text-decoration: none;
      border-bottom: 2px solid transparent;
      margin-bottom: -2px;
      transition: color 0.15s, border-color 0.15s;

      mat-icon {
        font-size: 18px;
        width: 18px;
        height: 18px;
      }

      &:hover {
        color: var(--sg-primary);
      }

      &.active {
        color: var(--sg-primary);
        border-bottom-color: var(--sg-primary);
        font-weight: 600;
      }
    }
  `]
})
export class AdminNavComponent {}
