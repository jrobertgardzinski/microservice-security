# TODO

Tylko otwarte rzeczy. Historia zrobionego = git log.
(Stara wersja z pełnym logiem faz: git log tego pliku.)

## Stan (2026-07-02) — kontekst, nie backlog

**13 feature'ów w `specs/`**, każdy napędzany czarną skrzynką przez HTTP (+ warstwa application
dla części): Register, Authenticate (+brute-force; od 2026-07-02 wymaga zweryfikowanego
emaila — 403 `EMAIL_NOT_VERIFIED`, rejestracja auto-wysyła link), RefreshSession
(+reuse-detection), Authorize, Logout, Verify email (request+confirm), Reset hasła
(request+complete), Change password, Change email (z re-weryfikacją nowego adresu; potwierdzenie
oznacza nowy adres jako zweryfikowany), Delete account (RODO), List active sessions,
Revoke all sessions. Persystencja: Micronaut Data JDBC + Flyway + Testcontainers (in-memory,
gdy brak datasource). Deployment: docker-compose (Postgres + serwis). Maile: od 2026-07-02 **zdarzenia przez Kafkę**
— transactional outbox w Postgresie (`outbox_events`, V5; ta sama transakcja co zmiana stanu),
poller publikuje na topik `mail-requests`, konsumuje `microservice-email` (at-least-once,
dedup po id zdarzenia). Awaria mail-serwisu nie psuje rejestracji — zdarzenie czeka.
**Delete account jest sagą** (orkiestracja, stan w `account_deletion_sagas`, V6): konto blokuje się
od razu (`users.pending_deletion`), memes czyści treści (komenda `memes-commands` przez outbox,
potwierdzenie `memes-events`), dopiero potwierdzenie kasuje usera i wysyła mail pożegnalny;
brak potwierdzenia w limicie (`account-deletion.purge-timeout`, domyślnie 2 min) = kompensacja
(odblokowanie + mail z przeprosinami).

## Otwarte — use case'y / security

- **MFA/TOTP: enroll + verify + recovery codes** — flagowy „wow"; największy otwarty temat.
- **Step-up auth** — ponowne uwierzytelnienie przy wrażliwej akcji; naturalni kandydaci już są
  (Change password, Delete account).
- **Role/permissions (RBAC)** — ZROBIONE (2026-07-04, model 1 płaski): enum `Role`
  (USER/MODERATOR/ADMIN) w domenie; `User` niesie zbiór ról (USER zawsze), port `setRoles`,
  kolumna `roles` (migracja V8, comma-set, in-memory i JDBC), `/me` zwraca role — źródło prawdy
  dla innych serwisów. Endpoint admina `PUT /admin/users/{email}/roles` (use case `SetUserRoles`)
  za drugą bramą: wołający musi być ADMIN — z DB albo z bootstrapu `security.bootstrap-admins`
  (rozwiązuje jajko-kura pierwszego admina). 4 scenariusze `roles.feature` + weryfikacja live na
  PG (nadanie MODERATOR utrwalone, 403 dla nie-admina, /me czyta role z PG). ZOSTAJE: strona
  konsumencka — bramy MODERATOR/ADMIN w memes/comments czytające `roles` z `/me`.
- **Enumeracja na `/register`** — rejestracja nadal zwraca 409 dla zajętego adresu. Fundament
  do domknięcia jest od 2026-07-02: logowanie wymaga zweryfikowanego emaila, więc `/register`
  może odpowiadać jednolicie („wysłaliśmy link") bez otwierania konta atakującemu. Zmiana
  kontraktu → decyzja usera.
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
