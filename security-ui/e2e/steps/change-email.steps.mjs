// UI glue for change-email.feature: the signed-in account screen requests the change, the
// confirmation link (mailed to the NEW address) lands as ?change=<token>, and ownership of the
// account follows the address — proven by signing in as the new one and failing as the old.
// The quiet anti-enumeration rule reads exactly like a fresh request; the truth (the notice to
// the taken address's owner) is read from the mailbox backdoor.

import { Given, Then, When } from '@cucumber/cucumber';
import { expect } from 'playwright/test';
import { UI } from '../support/world.mjs';
import { credentials, resolveEmail } from '../support/account.mjs';

let requestedNewEmail = '';
let freshRequestNotice = 'Check the new address — we sent a confirmation link.';

Given('another ACCOUNT already holds {string}', async function (takenEmail) {
  // a secondary actor is setup, not the act under test — seeded over the wire
  const r = await this.backdoor('/register', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ email: takenEmail, password: 'StrongPassword1!' }),
  });
  if (r.status !== 201) throw new Error(`could not seed the occupying account: ${r.status}`);
});

When('the USER requests to CHANGE the EMAIL to {string}', async function (newEmail) {
  requestedNewEmail = newEmail;
  await this.page.getByTestId('new-email').fill(newEmail);
  await this.page.getByTestId('change-email-submit').click();
  await expect(this.page.getByTestId('notice')).toBeVisible();
});

When('the USER CONFIRMS the EMAIL CHANGE with the token from the link', async function () {
  const response = await this.backdoor(
    `/test/mailbox/verification-token?email=${encodeURIComponent(requestedNewEmail)}`);
  if (!response.ok) throw new Error(`no change link was e-mailed to ${requestedNewEmail}`);
  const { token } = await response.json();
  await this.page.goto(`${UI}/?change=${encodeURIComponent(token)}`);
});

When('the USER CONFIRMS the EMAIL CHANGE with a garbage token', async function () {
  await this.page.goto(`${UI}/?change=garbage-token`);
});

Then('the USER can AUTHENTICATE as {string}', async function (asEmail) {
  await expect(this.page.getByTestId('notice')).toContainText('E-mail changed');
  await this.page.getByTestId('email').fill(resolveEmail(asEmail));
  await this.page.getByTestId('password').fill(credentials.password);
  await this.page.getByTestId('submit').click();
  await expect(this.page.getByTestId('signed-in-email')).toHaveText(resolveEmail(asEmail));
  await this.page.getByTestId('sign-out').click();
});

Then('the USER cannot AUTHENTICATE as {string}', async function (asEmail) {
  await this.page.getByTestId('tab-signin').click();
  await this.page.getByTestId('email').fill(resolveEmail(asEmail));
  await this.page.getByTestId('password').fill(credentials.password);
  await this.page.getByTestId('submit').click();
  await expect(this.page.getByTestId('notice')).toContainText('Wrong e-mail or password');
});

Then('the EMAIL CHANGE is rejected', async function () {
  await expect(this.page.getByTestId('notice')).toContainText('already used or has expired');
});

Then('the CHANGE request is quietly refused, indistinguishable from a fresh one', async function () {
  await expect(this.page.getByTestId('notice')).toHaveText(freshRequestNotice);
});

Then('the owner of {string} is notified by mail', async function (takenEmail) {
  const r = await this.backdoor(`/test/mailbox/notice?email=${encodeURIComponent(takenEmail)}`);
  if (!r.ok) throw new Error(`the taken address's owner was not notified (${r.status})`);
});
