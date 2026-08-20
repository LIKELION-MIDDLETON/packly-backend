# Centralton API

Java 17, Spring Boot 4, PostgreSQL 기반 Centralton 백엔드 MVP입니다.

현재 범위는 Google 신원 교환, Centralton JWT/refresh 세션, 필수 주소 프로필, 설문, 사진 1장 업로드,
AI `/analyze`→`/recommend` polling job, 추천 이력, 제품 사용 기록, 피드백, 일반 SOS 신고입니다.
운영 코드가 가짜 분석 결과를 만들지는 않으며 외부 AI URL이 실제로 필요합니다.

## 필수 환경변수

이름만 정리한 [`.env.example`](./.env.example)을 참고하세요. 실제 값은 커밋하지 않습니다.

- `DB_URL`, `DB_USERNAME`, `DB_PASSWORD`
- `JWT_PRIVATE_KEY`, `JWT_PUBLIC_KEY`, `JWT_KEY_ID`
- `JWT_ISSUER`, `JWT_AUDIENCE`, `JWT_ACCESS_TTL_SECONDS`, `JWT_REFRESH_TTL_DAYS`
- `GOOGLE_SERVER_CLIENT_ID`
- `DEV_AUTH_ENABLED`, `DEV_AUTH_HEADER_VALUE` (local/dev profile에서만 사용)
- `AI_ANALYSIS_BASE_URL`, `AI_ANALYSIS_CONNECT_TIMEOUT`, `AI_ANALYSIS_READ_TIMEOUT`
- `AI_RECOMMENDATION_BASE_URL`, `AI_RECOMMENDATION_CONNECT_TIMEOUT`, `AI_RECOMMENDATION_READ_TIMEOUT`
- `INTERNAL_CALLBACK_KEY`
- `CORS_ALLOWED_ORIGINS`

JWT는 PKCS#8 RSA private key와 X.509 public key를 사용합니다.

```bash
openssl genpkey -algorithm RSA -pkeyopt rsa_keygen_bits:2048 -out centralton-private.pem
openssl rsa -pubout -in centralton-private.pem -out centralton-public.pem
```

키 파일 내용은 환경변수 또는 외부 secret 설정으로 주입하세요.

## PostgreSQL 실행

권장 로컬 경로는 [로컬 Docker 문서](./docs/local-docker.md)의 Compose 실행입니다. Flyway가
`V1__centralton_mvp.sql` 이후 마이그레이션을 적용하고 Hibernate는 `ddl-auto=validate`로
스키마를 검증합니다.

```bash
cp .env.example .env
docker compose up --build -d
curl http://localhost:8080/api/health
```

H2 local profile은 smoke/개발 편의를 위한 것이며 PostgreSQL 고유 동작을 증명하지 않습니다.

```bash
SPRING_PROFILES_ACTIVE=local ./gradlew bootRun
```

## 외부 서비스

- Google: backend용 Web OAuth client ID를 `GOOGLE_SERVER_CLIENT_ID`로 설정합니다.
- AI 분석: sibling FastAPI의 `model/server.py`를 8001에 실행하고 `AI_ANALYSIS_BASE_URL`을 지정합니다.
- AI 추천: sibling FastAPI의 `main.py`를 8000에 실행하고 `AI_RECOMMENDATION_BASE_URL`을 지정합니다.
- `cnn_best.pt`는 AI checkout의 소유 artifact이며 이 저장소나 API 이미지로 복사하지 않습니다.
- `OPENAI_API_KEY`는 추천 AI 서비스에만 주입합니다. 백엔드 `.env`나 public response에는 넣지 않습니다.
- 모바일은 Spring만 호출합니다. `POST /api/v1/analyses/{analysisId}/photo` 이후
  `GET /api/v1/analyses/{analysisId}`를 polling하며, 실제 Google 서명 토큰과 모델/LLM 구동은
  별도 외부 의존성입니다.

## 검증

```bash
./gradlew clean test
./gradlew bootJar
docker compose config
curl http://localhost:8080/api/health
curl -i http://localhost:8080/api/v1/me
```

API 상세 계약은 [`docs/api-contract.md`](./docs/api-contract.md)를 참고하세요.
프론트 parser용 공개 응답 fixture는
[`src/test/resources/fixtures/public/recommendation-0-products.json`](./src/test/resources/fixtures/public/recommendation-0-products.json)과
[`recommendation-7-products.json`](./src/test/resources/fixtures/public/recommendation-7-products.json)에서 확인할 수 있습니다.
0개 제품도 정상 응답이며, 현재 AI는 7개 슬롯을 사용하고 백엔드 공개 계약은
호환성을 위해 중복 없는 0~8개 `displayOrder`를 수용합니다.
