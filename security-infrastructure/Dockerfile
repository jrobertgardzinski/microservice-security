FROM maven:3.9.6-eclipse-temurin-21 AS build
WORKDIR /app
COPY . .
RUN mvn clean package -DskipTests -pl security-infrastructure -am

FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=build /app/security-infrastructure/target/*.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
