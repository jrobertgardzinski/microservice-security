// UI glue for authenticate.feature. Failed attempts are produced by really submitting the form;
// the passage of time by the test clock (the same backdoor the JVM glue POSTs to). Seeding a
// verified user reads the "mailed" token from the test mailbox — the out-of-process twin of
// reading the capturing notifier bean.

import { Given, Then, When } from '@cucumber/cucumber';
import { expect } from 'playwright/test';

const WRONG_PASSWORD = 'WrongButStrongPassword1!';
const UNKNOWN_EMAIL = 'other@example.com';

let credentials = { email: '', password: '' };
let policy = { maxFailures: 3, maxBlockMinutes: 10 };

async function signInThroughUi(page, email, password) {
  await page.getByTestId('tab-signin').click();
  await page.getByTestId('email').fill(email);
  await page.getByTestId('password').fill(password);
  await page.getByTestId('submit').click();
}

async function failToAuthenticate(page, times) {
  for (let i = 0; i < times; i++) {
    await signInThroughUi(page, credentials.email, WRONG_PASSWORD);
    await expect(page.getByTestId('notice')).toContainText('Wrong e-mail or password');
  }
}

Given('a registered USER {string} with password {string}', async function (email, password) {
  credentials = { email, password };
  await this.page.getByTestId('tab-signup').click();
  await this.page.getByTestId('email').fill(email);
  await this.page.getByTestId('password').fill(password);
  await this.page.getByTestId('submit').click();
  await expect(this.page.getByTestId('inbox-screen')).toBeVisible();
  await this.page.getByTestId('back-to-signin').click();
  // "registered" implies completed onboarding: confirm the mailed token (a repeat scenario finds
  // the token already consumed and the address already verified — both are fine)
  const token = await this.verificationTokenFor(email);
  if (token) {
    await this.backdoor('/verify-email', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ token }),
    });
  }
});

Given('a registered USER {string} with password {string} whose EMAIL is not verified yet',
  async function (email, password) {
    credentials = { email, password };
    await this.page.getByTestId('tab-signup').click();
    await this.page.getByTestId('email').fill(email);
    await this.page.getByTestId('password').fill(password);
    await this.page.getByTestId('submit').click();
    await expect(this.page.getByTestId('inbox-screen')).toBeVisible();
    await this.page.getByTestId('back-to-signin').click();
  });

Given('AUTHENTICATION attempts from one source are limited by this policy:', function (table) {
  // informative, exactly like the JVM glue: the service runs its defaults; the table tells the
  // glue how many attempts trip the limit and how long a block can last
  for (const [key, value] of table.raw()) {
    const numbers = [...value.matchAll(/\d+/g)].map((m) => Number(m[0]));
    if (key.includes('failed attempts')) policy.maxFailures = numbers[0];
    else if (key.includes('block lasts')) policy.maxBlockMinutes = numbers[1];
  }
});

Given('the USER has reached the failure limit', async function () {
  await failToAuthenticate(this.page, policy.maxFailures);
});

Given('the USER has failed to AUTHENTICATE but stayed under the limit', async function () {
  await failToAuthenticate(this.page, policy.maxFailures - 1);
});

Given('the source is blocked', async function () {
  await failToAuthenticate(this.page, policy.maxFailures);
  // the limit-reaching attempt trips and creates an active block
  await signInThroughUi(this.page, credentials.email, credentials.password);
  await expect(this.page.getByTestId('notice')).toContainText('blocked');
});

When('the USER AUTHENTICATES with the correct CREDENTIALS', async function () {
  await signInThroughUi(this.page, credentials.email, credentials.password);
});

When(/^the USER tries to AUTHENTICATE with (.+)$/, async function (wrongCredentials) {
  const attempts = {
    'the wrong password': [credentials.email, WRONG_PASSWORD],
    'an unknown email': [UNKNOWN_EMAIL, credentials.password],
    'a wrong email and password': [UNKNOWN_EMAIL, WRONG_PASSWORD],
  };
  const attempt = attempts[wrongCredentials];
  if (!attempt) throw new Error(`Unknown credentials case: ${wrongCredentials}`);
  await signInThroughUi(this.page, attempt[0], attempt[1]);
});

When('{int} minutes passes', async function (minutes) {
  await this.advanceClockMinutes(minutes);
});

When('the block expires', async function () {
  await this.advanceClockMinutes(policy.maxBlockMinutes);
});

Then('the USER is AUTHENTICATED', async function () {
  await expect(this.page.getByTestId('signed-in-email')).toHaveText(credentials.email);
  await this.page.getByTestId('sign-out').click();
});

Then('the AUTHENTICATION is rejected', async function () {
  await expect(this.page.getByTestId('notice')).toContainText('Wrong e-mail or password');
});

Then('the AUTHENTICATION is rejected because the source is blocked', async function () {
  await expect(this.page.getByTestId('notice')).toContainText('blocked');
});

Then('the AUTHENTICATION is rejected because the EMAIL is not verified', async function () {
  await expect(this.page.getByTestId('notice')).toContainText('not verified');
});
