# Speisegeist

[![CI](https://github.com/philihoffi/Speisegeist/actions/workflows/ci.yml/badge.svg)](https://github.com/philihoffi/Speisegeist/actions/workflows/ci.yml)
[![CD](https://github.com/philihoffi/Speisegeist/actions/workflows/cd.yml/badge.svg)](https://github.com/philihoffi/Speisegeist/actions/workflows/cd.yml)
[![codecov](https://codecov.io/gh/philihoffi/Speisegeist/graph/badge.svg)](https://codecov.io/gh/philihoffi/Speisegeist)
[![Backend Coverage](https://codecov.io/gh/philihoffi/Speisegeist/graph/badge.svg?flag=backend)](https://codecov.io/gh/philihoffi/Speisegeist?flags=backend)
[![Frontend Coverage](https://codecov.io/gh/philihoffi/Speisegeist/graph/badge.svg?flag=frontend)](https://codecov.io/gh/philihoffi/Speisegeist?flags=frontend)
![Spring Boot](https://img.shields.io/badge/Spring%20Boot-4.x-6DB33F?logo=springboot&logoColor=white)
![Angular](https://img.shields.io/badge/Angular-22-DD0031?logo=angular&logoColor=white)
![PostgreSQL](https://img.shields.io/badge/PostgreSQL-17-4169E1?logo=postgresql&logoColor=white)
![Docker](https://img.shields.io/badge/Docker-ready-2496ED?logo=docker&logoColor=white)

Ein selbst gehostetes, KI-gestütztes Rezept- und Kochtool. Generiere Rezepte per KI, verwalte eine Zutatenbibliothek und bewerte deine Lieblingsrezepte — alles lokal auf deiner eigenen Infrastruktur.

**Stack:** Spring Boot 4 · PostgreSQL 17 · Angular 22 · Docker · GitHub Actions · Portainer

---

## Features

- **KI-Rezeptgenerator** — Streaming-Generierung via OpenRouter (GPT-4o, Claude, u. a.)
- **Rezeptbibliothek** — Suche, Filter nach Tags, Bewertungen
- **Zutatenkatalog** — Automatische Normalisierung & Deduplizierung
- **KI-Bilder** — Automatisch generierte Bilder für Rezepte und Zutaten
- **Admin-Dashboard** — Benutzerverwaltung, DB-Statistiken, API-Key-Info
- **JWT-Auth** — Registrierung, Login, rollenbasierte Zugriffskontrolle

---

## Lokale Entwicklung

### Voraussetzungen

- Java 21 · Node.js 22 · Docker

### Schnellstart

```bash
# 1. Umgebungsvariablen anlegen
cp .env.example .env
# .env editieren: JWT_SECRET (≥32 Zeichen) und OPENROUTER_API_KEY setzen

# 2. Datenbank starten
docker compose up -d postgres

# 3. Backend (Hot-Reload)
cd backend
./mvnw spring-boot:run

# 4. Frontend (Dev-Server auf :4200)
cd frontend
npm install && npm start
```

- App: http://localhost:4200
- API: http://localhost:4200/api (Proxy → Backend :8080)

### Kompletter Stack via Docker

```bash
docker compose up -d --build
```

---

## Tests

### Backend — Unit-Tests

```bash
cd backend
./mvnw test
```

Testet: `AuthService`, `RecipeService`, `IngredientService`, `AdminService`, `JwtTokenProvider`, `AuthController`, `RecipeController`, `AdminController`, `IngredientController`

### Backend — Integrationstests (braucht Docker)

```bash
cd backend
./mvnw test -Pintegration
```

Startet PostgreSQL via Testcontainers und testet `RecipeRepository`-Queries gegen eine echte Datenbank.

### Frontend

```bash
cd frontend
npm test
```

Testet: `AuthService`, `RecipeService`, `IngredientService`, `AuthGuard`, `AdminGuard`

### Coverage-Übersicht

| Flag | Scope |
|------|-------|
| `backend` | `backend/src/main/java/` (Services, Controller, Config) |
| `frontend` | `frontend/src/app/` (Services, Guards) |

Coverage-Details und PR-Diff-Kommentare: [codecov.io/gh/philihoffi/Speisegeist](https://codecov.io/gh/philihoffi/Speisegeist)

[![Codecov Sunburst](https://codecov.io/gh/philihoffi/Speisegeist/graphs/sunburst.svg)](https://codecov.io/gh/philihoffi/Speisegeist)

---

## Deployment (Portainer auf NAS)

Der Produktions-Stack läuft auf einem Synology NAS via Portainer. GitHub Actions baut die Docker Images automatisch nach jedem erfolgreichen Merge auf `master`.

### CI/CD-Pipeline

```
git push → CI (Tests) → CD (Docker Build → ghcr.io) → Portainer pollt alle 5 min
```

**Workflows:**
- `.github/workflows/ci.yml` — Unit- und Integrationstests
- `.github/workflows/cd.yml` — Docker Images bauen und nach ghcr.io pushen

### Portainer-Stack einrichten

1. Stack → Add Stack → Repository-Methode
2. Repository URL: `https://github.com/philihoffi/Speisegeist`
3. Compose path: `docker-compose.prod.yml`
4. GitOps updates: Polling, 5m, Re-pull image aktivieren
5. Environment Variables aus `.env.prod.example` befüllen

### Umgebungsvariablen (Produktion)

| Variable | Beschreibung |
|----------|-------------|
| `GITHUB_OWNER` | GitHub-Username (für Image-Referenzen) |
| `DB_PASSWORD` | PostgreSQL-Passwort |
| `JWT_SECRET` | HS256-Secret (mind. 32 Zeichen) |
| `OPENROUTER_API_KEY` | OpenRouter API-Key |
| `ADMIN_EMAIL` | E-Mail des ersten Admin-Accounts |
| `ADMIN_PASSWORD` | Passwort des ersten Admin-Accounts |
| `CORS_ALLOWED_ORIGINS` | Öffentliche Domain (z. B. `https://xyz.myfritz.net`) |

Vorlage: [`.env.prod.example`](.env.prod.example)

---

## Projektstruktur

```
backend/                    Spring Boot REST-API
  src/main/java/
    controller/             REST-Endpunkte (Auth, Recipes, Ingredients, Admin)
    service/                Geschäftslogik & OpenRouter-Integration
    entity/                 JPA-Entities
    repository/             Spring Data Repositories
    config/                 Security, JWT, CORS
  src/test/java/            Unit- und Integrationstests
  Dockerfile                Multi-Stage Build (Maven → JRE)

frontend/                   Angular SPA
  src/app/
    core/                   Services, Guards, Interceptors, Models
    features/               Feature-Module (Generator, Bibliothek, Admin, …)
    shared/                 Wiederverwendbare Komponenten
  Dockerfile                Multi-Stage Build (Node → Nginx)
  nginx.conf                Reverse Proxy + SSE-Support

docker-compose.yml          Lokale Entwicklung
docker-compose.prod.yml     Produktion (zieht Images von ghcr.io)
.github/workflows/          CI/CD-Pipelines
deploy/validate.sh          Smoke-Test nach Deployment
```

---

## Konfigurationsreferenz

| Property | Zweck | Default |
|----------|-------|---------|
| `jwt.secret` | HS256-Signing-Secret | — |
| `jwt.expiration` | Token-Laufzeit in Sekunden | `86400` |
| `openrouter.api.key` | API-Key für Rezeptgenerierung | — |
| `openrouter.api.retry-max-attempts` | Wiederholungen bei 429/5xx | `3` |
| `management.server.port` | Actuator-Port (intern, Healthcheck) | `9090` |
| `spring.web.cors.allowed-origins` | Erlaubte CORS-Origins | — |
