# syntax=docker/dockerfile:1
# Build context: the repository root (see docker-compose.prod.yml).

# ---- Front build ----
FROM node:20-alpine AS front
WORKDIR /front
COPY frontend/package.json frontend/package-lock.json ./
RUN npm ci
COPY frontend/ ./
RUN npm run build

# ---- Runtime: static files + reverse proxy ----
FROM nginx:1.27-alpine
# The image's entrypoint renders *.template with the container's environment (DOMAIN).
COPY deploy/nginx/default.conf.template /etc/nginx/templates/default.conf.template
COPY --from=front /front/dist /usr/share/nginx/html
