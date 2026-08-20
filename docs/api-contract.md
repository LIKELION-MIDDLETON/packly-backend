# Centralton 백엔드 MVP API 계약

모든 사용자 API는 Centralton access JWT의 내부 user UUID `sub`를 사용합니다. Google ID token은
`POST /api/v1/auth/google`의 신원 검증에만 사용하며 장기 Bearer token으로 받지 않습니다.

## 인증

- `POST /api/v1/auth/google` — `{ "idToken": "...", "termsAccepted": true }`
- `POST /api/v1/auth/refresh` — `{ "refreshToken": "..." }`
- `POST /api/v1/auth/logout` — access JWT + `{ "refreshToken": "..." }`
- `GET /api/v1/me`
- `PUT /api/v1/me/profile` — `{ "nickname": "...", "postalCode": "12345", "addressLine1": "...", "addressLine2": "..." }`

신규 Google `sub`에는 `termsAccepted=true`가 필요합니다. 이메일/비밀번호 API는 없습니다.
access JWT 기본 TTL은 15분, refresh family의 절대 만료는 기본 30일입니다. refresh 원문은
저장하지 않고 SHA-256 hash만 저장합니다.

인증 응답은 다음 camelCase 형태입니다.

```json
{
  "tokenType": "Bearer",
  "accessToken": "...",
  "accessTokenExpiresAt": "2026-08-19T12:15:00Z",
  "refreshToken": "...",
  "refreshTokenExpiresAt": "2026-09-18T12:00:00Z",
  "isNewUser": true,
  "user": {
    "id": "uuid", "email": "user@example.com", "displayName": "User",
    "avatarUrl": null, "nickname": null, "postalCode": null,
    "addressLine1": null, "addressLine2": null,
    "onboardingStatus": "PROFILE_REQUIRED"
  },
  "onboardingStatus": "PROFILE_REQUIRED"
}
```

신규 사용자는 Google 인증 직후 `PROFILE_REQUIRED`입니다. `nickname`은 trim 후 2~20자이며
대소문자와 공백을 정규화한 값이 DB에서 unique합니다. `postalCode`는 한국 우편번호 5자리이고
`addressLine1`은 필수, `addressLine2`는 선택입니다. 주소는 access JWT claim이나 로그에 넣지
않습니다. 저장 성공 시 상태는 `SURVEY_REQUIRED`로 진행하며, 충돌은 `409 NICKNAME_ALREADY_IN_USE`입니다.

온보딩 상태 전이는 다음 명시적 규칙만 허용합니다.

```text
PROFILE_REQUIRED → SURVEY_REQUIRED → PHOTO_REQUIRED
PHOTO_REQUIRED → RECOMMENDATION_PENDING → COMPLETED
```

기존 프로토타입 사용자는 V2 Flyway 마이그레이션에서 필수 프로필 필드가 비어 있으면
`PROFILE_REQUIRED`로 전환된다는 가정을 사용합니다. V3는 `PROFILE_REQUIRED` 이후 상태에서
필수 프로필 필드가 비어 있지 않도록 DB check constraint를 추가합니다.

로컬/개발 profile에서 `DEV_AUTH_ENABLED=true`이고 `DEV_AUTH_HEADER_VALUE`가 설정된 경우에만
`POST /api/v1/dev/auth/mock-google`이 존재합니다. `X-Centralton-Dev-Auth`가 일치해야 하며,
결정적인 mock Google 사용자에게 정상 Centralton access/rotating refresh session을 발급합니다.
운영 profile에서는 bean/route가 없어 404이고, 개발 인증을 켠 채 운영 profile로 시작하면 실패합니다.

## 설문과 분석

