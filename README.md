# SpringBoot Job Apply Tracker API

[Frontend - React-JobApplyTracker](https://github.com/vitorhugo-java/React-JobApplyTracker) 

A production-ready Spring Boot REST API for tracking job applications, built with Java 21, Spring Security JWT authentication, MariaDB, and comprehensive test coverage.

## Tech Stack

- **Java 21**
- **Spring Boot 3.2** (Web, Data JPA, Security, Validation)
- **Spring Security** with stateless JWT authentication + role-based authorization (`USER`, `BETA`, `ADMIN`)
- **JWT + Refresh Tokens** (access: 15 min, refresh: 7 days with rotation)
- **Resilience4j Rate Limiting** on auth endpoints
- **MariaDB** (production) / **Testcontainers** (tests)
- **Flyway** for DB migrations
- **JUnit 5 + Mockito** (unit tests)
- **Testcontainers + MockMvc** (integration tests)
- **RestAssured** (E2E tests)
- **Maven**

## Project Structure

```
.
├── src/
│   ├── main/java/com/jobtracker/
│   │   ├── config/          # Security, JWT, CORS, filters
│   │   ├── controller/      # REST controllers
│   │   ├── dto/             # Request/Response DTOs
│   │   ├── entity/          # JPA entities
│   │   ├── exception/       # Global exception handling
│   │   ├── mapper/          # Entity-DTO mappers
│   │   ├── repository/      # Spring Data JPA repositories
│   │   ├── service/         # Business logic
│   │   └── util/            # Utilities
│   ├── main/resources/
│   │   ├── application.yml
│   │   └── db/migration/    # Flyway migrations
│   └── test/java/com/jobtracker/
│       ├── unit/            # Mockito unit tests
│       ├── integration/     # SpringBootTest + Testcontainers + MockMvc
│       └── e2e/             # RestAssured end-to-end tests
├── pom.xml
├── Dockerfile
├── docker-compose.yml
└── .github/workflows/ci.yml
```

## API Endpoints

### Auth

| Method | Endpoint | Description |
|--------|----------|-------------|
| POST | `/api/v1/auth/register` | Register a new user |
| POST | `/api/v1/auth/login` | Login and receive tokens |
| POST | `/api/v1/auth/refresh` | Refresh access token |
| POST | `/api/v1/auth/logout` | Logout and revoke refresh token |
| POST | `/api/v1/auth/forgot-password` | Request password reset |
| POST | `/api/v1/auth/reset-password` | Reset password with token |
| GET | `/api/v1/auth/me` | Get current user info |

### Applications

| Method | Endpoint | Description |
|--------|----------|-------------|
| POST | `/api/v1/applications` | Create application |
| GET | `/api/v1/applications` | List all (paginated + filterable) |
| GET | `/api/v1/applications/{id}` | Get by ID |
| PUT | `/api/v1/applications/{id}` | Full update |
| PATCH | `/api/v1/applications/{id}/status` | Update status |
| PATCH | `/api/v1/applications/{id}/reminder` | Toggle reminder |
| DELETE | `/api/v1/applications/{id}` | Delete |
| GET | `/api/v1/applications/upcoming` | Upcoming next steps |
| GET | `/api/v1/applications/overdue` | Overdue next steps |

## Authorization Model

- JWT access tokens now include a `roles` claim (e.g., `ROLE_USER`, `ROLE_ADMIN`).
- Existing users are backfilled with `ROLE_USER` during migration.
- A default `ROLE_USER` is assigned on registration.

Flyway seeds the roles catalog (`USER`, `BETA`, `ADMIN`) and then assigns `ROLE_USER` to all existing users.

### Endpoints currently requiring `ROLE_USER`

- `GET /api/v1/auth/me`
- `PUT /api/v1/auth/me`
- `PUT /api/v1/auth/me/password`
- `POST|GET|PUT|PATCH|DELETE /api/v1/applications/**`
- `GET|POST /api/v1/gamification/**`
- `GET /api/v1/dashboard/summary`
- `POST /api/v1/account/test-email`

### Gamification

| Method | Endpoint | Description |
|--------|----------|-------------|
| GET | `/api/v1/gamification/profile` | Get current XP, level, rank title and streak snapshot |
| GET | `/api/v1/gamification/achievements` | List achievement catalog with unlocked state |
| POST | `/api/v1/gamification/events` | Apply a tracked XP event and return updated profile |

### Google Drive

| Method | Endpoint | Description |
|--------|----------|-------------|
| POST | `/api/v1/google-drive/oauth/start` | Generate the Google OAuth authorization URL for the authenticated user |
| GET | `/api/v1/google-drive/oauth/callback` | Google OAuth callback endpoint used by Google Cloud |
| GET | `/api/v1/google-drive/status` | Return current Google Drive connection status, configured root folder, and base resumes |
| DELETE | `/api/v1/google-drive/connection` | Disconnect the current user's Google account and remove stored Drive preferences |
| PUT | `/api/v1/google-drive/root-folder` | Validate and save the user's base Drive folder |
| POST | `/api/v1/google-drive/base-resumes` | Register a Google Docs base resume by Google Docs URL or file ID |
| DELETE | `/api/v1/google-drive/base-resumes/{baseResumeId}` | Remove a configured base resume |
| POST | `/api/v1/google-drive/applications/{applicationId}/resume-copies` | Copy a configured base resume into the application's Drive subfolder |

## Application Status Values

- `RH`
- `Fiz a RH - Aguardando Atualização`
- `Fiz a Hiring Manager - Aguardando Atualização`
- `Teste Técnico`
- `Fiz teste Técnico - aguardando atualização`
- `RH (Negociação)`

## Gamification System

The backend tracks gamification in `user_gamification`, `achievements`, and `user_achievements`. XP is awarded from application lifecycle events and stored per user, while each application keeps one-time award flags so the same action is not counted twice. The service also derives the current streak and unlocks achievements from the user's non-archived applications.

### XP rules

| Action | Backend event | XP |
|--------|---------------|----|
| New application | `APPLICATION_CREATED` | +10 |
| Recruiter DM sent | `RECRUITER_DM_SENT` | +15 |
| Interview progress | `INTERVIEW_PROGRESS` | +50 |
| Note added | `NOTE_ADDED` | +5 |
| Offer / win | `OFFER_WON` | +500 |

### Level formula

- `level = floor(sqrt(totalXp / 100)) + 1`
- `XP required for level N = 100 * (N - 1)^2`

Examples:

| Level | Total XP required |
|-------|-------------------|
| 1 | 0 |
| 2 | 100 |
| 3 | 400 |
| 4 | 900 |
| 5 | 1600 |

### Rank milestones

| Milestone level | XP threshold | Rank title |
|-----------------|--------------|------------|
| 1 | 0 | Desempregado de Aluguel |
| 6 | 2500 | Job Hunter Iniciante |
| 16 | 22500 | Sobrevivente do LinkedIn |
| 31 | 90000 | Mestre das Soft Skills |
| 51 | 250000 | Lenda das Contratacoes |

### Achievements

| Code | Name | Unlock condition in the backend today |
|------|------|----------------------------------------|
| `EARLY_BIRD` | Early Bird | Have 5 non-archived applications with `applicationDate` set and `createdAt` before `09:00` |
| `NETWORKING_PRO` | Networking Pro | Have 10 recruiter DMs sent inside any rolling 7-day window |
| `PERSISTENT` | Persistent | Reach a 5-day longest streak based on distinct `applicationDate` values |
| `GHOSTBUSTER` | Ghostbuster | Have any non-archived application currently in `GHOSTING` status |

### Current backend status mapping

The backend does not currently have literal `INTERVIEW` or `HIRED` statuses.

- `INTERVIEW_PROGRESS` is awarded when `interviewScheduled = true` or when the application enters one of these statuses: `Fiz a RH - Aguardando Atualização`, `Fiz a Hiring Manager - Aguardando Atualização`, `Teste Técnico`, `Fiz teste Técnico - aguardando atualização`, or `RH (Negociação)`.
- `OFFER_WON` is currently mapped to `RH (Negociação)` (`RH_NEGOCIACAO` in code), which is the backend's current closing-stage proxy until dedicated offer/hired statuses exist.
- `GHOSTBUSTER` currently unlocks from `GHOSTING` status itself; although the seeded achievement description mentions "30 days", the implemented unlock rule is status-based today.

## Running Locally

### With Docker Compose

Development (build locally):

```bash
docker-compose up -d
```

Production (use pre-built image from GitHub Container Registry):

1. Log in to GHCR (requires a Personal Access Token with `read:packages`):

```bash
# POSIX / macOS / WSL
echo $CR_PAT | docker login ghcr.io -u vitorhugo-java --password-stdin

# Windows PowerShell
# $env:CR_PAT | docker login ghcr.io -u vitorhugo-java --password-stdin
```

2. Pull the production compose file image(s):

```bash
docker compose -f docker-compose.prod.yml pull
```

3. Start the services from the production compose file:

```bash
docker compose -f docker-compose.prod.yml up -d
```

By default this compose file pulls image: `ghcr.io/vitorhugo-java/springboot-jobapplytracker:latest`. Change the image name in `docker-compose.prod.yml` if you publish with a different tag or repository.

The API will be available at `http://localhost:8080`.

### With Maven (requires a running MariaDB)

The GPT fallback auth is optional. When enabled, it authenticates requests with a static bearer token and maps them to a configurable account.

| Variable | Required | Description |
|----------|----------|-------------|
| `APP_GPT_FALLBACK_AUTH_ENABLED` | No | Enables the GPT fallback auth filter |
| `APP_GPT_FALLBACK_AUTH_TOKEN` | No | Static bearer token accepted by the fallback flow |
| `APP_GPT_FALLBACK_AUTH_ACCOUNT_EMAIL` | No | Email of the account used by the fallback flow |
| `APP_GPT_FALLBACK_AUTH_ACCOUNT_NAME` | No | Display name used when the fallback user is created |

```bash
export DB_URL=jdbc:mariadb://localhost:3306/jobtracker?createDatabaseIfNotExist=true
export DB_USERNAME=jobtracker
export DB_PASSWORD=jobtracker
export JWT_SECRET=your-secret-key-at-least-256-bits-long
export GOOGLE_DRIVE_CLIENT_ID=your-google-client-id
export GOOGLE_DRIVE_CLIENT_SECRET=your-google-client-secret
export GOOGLE_DRIVE_REDIRECT_URI=http://localhost:8080/api/v1/google-drive/oauth/callback
export GOOGLE_DRIVE_OAUTH_COMPLETE_URL=http://localhost:5173/settings/google-drive/callback
export OPENAI_GPT_CLIENT_ID=your-openai-gpt-client-id
export OPENAI_GPT_CLIENT_SECRET=your-openai-gpt-client-secret
export OPENAI_GPT_REDIRECT_URIS=https://chat.openai.com/aip/default/callback
export OPENAI_GPT_SCOPES=read:profile,read:applications,write:applications,read:resume,read:google-drive,read:metrics
export APP_GPT_FALLBACK_AUTH_ENABLED=true
export APP_GPT_FALLBACK_AUTH_TOKEN=your-static-fallback-token
export APP_GPT_FALLBACK_AUTH_ACCOUNT_EMAIL=hugo@seudominio.com
export APP_GPT_FALLBACK_AUTH_ACCOUNT_NAME=Hugo
mvn spring-boot:run
```

## Google Drive integration

This backend supports per-user Google Drive OAuth2 for resume-copy automation. It does **not** replace the app's JWT auth flow; users stay authenticated with the existing bearer token, and Google is connected separately with `POST /api/v1/google-drive/oauth/start`.

### Required Google Cloud setup

1. Create a Google Cloud OAuth client for a web application.
2. Enable the **Google Drive API**.
3. Add the backend callback URL as an authorized redirect URI. Example local value:
   - `http://localhost:8080/api/v1/google-drive/oauth/callback`
4. Configure these environment variables:

| Variable | Required | Description |
|----------|----------|-------------|
| `GOOGLE_DRIVE_CLIENT_ID` | Yes | Google OAuth client ID |
| `GOOGLE_DRIVE_CLIENT_SECRET` | Yes | Google OAuth client secret |
| `GOOGLE_DRIVE_REDIRECT_URI` | Yes | Backend callback URL registered in Google Cloud |
| `GOOGLE_DRIVE_OAUTH_COMPLETE_URL` | Yes | Frontend page that receives the final `status` and `message` query params after OAuth finishes |
| `GOOGLE_DRIVE_AUTHORIZATION_URI` | No | Override Google authorization endpoint |
| `GOOGLE_DRIVE_TOKEN_URI` | No | Override Google token endpoint |

### OAuth flow expectations

1. Frontend calls `POST /api/v1/google-drive/oauth/start` with the user's JWT bearer token.
2. Backend creates a short-lived OAuth state tied to that authenticated user and returns:
   - `authorizationUrl`
   - `state`
   - `redirectUri`
   - `scopes`
3. Frontend opens `authorizationUrl` in a new tab or popup.
4. Google redirects back to `GET /api/v1/google-drive/oauth/callback`.
5. Backend exchanges the authorization code for user-scoped Drive credentials, stores them, and redirects the browser to `GOOGLE_DRIVE_OAUTH_COMPLETE_URL` with:
   - `status=success|error`
   - `message=<url-encoded message>`

### Scope used

- `https://www.googleapis.com/auth/drive`

This scope is used so the backend can validate user-selected Drive folders, read chosen Google Docs metadata, create vacancy subfolders, and copy Google Docs files on behalf of the authenticated user.

### Supported files

- Base resumes must be **Google Docs** (`application/vnd.google-apps.document`).
- The root folder must be a **Google Drive folder**.
- The frontend Gemini button that opens `https://gemini.google.com/gem/f8ed7c14b062` is frontend-only and does not require a backend endpoint.

### Resume copy behavior

When the frontend later calls `POST /api/v1/google-drive/applications/{applicationId}/resume-copies`:

1. Backend verifies the current user owns the application.
2. Backend refreshes the user's Google access token if needed.
3. Backend... (truncated)