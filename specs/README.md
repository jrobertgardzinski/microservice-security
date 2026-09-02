# specs — one Gherkin, three entry points

Every `.feature` in this directory is the single source of truth for one use case. The same file
is driven through up to three layers, each with its own step definitions ("glue") and nothing
copied:

| layer | runner | selects by | glue |
|---|---|---|---|
| **application** (use-case objects, no HTTP) | `security-application` → `RunCucumberTest` | file name, listed in the suite | `…security.application.feature.*` |
| **infrastructure** (Micronaut HTTP) | `security-infrastructure` → one `RunHttp*Test` per file | file name, one suite per feature | `…security.infrastructure.feature.*` |
| **UI** (React + Playwright) | `security-ui` → `cucumber-js --config e2e/cucumber.mjs` | **tag**: `@ui and not @http-only` | `security-ui/e2e/steps/*.mjs` |

The JVM runners read this directory as a test resource (the `add-shared-specs` execution of
`build-helper-maven-plugin`); cucumber-js reads `../specs/*.feature` straight from disk.

## Tags are asymmetric

The JVM runners filter only on `not @wip`. A tag never excludes a file from them: they run exactly
the files their `@SelectClasspathResource` names.

- `@ui` — the feature ALSO runs in the browser. Put it on a feature a page exists for.
- `@http-only` — the feature is wire-level (cookie rotation, introspection, the OAuth dance, an
  admin's lever) and the browser does it implicitly or not at all. cucumber-js skips it.

So `register.feature` (`@ui`) runs in all three layers; `password-policy.feature` (`@http-only`)
runs in the infrastructure layer alone until a page reads the policy.

## Literals are samples of the REBUILD rung

Values in these files are literals, not placeholders. `user@example.com` and `StrongPassword1!`
mean "an email and a password that pass the DEFAULT policy" — the rebuild rung of the
configuration ladder, the one that changes only with a release. The specs live in the repo and are
versioned with the code, so they move at the same cadence as the defaults: if a release changes
`MinLength.DEFAULT`, these files are SUPPOSED to go red, because they document the defaults.

Two consequences:

- **A value that carries the rule stays a literal, with a comment saying why.** `Nine1!aaa` is
  nine characters because the scenario set the minimum to ten; `a..b@gmail.com` has two dots
  because two dots are the rule under test. Replacing either with "an invalid password" would hide
  which rule the scenario proves.
- **A scenario that moves the LIVE rung owns its own samples.** `password-policy.feature` raises
  the minimum to ten and relies on the Background accounts having sixteen-character passwords.
  A scenario raising it above what the reference literals satisfy must register its own users or
  change the number — visibly, in the same file.

Property-based tests one level down (jqwik, `@ForAll("accepted")` / `@ForAll("rejected")` in
`password` and `security-config`) express the same accepted/rejected split for a single value
object. Here the split is written as `accepted` / `invalid` in an Examples table; the input stays
concrete.

## Running one feature

```bash
# from the repo root — the runner class, not the file
./mvnw -pl security-infrastructure -am test -Dtest=RunHttpPasswordPolicyTest -Dsurefire.failIfNoSpecifiedTests=false
./mvnw -pl security-application  -am test -Dtest=RunCucumberTest            -Dsurefire.failIfNoSpecifiedTests=false

# the browser layer, against a running stack
cd security-ui && npm run e2e
```

In IntelliJ, run the `Run*Test` suite class from the gutter; it is a plain JUnit Platform suite.
If the feature resource is not found, reload the Maven project once so the IDE picks up the
`specs/` test resource. HTML reports land in each module's `target/` (`report.html`,
`report-http-*.html`).
