FROM maven:3.9.11-eclipse-temurin-25-alpine AS builder
WORKDIR /workspace
COPY services/search-service/pom.xml .
RUN mvn dependency:go-offline
COPY services/search-service/src ./src
RUN mvn package -DskipTests

FROM eclipse-temurin:25-jre-alpine AS runner
RUN apk add --no-cache curl && addgroup -g 1000 vnshop && adduser -D -u 1000 -G vnshop vnshop
WORKDIR /app
COPY --from=builder /workspace/target/*.jar app.jar
RUN chown -R vnshop:vnshop /app
USER vnshop
EXPOSE 8086
HEALTHCHECK --interval=30s --timeout=20s --start-period=30s --retries=3 CMD curl -f http://localhost:8086/actuator/health || exit 1
ENTRYPOINT ["java", "-jar", "app.jar"]
