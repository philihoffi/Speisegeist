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
# Unit-Tests (kein Docker nötig)
./mvnw test

# Integrationstests (braucht Docker für Testcontainers)
./mvnw test -Pintegration
```

**Unit-Tests** laufen mit Mockito ohne Spring-Kontext:
- `JwtTokenProviderTest` — Token-Generierung, Validierung, Extraktion, Ablauf
- `AuthServiceTest` — Registrierung, Login, E-Mail-Normalisierung
- `RecipeServiceTest` — Erstellen, Suchen, Generieren, Aktualisieren
- `IngredientServiceTest` — Normalisierung, Deduplizierung, CRUD
- `AdminServiceTest` — Statistiken, Benutzerliste, Rollen, Löschen

**Controller-Tests** mit MockMvc (kein Spring-Kontext):
- `AuthControllerTest` — Register 201, Login 200
- `RecipeControllerTest` — Search, Get, Create, Delete, Generate, Rate
- `IngredientControllerTest` — List, Get, Create (Validation), Update, Delete
- `AdminControllerTest` — Stats, Users, DeleteUser, UpdateRole

**Integrationstests** (`@Tag("integration")`) mit Testcontainers:
- `RecipeRepositoryTest` — findBySearch, findByTag gegen echtes PostgreSQL 17

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
