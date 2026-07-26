# Speisegeist — Backend

Spring Boot 4 REST-API mit JWT-Auth, PostgreSQL und OpenRouter-Integration.

## Entwicklung

```bash
# Datenbank muss laufen (aus dem Root-Verzeichnis):
# docker compose up -d postgres

./mvnw spring-boot:run
```

API läuft auf http://localhost:8080/api

## Tests

```bash
# Unit- und Controller-Tests (kein Docker nötig) — 128 Tests
./mvnw test

# Zusätzlich Integrationstests (braucht Docker für Testcontainers)
./mvnw test -Pintegration
```

Der JaCoCo-Report liegt danach unter `target/site/jacoco/index.html`.

**Service-Tests** (Mockito, kein Spring-Kontext):

| Test | Abgedeckt |
|------|-----------|
| `AuthServiceTest` | Registrierung, Login, E-Mail-Normalisierung |
| `RecipeServiceTest` | Erstellen, Suchen, Generieren, partielles Update |
| `IngredientServiceTest` | Normalisierung, Deduplizierung, CRUD |
| `AdminServiceTest` | Statistiken, Benutzerliste, Rollenwechsel, Löschen |
| `RecipeGeneratorServiceTest` | JSON-Parsing, Markdown-Fences, Retry bei 429, Prompt-Aufbau |
| `OpenRouterServiceImplTest` | Response-Parsing gegen `MockRestServiceServer`: Provider-Fehler (top-level, per-choice, `finish_reason=error`), Token-Limit, Key-Info, Bild-Dekodierung (b64) |
| `IngredientImageServiceTest` | Cache-Treffer ohne Provider-Aufruf, Generierung, Prompt-Inhalt |
| `RecipeImageServiceTest` | Cache-Treffer, Prompt aus Name/Beschreibung, max. 4 Zutaten |

**Controller-Tests** (MockMvc, standalone):

| Test | Abgedeckt |
|------|-----------|
| `AuthControllerTest` | Register 201, Login 200 |
| `RecipeControllerTest` | Search, Get, Create, Delete, Generate, Rate |
| `IngredientControllerTest` | List, Get, Create inkl. Validierung 400, Update, Delete |
| `AdminControllerTest` | Stats, Users, DeleteUser 404, UpdateRole |

**Config & Infrastruktur:**

| Test | Abgedeckt |
|------|-----------|
| `JwtTokenProviderTest` | Generierung, Validierung, Extraktion, Ablauf, manipuliertes Token |
| `JwtAuthenticationFilterTest` | SecurityContext-Befüllung, Admin-Authorities, ungültiges/fehlendes Token |
| `DataInitializerTest` | Admin-Anlage, E-Mail-Normalisierung, Passwort-Hashing, alle Skip-Bedingungen |
| `GlobalExceptionHandlerTest` | Status-Mapping aller 9 Handler + keine internen Details im Response |
| `StreamingJsonWriterTest` | NDJSON-Format, ein JSON-Objekt pro Zeile, Fehler-Records |

**Integrationstests** (`@Tag("integration")`, Testcontainers + echtes PostgreSQL 17):
- `RecipeRepositoryTest` — `findBySearch` (Zutat, Name, case-insensitive), `findByTag`

## Projektstruktur

```
src/main/java/com/philipphofmann/backend/
  controller/     REST-Endpunkte
  service/        Geschäftslogik
    AuthService             Registrierung, Login, JWT
    RecipeService           Rezept-CRUD, Bewertung, Generierung
    IngredientService       Katalog-CRUD, Normalisierung
    AdminService            Benutzer- und Rollenverwaltung
    RecipeGeneratorService  Streaming-Generierung via OpenRouter
    OpenRouterServiceImpl   HTTP-Client für OpenRouter API
    RecipeImageService      KI-generierte Rezeptbilder
    IngredientImageService  KI-generierte Zutatenbilder
  entity/         JPA-Entities (User, Recipe, Ingredient, …)
  repository/     Spring Data JPA Repositories
  config/
    SecurityConfig          Spring Security, JWT-Filter, CORS
    JwtTokenProvider        HS256 Token-Generierung und -Validierung
    JwtAuthenticationFilter Token aus Request extrahieren
    DataInitializer         Admin-Account beim ersten Start anlegen
  dto/            Request/Response Records
  exception/      Eigene Exceptions + GlobalExceptionHandler
src/main/resources/
  db/migration/   Flyway-Migrationsskripte
  application.properties
```

## Konfiguration

| Property | Beschreibung | Default |
|----------|-------------|---------|
| `jwt.secret` | HS256-Secret (mind. 32 Zeichen) | — |
| `jwt.expiration` | Token-Laufzeit in Sekunden | `86400` |
| `openrouter.api.key` | API-Key für KI-Generierung | — |
| `openrouter.api.retry-max-attempts` | Wiederholungen bei 429/5xx | `3` |
| `management.server.port` | Actuator-Port (Healthcheck intern) | `9090` |
| `spring.web.cors.allowed-origins` | Erlaubte CORS-Origins | — |

## Datenbank

Flyway verwaltet alle Schema-Migrationen unter `src/main/resources/db/migration/`.  
Lokale Entwicklung: PostgreSQL via `docker compose up -d postgres` (aus Root).  
Produktion: PostgreSQL-Container im `docker-compose.prod.yml`.

## Healthcheck

Der Actuator-Endpunkt `/actuator/health` läuft auf Port `9090` (vom öffentlichen Port getrennt).  
Docker und Portainer nutzen diesen Endpunkt für den Container-Healthcheck.
