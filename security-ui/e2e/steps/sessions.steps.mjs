// UI glue for list-sessions.feature and revoke-all-sessions.feature. Sessions are minted by
// real sign-ins; the harness records each /authenticate response straight off the browser's
// wire (access token from the body, refresh token from the Set-Cookie header the cross-origin
// page itself never sees) so the revoke-all proofs can show those very tokens dead afterwards.

import { Given, Then, When } from '@cucumber/cucumber';
import { expect } from 'playwright/test';
import { SECURITY } from '../support/world.mjs';
import { credentials } from '../support/account.mjs';

let minted = [];   // [{ accessToken, refreshToken }] in sign-in order, this scenario

async function signInRecordingTokens(world) {
  const responsePromise = world.page.waitForResponse(
    (r) => r.url().endsWith('/authenticate') && r.status() === 200);
  await world.page.getByTestId('tab-signin').click();
  await world.page.getByTestId('email').fill(credentials.email);
  await world.page.getByTestId('password').fill(credentials.password);
  await world.page.getByTestId('submit').click();
  const response = await responsePromise;
  const { accessToken } = await response.json();
  const setCookie = (await response.allHeaders())['set-cookie'] ?? '';
  const refreshToken = /refresh_token=([^;]+)/.exec(setCookie)?.[1] ?? '';
  minted.push({ accessToken, refreshToken });
  await expect(world.page.getByTestId('signed-in-email')).toHaveText(credentials.email);
}

async function twoSessions(world) {
  minted = [];
  await signInRecordingTokens(world);
  await world.page.getByTestId('sign-out').click();   // client-side only here: the session survives
  await signInRecordingTokens(world);                 // stay signed in on the second one
}

Given('the USER has AUTHENTICATED twice', async function () {
  await twoSessions(this);
});

Given('the USER has two active sessions', async function () {
  await twoSessions(this);
});

When('the USER LISTS their active sessions', async function () {
  await expect(this.page.getByTestId('session-list')).toBeVisible();
});

Then('two active sessions are listed', async function () {
  await expect(this.page.getByTestId('session-row')).toHaveCount(2);
  await this.page.getByTestId('sign-out').click();
});

When('the USER REVOKES all sessions', async function () {
  await this.page.getByTestId('revoke-all').click();
  await expect(this.page.getByTestId('notice')).toContainText('Signed out everywhere');
});

Then('neither ACCESS TOKEN authorizes any longer', async function () {
  for (const { accessToken } of minted) {
    const r = await fetch(`${SECURITY}/me`, { headers: { Authorization: `Bearer ${accessToken}` } });
    if (r.status !== 401) throw new Error(`a revoked access token still authorizes: ${r.status}`);
  }
});

Then('neither REFRESH TOKEN can be REFRESHED', async function () {
  for (const { refreshToken } of minted) {
    if (!refreshToken) throw new Error('the harness failed to record a refresh token');
    const r = await fetch(`${SECURITY}/refresh`, {
      method: 'POST',
      headers: { Cookie: `refresh_token=${refreshToken}` },
    });
    if (r.status !== 401) throw new Error(`a revoked refresh token still refreshes: ${r.status}`);
  }
});
