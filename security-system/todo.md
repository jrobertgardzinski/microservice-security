# Nawigacja Javadoc ↔ Allure po ubiquitous language

## Cel
Umożliwić nawigację między Javadoc a raportami Allure po hasłach z ubiquitous
language (np. „brute-force guard"). Z definicji pojęcia (Javadoc) wchodzimy w testy,
które je sprawdzają (Allure), i odwrotnie.

## Zasada przewodnia
Kluczem łączącym jest **stabilny kebab-slug per pojęcie** (`brute-force-guard`), a nie
tekst tytułu. **Nie parsować `@Label`** — tytuł jest dla człowieka i ewoluuje swobodnie;
nawigacja opiera się na jawnej, ustrukturyzowanej adnotacji.

Dostępność potwierdzona: `allure-junit5:2.34.0` ciągnie `allure-java-commons`
(`@Link` / `@LinkAnnotation`).

## Status — ZAIMPLEMENTOWANE w security-system
- [x] **Krok 1** — `@Concept` (`testkit/Concept.java`, `@LinkAnnotation(type="concept")`)
  + `src/test/resources/allure.properties` ze wzorcem.
- [x] **Krok 2** — zweryfikowane na jqwiku: link `concept` z poprawnym URL trafia do
  `allure-results` dla `@Example`/`@Property` (17/17 metod feature'owych). **Ryzyko zamknięte.**
- [x] **Krok 3 (źródło)** — `src/test/resources/glossary.html` z kotwicami per slug;
  lekki link Javadoc→glosariusz na `_BruteForceGuard` (`@see`). Taglet `@concept` ODŁOŻONY.
- [x] **Krok 4** — `scripts/concept-traceability.sh` (jq): slug → testy z `allure-results`.
- [x] **Krok 5** — guardrail `testkit/ConceptGlossaryTest`: każdy `@Concept` ma kotwicę w glosariuszu.

Slugi: `registration`, `credential-verification`, `brute-force-guard`, `session-tokens`,
`refresh-token-rotation`.

### Pozostało — docelowa architektura (poza security-system)

#### Gdzie ma mieszkać `@Concept` — do `unit-test-starter`, NIE nowy moduł
`@Concept` przez `@LinkAnnotation` **z definicji zależy od Allure**, więc nie wolno jej używać
na klasach produkcyjnych (zaciągnęłaby Allure do prod-classpath). Konsekwencje:
- **Strona testowa:** `@Concept` → `unit-test-starter` (dodać `src/main/java`; moduł już ciągnie
  `allure-junit5`/`allure-java-commons` w compile, a email/password/security już od niego zależą
  w test scope → natychmiastowe współdzielenie). Tam też wspólny `allure.properties`.
- **Strona Javadoc:** NIE współdzieli adnotacji — używa **tagletu `@concept <slug>`** (zwykły tag
  w komentarzu, zero zależności). Dwie strony dzielą **tylko słownik slugów**, nie typ Javy →
  dlatego osobny „moduł adnotacji" jest zbędny.
- Osobny moduł uzasadniony dopiero dla **tagletu**: malutki `glossary-taglet` (moduł Mavena
  konsumowany przez `maven-javadoc-plugin`).

#### Python jako hub UL ↔ Javadoc ↔ Allure (zastępuje bashowy MVP)
`concept-traceability.sh` robi tylko jeden kierunek (Allure→testy, stdout). Docelowo skrypt
Pythona, bo skleja 3 heterogeniczne artefakty i generuje jedną stronę nav + waliduje repo-wide.
- **SSOT = `glossary.yaml`** (`slug → {title, definition}`); skrypt **renderuje** `glossary.html`
  (koniec ręcznego HTML).
- **Wejścia:** `glossary.yaml`, wszystkie `*/target/allure-results/*-result.json`, wyjście Javadoc.
- **Model:** `Concept{slug, title, definition, defined_in[Javadoc], exercised_by[testy+raport]}`.
- **Wyjścia:** `glossary.html` (hub: definicja + „Defined in" + „Exercised by"),
  `concepts.json` (indeks maszynowy), **walidacja repo-wide** (exit≠0 dla slugu bez definicji).
- **Generuj, nie mutuj** źródeł; odpalane w CI po `mvn test` + `javadoc` + `allure generate`.
- Libki: `pyyaml` + `jinja2` + stdlib `json/glob`; Javadoc przez `BeautifulSoup` lub parsowanie źródeł.

#### Podział odpowiedzialności (docelowy)
| Gdzie | Co | Typ |
|---|---|---|
| `unit-test-starter` | `@Concept` + wspólny `allure.properties` | moduł Mavena (+`src/main/java`) |
| repo-root `glossary/` | `glossary.yaml` (SSOT) + `build_glossary.py` (hub + walidacja) | tooling polyglot |
| (później) `glossary-taglet` | taglet Javadoc `@concept` | malutki moduł Mavena |
| per-moduł | `ConceptGlossaryTest` | zostaje — szybki lokalny check |

`ConceptGlossaryTest` (Java) zostaje dla szybkiego feedbacku przy `mvn test`; Python robi walidację
cross-artefaktową w CI — różne poziomy, nie wykluczają się.

#### Kolejność
1. Przenieść `@Concept` (+ `allure.properties`) do `unit-test-starter` — wspólny fundament,
   odblokowuje email/password.
2. `glossary/glossary.yaml` + `build_glossary.py` (hub + walidacja); bash zwija się do niego.
3. Publikacja `glossary.html` na GitHub Pages pod URL ze wzorca; wpięcie w CI.
4. (opcjonalnie) taglet `@concept` + rozsianie linków na klasy domenowe.

## Do zrobienia (oryginalny plan — referencja)

### 1. Strona Allure — custom link type `concept`
- W `unit-test-starter` (współdzielone) zdefiniować adnotację przez `@LinkAnnotation`:
  ```java
  @Documented
  @Retention(RetentionPolicy.RUNTIME)
  @Target({ElementType.METHOD, ElementType.TYPE})
  @LinkAnnotation(type = "concept")
  public @interface Concept { String value(); }   // value() -> {} we wzorcu
  ```
- Wzorzec w `src/test/resources/allure.properties` (test-classpath):
  ```properties
  allure.link.concept.pattern=https://jrobertgardzinski.github.io/portfolio/glossary.html#{}
  ```
- Oznaczyć test obok ludzkiego tytułu:
  ```java
  @Label("Blocked when the brute-force guard blocks the IP")
  @Concept("brute-force-guard")
  ```

### 2. PIERWSZY KROK — weryfikacja na jqwiku
Przed rozsianiem: oznaczyć JEDNĄ metodę `AuthenticationTest#blocked_when_guard_blocks`,
`mvn test`, sprawdzić, że link `concept` faktycznie pojawia się w raporcie.
**Ryzyko:** jqwik raportuje przez junit-platform — potwierdzić, że `allure-junit-platform`
zbiera adnotacje z metod `@Example`/`@Property` (na typowym `allure-junit5` działa,
ale to nietypowy stack).

### 3. Strona Javadoc — glosariusz jako single source of truth
- Strona `glossary.html` z jedną kotwicą per pojęcie: `<h2 id="brute-force-guard">…</h2>`.
- Z klas domenowych linkować do kotwicy — własny **taglet** `@concept <slug>`
  w `maven-javadoc-plugin`, renderujący absolutny link do `glossary.html#<slug>`
  (wariant „na szybko": `<a href>` w Javadoc — działa, ale ścieżki względne łamliwe).

### 4. Kierunek odwrotny — glosariusz jako hub
Deep-linki Allure 2 per-tag są kruche (SPA, routing po hashu). Zamiast linkować
Javadoc→Allure bezpośrednio:
- po `mvn test` skrypt czyta `target/allure-results/*.json`, z pola `links` wyciąga linki
  typu `concept` + slug, grupuje testy po slugu,
- dorzuca do każdego wpisu glosariusza sekcję „Exercised by: …" z linkami do raportu.

Efekt: z glosariusza wejście i w definicję (Javadoc), i w testy (Allure).

### 5. Guardrail (pasuje do TODO o ArchUnit)
Test skanujący wszystkie `@Concept(value)` i sprawdzający, że każdy slug istnieje jako
`id="…"` w `glossary.html`. Literówka = czerwony build, nie cichy martwy link.
Symetrycznie można sprawdzić `@concept` w Javadoc.

## Uzasadnienie decyzji
- **Slug, nie tytuł** — stabilny, jawny klucz; tytuł zostaje czytelny dla człowieka.
- **`@Concept` przez `@LinkAnnotation`** — natywny mechanizm Allure (tak zbudowane są
  `@Issue`/`@TmsLink`), nie hack; renderuje się jako pierwszorzędny link.
- **Glosariusz jako hub** — omija słabość deep-linkingu Allure, daje dwukierunkowość.
- **Spójność** — ta sama filozofia co `@config-source` w feature'ach: ustrukturyzowane
  tagi → generowana traceability.

---

# Pokrycie testami — ZROBIONE

Wszystkie klasy produkcyjne security-system mają testy (17 testów, BUILD SUCCESS):
`RegisterTest`, `RefreshSessionTest`, `AuthenticationTest` oraz sub-kroki:

- [x] **`_BruteForceGuard`** — 3 gałęzie (aktywna blokada → `Blocked`; limit osiągnięty →
  tworzy blokadę + czyści failures → `Blocked`; poniżej limitu → `Passed`). Długość blokady
  z `ThreadLocalRandom` weryfikowana jako przynależność `until ∈ [minBlockMinutes, maxBlockMinutes]`
  (przez `ArgumentCaptor`). Twarda asercja nadal wymagałaby wstrzyknięcia źródła losowości — patrz niżej.
- [x] **`_VerifyCredentials`** — 3 przypadki (user+hash OK → `Passed`; zły hash → `Failed`;
  brak usera → `Failed` + `verifyNoInteractions(hashAlgorithmPort)`).
- [x] **`_GenerateSession`**, **`_CleanBruteForceRecords`**, **`_UpdateBruteForceRecords`** —
  delegacja/interakcje (`verify(...)` na mockach, deterministyczny `Clock`).

## Pozostały dług (opcjonalny)
- **`_BruteForceGuard` — `ThreadLocalRandom.current()` bezpośrednio.** Dla twardej asercji
  długości blokady wstrzyknąć źródło losowości (jak `Clock`) — ten sam pattern „pin nieoznaczonego
  źródła" co z UUID w `RegisterTest`. Dziś sprawdzamy tylko przedział.

## Konwencja hierarchii Allure (ustalona)
Trzy poziomy: `@Epic` → `@Feature` → `@Story`.
- `@Epic("Use case")` — wspólny dla całej warstwy.
- `@Feature(<use-case>)` — np. `"Authentication"`, `"Register"`, `"Refresh session"`.
- `@Story(<sub-step>)` — sub-kroki `_*` jako stories pod feature'em orchestratora,
  np. `"Brute-force guard"`, `"Verify credentials"`, `"Generate session"`,
  `"Clean brute-force records"`, `"Update brute-force records"`.

Orchestratory (`AuthenticationTest`, `RegisterTest`, `RefreshSessionTest`) zostają na
`@Epic`+`@Feature`. Task nawigacyjny: `@Concept(<slug>)` siada **obok** `@Story`/`@Label`
na metodzie — to ortogonalny link, nie element hierarchii.
