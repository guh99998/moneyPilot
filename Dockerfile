# Estágio 1: compila o jar (Maven wrapper do projeto, sem rodar testes: o CI já os roda)
FROM eclipse-temurin:21-jdk AS build
WORKDIR /build
COPY .mvn .mvn
COPY mvnw pom.xml ./
RUN sh mvnw -B -q dependency:go-offline
COPY src src
RUN sh mvnw -B -q package -DskipTests

# Estágio 2: só o JRE, usuário sem privilégio
FROM eclipse-temurin:21-jre
RUN apt-get update && apt-get install -y --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/* \
    && useradd --system --create-home app
WORKDIR /app
COPY --from=build /build/target/moneyPilot.jar app.jar
USER app
EXPOSE 8080
# Heap limitado: a VPS divide 4 GB com outros apps
ENV JAVA_TOOL_OPTIONS="-Xmx400m -XX:+UseSerialGC"
CMD ["java", "-jar", "app.jar"]
