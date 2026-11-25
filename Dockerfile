# Stage 1: Build executable Spring Boot JAR with Maven 3.9 and Java 21
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /app

COPY pom.xml ./
COPY src src
RUN mvn -B clean package -DskipTests

# Stage 2: Minimal Java 21 runtime image
FROM eclipse-temurin:21-jre
WORKDIR /app

RUN addgroup --system appgroup && adduser --system --ingroup appgroup appuser
COPY --from=build /app/target/taskflow-*.jar app.jar
RUN chown appuser:appgroup app.jar

USER appuser

ENV PORT=8080
EXPOSE 8080

ENTRYPOINT ["java", "-jar", "app.jar"]
