# Stage 1: build the jar with Maven.
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /build
COPY pom.xml .
# Download dependencies first so they are cached until the pom changes.
RUN mvn -q -B dependency:go-offline
COPY src ./src
RUN mvn -q -B -DskipTests package

# Stage 2: a small runtime image with only the jar and the Orekit data.
FROM eclipse-temurin:21-jre
RUN apt-get update \
    && apt-get install -y --no-install-recommends curl unzip \
    && rm -rf /var/lib/apt/lists/*

# Orekit needs this data folder (leap seconds, Earth orientation). It is downloaded once, here.
RUN curl -fsSL https://gitlab.orekit.org/orekit/orekit-data/-/archive/main/orekit-data-main.zip -o /tmp/orekit.zip \
    && unzip -q /tmp/orekit.zip -d /opt \
    && mv /opt/orekit-data-main /opt/orekit-data \
    && rm /tmp/orekit.zip

RUN useradd --system --create-home kaksha \
    && mkdir -p /app/data/celestrak \
    && chown -R kaksha:kaksha /app
USER kaksha
WORKDIR /app

COPY --from=build /build/target/*.jar app.jar

ENV OREKIT_DATA_DIR=/opt/orekit-data \
    CELESTRAK_CACHE_DIR=/app/data/celestrak

EXPOSE 8080
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=70", "-jar", "app.jar"]
