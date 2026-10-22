FROM node:22.23.3 AS web
WORKDIR /web
COPY frontend/package.json frontend/package-lock.json ./
RUN npm ci
COPY frontend/ .
RUN npm run build

FROM caddy:2.11.7
COPY ops/caddy/Caddyfile /etc/caddy/Caddyfile
COPY --from=web /web/dist /srv
RUN addgroup -S caddy && adduser -S -G caddy caddy && mkdir -p /data /config && chown -R caddy:caddy /data /config /srv
USER caddy
