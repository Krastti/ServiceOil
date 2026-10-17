FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /workspace
COPY pom.xml ./
COPY src/main ./src/main
RUN --mount=type=cache,target=/maven-cache \
    mvn -B -ntp -Dmaven.repo.local=/maven-cache -Dmaven.test.skip=true package \
    && mkdir -p /root/.m2/repository \
    && cp -a /maven-cache/. /root/.m2/repository/

FROM build AS tests
COPY src/test ./src/test
ENTRYPOINT ["mvn", "-B", "-ntp"]
CMD ["test"]

FROM tests AS postman
# Official CLI installer; cloud login and publishing are not part of verification.
RUN curl -fsSL https://dl-cli.pstmn.io/install/unix.sh -o /tmp/install-postman.sh \
    && sh /tmp/install-postman.sh --prefix /opt/postman --no-modify-path \
    && rm /tmp/install-postman.sh
COPY postman/specs/serviceoil.openapi.yaml ./postman/specs/serviceoil.openapi.yaml
COPY postman/collections/ServiceOil ./postman/collections/ServiceOil
COPY postman/collections/ServiceOilRosstat ./postman/collections/ServiceOilRosstat
COPY postman/mocks/rosstat ./postman/mocks/rosstat
COPY postman/simulations/rosstat.sim.yaml ./postman/simulations/rosstat.sim.yaml
ENTRYPOINT ["/opt/postman/bin/postman"]

FROM postman AS integration-tests
ENV PATH="/opt/postman/bin:${PATH}"
ENTRYPOINT ["mvn", "-B", "-ntp"]
CMD ["test"]

FROM eclipse-temurin:21-jre-alpine AS runtime
WORKDIR /app
COPY --from=build /workspace/target/ServiceOil-*.jar ./serviceoil.jar
COPY docker/certs/russian-trusted-root-ca.pem /tmp/russian-trusted-root-ca.pem
COPY docker/certs/russian-trusted-sub-ca-2024.pem /tmp/russian-trusted-sub-ca-2024.pem
RUN test "$(openssl x509 -in /tmp/russian-trusted-root-ca.pem -noout -fingerprint -sha256 | cut -d= -f2)" = "D2:6D:2D:02:31:B7:C3:9F:92:CC:73:85:12:BA:54:10:35:19:E4:40:5D:68:B5:BD:70:3E:97:88:CA:8E:CF:31" \
    && test "$(openssl x509 -in /tmp/russian-trusted-sub-ca-2024.pem -noout -fingerprint -sha256 | cut -d= -f2)" = "21:55:78:50:36:C9:00:DB:B5:F1:BB:2A:15:69:C8:0C:55:59:5B:D6:BF:94:86:7A:29:BB:DD:BC:7D:88:A3:F2" \
    && openssl verify -CAfile /tmp/russian-trusted-root-ca.pem /tmp/russian-trusted-sub-ca-2024.pem \
    && keytool -importcert -trustcacerts -noprompt -alias rosstat-russian-trusted-root-ca \
        -file /tmp/russian-trusted-root-ca.pem -keystore "$JAVA_HOME/lib/security/cacerts" -storepass changeit \
    && keytool -importcert -trustcacerts -noprompt -alias rosstat-russian-trusted-sub-ca-2024 \
        -file /tmp/russian-trusted-sub-ca-2024.pem -keystore "$JAVA_HOME/lib/security/cacerts" -storepass changeit \
    && rm /tmp/russian-trusted-root-ca.pem /tmp/russian-trusted-sub-ca-2024.pem
RUN mkdir -p /app/logs && chown 10001:10001 /app/logs
USER 10001:10001
EXPOSE 8080
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=70", "-jar", "/app/serviceoil.jar"]

FROM runtime AS test-runtime
COPY src/test/resources/contract-weekly.csv ./data/contract-weekly.csv
