# Kitchen

Your recipe book and cooking history, precise and permanent, shared with your household, usable from any AI.

Kitchen is an MCP server: each person keeps their AI assistant (Claude, ChatGPT…), and Kitchen brings the household's memory, the instructions and a few shared interfaces (shopping list, menu PDF).

https://kitchen.julco.dev

## Documentation

- [docs/SCOPE.md](docs/SCOPE.md): concept, decisions, what is in and out of v1
- [docs/PLAN.md](docs/PLAN.md): architecture, stack, data model, MCP server, phases
- [docs/INFRA.md](docs/INFRA.md): mini PC, k3s, Postgres, backups, deployment (cluster-wide infrastructure lives in the `homelab` repository)
- [CLAUDE.md](CLAUDE.md): rules for Claude Code

## Development

Prerequisites: Java 25, Docker, Node and pnpm at the versions pinned in `.tool-versions` and `package.json`.

```bash
cd apps/api && ./mvnw spring-boot:run   # API on :8080, starts PostgreSQL with Docker Compose
pnpm install && pnpm dev                # site on :5173
```

Google sign-in needs a Google OAuth client: copy `apps/api/.env.example` to `apps/api/.env` and fill it in. Open the site on http://localhost:5173 (the Vite dev server proxies the API).

Checks: `./mvnw verify` in `apps/api`; `pnpm lint`, `pnpm test`, `pnpm build` at the root.

## Status

Framing done (16 November 2026). Phases 1 (monorepo skeleton) and 2 (data model, isolation) done. Phase 3 (sign-in, households, invitations) implemented and tested automatically; the manual check with a real Google client (two accounts joining the same household) is pending.
