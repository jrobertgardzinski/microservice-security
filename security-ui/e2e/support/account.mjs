// The scenario's account, shared by every glue module (one source of truth — no per-module
// shadowing). Accounts persist across scenarios (isolation is by time, not restarts), so the
// seeding steps mint a SCENARIO-UNIQUE address from the Gherkin one (user@example.com ->
// user7@example.com): a feature that mutates the account (password change/reset, factor
// enrolment) can no longer poison another scenario's sign-in.

export const credentials = { email: '', password: '' };

let scenarioCounter = 0;
let baseEmail = '';

/** A fresh, this-scenario-only variant of the Gherkin address; remembers it as THE account. */
export function uniqueAccount(email, password) {
  scenarioCounter += 1;
  baseEmail = email;
  credentials.email = email.replace('@', `${scenarioCounter}@`);
  credentials.password = password;
  return credentials;
}

/** Maps a Gherkin address to the live one: the Background's address means THIS scenario's account. */
export function resolveEmail(email) {
  return email === baseEmail ? credentials.email : email;
}
