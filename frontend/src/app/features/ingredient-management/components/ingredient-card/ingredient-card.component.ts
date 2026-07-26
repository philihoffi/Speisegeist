import { Component, EventEmitter, Input, Output } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { IngredientImageComponent } from '../../../../shared/components/ingredient-image/ingredient-image.component';
import { Ingredient } from '../../../../core/models/ingredient.model';

@Component({
  selector: 'app-ingredient-card',
  standalone: true,
  imports: [
    CommonModule, FormsModule, MatFormFieldModule, MatInputModule,
    MatButtonModule, MatIconModule, IngredientImageComponent
  ],
  templateUrl: './ingredient-card.component.html',
  styleUrl: './ingredient-card.component.scss'
})
export class IngredientCardComponent {
  @Input({ required: true }) ingredient!: Ingredient;
  @Input() editingId: string | null = null;
  @Input() saving = false;

  @Output() edit = new EventEmitter<Ingredient>();
  @Output() cancel = new EventEmitter<void>();
  @Output() save = new EventEmitter<string>();
  @Output() remove = new EventEmitter<Ingredient>();

  editName = '';

  get isEditing(): boolean {
    return this.editingId === this.ingredient.id;
  }

  onSave(): void {
    const name = this.editName.trim();
    if (!name) return;
    this.save.emit(name);
  }

  onCancel(): void {
    this.cancel.emit();
  }

  onEdit(): void {
    this.editName = this.ingredient.name;
    this.edit.emit(this.ingredient);
  }

  onRemove(): void {
    this.remove.emit(this.ingredient);
  }
}
