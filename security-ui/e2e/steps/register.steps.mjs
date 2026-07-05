// UI glue for register.feature — the same sentences the application- and HTTP-level runners
// implement, expressed as what a person sees: the inbox screen, or the validation notice.

import { Given, Then, When } from '@cucumber/cucumber';
import { expect } from 'playwright/test';

async function registerThroughUi(page, email, password) {
  await page.getByTestId('tab-signup').click();
  await page.getByTestId('email').fill(email);
  await page.getByTestId('password').fill(password);
  await page.getByTestId('submit').click();
}

Given('the EMAIL {string} is already REGISTERED', async function (email) {
  await registerThroughUi(this.page, email, 'StrongPassword1!');
  // fresh and taken addresses land on the same screen by design; either way the address is in play
  await expect(this.page.getByTestId('inbox-screen')).toBeVisible();
  await this.page.getByTestId('back-to-signin').click();
});

When('the USER REGISTERS with EMAIL {string} and password {string}', async function (email, password) {
  await registerThroughUi(this.page, email, password);
});

Then('the USER is REGISTERED', async function () {
  await expect(this.page.getByTestId('inbox-screen')).toBeVisible();
});

Then('REGISTRATION is rejected', async function () {
  await expect(this.page.getByTestId('validation-errors')).toBeVisible();
});

Then('REGISTRATION is quietly refused, indistinguishable from a fresh one', async function () {
  // what the HTTP glue proves byte-for-byte, the UI proves screen-for-screen
  await expect(this.page.getByTestId('inbox-screen')).toBeVisible();
  await expect(this.page.getByTestId('validation-errors')).toHaveCount(0);
});

Then('the EMAIL is flagged as {word}', async function (flag) {
  await assertFlagged(this.page, 'email-errors', flag);
});

Then('the password is flagged as {word}', async function (flag) {
  await assertFlagged(this.page, 'password-errors', flag);
});

async function assertFlagged(page, listTestId, flag) {
  const items = page.getByTestId(listTestId).locator('li');
  if (flag === 'invalid') {
    await expect(items.first()).toBeVisible();
  } else if (flag === 'accepted') {
    await expect(items).toHaveCount(0);
  } else {
    throw new Error(`Unknown flag: ${flag}`);
  }
}
