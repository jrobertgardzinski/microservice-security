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

- **OAuth/social login (PRZED MFA — decyzja usera 2026-07-05)** — „po co zakładać konto, jak
  ktoś ma Google albo Facebooka". Google przez OIDC (Authorization Code + PKCE), port domenowy
  `IdentityProvider` + adapter w infrze (JDK HttpClient), Facebook jako drugi adapter później.
  Rejestracja zapada się w pierwsze logowanie: `email_verified` od providera spełnia bramę
  weryfikacji (bez maila). Tożsamości `(provider, subject) → user` (migracja V10), `User` bez
  zmian — JEDNO konto, WIELE tożsamości (hasło/Google/FB = równorzędne klucze). ŁĄCZENIE KONT
  (scenariusz usera: założył konto na Gmailu, zapomniał, wraca OAuth-em na ten sam adres) —
  trzy warianty: (a) lokalne konto ZWERYFIKOWANE + Google ręczy → auto-link i logowanie (obie
  strony dowiodły skrzynki); (b) lokalne konto NIEZWERYFIKOWANE (squatter na cudzy adres) →
  dowód Google bije nieudowodnione hasło: link + verified, ale stare hasło SKASOWANE (reset od
  nowa) i revoke wszystkich sesji; (c) odwrotnie — konto federacyjne, user próbuje /register →
  ciche 201, a mail „already registered" podpowiada „to konto loguje się przez Google / ustaw
  hasło resetem"; reset-password na koncie bez hasła = „ustaw hasło". Sesja na końcu identyczna
  jak dziś (SessionTokens/JWT/refresh cookie). Do stacku demo: STUB IdP jako kolejny
  mikroserwis-smak (Python stdlib, własne id_tokeny + JWKS) — smoke przechodzi OAuth na stubie,
  prod podmienia URL na Google (client-id/secret z env). Kolejność celowa: OAuth zmienia
  pierwsze ogniwo łańcucha MFA (credentials ALBO dowód providera), więc idzie pierwszy. — flagowy „wow"; największy otwarty temat.
  Wizja usera (2026-07-05): MFA = ŁAŃCUCH czynników przechodzonych JEDEN PO DRUGIM, np.
  credentials → kod e-mail → kod SMS; łańcuch konfigurowalny (port dostarczania kodu, adaptery
  email/SMS — serwisy kanałów już stoją: microservice-email, microservice-sms; dwie osie
  konfiguracji: deployment = co serwis oferuje, per-user = co user sobie włączył przy
  enrollmencie). TOTP/recovery codes jako ewentualne kolejne ogniwa. SPIĘCIE Z OAUTH
  (pytanie usera 2026-07-05 — TAK): provider zastępuje TYLKO pierwsze ogniwo (hasło ALBO
  callback providera = dwa wejścia do wspólnego egzekutora łańcucha; ogon email/SMS wspólny,
  sesja mintowana dopiero po całym łańcuchu). Polityka „czy dowód providera zwalnia z ogona"
  = wartość w configu (nie budować na claimach amr/acr — Google raportuje je słabo). Step-up
  federacyjnych: FULL_CHAIN = re-auth u providera (prompt=login) + ogon. CZEKA na analizę
  usera — nie ruszać samodzielnie.
- **Step-up auth** — ponowne uwierzytelnienie przy wrażliwej akcji; naturalni kandydaci już są
  (Change password, Delete account). Sprzężone z MFA; otwarte pytanie usera: step-up wymaga
  credentials + kodu czy samych credentials? (rozstrzygnie jego analiza MFA).
- ~~Role/permissions (RBAC)~~ — ZROBIONE W CAŁOŚCI. Serwerowo (2026-07-04, model 1 płaski):
  enum `Role` (USER/MODERATOR/ADMIN) w domenie; `User` niesie zbiór ról (USER zawsze), port
  `setRoles`, kolumna `roles` (migracja V8, comma-set, in-memory i JDBC), `/me` zwraca role —
  źródło prawdy dla innych serwisów. Endpoint admina `PUT /admin/users/{email}/roles` (use case
  `SetUserRoles`) za drugą bramą: wołający musi być ADMIN — z DB albo z bootstrapu
  `security.bootstrap-admins`. Strona konsumencka TEŻ ZROBIONA (2026-07-04, w sub-repo):
  memes (`50557b7`…`5dcdf70`) i comments (`3dcfc2a`) mają `Caller{email,roles}` z `/me`,
  DELETE mema/komentarza — autor swój, MODERATOR/ADMIN cudzy; testy + Gherkin w obu.
  Powiązany otwarty temat w memes/todo.md: flaga NSFW (moderator ukrywa/odkrywa treść).
