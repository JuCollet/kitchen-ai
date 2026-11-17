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

The MCP App widget (`packages/shopping-list`) is served by the API from its classpath: run `pnpm build` before starting the API (or restart the API after rebuilding it). Without it, the API serves a "widget not built" placeholder.

Checks: `./mvnw verify` in `apps/api`; `pnpm lint`, `pnpm test`, `pnpm build` at the root.

## Status

Framing done (16 November 2026). Phases 1 (monorepo skeleton), 2 (data model, isolation), 3 (sign-in, households, invitations), 4 (OAuth server and minimal MCP), 5 (MCP Apps spike), 6 (instructions, `get_context`, profiles, appliances, onboarding, health data consent) 7 (recipes, ratings, search, appliance mode validation, import by the AI), 8 (menus, period appliances, pantry, the four prompts) and 9 (shopping list: widget, fallback page, transfer to the pantry) done; phase 4 checked with the MCP Inspector and a real Claude through a temporary Cloudflare tunnel (`cloudflared tunnel --url http://localhost:5173`, with `KITCHEN_PUBLIC_URL` set to the tunnel URL). Phase 6 checked with a complete onboarding in a real Claude; phase 7 with a recipe imported from a URL and rated by two members; phase 8 with a weekly menu generated from the `weekly_menu` prompt in a real Claude; phase 9 with a shopping list created and shown in a real Claude and its fallback page (not yet checked in ChatGPT). Next step: phase 10, the menu PDF with Gotenberg.
