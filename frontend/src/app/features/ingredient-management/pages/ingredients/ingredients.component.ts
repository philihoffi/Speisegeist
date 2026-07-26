import { Component, OnDestroy, OnInit, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { MatCardModule } from '@angular/material/card';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { Subject, Subscription, debounceTime, distinctUntilChanged } from 'rxjs';
import { HeaderComponent } from '../../../../shared/components/header/header.component';
import { ErrorBannerComponent } from '../../../../shared/components/error-banner/error-banner.component';
import { LoadingSpinnerComponent } from '../../../../shared/components/loading-spinner/loading-spinner.component';
import { IngredientService } from '../../../../core/services/ingredient.service';
import { Ingredient } from '../../../../core/models/ingredient.model';
import { IngredientCardComponent } from '../../components/ingredient-card/ingredient-card.component';

/**
 * Ingredient management page: searchable list of the global ingredient catalog
 * with inline create, edit, and delete.
 */
@Component({
  selector: 'app-ingredients',
  standalone: true,
  imports: [
    CommonModule, FormsModule, HeaderComponent, ErrorBannerComponent, LoadingSpinnerComponent,
    IngredientCardComponent,
    MatCardModule, MatFormFieldModule, MatInputModule,
    MatButtonModule, MatIconModule
  ],
  templateUrl: './ingredients.component.html',
  styleUrl: './ingredients.component.scss'
})
export class IngredientsComponent implements OnInit, OnDestroy {
  searchTerm = '';
  newName = '';

  editingId = signal<string | null>(null);
  saving = signal(false);

  private search$ = new Subject<string>();
  private subscriptions = new Subscription();

  constructor(public ingredientService: IngredientService) {}

  ngOnInit(): void {
    this.load();
    this.subscriptions.add(
      this.search$.pipe(
        debounceTime(300),
        distinctUntilChanged()
      ).subscribe(() => this.load())
    );
  }

  ngOnDestroy(): void {
    this.subscriptions.unsubscribe();
  }

  onSearchChange(term: string): void {
    this.searchTerm = term;
    this.search$.next(term);
  }

  addIngredient(): void {
    const name = this.newName.trim();
    if (!name) return;

    this.saving.set(true);
    this.ingredientService.createIngredient({ name }).subscribe({
      next: () => {
        this.newName = '';
        this.saving.set(false);
        this.load();
      },
      error: () => this.saving.set(false)
    });
  }

  startEdit(ingredient: Ingredient): void {
    this.ingredientService.clearError();
    this.editingId.set(ingredient.id);
  }

  cancelEdit(): void {
    this.editingId.set(null);
  }

  saveEdit(ingredient: Ingredient, name: string): void {
    this.saving.set(true);
    this.ingredientService.updateIngredient(ingredient.id, { name }).subscribe({
      next: () => {
        this.editingId.set(null);
        this.saving.set(false);
      },
      error: () => this.saving.set(false)
    });
  }

  deleteIngredient(ingredient: Ingredient): void {
    if (!confirm(`Zutat "${ingredient.name}" wirklich löschen?`)) return;

    this.ingredientService.deleteIngredient(ingredient.id).subscribe({
      error: () => { /* error state handled by IngredientService.error$ */ }
    });
  }

  private load(): void {
    this.ingredientService.loadIngredients({
      search: this.searchTerm || undefined,
      page: 0,
      size: 500
    });
  }
}
