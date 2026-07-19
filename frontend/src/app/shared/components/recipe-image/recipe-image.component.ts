import { Component, Input, OnChanges, OnDestroy, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatIconModule } from '@angular/material/icon';
import { ApiService } from '../../../core/services/api.service';

/**
 * Zeigt das Rezeptbild aus dem Backend an. Lädt die Bytes per Blob-Request (der
 * Auth-Interceptor hängt den JWT an), erzeugt daraus eine Object-URL und räumt diese
 * beim Wechsel/Zerstören wieder auf. Zeigt einen Ladezustand und – bei Fehler oder bis
 * das Bild generiert ist – einen Ghost-Platzhalter.
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

  // Zoneless: In Subscribe-Callbacks gesetzter Zustand muss ein Signal sein.
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
