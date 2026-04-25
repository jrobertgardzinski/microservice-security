# Cheat sheet: primitive → VO

Krótka ściąga do refaktoringu z primitive obsession na Value Object.
Wzorce utrwalone w projekcie:
- pojedyncze VO: `FailureWindowMinutes` (`security-config/.../bruteforce/vo/`)
- hierarchia VO: `AbstractEmail` (`email-domain`),
  `AbstractTokenValidityInHours`, `AbstractToken`, `AbstractTokenExpiration`
  (`security-domain/.../vo/`)

## Kiedy warto

- Pole/argument `int`, `long`, `String` niesie znaczenie domenowe
  (`failureWindowMinutes`, `minLength`, a nie np. indeks pętli).
- Ma reguły walidacji (zakres, format, niepusty).
- Pojawia się w sygnaturach kilku metod i propaguje się przez warstwy.
- W call sites widać `config.xxx().value()` — to znak, że VO jest w połowie drogi
  i gdzieś przecieka do prymitywu.

## Wybór formy: record vs abstract class

**Pojedyncze VO bez rodzeństwa** → `record`. Najprościej.

**Hierarchia VO** (dwa+ typy dzielące walidację i strukturę) → `abstract class`
+ `final` subclasses. Records są `final`, nie mogą dziedziczyć — próba sklejenia
przez composition (`RefreshTokenValidityInHours(TokenValidityInHours)`) tworzy
train wrecki `new X(new Y(int))` i `a.x().y().value()`.

## Szablon: pojedynczy record

```java
public record FailureWindowMinutes(int value) {
    public static final int MIN = 3;
    public static final int MAX = 120;
    public static final FailureWindowMinutes DEFAULT = new FailureWindowMinutes(15);

    public FailureWindowMinutes {
        if (value < MIN || value > MAX)
            throw new IllegalArgumentException("Accepts values only from range " + MIN + "-" + MAX);
    }
}
```

Zasady:
- `MIN`, `MAX`, `DEFAULT` jako `public static final` w VO (single source of truth).
- `DEFAULT` to **instancja VO**, nie `int` — builder/caller dostaje gotowy obiekt.
- Walidacja w kompaktowym konstruktorze; rzucamy `IllegalArgumentException`.
- Pakiet `.../vo/` obok klas Config używających VO.

## Szablon: hierarchia abstract class

Gdy dwa lub więcej typów ma dzielić walidację i pole:

```java
public abstract class AbstractTokenValidityInHours {
    public static final int MIN = 1;

    private final int value;

    protected AbstractTokenValidityInHours(int value) {
        if (value < MIN) throw new IllegalArgumentException("TokenValidityInHours must be >= " + MIN);
        this.value = value;
    }

    public int value() { return value; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof AbstractTokenValidityInHours other)) return false;
        return value == other.value;
    }

    @Override
    public int hashCode() { return Objects.hash(value); }

    @Override
    public String toString() { return String.valueOf(value); }
}

public final class RefreshTokenValidityInHours extends AbstractTokenValidityInHours {
    public RefreshTokenValidityInHours(int value) { super(value); }
}

public final class AccessTokenValidityInHours extends AbstractTokenValidityInHours {
    public AccessTokenValidityInHours(int value) { super(value); }
}
```

Zasady:
- Baza abstract, subclasses `final`. Subclass to 3 linie: konstruktor + ewentualne
  static factories.
- Pole `private final` w bazie, walidacja w `protected` konstruktorze bazy.
- `equals`/`hashCode`/`toString` w bazie — `instanceof AbstractXxx`, porównanie po
  polu (nie po typie konkretnym!). `Refresh(1).equals(Access(1)) == true` jest
  akceptowalne, bo semantycznie obie reprezentują tę samą wartość godzinową;
  rozróżnienie daje typ statyczny w sygnaturach, nie `equals`.
- Static factories (`random()`, `validInHours(...)`) w subclasses, ze wspólnym
  `protected static` helperem w bazie, jeśli logika się powtarza.
