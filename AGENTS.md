# BranchLight Repository Guidelines

## Scope

- Make narrowly scoped changes that address the requested behavior.
- Do not reformat, rename, or reorganize unrelated code.
- Preserve existing behavior unless the task explicitly requires changing it.
- Do not commit generated or dependency directories such as `node_modules/` or
  `dist/`.

## Repository Layout

- `BranchLight-Frontend/` contains the current application. It is a React 19
  single-page app written in TypeScript and built with Vite.
  - `src/api/` contains frontend API clients.
  - `src/components/` contains focused UI components.
  - `src/types/` contains shared TypeScript types.
  - `src/test/` contains shared test setup.
  - `src/App.tsx`, `src/App.css`, and `src/App.test.tsx` contain the main app,
    styles, and app-level tests.
  - `src/main.tsx` is the browser entry point.
- `BranchLight-Backend/` contains the Spring Boot 4.1 backend. It uses Java 17
  and Maven.
  - `src/main/java/com/branchlight/backend/` contains application code.
  - `src/main/resources/` contains Spring configuration.
  - `src/test/java/com/branchlight/backend/` contains backend tests.

## Frontend Commands

Run frontend commands from `BranchLight-Frontend/`. Node.js 20+ and npm are
required.

- Install dependencies: `npm install`
- Start the Vite development server: `npm run dev`
- Type-check and create a production build: `npm run build`
- Run ESLint: `npm run lint`
- Run tests once: `npm run test:run`
- Run tests in watch mode: `npm test`
- Preview the production build: `npm run preview`

## Backend Commands

Run backend commands from `BranchLight-Backend/`. Java 17+ is required. Use the
checked-in Maven wrapper instead of a globally installed Maven.

- Windows build: `.\mvnw.cmd clean package`
- Windows test: `.\mvnw.cmd test`
- Windows run: `.\mvnw.cmd spring-boot:run`
- macOS/Linux build: `sh ./mvnw clean package`
- macOS/Linux test: `sh ./mvnw test`
- macOS/Linux run: `sh ./mvnw spring-boot:run`

## Coding Conventions

- Follow the existing strict TypeScript configuration.
- Use functional React components and keep components focused on one concern.
- Use PascalCase for components and shared types, camelCase for functions,
  variables, and props, and descriptive names for event handlers.
- Keep component prop types close to their components. Use `import type` for
  type-only imports.
- Match the existing formatting: two-space indentation, single quotes, no
  semicolons, and trailing commas in multiline constructs.
- Use relative imports within the frontend.
- Put reusable domain types in `src/types/`, static or mock data in `src/data/`,
  and UI components in `src/components/`.
- Name component tests `*.test.tsx`. Prefer Testing Library queries that reflect
  user-visible behavior and accessible roles.
- Preserve semantic HTML, labels, keyboard behavior, ARIA announcements, and
  safe external-link attributes when changing the UI.
- Do not introduce a new library when the existing stack or platform APIs are
  sufficient.
- For backend code, use Java 17, four-space indentation, constructor injection,
  and feature-focused packages under `com.branchlight.backend`.
- Keep controllers thin and keep configuration in the `config` package.

## Environment Variables and Secrets

- Never commit API keys, access tokens, passwords, private certificates, or
  other secrets.
- Never place secrets in source files, tests, committed configuration, example
  values, or client-side code.
- Treat every Vite variable prefixed with `VITE_` as public because it is
  bundled into browser code. Do not use `VITE_` variables for secrets.
- Store local values in uncommitted environment-specific files or in the
  developer's runtime environment. Commit only placeholder examples, never
  working credentials.
- Read future backend secrets from runtime environment variables and reference
  them from Spring configuration; do not hard-code them in
  `application.properties` or `application.yml`.
- Use uppercase, underscore-separated names with a `BRANCHLIGHT_` prefix for
  new project-specific environment variables.
- Document each new variable's name, purpose, and whether it is required,
  without documenting a real secret value.

## Validation

- Run relevant tests after each change, not only at the end of a task.
- For frontend behavior changes, run the affected test or `npm run test:run`.
- Run `npm run lint` after TypeScript or React changes.
- Run `npm run build` when changing application code, types, dependencies, or
  build configuration.
- For backend changes, run the relevant test or `.\mvnw.cmd test` on Windows
  (`sh ./mvnw test` on macOS/Linux).
- Report the commands run and any failures. Do not hide, skip, or weaken tests
  merely to make a change pass.
