FROM maven:3.9.9-eclipse-temurin-21 AS build
WORKDIR /workspace

COPY pom.xml .
COPY src ./src

RUN mvn -B -DskipTests clean package

FROM eclipse-temurin:21-jre-jammy AS runtime
WORKDIR /opt/app

RUN useradd -r -u 10001 spring

COPY --from=build /workspace/target/cc-app-backend-0.0.1-SNAPSHOT.jar app.jar

USER 10001
EXPOSE 8080

ENV SPRING_PROFILES_ACTIVE=prod

ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75.0", "-jar", "/opt/app/app.jar"]
