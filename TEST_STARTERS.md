# Test Starter Pattern

How shared test dependencies are organized across this portfolio, and why.

## The problem

Without starters, every Maven module that runs tests needs to declare:

- a JUnit Platform engine and Jupiter API
- a parameterized-test extension
- Mockito + the Jupiter integration
- AssertJ or Hamcrest
- jqwik (for property-based tests in domain modules)
- Cucumber + a JUnit Platform Suite + a DI container (for BDD modules)
- the matching Allure adapter for whichever runner is in use

That is 8–12 dependencies, repeated across ~12 modules, with versions that must
stay aligned. A bump of JUnit, Mockito, or Cucumber means editing every POM.
Versions silently drift. Onboarding a new module is copy-paste.

## The pattern

A small set of "starter" modules under `test-starter/` packages those
dependencies into reusable bundles. A consuming module declares **one**
dependency in `<scope>test</scope>` and inherits everything transitively.

```
test-starter/
├── test-starter-common      # JUnit 5 + Mockito + Hamcrest + AssertJ
├── unit-test-starter        # common + jqwik + allure-junit5
├── bdd-test-starter         # common + Cucumber + JUnit Platform Suite + allure-cucumber7-jvm
└── system-test-starter      # bdd + unit (modules that mix Cucumber and JUnit)
```

Each starter is a Maven module with `packaging=jar` and `<dependencies>` only —
no Java code (yet; ArchUnit rules will eventually live in `unit-test-starter`
as a test-jar).

This is the same pattern Spring Boot uses for `spring-boot-starter-*`, applied
locally to test infrastructure.

## Per-layer mapping

| Layer | Starter | Reason |
|---|---|---|
| `*-domain` | `unit-test-starter` | Pure unit + property-based tests on value objects |
| `*-config` | `unit-test-starter` | Boundary tests on config invariants — JUnit + jqwik |
| `*-system` | `bdd-test-starter` or `system-test-starter` | Behavior in feature files; pick `system-` if module also keeps JUnit-based rules |
| `*-application` | `bdd-test-starter` | Use cases described as Cucumber scenarios |
| `*-infrastructure` | `bdd-test-starter` | End-to-end feature coverage at the wiring layer |

Picking the wrong starter is harmless for compilation, but it pollutes the
classpath (e.g. dragging Cucumber into a domain module that has no `.feature`
files) and muddies the intent the POM communicates.

## Consumer usage

A module simply does:

```xml
<dependency>
    <groupId>com.jrobertgardzinski</groupId>
    <artifactId>unit-test-starter</artifactId>
    <scope>test</scope>
</dependency>
```

`<scope>test</scope>` is mandatory and intentional — it stops JUnit, Mockito,
and friends from leaking into the compile classpath of dependent modules.

The starter version is managed in a parent POM's `<dependencyManagement>`, so
the consumer omits `<version>`.

## Composition over a single fat starter

`system-test-starter` does not redefine its dependencies — it depends on both
`bdd-test-starter` and `unit-test-starter`. A consumer could import those two
directly, but the `system-test-starter` name is a label: the POM line
"`system-test-starter`" tells a reader *this is a system-layer module*,
without them having to inspect the dependencies.

This is why composition is preferred over one large opinionated starter:

- New layers can compose existing starters without growing the base
- Module type is visible at a glance from its POM
- Each starter stays small enough to reason about

## Transitive conflicts and exclusions

A non-trivial starter pulls in components that overlap with each other. Two
known conflicts in this portfolio:

**1. `unit-test-starter` excludes `junit-platform-engine` and
`junit-platform-commons` from jqwik:**

```xml
<dependency>
    <groupId>net.jqwik</groupId>
    <artifactId>jqwik</artifactId>
    <exclusions>
        <exclusion>
            <groupId>org.junit.platform</groupId>
            <artifactId>junit-platform-engine</artifactId>
        </exclusion>
        <exclusion>
            <groupId>org.junit.platform</groupId>
            <artifactId>junit-platform-commons</artifactId>
        </exclusion>
    </exclusions>
</dependency>
```

