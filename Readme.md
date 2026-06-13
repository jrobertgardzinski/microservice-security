# microservice-security

📖 **[Read the full documentation & living test report →](./Documentation.md)**

---

> **Project scope.** The **Domain → Config → System** core is fully tested and
> self-documenting — that's the part this repository proves today. The **Application**,
> **Infrastructure** and **UI** layers are a deliberately planned extension of the *same* BDD
> specification: same behaviour, additional entry points. The architecture below describes the
> full target shape; the executable specs describe what already holds.

---

A security microservice (registration, authentication, password hashing) built with
**Domain-Driven Design** and **Hexagonal Architecture**. It is organized into six layers
with dependencies pointing strictly downward — every layer may use the ones below it,
never the ones above:

```
UI  →  Infrastructure  →  Application  →  System  →  Config  →  Domain
```

What makes it interesting:

- **The rules are the tests, and the tests are the documentation — so it cannot drift.**
  The domain is proven by executable specifications (property-based testing with **jqwik**,
  rendered as living docs with **Allure**); concepts are explained in Javadoc.
- **Same behaviour, different entry point.** UI, Infrastructure and Application share the
  same BDD scenarios but each implements its own way in (filling a form, receiving data over
  the network, translating the outside world into the domain). The domain doesn't care how you
  reach it.
- **Configurable rules.** The Config layer turns domain rules into configuration. Their source
  decides how quickly they take effect: hardcoded config needs a new release, properties (or
  program arguments) need a restart, persisted config takes effect instantly.
- **Microservice or monolith — your choice.** Making the layer boundaries explicit keeps the
  deployment shape (split into services or kept as one) an open decision.

This repository is part of a larger portfolio of reusable modules — see my other repositories:
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
mkdir portfolio && cd portfolio

for repo in test-starter libs config email password microservice-security; do
  git clone "https://github.com/jrobertgardzinski/$repo.git"
done

for dir in test-starter libs config email password microservice-security; do
  ( cd "$dir" && ./mvnw clean install ) || break
done
```

### 🪟 Windows &nbsp;(PowerShell)

```powershell
mkdir portfolio; cd portfolio

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
