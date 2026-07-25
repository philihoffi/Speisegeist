import { Component, OnInit, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { HttpClient } from '@angular/common/http';
import { RouterModule } from '@angular/router';
import { MatIconModule } from '@angular/material/icon';
import { MatButtonModule } from '@angular/material/button';
import { HeaderComponent } from '../../../../shared/components/header/header.component';
import { LoadingSpinnerComponent } from '../../../../shared/components/loading-spinner/loading-spinner.component';
import { ErrorBannerComponent } from '../../../../shared/components/error-banner/error-banner.component';

interface OpenRouterKeyInfo {
  label: string | null;
  usageCredits: number | null;
  limitCredits: number | null;
  isFreeTier: boolean;
  rateLimitRequests: number | null;
  rateLimitInterval: string | null;
  error: string | null;
}

interface AdminStats {
  userCount: number;
  recipeCount: number;
  ingredientCount: number;
  ingredientImageCount: number;
  recipeImageCount: number;
  openRouterKey: OpenRouterKeyInfo;
}

@Component({
  selector: 'app-admin-dashboard',
  standalone: true,
  imports: [CommonModule, RouterModule, MatIconModule, MatButtonModule, HeaderComponent, LoadingSpinnerComponent, ErrorBannerComponent],
  templateUrl: './dashboard.component.html',
  styleUrl: './dashboard.component.scss'
})
export class AdminDashboardComponent implements OnInit {
  stats = signal<AdminStats | null>(null);
  loading = signal(true);
  error = signal<string | null>(null);

  constructor(private http: HttpClient) {}

  ngOnInit(): void {
    this.load();
  }

  load(): void {
    this.loading.set(true);
    this.error.set(null);
    this.http.get<AdminStats>('/api/admin/stats').subscribe({
      next: data => { this.stats.set(data); this.loading.set(false); },
      error: () => { this.error.set('Statistiken konnten nicht geladen werden.'); this.loading.set(false); }
    });
  }

  usagePercent(stats: AdminStats): number {
    const key = stats.openRouterKey;
    if (!key.usageCredits || !key.limitCredits) return 0;
    return Math.min(100, Math.round((key.usageCredits / key.limitCredits) * 100));
  }

  formatCredits(val: number | null): string {
    if (val === null || val === undefined) return '—';
    return '$' + val.toFixed(4);
  }
}
