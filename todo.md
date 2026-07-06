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

- ~~OAuth/social login~~ — ZROBIONE (2026-07-05): taniec Authorization Code + PKCE (S256) na
  brzegu — `/oauth/{provider}/start` + `/oauth/callback` (state jednorazowy, TTL 10 min, nonce,
  return-URL tylko z allowlisty, access token wraca FRAGMENTEM, refresh jak zwykle w HttpOnly
  cookie); `OidcClient` (czysty JDK HttpClient) wymienia kod i waliduje id_token: HS256
  weryfikowany client-secretem (stub), algorytmy asymetryczne (Google RS256) na mocy
  bezpośredniego kanału TLS wg OIDC Core 3.1.3.7 — iss/aud/exp/nonce twardo zawsze. Provider =
  czysty config (@EachProperty `security.oauth.providers.*`): w compose „google" wskazuje STUB
  IdP (`microservice-idp`, Python stdlib, :8091 — 8090 zajęte przez race-sim), prod podmienia
  URL-e i client-id/secret. Use case `FederatedSignIn` + VO `ProviderIdentity` +
  `FederatedIdentityRepository` (migracja V10; jedno konto — wiele tożsamości): świeży email →
  konto od urodzenia zweryfikowane i BEZHASŁOWE (hash odrzuconego losowego sekretu; hasło można
  nadać resetem); konto ZWERYFIKOWANE → auto-link, hasło nietknięte; NIEZWERYFIKOWANE (squatter)
  → przejęcie: link + verified + hasło skasowane + revoke wszystkich sesji; email bez poręczenia
  providera nie tyka niczego; pending-deletion odmawia jak przy haśle.
  `federated-sign-in.feature` (5 scenariuszy, warstwa application) + `OauthFlowHttpTest` (pełny
  taniec po drucie z fake'owym providerem: replay state'a, zły nonce, cudzy return-URL) + krok
  w infra-smoke (PASS live). Mail „already registered" podpowiada logowanie społecznościowe /
  ustawienie hasła resetem; UI galerii ma przycisk „Sign in with Google". ZOSTAJE na później:
  realny Google (client-id/secret od usera), odświeżanie linku federacyjnego
  przy change-email (dziś stały link bezpiecznie odpada i re-linkuje się przy następnym logowaniu).
  - ~~Uogólnienie na Facebook/GitHub/GitLab~~ — ZROBIONE (2026-07-06): `identity-source`
    per provider — `ID_TOKEN` (Google/GitLab, jak dotąd) albo `USERINFO` (Facebook/GitHub:
    exchange kodu → access_token → GET userinfo; mapowanie pól `subject-field`/`email-field`/
    `email-verified-field`, opcjonalny `emails-url` GitHub-shaped — primary verified wygrywa,
    `assume-email-verified` jako świadoma decyzja deploymentu — bez niej brak flagi =
    `EMAIL_NOT_VOUCHED`); `scope` i `label` per provider; `GET /oauth/providers` → UI galerii
    rysuje przyciski dynamicznie (dodanie providera = tylko config). W compose drugi provider
    „github" na TYM SAMYM stubie ćwiczy USERINFO; smoke kryje obie ścieżki (PASS live).
    Przepisy configu realnych providerów: [docs/oauth-providers.md](docs/oauth-providers.md).
    `OauthFlowHttpTest`: 6 testów (dotychczasowe + hub/emails-url + faces/assume + strict/refused
    + listing providerów). Ustawienia providera = `OauthProviderSettings` w WARSTWIE CONFIG
    (framework-free rekord, defaulty+walidacja w konstruktorze; widoczny w glosariuszu UL);
    infra tylko binduje propertisy (@EachProperty shim → @EachBean w BeanFactory).
