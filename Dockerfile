FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /build
COPY pom.xml .
COPY vehiculos-service/pom.xml vehiculos-service/
RUN mvn -B -q -pl vehiculos-service -am dependency:go-offline
COPY vehiculos-service/src vehiculos-service/src
RUN mvn -B -pl vehiculos-service -am -DskipTests package

FROM eclipse-temurin:21-jre
RUN apt-get update \
    && apt-get install -y --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/* \
    && groupadd --system --gid 10001 vehiculos \
    && useradd --system --uid 10001 --gid vehiculos --no-create-home --shell /usr/sbin/nologin vehiculos
WORKDIR /app
COPY --from=build /build/vehiculos-service/target/vehiculos-service-*.jar app.jar
USER vehiculos
EXPOSE 8083
ENTRYPOINT ["java", "-jar", "app.jar"]
