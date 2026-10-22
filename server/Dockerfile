FROM gradle:8.14.3-jdk21 AS build
WORKDIR /workspace
COPY . .
RUN gradle --no-daemon :app:bootJar

FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
COPY --from=build /workspace/app/build/libs/evently.jar app.jar
USER nobody
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
