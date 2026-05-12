FROM node:24-alpine AS builder
WORKDIR /workspace
COPY package*.json ./
RUN npm ci
COPY tsconfig*.json nest-cli.json ./
COPY src ./src
RUN npm run build

FROM node:24-alpine AS runner
RUN apk add --no-cache curl && addgroup -S vnshop && adduser -S -G vnshop vnshop
WORKDIR /app
COPY --from=builder /workspace/dist ./dist
COPY --from=builder /workspace/package*.json ./
RUN npm ci --omit=dev && npm cache clean --force
RUN chown -R vnshop:vnshop /app
USER vnshop
EXPOSE 8084
HEALTHCHECK --interval=30s --timeout=20s --start-period=30s --retries=3 CMD curl -f http://localhost:8084/health || exit 1
CMD ["node", "dist/main"]
