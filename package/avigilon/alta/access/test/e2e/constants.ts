/**
 * Test constants for Avigilon Alta Access e2e suite.
 *
 * Values resolved from slot environment — tests skip gracefully when
 * required values (email/password/orgId) are missing.
 */

export const AVIGILON_EMAIL = process.env.AVIGILON_EMAIL ?? '';
export const AVIGILON_PASSWORD = process.env.AVIGILON_PASSWORD ?? '';
export const AVIGILON_TOTP_CODE = process.env.AVIGILON_TOTP_CODE ?? '';
export const ORGANIZATION_ID = process.env.AVIGILON_ORG_ID ?? '';

export function hasCredentials(): boolean {
  return !!(AVIGILON_EMAIL && AVIGILON_PASSWORD && ORGANIZATION_ID);
}
