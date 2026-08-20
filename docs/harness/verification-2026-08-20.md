# Centralton backend verification ledger — 2026-08-20

## PR#9 follow-up verification

- AI checkout `cb4fa1f` was read directly. Its current `package_rules.SLOTS` has seven slots:
  toner, lotion, cream, cleanser/remover, essence/serum, sun care, mist/special care. Mask/pack is
  excluded from regular recommendations.
- The real AI `/recommend` was started locally without an OpenAI key. It returned HTTP 200, seven
  ordered slots, nullable `판매가`/daily fields, and `추천이유=null`. This verifies the HTTP boundary,
  not real GPT generation.
- Backend V5 adds nullable daily/sale/reason fields and `total_price_daily`. Existing legacy price
  and total fields remain nullable compatibility fields; daily price is never copied into legacy price.
- New adapter input accepts 0–7 products and rejects display slot 8. Existing database rows with legacy
  display slot 8 are not deleted and remain queryable.

## Baseline and scope

- Read `docs/api-contract.md`, source, tests, current Git status, and `karpathy-guidelines/SKILL.md` before editing.
- Existing user change preserved: `GoogleIdTokenVerifierAdapter.java` catches `IllegalArgumentException`, with its untracked test left intact.
- No `git reset`, `git checkout`, `git clean`, commit, or push was run.
- The sibling AI checkout was read directly. `/analyze` is `model/server.py`, `/recommend` is root `main.py`.

## Implemented contract

- Auth remains Google-only; mobile calls Spring only.
- `POST /api/v1/analyses/{analysisId}/photo` accepts one authenticated `image` multipart part, JPEG/PNG/WEBP, max 10MB, with magic-byte validation.
- `WAITING_FOR_PHOTO_ANALYSIS → ANALYZING → RECOMMENDING → COMPLETED/FAILED` is exercised. Analysis and recommendation failures use stable codes and do not expose provider URL/exception text.
- Analysis and recommendation URLs/timeouts are separate. Analysis sends the exact Korean-label `answers` keys and fixed `top_k=8`; recommendation keeps the numeric survey snapshot contract.
- New AI products accept 0–7. `displayOrder` is original AI slot position; filtered candidates may omit a
  middle slot, while duplicate/out-of-range positions are rejected. `applicationOrder` is only
  original slots 1–3; `usageGroup` is normalized. `order` remains a deprecated response alias.
  Legacy stored display slot 8 remains readable for compatibility.
- `recommendationId` is exposed from completed analysis polling.
- The frontend checkout was read directly: it sends JSON `{}` plus `Idempotency-Key` for create,
  one `image` FormData part without forcing a multipart boundary, and polls the returned analysis id.
  Its Jest suite passed 15 suites / 81 tests. Backend compatibility fixes include `image/jpg`
  normalization, idempotent photo replay without a second worker, and idempotency replay with the
  completed `recommendationId`.
- `recommendation-0-products.json` and `recommendation-7-products.json` are complete public
  `RecommendationResultResponse` fixtures; the PR#9 AI wire fixture is
  `ai-v5/recommend-response-pr9.json`. MockMvc serialization and fixture-shape tests cover nullable
  daily/sale fields and empty `products`.

## Commands and evidence

| Command/check | Result |
|---|---|
| `./gradlew test --rerun-tasks --no-daemon` | `BUILD SUCCESSFUL` (77 tests; 0 failures/errors) |
| `docker compose config` | success; output was not recorded because the local `.env` contains secrets |
| `docker compose up --build -d` | API image built; API and PostgreSQL started |
| PostgreSQL health | `healthy` |
| `GET /api/health` | HTTP 200, `status=UP` |
| Flyway schema | versions 1–5 all `success=true`; V5 daily/sale/reason columns present |
| Latest AI HTTP boundary | local `main.py /recommend` returned HTTP 200, seven slots, nullable sale/daily fields, and `추천이유=null` without an OpenAI key |
| Previous local HTTP smoke | pre-PR#9 mock auth → profile → `/me` → survey → analysis create → multipart photo → `COMPLETED` → `recommendationId`, 8 legacy products |
| Container restart | previously completed legacy recommendation remained queryable; V5 schema was applied on the rebuilt API |
| `docker compose down` | services stopped; named volume `centralton-api_centralton-postgres-data` retained |

The repeat HTTP smoke against the retained volume reached dev auth, analysis creation, and photo
upload, but the fixed dev user was already `COMPLETED` from the earlier successful smoke, so its
new analysis could not advance onboarding. This is preserved-state evidence, not a live-AI failure;
the fresh-state fixture-AI smoke remains the successful path recorded above. Health, Flyway, and
restart persistence passed again.

## Deterministic test coverage

- `FastApiPhotoAnalysisAdapterTest`: multipart field names, image part content type, fixed top_k, response mapping, timeout code.
- `SurveyAnalysisAnswersAdapterTest`: every persisted survey enum code maps to the exact `/analyze` Korean label table.
- `FastApiRecommendationAdapterTest`: empty/1/3/7 products, custom third slot, nullable canonical
  fields, legacy price/total fallback, display-order duplicate/slot-8 rejection plus filtered-slot
  omission acceptance, HTTP/timeout failures.
- `PhotoRecommendationPollingIntegrationTest`: MockMvc auth/profile/survey, real multipart route, async polling, PostgreSQL-compatible Flyway/H2 persistence, recommendation id, idempotency replay, ownership 404, `image/jpg` normalization, 10MB boundary, malformed multipart 400, duplicate upload no-op, and failure normalization.
- Existing callback/idempotency/security/profile tests remain green.

## Not claimed

- Real Google signed ID-token exchange was not exercised; it still requires a valid configured Google Web OAuth client ID and live Google verification.
- The actual CNN/LLM models and live AI services were not used in the deterministic test suite. The Docker HTTP smoke used a temporary local fixture server; live model inference, LLM credentials, and deployed-network behavior remain external dependencies.
- S3/object storage, photo retention policy, weather/UV, owned products, duplicate/ingredient conflict
  logic remain out of scope. Real OpenAI/GPT generation remains unverified.
