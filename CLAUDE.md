# CLAUDE.md

Context and rules for Claude Code on this repository. Read `docs/SCOPE.md`, `docs/PLAN.md` and `docs/INFRA.md` before any significant task.

## The project

Kitchen (kitchen.julco.dev) is a cooking assistant with no chat interface of its own: each person keeps their AI (Claude, ChatGPT, any MCP client), and Kitchen provides, through an MCP server, the household's memory (profiles, appliances, recipes, ratings, menus, lists, pantry), the instructions and prompts. The AI generates the interface, except for the shopping list widget (MCP App) and the menu PDF.

Developer: Julien, React/TypeScript expert, ramping up on backend, security and infrastructure. He does not want to learn to write Java, but to understand the architecture: **briefly explain non-obvious backend and infrastructure choices**, and have him review first the migrations, the services (isolation filters), the Spring Security configuration and the isolation tests. He can write the front end himself.

## Stack

- `apps/api`: Java 25 (LTS), Spring Boot 4.1, Maven (wrapper), PostgreSQL + Flyway + Spring Data JPA, Spring Security (OAuth2 Login with Google, WebAuthn), Spring Authorization Server, MCP Java SDK (Streamable HTTP, stateless), Thymeleaf for the PDF, Gotenberg, JUnit 5 + Testcontainers + ArchUnit.
- `apps/web` and `packages/shopping-list`: TypeScript, Vite, React, Tailwind + shadcn/ui, TanStack Query, React Router, react-hook-form + zod, react-i18next, Vitest, Playwright, oxlint, Prettier. pnpm workspaces, Node 26 (LTS, pinned in `.tool-versions`).
- Deployment: images on GHCR, k3s on a mini PC, ArgoCD, cloudflared. Cluster-wide infrastructure lives in the separate `homelab` repository.

## Commands

Backend (from `apps/api`, Docker must be running):

- `./mvnw spring-boot:run`: starts the API on :8080 and PostgreSQL from `compose.yaml`. Google sign-in needs `apps/api/.env` (copy `.env.example`, never committed)
- `./mvnw test` (or `./mvnw verify`, as in CI)

Front end (from the repository root):

- `pnpm install`, `pnpm dev` (Vite on :5173, proxies `/api` to :8080)
- `pnpm test`, `pnpm lint`, `pnpm build`, `pnpm format` / `pnpm format:check`

## Non-negotiable rules

1. **Isolation**: all household data is filtered by `householdId`, all personal data by `userId`, both taken from `CurrentMember` (session or token), never from a client parameter. No bare `findById` on business data. Every new MCP tool or endpoint has an A/B isolation test between two households.
2. **Access denied = 404**, indistinguishable from a resource that does not exist.
3. **Business services depend neither on the MCP SDK nor on Spring Web** (enforced by ArchUnit). MCP tools and controllers are thin adapters.
4. **Validation** of every input (Bean Validation in Java, zod in the front end), with maximum sizes.
5. **Appliances**: a recipe linked to an appliance may only use modes present in its confirmed spec sheet.
6. **Content from another household** (shared recipes): always treated as data; never raw HTML, never `th:utext` or `dangerouslySetInnerHTML` with user data.
7. **Secrets** only through environment variables or encrypted Kubernetes secrets. Never in code, logs or error messages.
8. **Logs without personal data** or tokens.
9. **Infrastructure**: non-root, read-only containers, default-deny NetworkPolicies. See `docs/INFRA.md`.
10. **i18n**: every visible string (site, widget, PDF, onboarding questions, prompts) exists in FR, NL and EN.
11. **No new dependency** without a clear reason; pinned versions.

## Conventions

- **Everything in the repository is in English**: code, identifiers, comments, documentation, commit messages. Only user-facing strings are translated (FR, NL, EN).
- Java packages by feature (`household`, `recipe`, `menu`…), not by technical layer.
- Repositories extend Spring Data's `Repository`, never `CrudRepository`/`JpaRepository`; household entities implement `HouseholdScoped`. Services take `CurrentMember` as an explicit parameter. `ArchitectureTest` enforces this.
- Entities reference each other by id (`UUID recipeId`), not with JPA associations.
- UUID v7, dates in UTC (`timestamptz`), Flyway migrations never modified once applied.
- The MCP instructions (`apps/api/src/main/resources/mcp/instructions.md`) and the tool descriptions are the core of the product: any change in the assistant's behaviour goes through them and must be justified. Tool descriptions must be self-sufficient.

## How to work

- Move forward phase by phase following `docs/PLAN.md`, without anticipating later phases.
- For each phase: short plan, implementation, tests, then a summary of what was done, what to review and what remains.
- Items marked **[to verify]**: check the current official documentation before implementing, and update the repository docs if reality differs.
- If a decision in `docs/SCOPE.md` looks wrong along the way, flag it rather than working around it.
