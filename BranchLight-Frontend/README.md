# BranchLight

BranchLight is the first frontend-only slice of a personal search application. It
provides a focused search form and displays five typed, mocked web results after
each valid submission.

This milestone intentionally has no backend, external search API, accounts,
database, notebooks, or AI-generated content.

## Requirements

- Node.js 20+
- npm

## Setup

```bash
npm install
```

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
├── components/
│   ├── SearchForm.tsx
│   ├── SearchResultCard.tsx
│   └── SearchResults.tsx
├── data/
│   └── mockSearchResults.ts
├── test/
│   └── setup.ts
├── types/
│   └── search.ts
├── App.css
├── App.test.tsx
├── App.tsx
└── main.tsx
```

The five mock results are reused for every valid query. A real search service can
replace that data source in a later milestone without changing the basic result
components.
