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
