#!/bin/bash
# The third entry point, end to end: start the security service in the `test` environment
# (in-memory stores, steerable clock, captured mailbox), start the Vite dev server, then run
# cucumber-js + Playwright over the SAME feature files the JVM runners select.
set -euo pipefail
cd "$(dirname "$0")"

JAR=../security-infrastructure/target/security-infrastructure-1.0.0-SNAPSHOT.jar
if [ ! -f "$JAR" ]; then
    echo "building the security jar first..."
    (cd .. && ./../mvnw -q -pl security-infrastructure -am package -DskipTests)
fi

cleanup() { kill "${SEC_PID:-}" "${UI_PID:-}" 2>/dev/null || true; }
trap cleanup EXIT

echo "== starting security (test environment: in-memory, steerable clock, captured mailbox)"
# 8180, not 8080 — the compose stack's dockerized security may hold the default port.
# The packaged jar has no src/test/resources/application-test.yml, so its overrides ride
# in as env vars: no Kafka (its health indicator would hang /health), no registration
# throttle (scenarios register from one source), plain-HTTP refresh cookies.
MICRONAUT_ENVIRONMENTS=test MICRONAUT_SERVER_PORT=8180 \
    KAFKA_ENABLED=false SECURITY_REGISTRATION_MAX_PER_WINDOW=0 SECURITY_COOKIE_SECURE=false \
    java -cp "$JAR:../security-infrastructure/target/lib/*" com.jrobertgardzinski.App \
    >/tmp/security-ui-e2e-service.log 2>&1 &
SEC_PID=$!

echo "== starting the Vite dev server"
npx vite --port 4200 >/tmp/security-ui-e2e-ui.log 2>&1 &
UI_PID=$!

for url in http://localhost:8180/health http://localhost:4200; do
    for i in $(seq 1 60); do
        curl -sf "$url" >/dev/null && break
        [ "$i" = 60 ] && { echo "FAIL: $url did not come up"; exit 1; }
        sleep 2
    done
done

echo "== driving the shared specs through the UI"
npx cucumber-js --config e2e/cucumber.mjs
