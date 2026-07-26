# Speisegeist — Frontend

Angular 22 SPA für den Speisegeist-Rezeptgenerator.

## Entwicklung

```bash
npm install
npm start        # Dev-Server auf http://localhost:4200
```

Der Dev-Server leitet `/api/**` automatisch an `http://localhost:8080` weiter (Proxy-Konfiguration in `angular.json`). Das Backend muss laufen.

## Tests

```bash
npm test         # Vitest (einmalig)
npm test -- --watch   # Watch-Modus
```

Getestet: `AuthService`, `RecipeService`, `IngredientService`, `AuthGuard`, `AdminGuard`

## Build

```bash
npm run build    # Produktions-Build → dist/frontend/browser/
```

Das Dockerfile baut das Artefakt automatisch und liefert es via Nginx aus.

## Projektstruktur

```
src/app/
  core/
    services/       AuthService, RecipeService, IngredientService
    guards/         AuthGuard (JWT erforderlich), AdminGuard (Rolle ADMIN)
    interceptors/   JWT-Header bei jedem API-Request
    models/         TypeScript-Interfaces
  features/
    auth/           Login, Registrierung
    dashboard/      Startseite
    recipe-library/ Rezeptbibliothek mit Suche und Tag-Filter
    recipe-detail/  Rezeptdetail, Bewertung, Bearbeitung
    recipe-generator/ KI-Generator mit Echtzeit-Streaming (SSE)
    recipe-manual/  Manuelles Rezept anlegen
    ingredient-management/ Zutatenkatalog
    admin/          Admin-Dashboard, Benutzerverwaltung
  shared/
    components/     RecipeCard, Header, Footer, ErrorBanner, LoadingSpinner
```

## Wichtige Umgebungsdetails

- **API-Prefix:** alle Requests gehen an `/api/...` (Nginx leitet im Produktions-Build weiter)
- **Authentifizierung:** JWT im `localStorage`, wird von `AuthInterceptor` als `Authorization: Bearer …` mitgeschickt
- **Streaming:** Rezeptgenerierung nutzt Server-Sent Events (SSE) — Nginx muss `proxy_buffering off` haben (bereits konfiguriert)
