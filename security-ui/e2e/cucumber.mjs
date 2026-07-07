// The third entry point's runner: the SAME Gherkin the JVM runners select, driven through the
// React UI by Playwright. Feature files come straight from ../specs — never copied. Selection is
// by tag, not by list: @ui features run here; @http-only marks the wire-level features (cookie
// rotation, introspection, the OAuth dance) that browsers do implicitly and the JVM runners own.
export default {
  paths: ['../specs/*.feature'],
  tags: '@ui and not @http-only',
  import: ['e2e/support/world.mjs', 'e2e/steps/*.mjs'],
  format: ['progress'],
  publishQuiet: true,
};
