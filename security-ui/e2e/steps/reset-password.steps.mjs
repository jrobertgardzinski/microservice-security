// UI glue for reset-password.feature. Requesting the reset is a real user act ("Forgot
// password?" on the sign-in screen); the mailed link lands as ?reset=<token>, exactly like a
// mail client would open it; the token itself is read from the test mailbox backdoor — the
// out-of-process twin of the JVM glue's capturing notifier bean.

import { Given, Then, When } from '@cucumber/cucumber';
import { expect } from 'playwright/test';
import { UI } from '../support/world.mjs';
import { credentials } from '../support/account.mjs';

async function resetTokenFor(world, email) {
  const response = await world.backdoor(`/test/mailbox/reset-token?email=${encodeURIComponent(email)}`);
  if (!response.ok) throw new Error(`no reset token was e-mailed to ${email}`);
  return (await response.json()).token;
}

async function typeNewPassword(world, newPassword) {
  await expect(world.page.getByTestId('reset-screen')).toBeVisible();
  await world.page.getByTestId('reset-password').fill(newPassword);
  await world.page.getByTestId('reset-submit').click();
}

Given('the USER requested a password RESET', async function () {
  await this.page.getByTestId('forgot-password').click();
  await this.page.getByTestId('forgot-email').fill(credentials.email);
  await this.page.getByTestId('forgot-submit').click();
  await expect(this.page.getByTestId('notice')).toContainText('reset link is on its way');
});

When('the USER RESETS the password to {string} with the RESET TOKEN from the link',
  async function (newPassword) {
    const token = await resetTokenFor(this, credentials.email);
    await this.page.goto(`${UI}/?reset=${encodeURIComponent(token)}`);
    await typeNewPassword(this, newPassword);
  });

When('the USER RESETS the password to {string} with a garbage RESET TOKEN',
  async function (newPassword) {
    await this.page.goto(`${UI}/?reset=garbage-token`);
    await typeNewPassword(this, newPassword);
  });

// shared by reset-password and change-password: prove a password from the sign-in screen,
// whatever screen the scenario left off on
async function backToSignIn(world) {
  if (await world.page.getByTestId('sign-out').isVisible()) {
    await world.page.getByTestId('sign-out').click();
  }
  await world.page.getByTestId('tab-signin').click();
}

Then('the USER can AUTHENTICATE with {string}', async function (password) {
  await backToSignIn(this);
  await this.page.getByTestId('email').fill(credentials.email);
  await this.page.getByTestId('password').fill(password);
  await this.page.getByTestId('submit').click();
  await expect(this.page.getByTestId('signed-in-email')).toHaveText(credentials.email);
  await this.page.getByTestId('sign-out').click();
});

Then('the USER cannot AUTHENTICATE with {string}', async function (password) {
  await backToSignIn(this);
  await this.page.getByTestId('email').fill(credentials.email);
  await this.page.getByTestId('password').fill(password);
  await this.page.getByTestId('submit').click();
  await expect(this.page.getByTestId('notice')).toContainText('Wrong e-mail or password');
});

Then('the password RESET is rejected', async function () {
  await expect(this.page.getByTestId('notice')).toContainText('already used or has expired');
});
