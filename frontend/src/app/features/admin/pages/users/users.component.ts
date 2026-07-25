import { Component, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterModule } from '@angular/router';
import { MatTableModule } from '@angular/material/table';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatSelectModule } from '@angular/material/select';
import { MatDialog, MatDialogModule } from '@angular/material/dialog';
import { HttpClient } from '@angular/common/http';
import { UserListItem } from '../../../../core/models/auth.model';
import { HeaderComponent } from '../../../../shared/components/header/header.component';
import { ErrorBannerComponent } from '../../../../shared/components/error-banner/error-banner.component';
import { LoadingSpinnerComponent } from '../../../../shared/components/loading-spinner/loading-spinner.component';

@Component({
  selector: 'app-admin-users',
  standalone: true,
  imports: [
    CommonModule, RouterModule, MatTableModule, MatButtonModule, MatIconModule,
    MatSelectModule, MatDialogModule, HeaderComponent, ErrorBannerComponent, LoadingSpinnerComponent
  ],
  templateUrl: './users.component.html',
  styleUrl: './users.component.scss'
})
export class AdminUsersComponent {
  private readonly apiUrl = '/api/admin';

  users = signal<UserListItem[]>([]);
  loading = signal(true);
  error = signal<string | null>(null);
  submittingId = signal<string | null>(null);
  columns = ['email', 'role', 'createdAt', 'lastLogin', 'actions'];

  constructor(private http: HttpClient, private dialog: MatDialog) {
    this.loadUsers();
  }

  loadUsers(): void {
    this.loading.set(true);
    this.error.set(null);
    this.http.get<UserListItem[]>(`${this.apiUrl}/users`).subscribe({
      next: data => { this.users.set(data); this.loading.set(false); },
      error: () => { this.error.set('Benutzer konnten nicht geladen werden.'); this.loading.set(false); }
    });
  }

  deleteUser(user: UserListItem): void {
    if (!confirm(`Benutzer ${user.email} wirklich löschen?`)) {
      return;
    }
    this.submittingId.set(user.id);
    this.http.delete(`${this.apiUrl}/users/${user.id}`).subscribe({
      next: () => { this.loadUsers(); this.submittingId.set(null); },
      error: () => { this.error.set('Löschen fehlgeschlagen.'); this.submittingId.set(null); }
    });
  }

  changeRole(user: UserListItem, newRole: string): void {
    this.submittingId.set(user.id);
    this.http.put<UserListItem>(`${this.apiUrl}/users/${user.id}/role`, { role: newRole }).subscribe({
      next: () => { this.loadUsers(); this.submittingId.set(null); },
      error: () => { this.error.set('Rollenänderung fehlgeschlagen.'); this.submittingId.set(null); }
    });
  }
}