- `GET /api/v1/me/survey`
- `PUT /api/v1/me/survey`
- `POST /api/v1/analyses` — `Idempotency-Key` 필수, body는 `{ "budgetTotal": 90000 }` 또는 빈 body
- `POST /api/v1/analyses/{analysisId}/photo` — 인증 JWT + `multipart/form-data`의 `image` 1개
- `GET /api/v1/analyses/{analysisId}`
- `POST /api/v1/internal/analyses/{analysisId}/cnn-result`
  - 사용자 JWT가 아닌 `X-Internal-Callback-Key`가 필요합니다.
  - body: `{ "sourceResultId": "...", "cnnResult": {...}, "llmResult": {...} }`

상태는 `WAITING_FOR_PHOTO_ANALYSIS → ANALYZING → RECOMMENDING → COMPLETED` 또는 `FAILED`입니다.
`POST /analyses`는 job을 만들고, photo upload가 성공하면 `202`와 `ANALYZING`을 반환합니다. 앱은
반환된 `id`로 GET polling을 합니다. 사진 분석 실패는 `ANALYZING → FAILED`, 추천 실패는
`RECOMMENDING → FAILED`입니다. 기존 trusted internal callback 경로는 호환성을 위해 유지되며,
모바일은 AI 서버나 internal callback을 직접 호출하지 않습니다.

사진 upload 계약:

- part 이름은 `image` 하나이며 `image/jpeg`, `image/png`, `image/webp`만 허용합니다. 클라이언트
  호환을 위해 `image/jpg`는 `image/jpeg`로 정규화합니다. 최대 10MB이고 MIME과
  JPEG/PNG/WEBP magic bytes를 함께 확인합니다. HEIC는 현재 모바일 계약에서 허용하지 않습니다.
- 주소, JWT, 토큰, 원본 사진은 로그나 public response에 넣지 않습니다. 현재 local MVP는 비동기 worker가
  재사용할 수 있도록 검증된 사진을 PostgreSQL `analysis_jobs.photo_data`에 임시 보관합니다.
- 외부 호출은 DB transaction 밖에서 수행합니다. `/analyze` read timeout 기본 30초, `/recommend` 기본
  10초입니다.

실행 흐름은 다음과 같습니다.

```text
앱 POST /analyses (Idempotency-Key)
  → 202 WAITING_FOR_PHOTO_ANALYSIS
앱 POST /analyses/{id}/photo (image)
  → 202 ANALYZING
Spring → AI_ANALYSIS_BASE_URL/analyze (multipart, answers, top_k=8)
  → cnn_result, llm_result, survey
Spring → AI_RECOMMENDATION_BASE_URL/recommend (JSON, numeric survey snapshot)
  → PostgreSQL recommendation/products
앱 GET /analyses/{id} polling
  → COMPLETED + recommendationId, 또는 FAILED + stable failureCode
```

분석 AI의 `answers`는 FastAPI 설문 계약에 맞춘 한글 라벨 JSON이며 key는
`skin_type`, `main_concern[]`, `duration`, `location[]`, `sensitivity`, `history`입니다.
추천 AI의 `survey`는 `/recommend` main.py 계약에 맞춘 숫자 JSON
(`skin_type`, `concerns`, `duration`, `areas`, `irritation`, `diagnosed`)입니다. 두 adapter를
혼동하지 않습니다. 실제 wire fixture는 `src/test/resources/fixtures`에 있습니다.

설문 enum은 다음 AI 숫자 코드로 한 adapter에서 변환됩니다.

- `skinType`: DRY 1, OILY 2, COMBINATION 3, DEHYDRATED_OILY 4, SENSITIVE 5, UNKNOWN 6
- `concerns`: ACNE 1, PORES_BLACKHEADS 2, REDNESS 3, DRYNESS_FLAKING 4,
  PIGMENTATION 5, WRINKLES_ELASTICITY 6, ITCHING_STINGING 7, NONE 8
- `duration`: NOT_APPLICABLE 1, UP_TO_ONE_WEEK 2, ONE_TO_FOUR_WEEKS 3,
  ONE_TO_THREE_MONTHS 4, OVER_THREE_MONTHS 5
