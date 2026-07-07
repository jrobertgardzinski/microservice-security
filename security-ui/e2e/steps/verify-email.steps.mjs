// UI glue for verify-email.feature. The act under test is following the mailed link — the app
// reads `?verify=<token>` at boot and POSTs it — so the scenarios navigate exactly like a mail
// client would. Requesting a (re-)verification is setup, done over the same backdoor wire the
// JVM glue uses in-process. "Verified" is proven the way a user sees it: signing in works.

import { Given, Then, When } from '@cucumber/cucumber';
import { expect } from 'playwright/test';
import { UI } from '../support/world.mjs';
import { credentials } from './authenticate.steps.mjs';
import { signInCompletingMfa } from './mfa.steps.mjs';

Given('the USER requested EMAIL VERIFICATION', async function () {
  const response = await this.backdoor('/verify-email/request', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ email: credentials.email }),
  });
  if (response.status !== 202) throw new Error(`verification request failed: ${response.status}`);
});

When('the USER VERIFIES the EMAIL with the VERIFICATION TOKEN from the link', async function () {
  const token = await this.verificationTokenFor(credentials.email);
  if (!token) throw new Error('no verification token was e-mailed');
  await this.page.goto(`${UI}/?verify=${encodeURIComponent(token)}`);
});

When('the USER VERIFIES the EMAIL with a garbage VERIFICATION TOKEN', async function () {
  await this.page.goto(`${UI}/?verify=garbage-token`);
});

Then('the EMAIL is verified', async function () {
  await expect(this.page.getByTestId('notice')).toContainText('E-mail verified');
  // the proof a user cares about: signing in now works (completing MFA if the shared account
  // has enrolled a factor in an earlier scenario — accounts deliberately persist)
  await signInCompletingMfa(this);
  await this.page.getByTestId('sign-out').click();
});

Then('the VERIFICATION is rejected', async function () {
  await expect(this.page.getByTestId('notice')).toContainText('already used or replaced');
});

Then('a VERIFICATION link has been e-mailed to the USER', async function () {
  const token = await this.verificationTokenFor(credentials.email);
  if (!token) throw new Error('expected registration to e-mail a verification link automatically');
});
