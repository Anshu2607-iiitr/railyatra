FROM public.ecr.aws/docker/library/maven:3.9.9-eclipse-temurin-21 AS build
WORKDIR /build
COPY pom.xml ./
COPY src ./src
RUN mvn --batch-mode --no-transfer-progress verify

FROM public.ecr.aws/docker/library/eclipse-temurin:21-jre
WORKDIR /app
RUN groupadd --system railyatra && useradd --system --gid railyatra railyatra
COPY --from=build --chown=railyatra:railyatra /build/target/railyatra-api-0.1.0.jar /app/app.jar
USER railyatra
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
