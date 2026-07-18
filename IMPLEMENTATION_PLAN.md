# RecipeAI (Speisegeist) — Implementierungsplan

**Ein selbst gehostetes, KI-gestütztes Rezept- & Kochtool für den persönlichen Gebrauch**

---

## 📋 Inhaltsverzeichnis

1. [Überblick](#überblick)
2. [Tech Stack](#tech-stack)
3. [Architektur](#architektur)
4. [Phase 1: Generator + Bibliothek](#phase-1-generator--bibliothek)
5. [Phase 2: Kochen + Einkaufen](#phase-2-kochen--einkaufen)
6. [Phase 3: Wochenplanung](#phase-3-wochenplanung)
7. [Kritische Dateien](#kritische-dateien)
8. [Abhängigkeiten](#abhängigkeiten)
9. [Verifikation](#verifikation)

---

## Überblick

**Ziel:** Ein vollständiges, selbst gehostetes Rezept-System mit KI-gestützter Generierung.

**Kernfeatures:**
- 🤖 KI-Rezeptgenerator (OpenRouter API)
- 📚 Rezeptbibliothek mit Suche & Rating
- 👥 Multi-User mit JWT-Auth
- 🍽️ Kochmodus mit Timern
- 🛒 Intelligente Einkaufslisten
- 📅 Wochenplanung mit KI-Vorschlägen

**Nutzungskontexte:**
- **Planung** (Desktop): Info-reich, Vergleich, Stöbern
- **Einkaufen** (Mobile): Große Checkboxen, eine Hand frei
- **Kochen** (Fullscreen): Ein Schritt pro Bildschirm, große Timer

---

## Tech Stack

### Backend
```
Spring Boot 4.1.0 (WAR)
├── Spring Data JPA + Hibernate
├── PostgreSQL 17
├── Flyway (Migrationen)
├── Spring Security + JWT (HS256)
├── Spring RestClient (OpenRouter)
└── JUnit + Mockito (Tests)
```

### Frontend
```
Angular 22
├── Angular Material 22
├── Angular CDK
├── Signals (State Management)
├── RxJS Observables
└── Vitest (Unit Tests)
```

### Deployment
```
Docker Compose
├── PostgreSQL Container
├── Spring Boot Container (WAR + Tomcat)
├── Nginx Container (Frontend + Reverse Proxy)
└── Docker Volumes (Persistierung)
```

### Externe Services
```
OpenRouter API
└── KI-Modelle für Rezeptgenerierung (konfigurierbar)
```

---

## Architektur

### Backend-Schichten

```
┌─────────────────────────────────────┐
│      REST API Controller Layer       │
│  /api/auth/*  /api/recipes/*        │
└──────────────────┬──────────────────┘
                   │
┌──────────────────▼──────────────────┐
│      Service Layer (Business Logic) │
│ AuthService, RecipeService,         │
│ OpenRouterIntegrationService        │
└──────────────────┬──────────────────┘
                   │
┌──────────────────▼──────────────────┐
│      Repository Layer (Data Access) │
│ UserRepository, RecipeRepository    │
└──────────────────┬──────────────────┘
                   │
┌──────────────────▼──────────────────┐
│      JPA + Hibernate + PostgreSQL   │
│   User, Recipe, RecipeIngredient    │
│         CookingStep Entities        │
└─────────────────────────────────────┘
```

### Frontend-Struktur

```
src/app/
├── core/
│   ├── services/ (ApiService, AuthService, RecipeService)
│   ├── models/ (TypeScript Interfaces)
│   ├── guards/ (AuthGuard)
│   └── interceptors/ (JWT, Error Handling)
│
├── auth/ (Login, Register Komponenten)
│
├── features/
│   ├── recipe-generator/
│   ├── recipe-library/
│   ├── recipe-detail/
│   ├── dashboard/
│   ├── shopping-list/ (Phase 2)
│   └── weekly-plan/ (Phase 3)
│
├── shared/ (Header, Footer, Material Komponenten, Theme)
│
├── app.routes.ts (Routing)
├── app.component.ts (Root Layout)
└── app.config.ts (Provider)
```

### Datenbankschema

```sql
-- User (Multi-User System)
users (id, email UNIQUE, passwordHash, createdAt, lastLogin)

-- Rezepte (User-scoped)
recipes (id, userId FK, name, description, prep_time, cook_time, 
         servings, estimated_kcal, rating, tags[], 
         source_type, source_model, created_at, updated_at)

-- Rezept-Zutaten (Owned Collection)
recipe_ingredients (id, recipeId FK, name, quantity, unit, 
                   warengruppe, notes, display_order)

-- Kochschritte (Owned Collection)
cooking_steps (id, recipeId FK, step_number, instruction, 
              duration_minutes)

-- Wochenplan (Phase 2)
weekly_plan_entries (id, userId FK, recipeId FK, day_of_week, 
                    meal_type, servings)

-- Einkaufsliste (Phase 2)
shopping_items (id, userId FK, recipeId FK, name, quantity, unit, 
               warengruppe, is_checked)
```

---

## Phase 1: Generator + Bibliothek

**Ziel:** Kern-Funktionalität: KI-Rezepte generieren, speichern, suchen, bewerten

**Dauer:** ~4-5 Wochen  
**Erfolgsmetrik:** Mindestens 3 echte Rezepte generiert, gekocht und in der Bibliothek gespeichert

### 1.1 Datenbank-Setup

**Dateien:**
- `backend/src/main/resources/db/migration/V1__initial_schema.sql`

**Tasks:**
- [ ] PostgreSQL Schema für User, Recipe, RecipeIngredient, CookingStep
- [ ] Indices auf userId, name, tags, createdAt
- [ ] Foreign Keys mit Cascade Delete
- [ ] Testlauf auf frischer PostgreSQL-Instanz

**Dependencies:** Keine

---

### 1.2 Backend: Auth (User Management)

**Dateien:**
- `backend/src/main/java/.../entity/User.java`
- `backend/src/main/java/.../repository/UserRepository.java`
- `backend/src/main/java/.../config/JwtTokenProvider.java`
- `backend/src/main/java/.../config/JwtAuthenticationFilter.java`
- `backend/src/main/java/.../config/SecurityConfig.java`
- `backend/src/main/java/.../service/AuthService.java`
- `backend/src/main/java/.../controller/AuthController.java`

**Dependencies:** Datenbank-Setup (1.1)

**Tasks:**
- [ ] User-Entity mit email (unique), passwordHash, timestamps
- [ ] UserRepository mit findByEmail()
- [ ] JwtTokenProvider: Token generieren/validieren (HS256, 24h Expiry)
- [ ] JwtAuthenticationFilter: Bearer Token extrahieren, SecurityContext setzen
- [ ] SecurityConfig: Stateless Session, JWT Filter Chain, CORS
- [ ] AuthService: register() + login() mit BCrypt
- [ ] AuthController: POST /api/auth/register, POST /api/auth/login
- [ ] Alle Endpoints brauchen JWT (außer /api/auth/*)

**Endpoints:**
```
POST   /api/auth/register     → 201 + {email, token}
POST   /api/auth/login        → 200 + {email, token}
```

---

### 1.3 Backend: Konfiguration & Dependencies

**Dateien:**
- `backend/pom.xml`
- `backend/src/main/resources/application.properties`
- `backend/src/main/resources/application-dev.properties`

**Dependencies:** Auth-Setup (1.2)

**Tasks:**
- [ ] pom.xml: spring-data-jpa, spring-security, postgresql, flyway, jjwt
- [ ] application.properties: Datasource, JPA, Flyway, JWT, Server
- [ ] application-dev.properties für Lokales Development
- [ ] Spring Boot startet ohne Fehler
- [ ] Flyway-Migrationen laufen automatisch

**Konfiguration:**
```properties
spring.datasource.url=jdbc:postgresql://postgres:5432/speisegeist
spring.jpa.hibernate.ddl-auto=validate
spring.flyway.locations=classpath:db/migration
jwt.secret=${JWT_SECRET}
jwt.expiration=86400
openrouter.api.key=${OPENROUTER_API_KEY}
```

---

### 1.4 Backend: OpenRouter Integration

**Dateien:**
- `backend/src/main/java/.../config/RestClientConfig.java`
- `backend/src/main/java/.../service/OpenRouterIntegrationService.java`
- `backend/src/main/java/.../openrouter/OpenRouterClient.java`
- `backend/src/main/java/.../openrouter/OpenRouterRequest.java`
- `backend/src/main/java/.../openrouter/OpenRouterResponse.java`

**Dependencies:** Konfiguration (1.3)

**Tasks:**
- [ ] RestClient Bean für OpenRouter (30s Timeout)
- [ ] OpenRouterClient: HTTP-Wrapper für API-Calls
- [ ] Fehlerbehandlung: 429 (Retry 3x mit Backoff), 401, 503
- [ ] Prompt-Building für Rezeptgenerierung
- [ ] JSON-Response zu Recipe-Entity Parsing
- [ ] Validierung: Alle Felder vorhanden, Mengen numerisch

**Retry-Logic:**
```
Versuch 1: Fehler → 5s warten
Versuch 2: Fehler → 10s warten
Versuch 3: Fehler → 20s warten
Nach 3x Fehler → OpenRouterUnavailableException
```

---

### 1.5 Backend: Recipe Management

**Dateien:**
- `backend/src/main/java/.../entity/Recipe.java`
- `backend/src/main/java/.../entity/RecipeIngredient.java`
- `backend/src/main/java/.../entity/CookingStep.java`
- `backend/src/main/java/.../repository/RecipeRepository.java`
- `backend/src/main/java/.../service/RecipeService.java`
- `backend/src/main/java/.../dto/RecipeDTO.java`
- `backend/src/main/java/.../controller/RecipeController.java`

**Dependencies:** OpenRouter-Integration (1.4), Auth-Setup (1.2)

**Tasks:**
- [ ] Recipe-Entity: name, description, ingredients[], steps[], userId FK, tags, rating, sourceMetadata
- [ ] RecipeIngredient & CookingStep als @Embeddable oder @Entity mit Cascade Delete
- [ ] RecipeRepository: findByUserId(), findByUserIdAndNameLike(), findByUserIdAndTagsContaining()
- [ ] RecipeService:
  - `generateRecipe(userId, ingredients, prefs)` → OpenRouter → validate → save
  - `searchRecipes(userId, query, filters)` → LIKE-Suche
  - `getRecipe(userId, recipeId)` → Ownership-Check
  - `updateRecipe(userId, recipeId, updates)` → nur name/description/rating editierbar
  - `deleteRecipe(userId, recipeId)`
  - `scaleRecipe(userId, recipeId, servings)` → DTO ohne zu speichern
  - `rateRecipe(userId, recipeId, rating)`
- [ ] RecipeDTO: Serialisierung ohne Entity-Details
- [ ] RecipeController: Alle Endpoints mit @PreAuthorize("isAuthenticated()")

**API Endpoints:**
```
POST   /api/recipes/generate        → 201 + Recipe
GET    /api/recipes?search=...      → 200 + Page<Recipe>
GET    /api/recipes/{id}            → 200 + Recipe (full detail)
POST   /api/recipes                 → 201 + Recipe (manual)
PUT    /api/recipes/{id}            → 200 + Recipe
DELETE /api/recipes/{id}            → 204 No Content
POST   /api/recipes/{id}/rating     → 200 + Recipe
GET    /api/recipes/{id}/scaled?servings=4 → 200 + Recipe (scaled)
```

---

### 1.6 Backend: Error Handling & Logging

**Dateien:**
- `backend/src/main/java/.../exception/GlobalExceptionHandler.java`
- `backend/src/main/java/.../exception/RecipeGenerationException.java`
- `backend/src/main/java/.../exception/RecipeNotFoundException.java`
- etc.

**Tasks:**
- [ ] @ControllerAdvice für globale Exception-Behandlung
- [ ] Konsistente ErrorResponse-DTOs
- [ ] Logging: DEBUG (Flows), INFO (API-Calls), WARN (Retries), ERROR (Failures)
- [ ] Keine sensitiven Daten in Logs (API Keys maskieren)

---

### 1.7 Frontend: Setup & Routing

**Dateien:**
- `frontend/package.json` (add @angular/material, @angular/cdk)
- `frontend/src/shared/styles/theme.scss`
- `frontend/src/app/app.routes.ts`
- `frontend/src/app/app.component.ts`
- `frontend/src/app/app.config.ts`

**Tasks:**
- [ ] Material Design 3 + CDK in package.json
- [ ] theme.scss: CSS Variables, Breakpoints (480px, 768px, 1200px)
- [ ] Routing: '', 'auth/login', 'auth/register', 'recipes/*'
- [ ] Root-Layout mit Header, Router-Outlet, Footer
- [ ] Dark Glassmorphism Hintergrund

**Breakpoints:**
```scss
$bp-mobile: 480px;
$bp-tablet: 768px;
$bp-desktop: 1200px;

@mixin mobile-only { @media (max-width: $bp-tablet - 1px) { @content; } }
@mixin tablet-and-up { @media (min-width: $bp-tablet) { @content; } }
@mixin desktop-only { @media (min-width: $bp-desktop) { @content; } }
```

---

### 1.8 Frontend: Auth

**Dateien:**
- `frontend/src/app/core/models/auth.model.ts`
- `frontend/src/app/core/services/auth.service.ts`
- `frontend/src/app/core/guards/auth.guard.ts`
- `frontend/src/app/core/interceptors/auth.interceptor.ts`
- `frontend/src/app/features/auth/login/login.component.ts`
- `frontend/src/app/features/auth/register/register.component.ts`

**Tasks:**
- [ ] auth.model.ts: User, LoginRequest, RegisterRequest, AuthResponse
- [ ] auth.service.ts: login(), register(), logout(), isAuthenticated$, currentUser$
- [ ] JWT in localStorage speichern
- [ ] auth.interceptor.ts: Authorization: Bearer <token> auf alle Requests
- [ ] auth.interceptor.ts: 401 → logout() + redirect /auth/login
- [ ] auth.guard.ts: canActivateFn für Route-Protection
- [ ] LoginComponent: Form + Submit → navigate /recipes/library
- [ ] RegisterComponent: Form + Validierung → auto-login
- [ ] Material Forms & Validation

---

### 1.9 Frontend: Recipe Models & API

**Dateien:**
- `frontend/src/app/core/models/recipe.model.ts`
- `frontend/src/app/core/services/api.service.ts`
- `frontend/src/app/core/services/recipe.service.ts`

**Tasks:**
- [ ] recipe.model.ts: Recipe, RecipeIngredient, CookingStep, SearchFilters, PageResponse Interfaces
- [ ] api.service.ts: HttpClient wrapper für alle Recipe-Endpoints
- [ ] recipe.service.ts: Signal-basierte State Management
  - recipeList$ (readonly Signal)
  - selectedRecipe$ (readonly Signal)
  - loading$ (readonly Signal)
  - error$ (readonly Signal)
- [ ] Methoden: loadRecipes(), selectRecipe(), generateRecipe(), deleteRecipe()
- [ ] HTTP-Fehlerbehandlung

---

### 1.10 Frontend: Recipe Generator

**Dateien:**
- `frontend/src/app/features/recipe-generator/generator.component.ts`
- `frontend/src/app/features/recipe-generator/generator.component.html`
- `frontend/src/app/features/recipe-generator/generator.component.scss`
- `frontend/src/app/features/recipe-generator/generator-result.component.ts`

**Tasks:**
- [ ] Generator-Form: Zutaten (Array mit Add/Remove), Präferenzen (Küche, Zeit, Portionen)
- [ ] Submit → ApiService.generateRecipe() → Loading Spinner
- [ ] Fehlerbehandlung: Toast mit User-freundliche Meldung
- [ ] Ergebnis-Komponente: vollständiges Rezept anzeigen
- [ ] Buttons: Speichern (POST /api/recipes), Verwerfen, Neu generieren
- [ ] Material Layout, responsive Design

---

### 1.11 Frontend: Recipe Library & Detail

**Dateien:**
- `frontend/src/app/features/recipe-library/library.component.ts`
- `frontend/src/app/features/recipe-list/recipe-list.component.ts`
- `frontend/src/app/features/recipe-detail/detail.component.ts`

**Tasks:**
- [ ] Library: Such-Input (debounced 300ms) + Filter-Sidebar
- [ ] Recipe-Liste: Paginated Cards (Karten-Grid)
- [ ] Card: Name, Tags, Rating, Prep-Time, Kcal
- [ ] Click → navigate /recipes/{id}
- [ ] Detail-View: Vollständiges Rezept
  - Name (editable), Description, Ingredients (Tabelle), Steps (nummeriert), Nährwerte
  - Portion-Scaler (live update, no API call)
  - Buttons: Edit, Delete (Confirm), Rate (Stars), Cook Mode (Phase 2)
- [ ] Source-Info anzeigen (generiert von KI, Modellname)
- [ ] Responsive: Mobile (1 col), Tablet (2 cols), Desktop (3 cols)

---

### 1.12 Frontend: Shared Components

**Dateien:**
- `frontend/src/app/shared/components/header/header.component.ts`
- `frontend/src/app/shared/components/footer/footer.component.ts`
- `frontend/src/app/shared/components/recipe-card/recipe-card.component.ts`
- `frontend/src/app/shared/components/error-banner/error-banner.component.ts`
- `frontend/src/app/shared/components/loading-spinner/loading-spinner.component.ts`

**Tasks:**
- [ ] Header: Navigation (Dashboard, Generator, Library), User-Dropdown (Email + Logout)
- [ ] Footer: Copyright, Links (optional)
- [ ] Recipe-Card: Reusable Komponente (Input: Recipe, Output: Click)
- [ ] Error-Banner: Global Error Toast (subscribed to error$ service)
- [ ] Loading-Spinner: Material MatProgressSpinner

---

### 1.13 Infrastructure & Docker

**Dateien:**
- `docker-compose.yml`
- `backend/Dockerfile`
- `frontend/Dockerfile`
- `frontend/nginx.conf`
- `.env.example`
- `README.md`

**Tasks:**
- [ ] docker-compose.yml: PostgreSQL + Backend + Frontend
- [ ] Backend Dockerfile: Multi-stage Maven Build → WAR → Tomcat
- [ ] Frontend Dockerfile: Multi-stage Node Build → Nginx
- [ ] nginx.conf: SPA Routing + /api Reverse Proxy zu Backend
- [ ] .env.example: OPENROUTER_API_KEY, DB_PASSWORD
- [ ] README.md: Setup-Anleitung, Local Dev, Docker Compose

---

### 1.14 Testing & Verification

**Phase 1 Abnahmekriterien:**

**Backend:**
- [ ] Unit Tests: ≥40% Coverage (Services, Repositories)
- [ ] Integration Tests: JPA Entities, Controller (MockMvc)
- [ ] Flyway-Migrationen laufen auf Fresh PostgreSQL
- [ ] Error Handling: 401, 404, 503, 400

**Frontend:**
- [ ] TypeScript Compilation: `npm run build` fehlerfrei
- [ ] Auth Flow: Register → Login → Protected Routes
- [ ] Responsive: 480px, 768px, 1200px getestet

**E2E User Story (muss komplett funktionieren):**
```
1. User registriert sich (POST /api/auth/register)
2. User loggt sich ein (POST /api/auth/login)
3. User navigiert zu Generator
4. User gibt Zutaten ein: Tofu, Tomaten, Zwiebeln
5. User klickt "Generieren" → Loading Spinner
6. Rezept wird angezeigt: Name, Zutaten mit Mengen, Schritte, Kcal
7. User klickt "Speichern" → Rezept in DB gespeichert
8. User navigiert zu Bibliothek
9. User sucht "Tofu" → Rezept erscheint
10. User klickt Rezept → Detail-View
11. User skaliert auf 4 Portionen → Mengen live updated
12. User editiert Rezept-Name → Speichern → Änderung sichtbar
13. User löscht Rezept → Confirm → Rezept weg
14. Keine Fehlerseiten, kein Console-Fehler
```

**Docker Verification:**
- [ ] `docker-compose up -d` startet alle Services
- [ ] http://localhost:4200 ist erreichbar
- [ ] PostgreSQL: `\d recipes` zeigt Tabelle
- [ ] Backend-Logs: "Started BackendApplication" mit 0 Errors
- [ ] JWT wird korrekt validiert
- [ ] Daten persistent über Container-Restart

---

## Phase 2: Kochen + Einkaufen

**Ziel:** Cooking Mode mit Timern, Portion-Scaling, intelligente Einkaufslisten

**Dauer:** ~3-4 Wochen  
**Erfolgsmetrik:** Kompletter Kochabend (inklusive Einkauf) mit dem Tool durchgeführt

### 2.1 Kochmodus

**Neue Komponente:**
- `frontend/src/app/features/cooking-mode/cooking.component.ts`
- `frontend/src/app/features/cooking-mode/cooking-step.component.ts`
- `frontend/src/app/features/cooking-mode/cooking-timer.component.ts`

**Features:**
- Vollbild-Modus
- Ein Schritt pro Bildschirm
- Großer Timer (200px Font) mit Audio-Alert bei Timeout
- Vor/Zurück Navigation (große Buttons für nasse Hände)
- Zutaten-Sidebar (ausblendbar)
- Display bleibt an (Wakely API / Manifest)

---

### 2.2 Einkaufsliste

**Neue Entities:**
- `ShoppingItem` (id, userId FK, recipeId FK, name, quantity, unit, warengruppe, is_checked)

**Service & Controller:**
- `ShoppingListService`: generateFromRecipes(), aggregateByWarengruppe()
- `ShoppingListController`: GET /api/shopping-list, PATCH /api/shopping-items/{id}

**Frontend:**
- `shopping-list.component.ts`: Gruppiert nach Warengruppe, große Checkboxen
- Mobile-optimiert: Thumb-freundliche Targets (≥48px)

---

### 2.3 Portion-Skalierung

**Logik:**
- Mengen-Umrechnung mit sinnvollen Rundungen
- 1,33 EL → 1 EL (nicht 1,3333)
- 1,67 EL → 2 EL
- Client-seitig (keine API-Calls)

---

## Phase 3: Wochenplanung

**Ziel:** KI-gestützte Wochenplanung mit Kcal-Zielen und Meal-Prep

**Dauer:** ~2-3 Wochen

### 3.1 Wochenplan Entity & Service

**Entities:**
- `WeeklyPlanEntry` (id, userId FK, recipeId FK, day_of_week, meal_type, servings)

**Service:**
- `WeeklyPlanService`: generateWeeklyPlan(userId, kcalTarget, mealPrepPrefs)

---

### 3.2 Frontend: Plan-Komponente

**Features:**
- Kalender-Grid (Mo–So)
- Mahlzeits-Slots (Frühstück/Mittag/Abend)
- Drag-Drop oder KI-Vorschlag
- Kcal-Summe pro Tag sichtbar
- Wochen-Einkaufsliste Export

---

## Kritische Dateien

### Backend (Priorität nach Dependencies)

#### Datenbank & Entities
```
backend/src/main/resources/db/migration/V1__initial_schema.sql
backend/src/main/java/com/philipphofmann/backend/entity/User.java
backend/src/main/java/com/philipphofmann/backend/entity/Recipe.java
backend/src/main/java/com/philipphofmann/backend/entity/RecipeIngredient.java
backend/src/main/java/com/philipphofmann/backend/entity/CookingStep.java
```

#### Repositories
```
backend/src/main/java/com/philipphofmann/backend/repository/UserRepository.java
backend/src/main/java/com/philipphofmann/backend/repository/RecipeRepository.java
```

#### Security & Config
```
backend/src/main/java/com/philipphofmann/backend/config/JwtTokenProvider.java
backend/src/main/java/com/philipphofmann/backend/config/JwtAuthenticationFilter.java
backend/src/main/java/com/philipphofmann/backend/config/SecurityConfig.java
backend/src/main/java/com/philipphofmann/backend/config/RestClientConfig.java
backend/src/main/resources/application.properties
backend/src/main/resources/application-dev.properties
```

#### Services
```
backend/src/main/java/com/philipphofmann/backend/service/AuthService.java
backend/src/main/java/com/philipphofmann/backend/service/OpenRouterIntegrationService.java
backend/src/main/java/com/philipphofmann/backend/service/RecipeService.java
```

#### Controllers & DTOs
```
backend/src/main/java/com/philipphofmann/backend/controller/AuthController.java
backend/src/main/java/com/philipphofmann/backend/controller/RecipeController.java
backend/src/main/java/com/philipphofmann/backend/dto/RecipeDTO.java
backend/src/main/java/com/philipphofmann/backend/dto/AuthRequest.java
backend/src/main/java/com/philipphofmann/backend/exception/GlobalExceptionHandler.java
```

### Frontend (Priorität nach Dependencies)

#### Core (Models, Services, Config)
```
frontend/src/app/core/models/auth.model.ts
frontend/src/app/core/models/recipe.model.ts
frontend/src/app/core/services/auth.service.ts
frontend/src/app/core/services/api.service.ts
frontend/src/app/core/services/recipe.service.ts
frontend/src/app/core/guards/auth.guard.ts
frontend/src/app/core/interceptors/auth.interceptor.ts
frontend/src/app/app.routes.ts
frontend/src/app/app.config.ts
```

#### Auth Features
```
frontend/src/app/features/auth/login/login.component.ts
frontend/src/app/features/auth/register/register.component.ts
```

#### Recipe Features
```
frontend/src/app/features/recipe-generator/generator.component.ts
frontend/src/app/features/recipe-generator/generator-result.component.ts
frontend/src/app/features/recipe-library/library.component.ts
frontend/src/app/features/recipe-list/recipe-list.component.ts
frontend/src/app/features/recipe-detail/detail.component.ts
```

#### Shared & Layout
```
frontend/src/app/app.component.ts
frontend/src/shared/styles/theme.scss
frontend/src/shared/components/header/header.component.ts
frontend/src/shared/components/recipe-card/recipe-card.component.ts
frontend/src/shared/components/error-banner/error-banner.component.ts
frontend/src/shared/components/loading-spinner/loading-spinner.component.ts
```

### Infrastructure
```
docker-compose.yml
backend/Dockerfile
frontend/Dockerfile
frontend/nginx.conf
.env.example
README.md
```

---

## Abhängigkeiten

### Backend Dependency Graph

```
V1__initial_schema.sql (DB-Migration)
├── User.java
│   ├── UserRepository
│   │   └── AuthService
│   │       └── AuthController
│   └── JwtTokenProvider ← SecurityConfig
│       └── JwtAuthenticationFilter
│
├── Recipe.java
│   ├── RecipeRepository
│   │   └── RecipeService
│   │       ├── AuthService (user ownership)
│   │       └── OpenRouterIntegrationService
│   │           └── RecipeController

RestClientConfig
└── OpenRouterIntegrationService
    └── RecipeService
```

### Frontend Dependency Graph

```
theme.scss (Material & Breakpoints)
└── Header, Recipe-Card, alle Komponenten

auth.model.ts
└── auth.service.ts
    ├── LoginComponent
    ├── RegisterComponent
    ├── auth.guard.ts
    ├── auth.interceptor.ts
    └── app.routes.ts

recipe.model.ts
└── api.service.ts
    └── recipe.service.ts
        ├── RecipeGeneratorComponent
        ├── RecipeLibraryComponent
        └── RecipeDetailComponent

app.routes.ts
├── LoginComponent
├── RegisterComponent
├── RecipeGeneratorComponent
├── RecipeLibraryComponent
└── RecipeDetailComponent
```

---

## Verifikation

### Phase 1 Success Criteria

#### Funktional
- [ ] User kann sich registrieren + einloggen
- [ ] User kann Rezept generieren (3+ Zutaten eingeben)
- [ ] Generiertes Rezept zeigt: Name, Zutaten mit Mengen, Schritte, Kcal
- [ ] Rezept speichern funktioniert (POST /api/recipes)
- [ ] Bibliothek zeigt alle User-Rezepte
- [ ] Suche nach Zutat/Tag filtern Rezepte
- [ ] Rezept-Detail zeigt alle Felder
- [ ] Portion-Scaler funktioniert (live update)
- [ ] Edit/Delete/Rate-Buttons funktionieren
- [ ] Logout funktioniert

#### Performance
- [ ] Rezept-Generierung: <30 Sekunden
- [ ] Such-Ergebnisse: <1 Sekunde
- [ ] Detail-View: <500ms Load
- [ ] Page-Load: <3 Sekunden

#### Quality
- [ ] Backend Unit Tests: ≥40% Coverage
- [ ] Frontend TypeScript: 0 Errors
- [ ] Kein Console-Fehler beim Golden Path
- [ ] Responsive: 480px ✓, 768px ✓, 1200px ✓
- [ ] Error-Handling: 401, 404, 503 zeigen User-freundliche Messages

#### Deployment
- [ ] `docker-compose up -d` → alle Services up
- [ ] PostgreSQL: Daten persistent
- [ ] http://localhost:4200 erreichbar
- [ ] http://localhost:8080/api/auth/login erreichbar
- [ ] Rezept generieren → speichern → Bibliothek → erfolgreich

#### Real-World Test
- [ ] User generiert 3+ echte Rezepte
- [ ] User kocht mindestens 1 Rezept nach
- [ ] Alle Schritte funktionieren ohne Fehler

---

## Nächste Schritte

1. **Repository Setup:**
   - [ ] `backend/` und `frontend/` aus Scaffolds cleanen
   - [ ] `.env` mit OPENROUTER_API_KEY + DB_PASSWORD füllen

2. **Lokales Development starten:**
   ```bash
   # Backend
   cd backend
   mvn spring-boot:run -Dspring-boot.run.arguments="--spring.profiles.active=dev"
   
   # Frontend
   cd frontend
   npm install
   npm start
   
   # PostgreSQL
   docker run -d --name postgres-dev -e POSTGRES_PASSWORD=dev123 -p 5432:5432 postgres:17-alpine
   ```

3. **Phase 1 Implementation:**
   - Beginne mit Section 1.1 (Datenbank-Setup)
   - Folge den Dependencies
   - Nach jeder Section testen

4. **Docker Deployment testen:**
   ```bash
   docker-compose up -d
   ```

---

**Status:** 🟢 Bereit für Implementation  
**Letzte Aktualisierung:** 2026-07-18  
**Version:** 1.0
