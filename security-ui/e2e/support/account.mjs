// The scenario's account, shared by every glue module (one source of truth — no per-module
// shadowing). Accounts persist across scenarios (isolation is by time, not restarts), so the
// seeding steps mint a SCENARIO-UNIQUE address from the Gherkin one (user@example.com ->
// user7@example.com): a feature that mutates the account (password change/reset, factor
// enrolment) can no longer poison another scenario's sign-in.

export const credentials = { email: '', password: '' };

let scenarioCounter = 0;

/** A fresh, this-scenario-only variant of the Gherkin address; remembers it as THE account. */
export function uniqueAccount(email, password) {
  scenarioCounter += 1;
  credentials.email = email.replace('@', `${scenarioCounter}@`);
  credentials.password = password;
  return credentials;
}
