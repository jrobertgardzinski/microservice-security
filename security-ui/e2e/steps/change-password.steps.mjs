// UI glue for change-password.feature: sign in, prove the current password in the account
// screen's change form, and show the outcome the way a user meets it. The can/cannot
// AUTHENTICATE proofs are the shared ones from the reset glue.

import { Given, Then, When } from '@cucumber/cucumber';
import { expect } from 'playwright/test';
import { signInCompletingMfa } from './mfa.steps.mjs';

Given('the USER has AUTHENTICATED', async function () {
  await signInCompletingMfa(this);
});

When('the USER CHANGES the password from {string} to {string}', async function (current, next) {
  await this.page.getByTestId('current-password').fill(current);
  await this.page.getByTestId('new-password').fill(next);
  await this.page.getByTestId('change-password-submit').click();
  // wait for the outcome notice — leaving the screen mid-flight would race the change itself
  await expect(this.page.getByTestId('notice')).toBeVisible();
});

Then('the password CHANGE is rejected', async function () {
  await expect(this.page.getByTestId('notice')).toContainText('Wrong current password');
});
