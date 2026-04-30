FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /src
COPY pom.xml .
RUN mvn -B dependency:go-offline -DskipTests
COPY src ./src
RUN mvn -B package -DskipTests

FROM eclipse-temurin:17-jre
RUN useradd -m -u 1001 ad
USER ad
COPY --from=build /src/target/adstream-*.jar /app/adstream.jar
WORKDIR /app
ENTRYPOINT ["java", "-jar", "adstream.jar"]
