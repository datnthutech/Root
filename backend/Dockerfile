FROM gradle:8.10.2-jdk21 AS build
WORKDIR /app
COPY backend/settings.gradle backend/build.gradle ./
COPY backend/gradle ./gradle
RUN gradle --no-daemon -q dependencies > /dev/null || true
COPY backend/src ./src
RUN gradle --no-daemon -q bootJar -x test

FROM eclipse-temurin:21-jre
RUN useradd -r -u 1001 app
WORKDIR /app
COPY --from=build /app/build/libs/giapha-0.0.1-SNAPSHOT.jar /app/app.jar
USER app
EXPOSE 8080
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-jar", "/app/app.jar"]
