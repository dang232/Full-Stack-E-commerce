# --- build stage ---
FROM node:22-alpine AS build
WORKDIR /app

# Copy lock + manifest first for layer caching
COPY package.json package-lock.json* ./
RUN npm ci --no-audit --no-fund

COPY . .

# Vite inlines VITE_* env vars at build time. Compose passes these via build args.
ARG VITE_API_URL=http://localhost:8080
ARG VITE_KEYCLOAK_URL=http://localhost:8085
ARG VITE_KEYCLOAK_REALM=vnshop
ARG VITE_KEYCLOAK_CLIENT_ID=vnshop-web
ENV VITE_API_URL=$VITE_API_URL \
    VITE_KEYCLOAK_URL=$VITE_KEYCLOAK_URL \
    VITE_KEYCLOAK_REALM=$VITE_KEYCLOAK_REALM \
    VITE_KEYCLOAK_CLIENT_ID=$VITE_KEYCLOAK_CLIENT_ID

RUN npx vite build

# --- runtime stage ---
FROM nginx:1.27-alpine AS runtime
COPY nginx.conf /etc/nginx/conf.d/default.conf
COPY --from=build /app/dist /usr/share/nginx/html

EXPOSE 80
# busybox wget on alpine resolves "localhost" to ::1 first; nginx only listens
# on IPv4 in our config, so use 127.0.0.1 explicitly. Without this every
# 30s probe spuriously fails and the container shows "unhealthy" forever.
HEALTHCHECK --interval=30s --timeout=3s --retries=3 \
  CMD wget -qO- http://127.0.0.1/ >/dev/null || exit 1

CMD ["nginx", "-g", "daemon off;"]
