# BranchLight Backend

Minimal Spring Boot backend for BranchLight. It currently exposes:

```text
GET /api/health
POST /api/search
```

The health response is:

```json
{"status":"UP"}
```

The search endpoint accepts `{"query":"..."}` and currently returns five
deterministic stub results: one for each search role. It does not call an
external search API.

A Brave Search provider and a metadata-only preliminary candidate ranker are
available for future search orchestration, but neither is connected to
`POST /api/search`.

An OpenAI query-variant generator is available but is not yet connected to
`POST /api/search`. When invoked, it makes one Responses API request using
Structured Outputs and returns one query for each query purpose. Query
generation is disabled by default. When it is disabled, unavailable, times
out, throws an exception, or returns invalid variants, query generation uses
the deterministic generator instead.

## Requirements

- Java 17 or newer
- Internet access on the first Maven wrapper run

The checked-in Maven wrapper downloads its own Maven distribution, so a global
Maven installation is not required.

## Test

From `BranchLight-Backend/`:

```powershell
.\mvnw.cmd test
```

On macOS or Linux:

```bash
sh ./mvnw test
```

## Run Locally

From `BranchLight-Backend/`:

```powershell
.\mvnw.cmd spring-boot:run
```

On macOS or Linux:

```bash
sh ./mvnw spring-boot:run
```

The health endpoint is available at
`http://localhost:8080/api/health`.

Submit a stub search from PowerShell:

```powershell
Invoke-RestMethod `
  -Method Post `
  -Uri 'http://localhost:8080/api/search' `
  -ContentType 'application/json' `
  -Body '{"query":"virtual threads"}'
```

## Brave Search Provider

Set `BRAVE_SEARCH_API_KEY` in the backend process environment to enable the
Brave provider bean. The credential is sent only from the backend in Brave's
`X-Subscription-Token` request header.

Optional provider settings:

- `BRANCHLIGHT_BRAVE_SEARCH_BASE_URL` changes the API base URL. It defaults to
  `https://api.search.brave.com`.
- `BRANCHLIGHT_BRAVE_SEARCH_TIMEOUT` changes both connection and response
  timeouts. It defaults to `5s` and accepts Spring duration values such as
  `750ms` or `10s`.

Do not place the API key in frontend environment variables, source files, or
committed configuration.

## Search Performance Tuning

Search concurrency is configured in `src/main/resources/application.yml` and
can be overridden in the backend `.env` file:

```properties
BRANCHLIGHT_PROVIDER_QUERY_PARALLELISM=3
BRANCHLIGHT_PAGE_FETCH_PARALLELISM=6
BRANCHLIGHT_PAGE_PROCESSING_PARALLELISM=4
```

- Provider-query parallelism controls simultaneous original and generated
  query requests. Keep it within provider rate limits.
- Page-fetch parallelism controls simultaneous remote page downloads and has
  the largest effect on end-to-end latency.
- Page-processing parallelism controls concurrent extraction, passage
  splitting, feature extraction, eligibility, and role scoring.

Each value must be between `1` and `32`; `1` provides sequential execution.
Search logs include provider-query timings, per-page fetch/preparation/
evaluation timings, aggregate stage timings, and total search duration.

## OpenAI Query Generation

The OpenAI client and query generator are created only when query generation
is enabled and a nonblank backend API key is available:

- `BRANCHLIGHT_OPENAI_QUERY_GENERATION_ENABLED` enables or disables query
  generation. It defaults to `false`.
- `OPENAI_API_KEY` supplies the required backend-only API credential.
- `OPENAI_QUERY_MODEL` selects the model used for query generation. It is
  required when query generation is enabled with an API key.
- `BRANCHLIGHT_QUERY_VARIANT_MAX_LENGTH` sets the maximum accepted length of
  each OpenAI-generated query variant. It defaults to `400`.

The application starts without an OpenAI API key; in that case, no OpenAI
client bean is created. Keep `OPENAI_API_KEY` in the backend process
environment only. Never put it in a `VITE_` variable, frontend code, logs,
exception messages, API responses, or committed files.

