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
npm test              # Vitest über den Angular-Builder
npm run test:coverage # zusätzlich Coverage-Report (coverage/frontend/)
```

| Test | Abgedeckt |
|------|-----------|
| `api.service.spec.ts` | Alle 8 HTTP-Methoden, Query-Param-Aufbau, Fehlerweitergabe |
| `auth.service.spec.ts` | Login, Registrierung, Logout, Token-Persistenz, `isAdmin` |
| `recipe.service.spec.ts` | Laden, Generieren, Löschen, Fehler- und Loading-State |
| `ingredient.service.spec.ts` | Laden, Anlegen inkl. Sortierung, Löschen, `catalogChanged$` |
| `auth.guard.spec.ts` | Zugriff mit Token, Redirect ohne Token |
| `admin.guard.spec.ts` | Zugriff als ADMIN, Redirect als USER |
| `auth.interceptor.spec.ts` | Bearer-Header, 401-Logout, Ausnahme für Auth-Endpunkte |
| `recipe-form.util.spec.ts` | Draft-Konvertierung, Payload-Bau, Neunummerierung, Leerzeilen-Filter |

Der Coverage-Scope ist bewusst auf `src/app/core/**` begrenzt — Komponenten sind
template-lastig und wären mit E2E-Tests sinnvoller abgedeckt als mit Unit-Tests.

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
