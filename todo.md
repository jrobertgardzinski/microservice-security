# TODO

Tylko otwarte rzeczy. Historia zrobionego = git log.
(Stara wersja z pełnym logiem faz: git log tego pliku.)

## Stan (2026-07-02) — kontekst, nie backlog

**13 feature'ów w `specs/`**, każdy napędzany czarną skrzynką przez HTTP (+ warstwa application
dla części): Register, Authenticate (+brute-force), RefreshSession (+reuse-detection), Authorize,
Logout, Verify email (request+confirm), Reset hasła (request+complete), Change password,
Change email (z re-weryfikacją nowego adresu), Delete account (RODO), List active sessions,
Revoke all sessions. Persystencja: Micronaut Data JDBC + Flyway + Testcontainers (in-memory,
gdy brak datasource). Deployment: docker-compose (Postgres + serwis). Maile: standalone
`microservice-email` przez `EmailServiceClient` (nagłówek `X-Api-Key`).

## Otwarte — use case'y / security

- **MFA/TOTP: enroll + verify + recovery codes** — flagowy „wow"; największy otwarty temat.
- **Step-up auth** — ponowne uwierzytelnienie przy wrażliwej akcji; naturalni kandydaci już są
  (Change password, Delete account).
- **Role/permissions (RBAC)** — dziś Authorize mówi „token ważny + kto", nie „czy może X";
  osobny wymiar (model ról).
- **Gating po weryfikacji emaila** — verify-email jest addytywny: niezweryfikowane konto wciąż
  może wszystko. Zdecydować, co blokować przed weryfikacją (przy okazji domyka enumerację
  na `/register`).
- **Hardening rejestracji** — IP/throttling na register (DoS przez haszowanie, masowe konta);
  osobna kontrola od guarda uwierzytelniania.
- **JWT self-contained** — alternatywa/uzupełnienie opaque tokenów (osobny temat: infra
  kluczy/podpisów).
- **`Source` jako podmiot domeny** — guard/`AuthenticationBlock` biorą `Source`, `IpAddress`
  staje się polem. TOŻSAMOŚĆ (klucz bloku, equals/hashCode: IP/podsieć/ASN/konto) vs
  OBSERWOWANE (machineName, browserVersion — forensics, POZA equals, inaczej zmiana
  user-agenta omija lockout). Rozważyć `DeviceFingerprint`/`RequestContext`. Uwaga RODO.

## Otwarte — wejścia i dokumentacja

- **UI jako 3. wejście** — Angular + cucumber-js/Playwright napędza TE SAME feature'y ze
  `specs/` (po to leżą w neutralnym katalogu, nie w `src/test` modułu JVM). Gdy scenariusze
  się rozjadą między wejściami → tagi `@http`/`@ui` + filtr per runner.

## Otwarte — porządki

- **Gałęzie remote-only poza rewritem autorów** (decyzja usera przed skasowaniem):
  `password:gemini-refactor`, `microservice-security:overnight/todo-cleanup` oraz omyłkowa
  gałąź `origin` na kilku remote'ach.

## Gotchas (operacyjne, warte pamięci)

- Po przeniesieniu pliku `.feature` zrób `mvn clean` — stara kopia w `target/test-classes`
  dubluje scenariusze.
- Runtime wymaga `org.yaml:snakeyaml`, inaczej Micronaut MILCZĄCO ignoruje `application*.yml`
  (w tym datasource!).
- `docker compose build` wymaga wcześniejszego `mvn package` (jar + `target/lib` budowane
  na hoście — build w kontenerze nie widzi security-libów z innych repo).
- Po micronaut-data POST bez `consumes` rejestruje trasę dwuznacznie (stąd `consumes=ALL`
  na `/refresh`).
