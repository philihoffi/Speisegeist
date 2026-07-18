# RecipeAI (Speisegeist)

Ein selbst gehostetes, KI-gestütztes Rezept- & Kochtool für den persönlichen Gebrauch.
Backend: Spring Boot 4 (Java 17/21) + PostgreSQL + JWT. Frontend: Angular 22 + Angular Material.

## Voraussetzungen

- Java 17 oder 21 (für das Backend)
- Node.js 22 (für das Frontend)
- Docker & Docker Compose (für den Gesamt-Stack)
- Eine OpenRouter-API-Keys (`.env` → `OPENROUTER_API_KEY`)

## Lokale Entwicklung

### 1. Umgebung

Kopiere `.env.example` zu `.env` und trage mindestens `JWT_SECRET` (≥32 Zeichen) und
`OPENROUTER_API_KEY` ein.

```bash
cp .env.example .env
```

### 2. Datenbank

```bash
docker compose up -d postgres
```

### 3. Backend

```bash
cd backend
mvn spring-boot:run -Dspring-boot.run.arguments="--spring.profiles.active=dev"
```

### 4. Frontend

```bash
cd frontend
npm install
npm start
```

- Frontend: http://localhost:4200
- API (via Dev-Proxy): http://localhost:4200/api → http://localhost:8080/api

## Docker Compose (komplett)

Startet PostgreSQL, Backend und Frontend (Nginx) gemeinsam:

```bash
docker compose up -d --build
```

- Frontend: http://localhost:4200 (Nginx, reverse-proxy `/api` → Backend)
- Backend direkt: http://localhost:8080/api

## Tests

### Backend

```bash
cd backend
mvn test
```

Unit-Tests für `AuthService` und `RecipeService`, sowie ein Integration-Test für die
Such-Queries (Repository) laufen gegen eine H2-In-Memory-Datenbank.

### Frontend

```bash
cd frontend
npm test
```

## Wichtige Konfigurationswerte (application.properties)

| Property | Zweck |
|----------|-------|
| `jwt.secret` | HS256-Secret für Token-Signatur (aus `.env`) |
| `jwt.expiration` | Token-Gültigkeit in Sekunden (Default 86400) |
| `openrouter.api.key` | API-Key für die Rezeptgenerierung |
| `openrouter.api.retry-max-attempts` | Anzahl Versuche bei 429/5xx/Timeout |
| `spring.web.cors.allowed-origins` | Erlaubte CORS-Origins |

## Projektstruktur

```
backend/   Spring Boot REST-API (Auth, Rezepte, OpenRouter-Integration)
frontend/  Angular SPA (Generator, Bibliothek, Detail)
docker-compose.yml  PostgreSQL + Backend + Frontend(Nginx)
```