- **Publiczne fabryki w subclass przyjmują typ konkretny, nie abstrakcyjny** —
  nawet gdyby abstract class wystarczała technicznie. Przykład:
  `RefreshTokenExpiration.validInHours(RefreshTokenValidityInHours, Clock)`,
  nie `(AbstractTokenValidityInHours, Clock)`. Kompilator blokuje pomyłkę
  typów (`AccessValidity` dla `RefreshExpiration`), autouzupełnianie filtruje
  argumenty, sygnatura dokumentuje się sama. Wewnętrzny helper w bazie
  (`plusHours(AbstractTokenValidityInHours, Clock)`) pozostaje liberalny — tam
  type-safety nie ma znaczenia, bo kontrakt jest wewnętrzny.

Dlaczego nie `sealed interface` + records:
- records nie mogą mieć `protected` konstruktora ani pól — walidacja i pole
  musiałyby się powtarzać w każdym rekordzie.
- `equals`/`hashCode` recordów porównują typ konkretny, co dla VO-wrappera
  (gdzie subclass niczego nie dodaje poza rozróżnieniem typu) tworzy pułapki.
- Abstract class to prostszy pattern który działa od Javy 1.0.

## Kiedy `MAX` nie ma sensu

Nie każda wartość ma górną granicę. Wymuszanie `MAX` „na siłę" to magic number
udający regułę domenową.

Pomijamy `MAX`, gdy:
- Domena nie ogranicza (np. liczba iteracji algorytmu, liczba wywołań w logu).
- Ograniczenie techniczne to `Integer.MAX_VALUE`/`Long.MAX_VALUE` i tyle.
- Górna granica to polityczna decyzja kontekstu (prod vs. test), nie właściwość pola.

## Train wrecki — jak je rozpoznać

Dwa objawy tego samego smella:
- **Konstrukcja:** `new X(new Y(new Z(v)))` — zagnieżdżone opakowania.
- **Odczyt:** `a.x().y().value()` — łańcuch getterów przez warstwy VO.

