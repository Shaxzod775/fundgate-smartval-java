# Build stage
FROM eclipse-temurin:21-jdk-alpine AS build
WORKDIR /app
COPY pom.xml .
COPY smartval-common/pom.xml smartval-common/
COPY smartval-gateway/pom.xml smartval-gateway/
COPY smartval-fundgate/pom.xml smartval-fundgate/
COPY smartval-valuation/pom.xml smartval-valuation/
COPY smartval-submission/pom.xml smartval-submission/
COPY smartval-email/pom.xml smartval-email/
COPY smartval-chat/pom.xml smartval-chat/
COPY smartval-crm/pom.xml smartval-crm/
COPY smartval-spam/pom.xml smartval-spam/
COPY smartval-scheduler/pom.xml smartval-scheduler/

RUN apk add --no-cache maven && mvn dependency:go-offline -pl smartval-common,smartval-email -am -B

COPY smartval-common/src smartval-common/src
COPY smartval-email/src smartval-email/src
RUN mvn package -pl smartval-common,smartval-email -am -DskipTests -B

# Runtime stage
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
RUN addgroup -g 1000 appuser && adduser -u 1000 -G appuser -D appuser
COPY --from=build /app/smartval-email/target/*.jar app.jar
RUN chown appuser:appuser app.jar
USER appuser
EXPOSE 8084
HEALTHCHECK --interval=30s --timeout=3s CMD wget -qO- http://localhost:8084/actuator/health || exit 1
ENTRYPOINT ["java", "-jar", "app.jar"]