- `areas`: FOREHEAD 1, NOSE 2, CHEEKS 3, CHIN 4, EYE_AREA 5, WHOLE_FACE 6, NONE 7
- `irritation`: NEVER 1, SOMETIMES 2, OFTEN 3
- `diagnosed`: NONE 1, ATOPIC_DERMATITIS 2, SEVERE_ACNE 3,
  SEBORRHEIC_DERMATITIS 4, PSORIASIS 5, OTHER 6

`NONE`은 복수 선택과 함께 쓸 수 없습니다. 기타 진단 텍스트는 DB에만 저장하고 AI에는 code 6만
전달합니다. `areas`는 저장·전달하지만 현재 AI 추천 가중치에 쓰인다고 표현하지 않습니다.

설문 PUT 예시는 다음과 같습니다.

```json
{
  "skinType": "DRY",
  "concerns": ["PORES_BLACKHEADS", "DRYNESS_FLAKING"],
  "duration": "ONE_TO_FOUR_WEEKS",
  "areas": ["CHEEKS"],
  "irritation": "SOMETIMES",
  "diagnosed": "NONE",
  "otherDiagnosis": null
}
```

설문 응답에는 위 필드와 `submittedAt`, 재현용 `aiNumericSnapshot`이 추가됩니다. 분석 응답은
`id`, `status`, nullable `budgetTotal`, nullable `failureCode`, `createdAt`, `updatedAt`,
nullable `recommendationId`입니다. `recommendationId`는 완료 전 null이고 완료 후 같은 분석의
추천 id입니다.

## 추천과 기록

- `GET /api/v1/recommendations/latest`
- `GET /api/v1/recommendations/{recommendationId}`
- `GET /api/v1/recommendations?cursor=&limit=20` — `(createdAt,id)` keyset cursor, 최대 50
- `PUT /api/v1/recommendations/{recommendationId}/products/{productId}/usage`
- `PUT /api/v1/recommendations/{recommendationId}/feedback`
- `POST /api/v1/sos-reports`
- `GET /api/v1/sos-reports`
- `GET /api/v1/sos-reports/{id}`

AI 응답은 영어 camelCase로 정규화됩니다. 현재 `/recommend`는 마스크/팩을 제외한
7개 슬롯을 사용하여 후보/예산 필터 결과에 따라 0~7개를 반환합니다. 백엔드 공개 계약과
DB는 기존 8번 슬롯 및 후속 추가를 안전하게 수용하기 위해 0~8개, `displayOrder` 1~8을
유지합니다. 원본 `순서`는 `displayOrder`이며 공통 도포 순서가 아닙니다.

제품 응답은 다음 의미를 사용합니다.

- `displayOrder`: AI 원본 슬롯 위치 1~8. `order`는 기존 모바일 호환용 deprecated alias입니다.
- `applicationOrder`: 원본 슬롯 1=toner, 2=lotion, 3=cream에만 각각 1,2,3을 부여하고 나머지는 null입니다.
  3번 슬롯의 원본 slot 문자열은 진단별 이름으로 바뀔 수 있어 문자열로 재판정하지 않습니다.
- `slot`: AI 원본 문자열 보존. `usageGroup`: `CLEANSE`, `CORE_ROUTINE`, `TREATMENT`, `PROTECT`,
  `OCCASIONAL` 중 하나. `slotKey`, `section`, `isCoreRoutine`은 서버 중복 필드가 아니며 프론트가
  `slot`/`displayOrder`/`usageGroup`에서 파생합니다.
- `dailyPrice`: nullable 일일가격. `dailyVolume`: nullable 일일용량 문자열.
- `totalVolume`: nullable 전체용량 문자열. `salePrice`: nullable 판매가.
- `recommendationReason`: nullable 추천이유. `price`는 deprecated 판매가 호환 alias이며
  `dailyPrice`로 채우지 않습니다.

