# Repository Guidelines

## Project Structure

- `rebolsysBackend/` is the Java 21 Spring Boot API backed by Oracle. Java code lives in `src/main/java`, JUnit tests in `src/test/java`, and schema and update scripts in `src/main/resources/db`. API contracts and Postman collections live in `api/`.
- `rebolsysFrontend/` is the Angular 22 app organized by module: `src/app/core/{auth,guards,interceptors,config,models,services,utils}`, `src/app/layout/`, `src/app/shared/`, and `src/app/features/{accesos,caja,clientes,configuracion-sistema,inicio,limpieza,mantenimiento,reportes,reservas,ventas}` (each module follows `components/`, `models/`, `pages/`, `services/`, `utils/`). Static assets live in `public/`.
- `PharmaBackend/` and `pharma-frontend/` are separate reference projects. `docs/` contains REBOLSYS design notes and diagrams. `unidad 2/` contains Oracle coursework.

## Build and Run Locally

- From `rebolsysBackend/`, run `.\mvnw.cmd spring-boot:run` to start the API on port 8080 or `.\mvnw.cmd package` to test and package it.
- From `rebolsysFrontend/`, use a Node.js version compatible with Angular 22. Run `npm ci` to install locked dependencies, `npm start` to serve on port 4200 with `/api` proxied to port 8080, and `npm run build` to compile the app.

## Coding Style and Naming

- Match indentation in the Java file you edit. Use PascalCase names such as `ReservaServiceImpl` and `ReservaRegistroRequestDTO`. Keep business rules and transactions in `service/impl`, DTO validation at the API boundary, and queries in `repository`.
- Use two spaces and single quotes in TypeScript. Use kebab-case filenames and separate component `.ts`, `.html`, and `.css` files. Prettier is available in both Angular projects.

## UI Consistency Rules

- Use only design tokens defined in `rebolsysFrontend/src/styles.css` (`--space-*`, `--text-*`, `--radius-*`, `--control-h*`, and semantic colors); avoid hardcoded hex colors or ad-hoc font sizes/radii.
- Use `.button` plus a standard variant (`primary`, `subtle`, `danger-outline`, `danger`, `small`) without creating custom button classes (`.btn-*`).
- Use `.button.danger-outline` for destructive triggers on cards and tables; reserve solid `.button.danger` for final confirmation buttons inside modals.
- Include an `<app-icon>` on every button within a button group, and keep the standard action order: **Consultar** → **Editar** → **acción destructiva** (`Retirar` / `Dar de baja`).
- Avoid inline `style="..."` attributes in component templates.

## Testing

- Run `.\mvnw.cmd test` for the backend JUnit suite. Mirror package paths with `*Test.java` and set `RUN_ORACLE_TESTS=true` for opt-in integration and concurrency tests against a development schema.
- Run `npm test -- --watch=false` for Angular/Vitest. Keep `*.spec.ts` beside the code they exercise. REBOLSYS frontend specs are Git-ignored and may need to be obtained separately. Cover changed behavior and relevant failure paths.

## Commits and Pull Requests

Commit from the workspace root, where one Git repository tracks both REBOLSYS projects. Use concise Spanish summaries. Recent history includes imperative subjects and Conventional Commit prefixes such as `feat:` and `fix(ventas):`. In PRs, summarize changes, report checks, link relevant issues, and include screenshots for UI changes.

## Local Configuration

Use `DB_URL`, `DB_USERNAME`, and `DB_PASSWORD` or the ignored `.local/application-local-private.properties`. Keep secrets and private keys out of commits. Flyway is disabled in REBOLSYS. Consult the backend README before applying Oracle scripts manually.
