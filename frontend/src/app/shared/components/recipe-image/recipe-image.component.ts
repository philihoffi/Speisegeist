import { Component, Input, OnChanges, OnDestroy, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatIconModule } from '@angular/material/icon';
import { ApiService } from '../../../core/services/api.service';

/**
 * Displays a recipe image fetched from the backend. Loads the bytes via a blob
 * request (the auth interceptor attaches the JWT), creates an object URL, and
 * revokes it again on change/destroy. Shows a loading state and, until the image
 * is ready or on error, a placeholder.
 */
@Component({
  selector: 'app-recipe-image',
  standalone: true,
  imports: [CommonModule, MatProgressSpinnerModule, MatIconModule],
  templateUrl: './recipe-image.component.html',
  styleUrl: './recipe-image.component.scss'
})
export class RecipeImageComponent implements OnChanges, OnDestroy {
  @Input({ required: true }) recipeId!: string;
  @Input() alt = '';

  // Zoneless: state set inside subscribe callbacks must be a signal.
  imageUrl = signal<string | null>(null);
  loading = signal(false);
  failed = signal(false);

  private objectUrl: string | null = null;

  constructor(private api: ApiService) {}

  ngOnChanges(): void {
    this.load();
  }

  private load(): void {
    if (!this.recipeId) return;
    this.revoke();
    this.imageUrl.set(null);
    this.failed.set(false);
    this.loading.set(true);

    this.api.getRecipeImage(this.recipeId).subscribe({
      next: (blob) => {
        this.objectUrl = URL.createObjectURL(blob);
        this.imageUrl.set(this.objectUrl);
        this.loading.set(false);
      },
      error: () => {
        this.loading.set(false);
        this.failed.set(true);
      }
    });
  }

  private revoke(): void {
    if (this.objectUrl) {
      URL.revokeObjectURL(this.objectUrl);
      this.objectUrl = null;
    }
  }

  ngOnDestroy(): void {
    this.revoke();
  }
}
