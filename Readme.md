# microservice-security

> 👋 **Welcome, and thanks for scanning!** You've reached a live slice of my work — a small
> security microservice that registers users, signs them in, and keeps their sessions alive.
> If you have two minutes, the three specs below tell the whole story in plain English; no Java needed.

**[Find me on LinkedIn](https://www.linkedin.com/in/robert-gardzi%C5%84ski-26559a188/)**

**[Watch the project report (video walkthrough)](https://youtu.be/_sHEI4u_p5c?si=3Dy1_rCpWyUQRnU3)**

## What it does — three specs, in plain English

These are the project's **executable** specifications (Gherkin): the test suite (Cucumber) runs them
when you build the project, so the behaviour they describe is verified, not aspirational. By design the
*same* scenarios are meant to be driven from every entry-point layer — **application**,
**infrastructure** and **UI** (same behaviour, a different way in); today they run from the application
layer. Each one reads like a short story:

- **[Registration](./security-application/src/test/resources/com/jrobertgardzinski/security/application/register.feature)**
  — a new user signs up with an email and a password; sign-up is refused when either is invalid,
  and the answer says *which* one.
- **[Authentication](./security-application/src/test/resources/com/jrobertgardzinski/security/application/authenticate.feature)**
  — correct credentials sign the user in; repeated failures from the same source temporarily
  lock it, to stop password guessing.
- **[Refreshing a session](./security-application/src/test/resources/com/jrobertgardzinski/security/application/refresh-session.feature)**
  — a user keeps a session alive by refreshing it; an expired or missing session can't be refreshed.

**[For more detailed documentation, click here](./Documentation.md)**. It's a document generated from allure reports based on unit tests. It covers the lowest layers: **domain**, **config** and **system**

---

> **Project scope — what holds today.** The **Domain → Config → System** core is fully tested and
> self-documenting. The three specs above already run at the **Application** layer. **Infrastructure**
> and **UI** are designed-in but not built yet. The diagram below is the full target shape; the
> executable specs are what's actually proven — nothing here is hand-waved.

---

## Architecture — six layers, dependencies pointing down

A security microservice (registration, authentication, password hashing) built with
**Domain-Driven Design** and **Hexagonal Architecture**. Every layer may use the ones below it,
never the ones above:

```
UI  →  Infrastructure  →  Application  →  System  →  Config  →  Domain
```

### The three top layers — one behaviour, many doors

UI, Infrastructure and Application share the *same* BDD scenarios, but each implements them its
own way:

- **UI** — drives user interactions (fill a form, click).
- **Infrastructure** — expects data over the network.
- **Application** — translates between the outside world and the domain.

*Same behaviour, different entry point. The domain doesn't care how you reach it.*

### The core — proven, not promised

Proven by executable specifications (**jqwik** + **Allure**); concepts explained in Javadoc.

- **System** — composes Config + Domain into use cases.
- **Config** — domain rules made configurable. Where a rule's value lives decides how fast a change
  takes effect: hardcoded needs a new release, properties (or program arguments) need a restart,
  persisted config takes effect instantly.
- **Domain** — self-validating value objects.

> **The rules are the tests, and the tests are the documentation — so it cannot drift.**

### Two payoffs of clean boundaries

- **Microservice or monolith — your choice.** This module is five layers, Domain → Infrastructure.
  Making the boundaries explicit keeps the deployment shape — split into services or kept as one —
  an open decision.
- **Reusable as a library.** A library spans Domain → System, and its System and Config layers can be
  specialized per consumer. An email library, for instance, can ship strict System/Config for this
  security microservice, and separate, relaxed ones for other services (newsletters, notifications,
  RSS feeds).

This repository is part of a larger security of reusable modules — see my other repositories:
**https://github.com/jrobertgardzinski?tab=repositories**

---

## Requirements

| Tool  | Version    | Notes                                                                          |
| ----- | ---------- | ------------------------------------------------------------------------------ |
| Git   | any recent | to clone the repositories                                                      |
| JDK   | **25**     | the project compiles against Java 25 (`maven.compiler.release=25`)             |
| Maven | —          | **not required** — every repository bundles a Maven Wrapper (`mvnw` / `mvnw.cmd`) that downloads the right Maven version automatically |

Check what you already have:

```bash
git --version
java -version
```

<details>
<summary><strong>Don't have Java 25 yet? Quick install per platform</strong></summary>

**Linux / macOS — via [SDKMAN!](https://sdkman.io) (recommended):**

```bash
curl -s "https://get.sdkman.io" | bash
source "$HOME/.sdkman/bin/sdkman-init.sh"
sdk install java 25-tem      # Temurin JDK 25
```

**macOS — via Homebrew:**

```bash
brew install openjdk@25 git
```

**Windows — via [winget](https://learn.microsoft.com/windows/package-manager/) (built into Windows 10/11):**

```powershell
winget install EclipseAdoptium.Temurin.25.JDK
winget install Git.Git
```

After installing, **open a new terminal** so the updated `PATH` is picked up.

</details>

---

## Clone & build the whole project

This microservice depends on a few sibling modules that live in separate repositories
(`test-starter`, `libs`, `config`, `email`, `password`). The commands below clone all of them
into one workspace folder and build them in the correct order via the bundled **Maven Wrapper**
(`./mvnw`), installing each into your local Maven repository (`~/.m2`) so the final build can
resolve them. No system-wide Maven needed — the wrapper fetches the right version on first run.

### 🐧 Linux &nbsp;/&nbsp; 🍎 macOS &nbsp;(bash / zsh)

```bash
mkdir security && cd security

for repo in test-starter libs config email password microservice-security; do
  git clone "https://github.com/jrobertgardzinski/$repo.git"
done

for dir in test-starter libs config email password microservice-security; do
  ( cd "$dir" && ./mvnw clean install ) || break
done
```

### 🪟 Windows &nbsp;(PowerShell)

```powershell
mkdir security; cd security

foreach ($repo in 'test-starter','libs','config','email','password','microservice-security') {
  git clone "https://github.com/jrobertgardzinski/$repo.git"
}

foreach ($dir in 'test-starter','libs','config','email','password','microservice-security') {
  Push-Location $dir
  .\mvnw.cmd clean install
  if ($LASTEXITCODE -ne 0) { Pop-Location; break }
  Pop-Location
}
```

When the last build finishes you have the whole project compiled and all of its tests
(the living specification) executed locally.

> 💡 **Faster build, skipping tests:** append `-DskipTests` to the wrapper command (e.g.
> `./mvnw clean install -DskipTests`). The tests run in just a few seconds, though — and they
> *are* the documentation — so running them is worth it.

---

## Questions or problems?

Stuck on the build, or just want to talk through the design? **Feel free to reach out — I'm keen to help!** 🙂
Easiest via **[LinkedIn](https://www.linkedin.com/in/robert-gardzi%C5%84ski-26559a188/)**.