- **MFA: łańcuch czynników, metody PLUG-AND-PLAY, minimum per rola** — flagowy „wow"; największy
  otwarty temat. PEŁNY PROJEKT: [docs/mfa-design.md](docs/mfa-design.md) (2026-07-05). Skrót:
  port `AuthenticationFactor` + `FactorRegistry` = dodanie metody (TOTP/Google Authenticator,
  WebAuthn…) to nowy adapter-bean, zero zmian w rdzeniu (email/SMS to tylko dwa adaptery); egzekutor
  łańcucha (`PendingAuthentication` + ticket, jak OauthFlowStore) mintuje sesję dopiero po ostatnim
  ogniwie; `MfaPolicy` wymusza minimum per rola (USER 1 / MODERATOR 2 / ADMIN 3, konfig) w TRZECH
  miejscach — brama logowania (sesja `enrolment_only` dla niedopełnionych), grant roli (`/me`
  `mfaCompliant`), usuwanie czynnika (podłoga); bootstrap-admin grace do pierwszego enrollmentu.
  Fazy A–G w dokumencie. 4 decyzje usera ROZSTRZYGNIĘTE (2026-07-05): floor liczy cały łańcuch
  z pierwszym; niedopełniony → sesja enrolment-only; federacyjni do PEŁNEGO floora (OAuth się nie
  liczy); pierwszy factor = e-mail (TOTP w fazie B).
  - ~~FAZA A~~ — ZROBIONE (2026-07-05): port `AuthenticationFactor` + `FactorRegistry` +
    egzekutor łańcucha (`PendingAuthentication` + jednorazowy ticket w `PendingAuthenticationStore`)
    + `EmailCodeFactor` nad portem `CodeChannel` (outbox `AUTH_CODE` prod / capturing test);
    `Authentication` rozgałęzia po bramie zweryfikowanego maila (brak czynników = sesja jak dawniej;
    są = 202 `MFA_REQUIRED` + ticket), `ContinueAuthentication` domyka; enrollment `EnrolFactor`
    (start wysyła kod, confirm pieczętuje). `ChallengeCodeConfig` (TTL/próby/długość, default 5/5/6)
    w warstwie config; kody SHA-256 hash. Migracja V11 `enrolled_factors`. Endpointy: `/authenticate`
    (202), `/authenticate/factor`, `/account/factors` (list/enroll/confirm/remove). Mail `AUTH_CODE`
    w microservice-email. UI: security-ui (dwustopniowe + enrollment) i galeria memów (krok kodu).
    Testy: `mfa.feature` (application) + `MfaHttpTest` (po drucie) + krok w infra-smoke (live).
  - ~~FAZA B~~ — ZROBIONE (2026-07-05): `EmailCodeFactor` → generyczny `CodeFactor(kanał)` — email i
    SMS to dwie instancje jednej klasy (nowy kanał = nowy bean, nie nowy factor); `HttpSmsCodeChannel`
    → microservice-sms; `TotpFactor` (RFC 6238/HMAC-SHA1, Google Authenticator) — factor posiadania,
    enrollment mintuje sekret + otpauth URI, nic nie wysyła. Port zyskał `beginEnrolment`
    (`EnrolmentSetup`: secret, co pokazać, opcjonalny challenge). Testy: `TotpFactorTest` (wektor RFC),
    `MfaHttpTest` (TOTP po drucie), smoke (TOTP live). UI: manager enrollmentu (wszystkie oferowane
    metody). SMS: unit+HTTP (stub nie ma czytelnej skrzynki na live).
  - ~~FAZA C~~ — ZROBIONE (2026-07-05, drugi twardy wymóg usera): `MfaPolicy` (min per rola USER 1/
    MOD 2/ADMIN 3, konfig); `MfaCompliance` liczy cały łańcuch (hasło jako #1 + czynniki; konta
    federacyjne PASSWORDLESS — nowa tabela V12, ustawiana przy federated create/takeover, czyszczona
    przy resecie hasła → OAuth NIE liczy się do floora). Egzekwowanie w 3 miejscach: brama
    (`AuthorizationFilter` wpuszcza niedopełnionego tylko do /me i /account/factors, reszta 403
    `MFA_ENROLMENT_REQUIRED` — sesja realna, „zabudowana"), grant (`/me` niesie
    mfaCompliant/requiredFactors/haveFactors), usuwanie (409 `WOULD_BREAK_MFA_FLOOR`). Bootstrap-admin
    grace do pierwszego enrollmentu. UI nudge. `MfaRoleFloorHttpTest` (pełny łuk) + `MfaPolicyRulesTest`.
    ODSTĘPSTWO od doc: żywe sprawdzanie compliance w filtrze zamiast trwałej flagi `enrolment_only`
    na wierszu sesji — prościej (bez zmian schematu sesji) i poprawniej (aktualizuje się natychmiast
    po enrollmencie, bez re-logowania).
  - ~~FAZA F~~ — ZROBIONE (2026-07-05): OAuth to tylko ogniwo #1. `_MfaChain` → publiczny `MfaChain`
    (jeden bean: logowanie hasłem, federacyjne, kontynuacja). `FederatedSignIn` po rozwiązaniu konta
    sprawdza czynniki: brak → sesja, są → `MfaRequired` + ticket w tym samym store; callback OAuth
    zwraca `#mfaTicket`, galeria dokańcza przez `/authenticate/factor`. Zamyka dziurę z fazy C
    (federacyjny admin z czynnikami był wpuszczany bez nich). Scenariusz w federated-sign-in.feature.
  - ~~FAZA E~~ — ZROBIONE (2026-07-05): step-up. `StepUpPolicy` (per akcja NONE/SECOND_FACTORS/
    FULL_CHAIN; delete=FULL_CHAIN, change-password=SECOND_FACTORS, config). `StepUp` odpala łańcuch na
    żywej sesji (FULL_CHAIN = najpierw hasło, potem czynniki przez wspólny `MfaChain`), po ostatnim
    czynniku mintuje jednorazowy `SessionElevation` na access-tokenie. `DeleteAccountController` konsumuje
    elewację przez `StepUpGuard` (403 `STEP_UP_REQUIRED` bez niej — skradziona sesja nie usunie konta).
    Endpointy `/account/step-up` (+`/factor`). `StepUpHttpTest` (ścieżka hasłowa i czynnikowa), dialog
    delete w galerii robi step-up, saga w smoke poprzedzona step-upem. Live PASS.
  - ~~FAZA D — admin reset~~ — ZROBIONE (2026-07-05): `PUT /admin/users/{email}/factors/reset`
    (ADMIN + step-up), `EnrolledFactorRepository.removeAll`; użytkownik po resecie spada pod podłogę.
    `AdminFactorResetHttpTest`.
  - ~~recovery codes~~ — ZROBIONE (2026-07-06), dokładnie jako czynnik ALTERNATYWNY (nie ogniwo):
    `MfaChain.verify` po odmowie czynnika próbuje skonsumować nieużyty recovery code (normalizacja
    case/myślników → SHA-256 → warunkowy UPDATE, więc jednorazowość jest atomowa) — sign-in
    continuation i step-up łapią to bez własnych zmian. `RecoveryCodeRepository` (V13, spent
    zostaje wierszem — UI mówi „N z 10"), `GenerateRecoveryCodes` (alfabet bez homoglifów, grupy
    po 5) + `RecoveryCodeConfig` (warstwa config). `POST /account/recovery-codes` pokazuje batch
    RAZ i unieważnia stary; `GET` = licznik. UI: security-ui generuje/liczy + hint na ekranie
    kodu; galeria hint. Testy: mfa.feature (2 scenariusze), MfaHttpTest (spend/replay/regeneracja),
    infra-smoke krok. Szczegóły w docs/mfa-design.md.
  - ~~FAZA G — MFA w e2e security-ui~~ — ZROBIONE (2026-07-06): mfa.feature (5 scenariuszy, w tym
    oba recovery) przez realny UI (cucumber-js/Playwright, wspólne Gherkiny); backdoor
    `/test/mailbox/signin-code` (AUTH_CODE); recovery codes zbierane ze strony po generacji —
    jedyne miejsce, gdzie plaintext istnieje. 22/22 e2e. Znalazło i naprawiło realny bug UI:
    fetch `r.ok` true dla 202 → gałąź MFA martwa po rebuildzie na Reacta (signIn + submitFactor).
    Poza zakresem e2e security-ui: TOTP (MfaHttpTest+smoke), step-up przy delete (galeria+smoke;
    security-ui nie ma UI delete).
- **Step-up auth** — WPISANE W PROJEKT MFA (faza E, [docs/mfa-design.md](docs/mfa-design.md)):
  ten sam egzekutor łańcucha odpalony na żywej sesji → jednorazowy znacznik `elevated`; polityka
  per akcja w configu NONE/SECOND_FACTORS/FULL_CHAIN (delete-account=FULL_CHAIN,
  change-password=SECOND_FACTORS, enrol/remove i admin-reset=SECOND_FACTORS). Odpowiedź na dawne
  pytanie usera (credentials vs credentials+kod): to WARTOŚĆ W CONFIGU per akcja, nie jedno-lub-drugie.
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
  Analogiczna enumeracja na `/account/email` — TEŻ ZAMKNIĘTA (2026-07-05): zajęty adres
  odpowiada jak świeży request (202 EMAIL_CHANGE_LINK_SENT), właściciel dostaje notkę mailem
  (ten sam port `RegistrationNoticeNotifier`); reguła + scenariusz w change-email.feature.
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
