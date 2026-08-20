# Centralton AI fixtures

These files are deterministic wire fixtures for tests only.

- `ai-v5/recommend-request.json` and `ai-v5/recommend-response.json` are the legacy FastAPI `/recommend` contract.
  `ai-v5/recommend-response-pr9.json` is the current 7-slot response with nullable daily/sale fields.
  External Korean keys stay in the fixture; the public API is camelCase.
- `photo/analyze-response.json` is the external `/analyze` response shape.
- `photo/callback.json` is a trusted internal callback example, not a public mobile API.
- `public/` contains mobile polling/recommendation response examples, including the complete
  zero-product `RecommendationResultResponse` shape in `recommendation-0-products.json` and the
  current seven-slot shape in `recommendation-7-products.json`.

No Google token, dev header, address, secret, model file, or real photo is stored here. `order` is a deprecated compatibility alias for `displayOrder`; `applicationOrder` is only the original slots 1–3, and is not a universal seven-step application sequence.
Because the AI budget is divided across seven slots, a filtered response may contain a non-contiguous subset of display orders; clients must not assume that display orders are gap-free.
`image/jpg` upload is accepted only as a compatibility alias and is normalized to `image/jpeg` before analysis.
