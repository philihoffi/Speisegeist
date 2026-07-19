import { Component } from '@angular/core';
import { CommonModule } from '@angular/common';

/**
 * Application footer showing the current year.
 */
@Component({
  selector: 'app-footer',
  standalone: true,
  imports: [CommonModule],
  templateUrl: './footer.component.html',
  styleUrl: './footer.component.scss'
})
export class FooterComponent {
  year = new Date().getFullYear();
}
