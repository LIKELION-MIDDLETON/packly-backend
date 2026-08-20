# Centralton backend verification ledger — 2026-08-19

## Contract and configuration

- Authentication remains Google-only at `POST /api/v1/auth/google`; Centralton issues RS256 access JWTs and rotating opaque refresh tokens.
- New users start at `PROFILE_REQUIRED`. A valid `PUT /api/v1/me/profile` moves the user to `SURVEY_REQUIRED`; survey save moves to `PHOTO_REQUIRED`.
- `PROFILE_REQUIRED → SURVEY_REQUIRED → PHOTO_REQUIRED → RECOMMENDATION_PENDING → COMPLETED` is implemented as explicit transitions.
- `DEV_AUTH_ENABLED=true` and a non-empty `DEV_AUTH_HEADER_VALUE` are required in `local`/`dev` for the mock route. Other profiles do not register the route; enabling it outside development fails startup.
- Real secret values were not stored in source, image layers, or this ledger.

## Migration evidence

- `V1__centralton_mvp.sql`: baseline schema.
- `V2__user_profile_and_onboarding.sql`: nullable profile columns for upgrade compatibility, nickname unique constraint, and `PROFILE_REQUIRED` backfill for incomplete existing users.
- `V3__profile_fields_follow_onboarding_status.sql`: conditional profile-field check constraint.
- PostgreSQL 16.4 applied Flyway versions 1, 2, and 3 successfully.

## Commands and results

| Command/check | Result |
|---|---|
| `./gradlew clean test bootJar` | `BUILD SUCCESSFUL` |
| `docker compose config` | `OK` |
| `docker compose up --build -d` | image built; postgres and api started |
| PostgreSQL healthcheck | `healthy` |
| API container user/health | `centralton` / `healthy` |
| `GET /api/health` | HTTP 200, `status=UP` |
| Dev mock → profile → `/me` → survey | mock `PROFILE_REQUIRED`, profile `SURVEY_REQUIRED`, survey HTTP 200 |
| Wrong dev header | HTTP 401 |
| Container restart | user row retained; Flyway history retained |
| `docker compose down` | services stopped; named volume retained |

The first Docker check found Docker Desktop stopped. Docker Desktop was started, then the compose
build and PostgreSQL checks were completed. Testcontainers was not added because the required real
PostgreSQL coverage was exercised through the mandated Compose smoke path while keeping the normal
test task H2-only and daemon-independent.
