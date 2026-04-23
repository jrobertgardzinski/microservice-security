# TODO

## jqwik — globalne raportowanie wygenerowanych wartości

**Problem:** jqwik (1.9.x) nie ma globalnej propercji w `jqwik.properties` do włączenia
`Reporting.GENERATED`. Jedyny mechanizm to adnotacja `@Report(Reporting.GENERATED)` na
metodzie lub klasie — trzeba ją powtarzać w każdym teście property-based, co jest uciążliwe.

**Pomysły do zrealizowania:**

1. **Meta-adnotacja w `unit-test-starter`** — złożyć `@Property` + `@Report(Reporting.GENERATED)`
   w jedną własną adnotację (np. `@VerboseProperty`), udostępnioną wszystkim modułom
   przez `test-starter/unit-test-starter`:
   ```java
   @Target({ElementType.METHOD, ElementType.TYPE})
   @Retention(RetentionPolicy.RUNTIME)
   @Property
   @Report(Reporting.GENERATED)
   public @interface VerboseProperty { }
   ```
   W testach używać `@VerboseProperty` zamiast `@Property`. Jedno miejsce do zmiany.

2. **Feature request do upstream** — zgłosić na https://github.com/jqwik-team/jqwik/issues
   propozycję dodania `defaultReporting` do `jqwik.properties` (analogicznie do istniejących
   `defaultTries`, `defaultGeneration`, `defaultEdgeCases`).

**Stan obecny:** używamy `@Report(Reporting.GENERATED)` ad-hoc tam gdzie potrzeba widoczności
wygenerowanych sampli w konsoli.
