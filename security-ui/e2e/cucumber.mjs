// The third entry point's runner: the SAME Gherkin the JVM runners select, driven through the
// React UI by Playwright. Feature files come straight from ../specs — never copied.
export default {
  paths: ['../specs/register.feature', '../specs/authenticate.feature'],
  import: ['e2e/support/world.mjs', 'e2e/steps/*.mjs'],
  format: ['progress'],
  publishQuiet: true,
};
