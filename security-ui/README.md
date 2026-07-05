# security-ui

The auth service's own face — and, more importantly, **the specs' third entry point.**

`register.feature` and `authenticate.feature` live in the neutral top-level `specs/` directory
precisely so more than one runner can drive them. Two already do (the application-level Cucumber
glue and the HTTP-level glue, both JVM). This module adds a third: **cucumber-js + Playwright**
driving the *same Gherkin* through a real Angular UI in a real browser — same behaviour, a third
entry point.

## The app

A deliberately plain Angular standalone app: sign in, create an account, the "check your mailbox"
screen, confirming a mailed verification link (`?verify=<token>`), and `/me`. Every element a
scenario touches carries a `data-testid`, so the glue speaks the UI's language, not the DOM's.

```bash
npm install
npm start          # ng serve on :4200, talks to the stack's security at :8080
npm run build
```

## The e2e run

```bash
npm run e2e:full   # or ./run-e2e.sh
```

`run-e2e.sh` starts security in the **`test` environment** (in-memory stores, a steerable clock,
a captured mailbox — the same environment the JVM tests use) on port **8180**, starts the Angular
dev server, and runs cucumber-js over `../specs/*.feature`. The glue reaches the two backdoors the
`test` environment exposes over HTTP — `/test/clock` (advance time to expire brute-force blocks)
and `/test/mailbox` (read the token a registration "mailed") — the out-of-process twins of what
the in-process JVM glue reads from beans. `npm run e2e` alone assumes both servers are already up.

If the UI and HTTP scenarios ever diverge, tag them `@http` / `@ui` and filter per runner (none do
yet — the point is that they don't have to).
