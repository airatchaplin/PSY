FROM maven:3.9.9-eclipse-temurin-21 AS build
WORKDIR /app

# Longer timeouts help flaky Maven Central / proxy networks
ENV MAVEN_OPTS="-Dmaven.wagon.http.retryHandler.count=5 -Dmaven.wagon.httpconnectionManager.ttlSeconds=120"

COPY pom.xml .
COPY src ./src

# Single build step (skip fragile dependency:go-offline)
RUN mvn -B -DskipTests \
    -Dhttps.protocols=TLSv1.2 \
    -Dmaven.wagon.http.retryHandler.count=5 \
    -Dmaven.wagon.httpconnectionManager.ttlSeconds=120 \
    package

FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=build /app/target/trainer-0.1.0-SNAPSHOT.jar app.jar
EXPOSE 8081
ENTRYPOINT ["java", "-jar", "app.jar"]
