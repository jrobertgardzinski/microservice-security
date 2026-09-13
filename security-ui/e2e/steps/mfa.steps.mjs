// UI glue for mfa.feature: the e-mail factor is enrolled through the factors manager, the mailed
// codes are read from the test mailbox (/test/mailbox/signin-code — the out-of-process twin of
// the capturing channel bean), and recovery codes are read from the ONE place they ever appear:
// the page, right after generation.
//
// Accounts persist across scenarios (isolation is by time, not restarts), so signing in may meet
// the factor step already — the helper completes it with the mailed code, exactly like a user.

import { Given, Then, When } from '@cucumber/cucumber';
import { expect } from 'playwright/test';
import { credentials, uniqueAccount } from '../support/account.mjs';

/**
 * Adding a factor now sits behind a step-up (P18 poz. 2): a merely-live session must not be able to
 * rewrite how an account signs in. The UI answers a 403 by asking for proof rather than by failing,
 * so the glue does what the person does — types the password, and the mailed code if a factor is
 * already enrolled. Silent when no proof is asked for, so the same call fits every enrolment.
 */
async function proveForEnrol(world, proceeded) {
  const prompt = world.page.getByTestId('enrol-stepup');
  // Which of the two things happened — the server asked for proof, or it did the thing — is
  // decided by WAITING FOR EITHER, never by a timeout. isVisible() answers about this instant and
  // the 403 that summons this prompt is a round trip away, so an instantaneous check read "no
  // prompt" and the caller then waited for a screen that was never coming. The first repair waited
  // five seconds for the prompt and read the timeout as "no proof was asked for" — which is a guess
  // wearing a stopwatch: it says the same thing about a service that is merely slow, it costs five
  // seconds on every enrolment that needs no proof, and on a loaded CI box it turns a real prompt
  // into a silent skip. The caller knows what appears when no proof is demanded; it passes that in.
  await expect(prompt.or(proceeded)).toBeVisible();
  if (!(await prompt.isVisible())) {
    return;   // the action went through — nothing was asked for, and we KNOW that
  }
  // The panel asks the server what it wants before asking the person (UI-14), so an account that
  // already carries a factor opens on the FACTOR half and there is no password to type: the
  // server would not have checked one. Wait for whichever half is on screen.
  const passwordInput = world.page.getByTestId('enrol-stepup-password');
  const codeHalf = world.page.getByTestId('enrol-stepup-code');
  const passkeyHalf = world.page.getByTestId('enrol-stepup-passkey');
  await expect(passwordInput.or(codeHalf).or(passkeyHalf)).toBeVisible();
  if (await passwordInput.isVisible()) {
    await passwordInput.fill(credentials.password);
    await world.page.getByTestId('enrol-stepup-submit').click();
  }
  // the same trap one level down, and this copy of it was still live: an account that already
  // carries a factor is asked for a code, and that answer is another round trip away — but
  // isVisible() takes no timeout, it answers about THIS INSTANT and the option is silently
  // ignored. So the code input, which appears a re-render after the 202, was never seen: the
  // helper skipped the factor half and then waited for a panel that could not close.
  // Nothing caught it for months, because every OTHER scenario's account elevates on the
  // password alone — /account/step-up/factor was not called once in the whole suite.
  // the same either/or: the password alone elevated (the prompt closes and the action goes
  // through), or a factor is owed and its input is on screen
  await expect(codeHalf.or(passkeyHalf).or(proceeded)).toBeVisible();
  if (await codeHalf.isVisible()) {
    await codeHalf.fill(await mailedSignInCode(world));
    await world.page.getByTestId('enrol-stepup-code-submit').click();
  }
  await expect(prompt).toBeHidden();
}

let recoveryCodes = [];
let spentRecoveryCode = '';

export { proveForEnrol };

async function signInPasswordStep(world) {
  await world.page.getByTestId('tab-signin').click();
  await world.page.getByTestId('email').fill(credentials.email);
  await world.page.getByTestId('password').fill(credentials.password);
  await world.page.getByTestId('submit').click();
}

async function mailedSignInCode(world) {
  const response = await world.backdoor(
    `/test/mailbox/signin-code?email=${encodeURIComponent(credentials.email)}`);
  if (!response.ok) throw new Error(`no sign-in code captured for ${credentials.email}`);
  return (await response.json()).code;
}

/** Reach the signed-in home whatever the account has enrolled by now (shared across glue). */
export async function signInCompletingMfa(world) {
  await signInPasswordStep(world);
  const factorStep = world.page.getByTestId('mfa-screen');
  const home = world.page.getByTestId('signed-in-email');
  await expect(factorStep.or(home)).toBeVisible();
  if (await factorStep.isVisible()) {
    await world.page.getByTestId('mfa-code').fill(await mailedSignInCode(world));
    await world.page.getByTestId('mfa-submit').click();
  }
  await expect(home).toHaveText(credentials.email);
}

