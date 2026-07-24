# BranchLight

BranchLight is a React frontend for a personal search application. It sends
searches to the local Spring Boot backend and displays categorized results.

## Requirements

- Node.js 20+
- npm
- The BranchLight backend running on port 8080

## Setup

```bash
npm install
```

Start the backend from `../BranchLight-Backend/`, then start the frontend:

```powershell
.\mvnw.cmd spring-boot:run
```

```bash
npm run dev
```

The Vite development server proxies `/api` requests to
`http://localhost:8080`. A production deployment should route `/api` to the
Spring Boot backend as well.

## Commands

```bash
npm run dev       # Start the Vite development server
npm run test:run  # Run the test suite once
npm test          # Run tests in watch mode
npm run build     # Type-check and create a production build
npm run lint      # Run ESLint
npm run preview   # Preview the production build locally
```

## Project structure

```text
src/
├── api/
│   └── search.ts
├── components/
│   ├── SearchForm.tsx
│   ├── SearchResultCard.tsx
│   └── SearchResults.tsx
├── test/
│   └── setup.ts
├── types/
│   └── search.ts
├── App.css
├── App.test.tsx
├── App.tsx
└── main.tsx
```
