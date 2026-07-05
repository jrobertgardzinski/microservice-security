// Shared world for the UI glue: one browser for the run, a fresh page per scenario, and the two
// backdoors every out-of-process harness needs — the SAME ones the JVM glue uses in-process:
// the captured mailbox (/test/mailbox) and the steerable clock (/test/clock), both present only
// under the `test` environment.
//
// Isolation between scenarios comes from time, not restarts: the After hook advances the clock
// past the brute-force failure window AND the longest block AND the registration-throttle window,
// so one scenario's failed attempts never bleed into the next — while accounts (deliberately)
// persist, which the quiet anti-enumeration register absorbs.

import { After, AfterAll, Before, BeforeAll, setDefaultTimeout, setWorldConstructor } from '@cucumber/cucumber';
import { chromium } from 'playwright';

export const UI = process.env.UI_URL ?? 'http://localhost:4200';
export const SECURITY = process.env.SECURITY_URL ?? 'http://localhost:8180';   // NOT 8080: the compose stack may hold it

setDefaultTimeout(30_000);

let browser;

class UiWorld {
  page = null;

  async open() {
    if (!this.page) {
      this.page = await browser.newPage();
      // the app reads window.SECURITY_URL at boot — point it at the harness's own service
      await this.page.addInitScript(`window.SECURITY_URL = ${JSON.stringify(SECURITY)};`);
    }
    await this.page.goto(UI);
    return this.page;
  }

  async backdoor(path, init) {
    return fetch(`${SECURITY}${path}`, init);
  }

  async verificationTokenFor(email) {
    const response = await this.backdoor(`/test/mailbox/verification-token?email=${encodeURIComponent(email)}`);
    if (!response.ok) return null;
    return (await response.json()).token;
  }

  async advanceClockMinutes(minutes) {
    const response = await this.backdoor('/test/clock/advance', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ duration: `PT${minutes}M` }),
    });
    if (!response.ok) throw new Error(`clock advance failed: ${response.status}`);
  }
}

setWorldConstructor(UiWorld);

BeforeAll(async () => {
  browser = await chromium.launch();
});

Before(async function () {
  await this.open();
});

After(async function () {
  // outlast the 15-minute failure window, any block (max 10 min) and the register throttle window
  await this.advanceClockMinutes(31);
  if (this.page) {
    await this.page.close();
    this.page = null;
  }
});

AfterAll(async () => {
  await browser?.close();
});
