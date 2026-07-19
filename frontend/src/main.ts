import { bootstrapApplication } from '@angular/platform-browser';
import { appConfig } from './app/app.config';
import { AppComponent } from './app/app.component';

/**
 * Bootstraps the Angular application with the defined configuration.
 */
bootstrapApplication(AppComponent, appConfig)
  .catch((err) => console.error(err));