jqwik bundles its own JUnit Platform components at potentially older versions.
The `junit-bom` aligns versions globally; excluding the duplicates lets that
alignment win.

**2. `system-test-starter` excludes `gherkin` from `allure-cucumber7-jvm`:**

`bdd-test-starter` already pulls in `gherkin` directly (a single managed
version). Allure's Cucumber adapter ships its own copy. Excluding the
adapter's copy avoids two `gherkin` jars on the classpath.

**Spotting these conflicts:**

```bash
mvn dependency:tree -Dverbose
```

`(omitted for conflict)` and `(omitted for duplicate)` in the output flag
candidates for `<exclusion>`. Symptoms in CI without an exclusion: confusing
`NoSuchMethodError`, "Multiple JUnit Platform engines found", or duplicate
class warnings.

## What starters do not solve

Starters bundle dependencies. They do not solve:

- **Shared test code** (base classes, contract tests, fixtures) — that is the
  job of the `maven-jar-plugin`'s `test-jar` goal. A module exposes its
  `src/test/java` classes as a separate artifact (`<type>test-jar</type>`),
  which other modules consume in `<scope>test</scope>`. This keeps test
  utilities out of `src/main/java` (no JUnit leaking into compile scope).

- **Architectural rules** (e.g. ArchUnit). These belong in a base abstract
  test class shipped from a starter as a test-jar; subclasses in each module
  add `@AnalyzeClasses(packagesOf = SomeDomainClass.class)`. Hardcoded
  package strings would couple the rules to the consumer's package layout.

- **Version coordination across the whole portfolio.** A starter narrows
  *which* dependencies enter a module, but the starter itself still needs its
  versions managed somewhere. A dedicated `test-starter-bom` (Maven BOM
  module) is the natural next step — it removes the duplicated `junit-bom`,
  `mockito-bom`, `cucumber-bom`, and `assertj-bom` declarations currently
  copied across the parent POMs of `email/`, `password/`, and
  `microservice-security/`.

## Trade-offs

Honest list of what you give up by adopting this pattern:

- **Opinionated by default.** A starter that includes Allure forces every
  consumer into Allure. Opting out requires `<exclusions>`. Acceptable
  while the whole portfolio shares one reporting stack.
- **Hidden dependencies.** Consumers don't see what the starter pulls in.
  Less risky for `<scope>test</scope>` than for compile dependencies, but
  worth `mvn dependency:tree` checks after every starter change.
- **Two places change together.** A starter version lives in
  `<dependencyManagement>` of every parent that uses it. Adding a new top-
  level project means another duplication. A `test-starter-bom` removes
  this; its absence is a known gap.
- **Exclusion debt.** Every exclusion in a starter is a piece of
  hidden knowledge ("we discovered the hard way that X conflicts with Y").
  Worth a comment on the exclusion in the POM when added.

## Alternatives considered

- **Plain BOM (versions only).** Each consumer still lists 8–12
  dependencies. Less duplication of versions, none of the "one line in the
  POM" benefit, none of the layer-as-label communication.
- **Inheritance via `<dependencies>` in a parent POM.** Maximally DRY, but
  every child gets every dependency. A domain module with no Cucumber would
  still pull Cucumber. No opt-out without rearranging the inheritance tree.
- **Starter (this choice).** Middle ground: opt-in by name, version managed
  in one place per parent, layer intent visible.

## Maintenance notes

- When adding a dependency to a starter, run `mvn dependency:tree -Dverbose`
  on a representative consumer and check for new `(omitted ...)` lines.
- When bumping a major version (JUnit 5 → 6, Cucumber 7 → 8), expect
  exclusions to need revisiting; the conflict surface changes.
- Keep starters narrow. If a single consumer needs a one-off library
  (e.g. Testcontainers in only one infrastructure module), declare it in
  that module's POM — do not push it into a starter.
- Allure adapter versions must match across `unit-test-starter` and
  `bdd-test-starter`. Today both are pinned to `2.34.0`. A drift here
  (e.g. `email/pom.xml` overriding `allure-junit5` to `2.27.0`) defeats the
  point of the pattern.
