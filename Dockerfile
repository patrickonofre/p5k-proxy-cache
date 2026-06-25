# syntax=docker/dockerfile:1

### Build stage — compile and package the fat jar with JDK 26
FROM eclipse-temurin:26-jdk AS build
WORKDIR /workspace
COPY .mvn/ .mvn/
COPY mvnw pom.xml ./
COPY src/ src/
RUN chmod +x mvnw
RUN --mount=type=cache,target=/root/.m2 \
    ./mvnw -B -ntp -DskipTests clean package \
    && cp target/*.jar app.jar

### Runtime stage — slim JRE 26, non-root
FROM eclipse-temurin:26-jre-alpine AS runtime
WORKDIR /app
RUN addgroup -S app && adduser -S app -G app
COPY --from=build /workspace/app.jar app.jar
USER app
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
