# Runtime image for the security service. The application jar and its runtime classpath are built
# beforehand on the host (`mvn -pl security-infrastructure -am package -DskipTests`, which also copies
# the runtime dependencies into target/lib). We run from the classpath rather than a fat jar so each
# dependency keeps its own META-INF/micronaut — Micronaut discovers beans across the jars with no
# fragile metadata merging. Build context is the microservice-security reactor root.
FROM eclipse-temurin:25-jre
# Not root. Nothing here needs it — the service binds 8080 (above 1024), writes no files, and
# installs nothing — so running as root only means that a defect in this process starts with the
# whole container's authority instead of one unprivileged account's.
RUN useradd --system --create-home --uid 10001 security
WORKDIR /app
COPY --chown=security:security security-infrastructure/target/lib/ lib/
COPY --chown=security:security security-infrastructure/target/security-infrastructure-1.0.0-SNAPSHOT.jar app.jar
USER security
EXPOSE 8080
ENTRYPOINT ["java", "-cp", "app.jar:lib/*", "com.jrobertgardzinski.App"]
