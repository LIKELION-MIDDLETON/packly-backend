# 로컬 PostgreSQL 실행

Docker Compose는 PostgreSQL 16.4와 Centralton API를 함께 실행합니다. PostgreSQL 데이터는
`centralton-postgres-data` named volume에 보존됩니다.

## 처음 실행

```bash
cp .env.example .env
# .env의 POSTGRES_PASSWORD와 DEV_AUTH_HEADER_VALUE를 로컬용 난수로 교체
docker compose config
docker compose up --build -d
curl http://localhost:8080/api/health
```

API 컨테이너는 `dev` profile로 실행할 때만 임시 RSA 키와 개발용 mock 인증을 사용할 수 있습니다.
`DEV_AUTH_ENABLED=true`로 바꾸고 `DEV_AUTH_HEADER_VALUE`를 설정하면 다음 요청으로 결정적인 개발
사용자 세션을 발급할 수 있습니다. 헤더 값은 셸 기록·로그에 남기지 않도록 관리하세요.

```bash
curl -X POST http://localhost:8080/api/v1/dev/auth/mock-google \
  -H 'X-Centralton-Dev-Auth: <.env의 DEV_AUTH_HEADER_VALUE>'
```

운영 실행에서는 `SPRING_PROFILES_ACTIVE`를 `prod` 등으로 지정하고 RSA 키와 Google client ID를
주입합니다. 개발 인증은 `local`/`dev` profile에만 bean이 생성되며, 다른 profile에서는 해당
경로가 404입니다. 다른 profile에서 `DEV_AUTH_ENABLED=true`로 시작하려 하면 애플리케이션이
실패하도록 방어합니다.

## 종료·데이터·마이그레이션

```bash
docker compose stop       # 컨테이너만 중지, 데이터 유지
docker compose down       # 컨테이너/네트워크 제거, named volume 유지
docker compose up -d      # 기존 데이터와 Flyway 상태로 재시작
```

Flyway는 API 시작 시 자동으로 `src/main/resources/db/migration`의 버전을 적용합니다. 현재
`V2__user_profile_and_onboarding.sql`은 프로필 컬럼을 추가하고, 기존 사용자의 필수 프로필이
비어 있으면 `PROFILE_REQUIRED`로 전환합니다. `V3__profile_fields_follow_onboarding_status.sql`은
그 이후 상태에서 필수 프로필 필드가 비어 있지 않도록 DB check constraint를 추가합니다. 기존
프로토타입 사용자는 이 마이그레이션 가정을 따릅니다.

`V4__photo_analysis_and_eight_product_contract.sql`은 검증된 사진 임시 저장 컬럼과 추천 제품의
`display_order`, `application_order`, `usage_group`을 추가하고 기존 `order_index` 1~3 데이터를
그대로 표시 순서로 backfill합니다. `V5__daily_recommendation_fields.sql`은 최신 AI의 일일가격,
일일용량, 전체용량, 판매가, 추천이유, 총액_일일을 nullable canonical 컬럼으로 추가합니다.
기존 price/total price 값은 보존하며 기존 사진은 없으므로 기존 job은 계속
`WAITING_FOR_PHOTO_ANALYSIS`에서 시작합니다.

AI는 두 URL을 분리합니다. `AI_ANALYSIS_BASE_URL`은 `/analyze`(기본 read timeout 30초),
`AI_RECOMMENDATION_BASE_URL`은 `/recommend`(기본 read timeout 10초)입니다. Docker Compose에서
각각 host의 8001, 8000을 기본값으로 사용하며 실제 AI가 떠 있지 않아도 H2/MockWebServer 테스트는
완주합니다. `/recommend`에 OpenAI 추천이유 생성을 연결할 때는 AI 서비스의 OpenAI client
timeout을 Spring의 10초보다 짧게 설정하고 retry를 0~1회로 제한해야 Spring이 먼저 작업을
불필요하게 FAILED 처리하지 않습니다. OpenAI 키가 없으면 AI 서비스는 추천이유만 null로
반환하는 stub 경계로 검증할 수 있으며, 실제 GPT 호출은 별도 환경 의존성입니다.

데이터를 의도적으로 초기화할 때만 다음 명령을 사용합니다.

```bash
docker compose down -v
docker compose up --build -d
```

실제 비밀값이 들어간 `.env`와 키 파일은 커밋하지 않습니다. `.env.example`의 값은 예시일 뿐이며,
운영에서는 secret manager 또는 배포 환경의 비밀 주입 기능을 사용합니다.
