# syntax=docker/dockerfile:1
#
# Kitchen web image: the static site served by nginx. The build context is the repository root
# (the site depends on packages/shopping-list):
#   docker build -f apps/web/Dockerfile -t kitchen-web .

FROM node:26.13.1-slim AS build
WORKDIR /repo
RUN npm install --global pnpm@12.21.0
COPY package.json pnpm-lock.yaml pnpm-workspace.yaml ./
COPY apps/web/package.json apps/web/
COPY packages/shopping-list/package.json packages/shopping-list/
RUN pnpm install --frozen-lockfile --filter @kitchen/web...
COPY apps/web apps/web
COPY packages/shopping-list packages/shopping-list
RUN pnpm --filter @kitchen/web build

# "unprivileged" nginx: runs as a non-root user and listens on 8080 (a non-root process cannot
# bind port 80).
FROM nginxinc/nginx-unprivileged:1.31.7-alpine
COPY --chmod=644 apps/web/nginx.conf /etc/nginx/conf.d/default.conf
COPY --from=build /repo/apps/web/dist /usr/share/nginx/html
EXPOSE 8080