async function signOut(world) {
  await world.page.getByTestId('sign-out').click();
}

Given('a verified USER {string} with password {string}', async function (email, password) {
  uniqueAccount(email, password);
  await this.page.getByTestId('tab-signup').click();
  await this.page.getByTestId('email').fill(credentials.email);
  await this.page.getByTestId('password').fill(password);
  await this.page.getByTestId('submit').click();
  await expect(this.page.getByTestId('inbox-screen')).toBeVisible();
  await this.page.getByTestId('back-to-signin').click();
  // a repeat scenario re-registers the same address: the quiet anti-enumeration register absorbs
  // it and no fresh token is mailed — the address is already verified, which is all this step wants
  const token = await this.verificationTokenFor(credentials.email);
  if (token) {
    await this.backdoor('/verify-email', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ token }),
    });
  }
});

Given('the USER has enrolled the e-mail FACTOR', async function () {
  await signInCompletingMfa(this);
  // the factor list loads asynchronously: wait until the page shows either the "add" offer or
  // the already-enrolled entry (a repeat scenario) before deciding which world this is
  const addButton = this.page.getByTestId('add-EMAIL_CODE');
  const enrolledEntry = this.page.getByTestId('factor-list').getByText('e-mail code');
  await expect(addButton.or(enrolledEntry)).toBeVisible();
  if (await addButton.isVisible()) {
    await addButton.click();
    await proveForEnrol(this, this.page.getByTestId('enroll-code'));
    // the code input appears only once the server answered 202 — i.e. the code is really out;
    // reading the mailbox any earlier races the enrolment start and finds a stale code
    const codeInput = this.page.getByTestId('enroll-code');
    await expect(codeInput).toBeVisible();
    await codeInput.fill(await mailedSignInCode(this));
    await this.page.getByTestId('enroll-submit').click();
    await expect(enrolledEntry).toBeVisible();
  }
  await signOut(this);
});

Given('the USER has AUTHENTICATED the password step', async function () {
  await signInPasswordStep(this);
  await expect(this.page.getByTestId('mfa-screen')).toBeVisible();
});

When('the USER AUTHENTICATES with the correct password', async function () {
  await signInPasswordStep(this);
});

When('the USER submits the mailed CODE', async function () {
  await this.page.getByTestId('mfa-code').fill(await mailedSignInCode(this));
  await this.page.getByTestId('mfa-submit').click();
});

When('the USER submits a wrong CODE', async function () {
  await this.page.getByTestId('mfa-code').fill('000000');
  await this.page.getByTestId('mfa-submit').click();
});

Given('the USER has GENERATED RECOVERY CODES', async function () {
  await signInCompletingMfa(this);
  await this.page.getByTestId('generate-recovery').click();
  // minting codes that bypass the whole factor chain is behind the same step-up door as adding a
  // factor. The UI asks for that proof now; before it did, this click answered 403 in silence and
  // the codes never appeared — which is what had this suite red every night since 2026-07-30
  await proveForEnrol(this, this.page.getByTestId('recovery-codes'));
  // the page is the only place the plain codes ever exist — harvest them like a user would
  await expect(this.page.getByTestId('recovery-codes')).toBeVisible();
  recoveryCodes = await this.page.getByTestId('recovery-codes').locator('li').allTextContents();
  if (recoveryCodes.length === 0) throw new Error('no recovery codes appeared on the page');
  await signOut(this);
});

When('the USER submits a RECOVERY CODE instead of the mailed CODE', async function () {
  spentRecoveryCode = recoveryCodes[0];
  await this.page.getByTestId('mfa-code').fill(spentRecoveryCode);
  await this.page.getByTestId('mfa-submit').click();
});

Given('the USER has signed in with a RECOVERY CODE once already', async function () {
  await signInPasswordStep(this);
  await expect(this.page.getByTestId('mfa-screen')).toBeVisible();
  spentRecoveryCode = recoveryCodes[0];
  await this.page.getByTestId('mfa-code').fill(spentRecoveryCode);
  await this.page.getByTestId('mfa-submit').click();
  await expect(this.page.getByTestId('signed-in-email')).toHaveText(credentials.email);
  await signOut(this);
});

When('the USER submits the same RECOVERY CODE again', async function () {
  await this.page.getByTestId('mfa-code').fill(spentRecoveryCode);
  await this.page.getByTestId('mfa-submit').click();
});

Then('a FACTOR CODE is required, not a session', async function () {
  await expect(this.page.getByTestId('mfa-screen')).toBeVisible();
});

Then('the USER is signed in', async function () {
  await expect(this.page.getByTestId('signed-in-email')).toHaveText(credentials.email);
  await signOut(this);
});

Then('the sign-in is refused and a session is not issued', async function () {
  await expect(this.page.getByTestId('notice')).toContainText('Wrong code');
  await expect(this.page.getByTestId('mfa-screen')).toBeVisible();
});
