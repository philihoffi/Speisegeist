import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatIconModule } from '@angular/material/icon';
import { MatButtonModule } from '@angular/material/button';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { Subject, debounceTime, distinctUntilChanged } from 'rxjs';
import { HeaderComponent } from '../../../../shared/components/header/header.component';
import { RecipeCardComponent } from '../../../../shared/components/recipe-card/recipe-card.component';
import { RecipeService } from '../../../../core/services/recipe.service';

@Component({
  selector: 'app-library',
  standalone: true,
  imports: [
    CommonModule, FormsModule, RouterLink, HeaderComponent, RecipeCardComponent,
    MatFormFieldModule, MatInputModule, MatIconModule, MatButtonModule, MatProgressSpinnerModule
  ],
  templateUrl: './library.component.html',
  styleUrl: './library.component.scss'
})
export class LibraryComponent implements OnInit {
  searchTerm = '';
  private search$ = new Subject<string>();

  constructor(public recipeService: RecipeService) {}

  ngOnInit(): void {
    this.recipeService.loadRecipes();
    this.search$.pipe(
      debounceTime(300),
      distinctUntilChanged()
    ).subscribe(term => {
      this.recipeService.loadRecipes({ search: term || undefined });
    });
  }

  onSearchChange(term: string): void {
    this.searchTerm = term;
    this.search$.next(term);
  }
}
