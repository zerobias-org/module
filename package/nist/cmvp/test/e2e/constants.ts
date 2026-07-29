/**
 * Test constants — resolved from slot environment.
 * CMVP has no authentication, so there's nothing to skip on — these are just
 * stable, real values against the live public registry.
 */

// A long-lived, active FIPS 140-3 certificate used as a known-good fixture
// (also the sample certificate referenced throughout the connector's design docs).
export const TEST_CERTIFICATE_NUMBER = process.env.CMVP_TEST_CERTIFICATE_NUMBER ?? '5425';