- ~~Enumeracja na `/register`~~ — ZROBIONE (2026-07-05, decyzja usera): zajęty adres odpowiada
  IDENTYCZNIE jak świeża rejestracja (201, `{"status":"CHECK_YOUR_MAILBOX"}`, bez `id`); prawda
  idzie mailem do właściciela adresu — niezweryfikowany dostaje świeży link (pewnie zgubił
  pierwszy), zweryfikowany notkę „masz już konto" (nowy port `RegistrationNoticeNotifier`,
  outbox typ `ALREADY_REGISTERED`, szablon w microservice-email). Hash liczony zawsze przed
  sprawdzeniem zajętości (bez kanału czasowego). Rule 3 register.feature przepisana na „quiet
  refusal"; kroki delete-account („email nie jest wolny"/„można znów") dowodzą przez kanał
  mailowy, nie status. `RegisterEnumerationHttpTest` przybija nierozróżnialność.
  UWAGA: analogiczna enumeracja zostaje na `/account/email` (RequestEmailChange → 409
  EMAIL_TAKEN?) — endpoint uwierzytelniony, mniejsze ryzyko, ale do rozważenia tym samym wzorcem.
- ~~Hardening rejestracji (throttling)~~ — ZROBIONE (2026-07-04): throttle okna stałego per-IP
  (`security.registration.max-per-window` default 5 / `window-minutes` 15, 0 wyłącza),
  sprawdzany PRZED kosztowną pracą (Argon2 + insert), 429 + Retry-After; źródło = spoof-odporny IP
  z `ClientIpResolver`. Unit (3) + HTTP test (429) + live na PG.
- ~~Throttling na pozostałe kosztowne wejścia~~ — ZROBIONE (2026-07-05): `RegistrationThrottle`
  uogólniony do `SourceThrottle` (pakiet `system/throttle`); osobne instancje (@Named, osobne
  okna — burst na jeden endpoint nie zjada drugiego) dla `/register`,
  `/reset-password/request` (`security.password-reset.*`) i `/verify-email/request`
  (`security.verification.*`), oba defaulty 5/15 min, 0 wyłącza; 429 + Retry-After
  (TOO_MANY_RESET_REQUESTS / TOO_MANY_VERIFICATION_REQUESTS). Celowo OSOBNO od guarda
  uwierzytelniania (tamten broni kont przed zgadywaniem haseł, ten serwisu przed wolumenem).
  `RequestThrottleHttpTest` (3), unit throttle bez zmian. Compose podnosi limit rejestracji
  do 100 (smoke rejestruje kilka kont z jednego IP).
- ~~JWT self-contained~~ — ZROBIONE (2026-07-05) jako UZUPEŁNIENIE, nie zamiana: wartość access
  tokena to podpisany JWS (EdDSA/Ed25519, czyste JDK — zero nowych zależności) z iss/sub/roles/
  iat/exp/jti; port domenowy `AccessTokenMint` (RANDOM w unit testach, `JwtAccessTokenMint` w
  infra), mintowany przy authenticate I refresh. Security dalej traktuje wartość jako opaque
  (hash w bazie, introspekcja) ⇒ logout/revoke-all natychmiastowe. Inne serwisy MOGĄ weryfikować
  offline: `GET /.well-known/jwks.json` (OKP/Ed25519, kid) — kompromis świadomy: offline nie
  widzi logoutu/zmiany ról do wygaśnięcia; kto chce natychmiastowości, woła `/me` jak dotąd.
  Klucze: `security.jwt.private-key`/`public-key` (base64 PKCS#8/X.509), brak = efemeryczne
  (restart psuje TYLKO weryfikację offline — w stronę bezpieczną). `JwtAccessTokenHttpTest`:
  weryfikacja podpisu przez JWKS + logout zabija ważny podpisowo token. Zweryfikowane live.
  EWENTUALNY NASTĘPNY KROK: konsument (memes/comments) weryfikujący offline zamiast /me.
- ~~`Source` jako podmiot domeny~~ — ZROBIONE (2026-07-05): VO `Source(ipAddress, userAgent)`;
  TOŻSAMOŚĆ = samo IP (jedyne pole w equals/hashCode — klucz bloków i liczników; podsieć/ASN
  mogą kiedyś doostrzyć tę oś), OBSERWOWANE = userAgent (forensyka, celowo POZA equals — pin
  w `_BruteForceGuardTest`: rotacja user-agenta trafia w TEN SAM blok). `AuthenticationRequest`/
  `AuthenticationBlock`/guard/repozytoria biorą `Source`; kontroler dokłada nagłówek User-Agent
  z brzegu. Persystencja: `rejected_authentications.user_agent` (V9) — RODO: żyje dokładnie tak
  długo jak rekordy porażek (czyszczone razem); bloki trzymają samo IP (odtworzony `Source` bez
  kontekstu obserwowanego). Zweryfikowane live (V9 + kolumna wypełniana). Rozważane kiedyś:
  `DeviceFingerprint`/`RequestContext`, gdy obserwowanych atrybutów przybędzie.

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
