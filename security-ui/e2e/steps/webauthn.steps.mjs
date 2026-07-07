// UI glue for mfa-webauthn.feature: a real passkey through the browser, using Chromium's virtual
// authenticator (CDP WebAuthn). The UI does the WebAuthn dance; the test only stands up a fake
// authenticator, enrols, and signs in — exactly what a user with a platform passkey would do.

import { Given, Then } from '@cucumber/cucumber';
import { expect } from 'playwright/test';
import { credentials } from '../support/account.mjs';

Given('the USER has a virtual authenticator', async function () {
  const client = await this.page.context().newCDPSession(this.page);
  await client.send('WebAuthn.enable');
  await client.send('WebAuthn.addVirtualAuthenticator', {
    options: {
      protocol: 'ctap2',
      transport: 'internal',
      hasResidentKey: true,          // a discoverable credential — sign-in needs no allowCredentials
      hasUserVerification: true,
      isUserVerified: true,          // the authenticator reports UV without a real gesture
      automaticPresenceSimulation: true,
    },
  });
});

Given('the USER has ENROLLED a PASSKEY', async function () {
  // sign in (no factors yet, so the password is enough), then add the passkey from the account page
  await this.page.getByTestId('tab-signin').click();
  await this.page.getByTestId('email').fill(credentials.email);
  await this.page.getByTestId('password').fill(credentials.password);
  await this.page.getByTestId('submit').click();
  await expect(this.page.getByTestId('signed-in-email')).toHaveText(credentials.email);

  await this.page.getByTestId('add-WEBAUTHN').click();
  // the UI creates the credential and confirms in one gesture — wait for the passkey to appear
  await expect(this.page.getByTestId('factor-list').getByText('passkey')).toBeVisible();
  await this.page.getByTestId('sign-out').click();
});

// "the USER AUTHENTICATES with the correct password" is already defined in mfa.steps.mjs — reuse it.

Then('the USER is signed in by the PASSKEY', async function () {
  // the passkey step runs automatically: the mfa screen shows the passkey prompt, then the
  // authenticator signs the challenge and the session lands — no code was ever typed
  await expect(this.page.getByTestId('signed-in-email')).toHaveText(credentials.email);
  await this.page.getByTestId('sign-out').click();
});
