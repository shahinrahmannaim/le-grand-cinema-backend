FROM eclipse-temurin:21-jdk AS build
WORKDIR /workspace
COPY .mvn .mvn
COPY mvnw pom.xml checkstyle.xml ./
RUN chmod +x mvnw && ./mvnw -B dependency:go-offline
COPY src src
RUN ./mvnw -B verify

FROM eclipse-temurin:21-jre
WORKDIR /app
RUN groupadd --system cinema && useradd --system --gid cinema cinema
COPY --from=build --chown=cinema:cinema /workspace/target/cinema-backend-0.0.1-SNAPSHOT.jar app.jar
USER cinema
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=65.0 -XX:+ExitOnOutOfMemoryError"
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
