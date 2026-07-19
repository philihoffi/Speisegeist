import { Component, OnDestroy, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatIconModule } from '@angular/material/icon';
import { MatButtonModule } from '@angular/material/button';
import { MatChipsModule } from '@angular/material/chips';
import { MatPaginatorModule, PageEvent } from '@angular/material/paginator';
import { Subject, Subscription, debounceTime, distinctUntilChanged } from 'rxjs';
import { HeaderComponent } from '../../../../shared/components/header/header.component';
import { RecipeCardComponent } from '../../../../shared/components/recipe-card/recipe-card.component';
import { ErrorBannerComponent } from '../../../../shared/components/error-banner/error-banner.component';
import { LoadingSpinnerComponent } from '../../../../shared/components/loading-spinner/loading-spinner.component';
import { RecipeService } from '../../../../core/services/recipe.service';

@Component({
  selector: 'app-library',
  standalone: true,
  imports: [
    CommonModule, FormsModule, RouterLink, HeaderComponent, RecipeCardComponent,
    ErrorBannerComponent, LoadingSpinnerComponent,
    MatFormFieldModule, MatInputModule, MatIconModule, MatButtonModule,
    MatChipsModule, MatPaginatorModule
  ],
  templateUrl: './library.component.html',
  styleUrl: './library.component.scss'
})
export class LibraryComponent implements OnInit, OnDestroy {
  searchTerm = '';
  selectedTag: string | null = null;
  availableTags: string[] = [];
  pageIndex = 0;
  pageSize = 12;

  private search$ = new Subject<string>();
  private subscriptions = new Subscription();

  constructor(public recipeService: RecipeService) {}

  ngOnInit(): void {
    this.load();
    this.subscriptions.add(
      this.search$.pipe(
        debounceTime(300),
        distinctUntilChanged()
      ).subscribe(() => {
        this.pageIndex = 0;
        this.load();
      })
    );
    this.subscriptions.add(
      this.recipeService.recipes$.subscribe(recipes => {
        // Tags aus den geladenen Rezepten sammeln; gewählten Tag nicht verlieren
        const tags = new Set<string>(this.selectedTag ? [this.selectedTag] : []);
        recipes.forEach(r => r.tags.forEach(t => tags.add(t)));
        this.availableTags = [...tags].sort((a, b) => a.localeCompare(b, 'de'));
      })
    );
  }

  ngOnDestroy(): void {
    this.subscriptions.unsubscribe();
  }

  onSearchChange(term: string): void {
    this.searchTerm = term;
    this.search$.next(term);
  }

  onTagChange(tag: string | undefined): void {
    this.selectedTag = tag || null;
    this.pageIndex = 0;
    this.load();
  }

  onPageChange(event: PageEvent): void {
    this.pageIndex = event.pageIndex;
    this.pageSize = event.pageSize;
    this.load();
  }

  private load(): void {
    this.recipeService.loadRecipes({
      search: this.searchTerm || undefined,
      tag: this.selectedTag || undefined,
      page: this.pageIndex,
      size: this.pageSize
    });
  }
}