Przyczyna: VO-w-VO robione przez composition zamiast dziedziczenia. Dwa typy,
które mają dzielić semantykę (oba są „token validity", oba są „token", oba są
„token expiration"), są sklejone composition zamiast abstract class.

**Remedy:** podmiana composition → hierarchia abstract class (patrz szablon wyżej).
Po refaktorze:
- `new RefreshTokenValidityInHours(24)` zamiast `new RefreshTokenValidityInHours(new TokenValidityInHours(24))`
- `config.refreshTokenValidityInHours().value()` zamiast `config.refreshTokenValidityInHours().tokenValidityInHours().value()`

## Cross-field validation

Walidacja **jednego pola** → w VO.
Walidacja **relacji między polami** → w konstruktorze rekordu-kontenera (Config).

```java
public record BruteForceConfig(
        FailureWindowMinutes failureWindowMinutes,
        MaxFailures maxFailures,
        MinBlockMinutes minBlockMinutes,
        MaxBlockMinutes maxBlockMinutes) {

    public BruteForceConfig {
        if (maxBlockMinutes.value() < minBlockMinutes.value())
            throw new IllegalArgumentException("maxBlockMinutes must be >= minBlockMinutes");
    }
}
```

## Builder z VO

Settery biorą `int` (user-friendly API), pola trzymają VO, DEFAULT z VO.

```java
public static class Builder {
    private FailureWindowMinutes failureWindowMinutes = FailureWindowMinutes.DEFAULT;
    private MaxFailures maxFailures = MaxFailures.DEFAULT;

    public Builder failureWindowMinutes(int failureWindowMinutes) {
        this.failureWindowMinutes = new FailureWindowMinutes(failureWindowMinutes);
        return this;
    }

    public BruteForceConfig build() {
        return new BruteForceConfig(failureWindowMinutes, maxFailures, ...);
    }
}
```

- Żadnych magic numbers w builderze — `XXX.DEFAULT` odpowiada za „co, gdy nie ustawiono".
- Wrap w VO w setterach → walidacja odpala się jak najwcześniej, nie dopiero w `build()`.

## Propagacja do domeny

Gdy config ma VO, ale metody domenowe dalej biorą `int` — to pół roboty.
Zmieniamy sygnatury, żeby typ chronił przed pomyłką:

```java
// przed:
public static SessionTokens createFor(Email email, int refreshHours, int accessHours, Clock clock)

// po:
public static SessionTokens createFor(Email email, SessionTokensConfig config, Clock clock)
```

Znikają `config.xxx().value()` w call sites → VO rzeczywiście „żyje".

Miejsca do zrobienia oznaczamy komentarzem `//todo primitive obsession.`.

## Testy VO

Jeden test property-based per VO (jqwik). Dwie metody: invalid + valid.

```java
@Epic("Security")
@Feature("Security Configuration - BruteForceConfig")
class FailureWindowMinutesTest {

    @Story("Failure Window Minutes Configuration")
    @Property
    @Label("Invariant - rejects")
    void invariantsRejects(@ForAll("invalidValues") Tuple.Tuple2<String, Integer> boundary) {
        Allure.parameter(boundary.get1(), boundary.get2());
        assertThrows(IllegalArgumentException.class, () -> new FailureWindowMinutes(boundary.get2()));
    }

    @Provide
    Arbitrary<Tuple.Tuple2<String, Integer>> invalidValues() {
        return Arbitraries.of(
                Tuple.of("Min int", Integer.MIN_VALUE),
                Tuple.of("MIN - 1", FailureWindowMinutes.MIN - 1),
                Tuple.of("MAX + 1", FailureWindowMinutes.MAX + 1),
                Tuple.of("Max int", Integer.MAX_VALUE)
        );
    }

    @Story("Failure Window Minutes Configuration")
    @Property
    @Label("Invariant - accepts")
    void invariantsAccept(@ForAll("validValues") Tuple.Tuple2<String, Integer> boundary) {
        Allure.parameter(boundary.get1(), boundary.get2());
        assertDoesNotThrow(() -> new FailureWindowMinutes(boundary.get2()));
    }

    @Provide
    Arbitrary<Tuple.Tuple2<String, Integer>> validValues() {
        Random random = new Random();
        return Arbitraries.of(
                Tuple.of("MIN", FailureWindowMinutes.MIN),
                Tuple.of("between", random.nextInt(FailureWindowMinutes.MIN + 1, FailureWindowMinutes.MAX)),
                Tuple.of("MAX", FailureWindowMinutes.MAX)
        );
    }
}
```

Dla VO bez `MAX` — pomijamy przypadki `"MAX + 1"`, `"Max int"`, `"MAX"`,
`"between"` zamienia się na jedną losową wartość `>= MIN`.

Dla hierarchii abstract class testujemy każdy konkretny typ osobno (walidacja
jest dziedziczona, ale test trzyma się typu używanego w call sites).

Testy walidacji self-field w Config zbędne — są pokryte na poziomie VO.
W Config testujemy tylko cross-field + defaulty builder'a.

## Checklist

- [ ] Jeden typ bez rodzeństwa → `record`; hierarchia → abstract class + `final` subclasses.
- [ ] Walidacja self-field w kompaktowym konstruktorze (record) lub `protected` konstruktorze bazy (abstract).
- [ ] `MIN` (+ `MAX`, jeśli sensowny) + `DEFAULT` jako instancja VO.
- [ ] Config trzyma VO zamiast prymitywów; cross-field w konstruktorze Config.
- [ ] Builder: pola z `XXX.DEFAULT`, settery przyjmują `int`, wrapują w VO.
- [ ] Wszystkie metody domenowe biorą VO (usunięte `.value()` w call sites).
- [ ] Żadnego train wrecka (`new X(new Y(...))`, `a.x().y().value()`) — jeśli się pojawia, rozbij przez abstract class.
- [ ] Test VO (invalid + valid).
- [ ] Test Config tylko na cross-field + defaulty, bez dublowania walidacji VO.
- [ ] Miejsca do zrobienia później oznaczone `//todo primitive obsession.`.