## Preliminary Candidate Ranking

The preliminary ranker scores already-merged candidates using only their
retrieval metadata. It does not fetch pages, call an LLM, or use embeddings.
It keeps the top 25 candidates by default and is not yet connected to
`POST /api/search`.

All ranking weights and the result cap can be overridden with optional backend
environment variables:

- `BRANCHLIGHT_PRELIMINARY_RANKING_MAXIMUM_CANDIDATES` controls the number of
  candidates retained and defaults to `25`.
- `BRANCHLIGHT_PRELIMINARY_RANKING_MINIMUM_DISTINCT_TITLE_TERMS` controls when
  a short or repetitive title is treated as low quality and defaults to `2`.
- `BRANCHLIGHT_PRELIMINARY_RANKING_MINIMUM_DISTINCT_SNIPPET_TERMS` controls
  when the combined snippets are treated as low quality and defaults to `4`.
- `BRANCHLIGHT_PRELIMINARY_RANKING_TITLE_LEXICAL_OVERLAP_WEIGHT` weights title
  overlap with the original query and defaults to `3.0`.
- `BRANCHLIGHT_PRELIMINARY_RANKING_SNIPPET_LEXICAL_OVERLAP_WEIGHT` weights
  snippet overlap with the original query and defaults to `2.0`.
- `BRANCHLIGHT_PRELIMINARY_RANKING_PROVIDER_RANK_PRIOR_WEIGHT` weights the
  provider-rank prior and defaults to `1.0`.
- `BRANCHLIGHT_PRELIMINARY_RANKING_GENERATED_QUERY_DISCOVERY_WEIGHT` weights
  distinct generated-query discoveries and defaults to `0.25`.
- `BRANCHLIGHT_PRELIMINARY_RANKING_RETRIEVAL_PURPOSE_DIVERSITY_WEIGHT` weights
  distinct retrieval purposes and defaults to `0.25`.
- `BRANCHLIGHT_PRELIMINARY_RANKING_TITLE_SPECIFICITY_WEIGHT` weights title
  specificity and defaults to `0.5`.
- `BRANCHLIGHT_PRELIMINARY_RANKING_LOW_QUALITY_PENALTY_WEIGHT` penalizes an
  empty or punctuation-only title or combined snippet and defaults to `1.0`.

## Page Fetching

The HTTP page fetcher can download shortlisted HTML and plain-text pages. It is
not yet connected to `POST /api/search`. It rejects non-HTTP URLs and
non-public network destinations, validates each redirect destination, limits
response bytes, and returns a structured failure for an individual page
without aborting a batch.

Optional backend settings:

- `BRANCHLIGHT_PAGE_FETCH_CONNECTION_TIMEOUT` controls the connection timeout
  and defaults to `3s`.
- `BRANCHLIGHT_PAGE_FETCH_RESPONSE_TIMEOUT` controls the complete response
  timeout, including body download, and defaults to `10s`.
- `BRANCHLIGHT_PAGE_FETCH_MAXIMUM_REDIRECTS` controls how many redirects may
  be followed and defaults to `5`.
- `BRANCHLIGHT_PAGE_FETCH_MAXIMUM_RESPONSE_BYTES` controls the maximum encoded
  response body size and defaults to `1048576` bytes.

## CORS

Requests from the Vite development server at `http://localhost:5173` are
allowed by default. Override the allowed origins with the optional
`BRANCHLIGHT_CORS_ALLOWED_ORIGINS` environment variable. Use a comma-separated
list of exact origins when more than one local frontend origin is needed.

PowerShell example:

```powershell
$env:BRANCHLIGHT_CORS_ALLOWED_ORIGINS='http://localhost:5173,http://localhost:4173'
.\mvnw.cmd spring-boot:run
```

Bash example:

```bash
BRANCHLIGHT_CORS_ALLOWED_ORIGINS='http://localhost:5173,http://localhost:4173' \
  sh ./mvnw spring-boot:run
```
