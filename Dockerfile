FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /workspace
COPY pom.xml ./
COPY src ./src
RUN mvn -B -ntp -DskipTests package

FROM build AS tests
ENTRYPOINT ["mvn", "-B", "-ntp"]
CMD ["test"]

FROM build AS postman
# Official CLI installer; cloud login and publishing are not part of verification.
RUN curl -fsSL https://dl-cli.pstmn.io/install/unix.sh -o /tmp/install-postman.sh \
    && sh /tmp/install-postman.sh --prefix /opt/postman --no-modify-path \
    && rm /tmp/install-postman.sh
COPY postman/specs/serviceoil.openapi.yaml ./postman/specs/serviceoil.openapi.yaml
COPY postman/collections/ServiceOil ./postman/collections/ServiceOil
ENTRYPOINT ["/opt/postman/bin/postman"]

FROM eclipse-temurin:21-jre-alpine AS runtime
WORKDIR /app
COPY --from=build /workspace/target/ServiceOil-0.0.1-SNAPSHOT.jar ./serviceoil.jar
COPY data/rosstat-regional.csv ./data/rosstat-regional.csv
USER 10001:10001
EXPOSE 8080
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=70", "-jar", "/app/serviceoil.jar"]
