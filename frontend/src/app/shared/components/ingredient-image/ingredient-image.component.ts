import { Component, Input, OnChanges, OnDestroy, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatIconModule } from '@angular/material/icon';
import { IngredientService } from '../../../core/services/ingredient.service';

/**
 * Loads and displays an AI-generated ingredient image from the backend via
 * a blob request. Lazily generates the image on first access. Shows a
 * loading indicator and a placeholder fallback on error.
 */
@Component({
  selector: 'app-ingredient-image',
  standalone: true,
  imports: [CommonModule, MatProgressSpinnerModule, MatIconModule],
  templateUrl: './ingredient-image.component.html',
  styleUrl: './ingredient-image.component.scss'
})
export class IngredientImageComponent implements OnChanges, OnDestroy {
  @Input({ required: true }) ingredientId!: string;
  @Input() alt = '';

  imageUrl = signal<string | null>(null);
  loading = signal(false);
  failed = signal(false);

  private objectUrl: string | null = null;

  constructor(private ingredientService: IngredientService) {}

  ngOnChanges(): void {
    this.load();
  }

  private load(): void {
    if (!this.ingredientId) return;
    this.revoke();
    this.imageUrl.set(null);
    this.failed.set(false);
    this.loading.set(true);

    this.ingredientService.getIngredientImage(this.ingredientId).subscribe({
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