추천 응답은 0개 제품도 정상이며 전체 제품을 하나의 공통 도포 순서로 렌더링해서는 안 됩니다.
예산 `budgetTotal`은 현재 AI 엔진에서 7개 슬롯 기준 슬롯별 상한(`budget_total / 7`)으로
적용되므로, 후보가 없는 슬롯은 중간 위치라도 응답에서 생략될 수 있습니다. 따라서 프론트는
`displayOrder`가 1부터 연속한다고 가정하지 말고, 중복 없는 1~8의 부분집합으로 처리해야 합니다.
완전한 공개 0-product 응답 예시는
`src/test/resources/fixtures/public/recommendation-0-products.json`에 있습니다.
7-product 응답 예시는 `src/test/resources/fixtures/public/recommendation-7-products.json`에 있습니다.
SOS는 `message`, 선택적 `symptomLabels`, 상태만 저장하며
원인 제품·진단·자동 회복 루틴을 생성하지 않습니다.

추천 응답 필드는 다음과 같습니다.

```text
id, analysisId, createdAt, diagnosis, headline, summary, confidence, triage,
medicalAdvice { recommended, reasons[] }, reflectedSurvey,
products[] { id, order(deprecated), displayOrder, applicationOrder, slot, usageGroup,
             goodsNo, brand, name, price(deprecated), dailyPrice, dailyVolume, totalVolume,
             salePrice, recommendationReason, suitability,
             suitabilitySource, functionalInfo, unscented, comedogenicScore, productUrl },
totalPrice(deprecated), totalPriceDaily, analysisSummary, careRecommendations[], disclaimer
```

이력 응답은 `{ "items": [...], "nextCursor": "... 또는 null" }`입니다.

- usage request: `{ "usedOn": "2026-08-19", "completed": true }`
  - response: `id, recommendationId, productId, usedOn, completed, createdAt, updatedAt`
- feedback request: `{ "rating": 1..5, "comment": "optional" }`
  - response: `id, recommendationId, rating, comment, createdAt, updatedAt`
- SOS request: `{ "recommendationId": "optional uuid", "message": "...", "symptomLabels": ["REDNESS"] }`
  - response: `id, recommendationId, message, symptomLabels, status, createdAt, updatedAt`

## 오류와 제외 범위

오류 body는 `type`, `title`, `status`, `detail`, `instance` ProblemDetail 필드와
camelCase 확장 필드 `code`, `fieldErrors`, `timestamp`를 사용합니다. Content-Type은
`application/problem+json`입니다. 사용자 소유가 아닌
분석·추천·제품·SOS는 리소스 열거를 막기 위해 404로 처리합니다.

- 400: 요청 형식/Bean Validation
- 401: 사용자 또는 internal credential 실패
- 404: 존재하지 않거나 소유하지 않은 리소스
- 409: 중복 source/idempotency 또는 잘못된 상태 전이
- 502: AI 400/계약 위반 응답 (`AI_ANALYSIS_INVALID_*`, `AI_INVALID_*`)
- 503: AI timeout/연결/5xx (`AI_ANALYSIS_TIMEOUT`, `AI_ANALYSIS_UNAVAILABLE`,
  `AI_ANALYSIS_SERVER_ERROR`, `AI_TIMEOUT`, `AI_UNAVAILABLE`, `AI_SERVER_ERROR`) 또는 외부 인증 설정 장애

현재 계약에는 날씨/UV 반영, 보유제품, 중복 제거, Skin Firewall,
구매·배송, AM/PM 자동 루틴, 사진 장수·각도 규격이 없습니다.
최신 AI의 `일일가격`, `일일용량`, `전체용량`, `판매가`, `추천이유`, `총액_일일`은 canonical
필드로 저장·노출합니다. 기존 `가격`/`총액`은 전환 기간 legacy fallback으로만 읽으며,
nullable `일일가격`을 기존 `price`로 매핑하지 않습니다. OpenAI 키가 없으면 추천이유만 null이고
추천 구성 자체는 반환됩니다.
